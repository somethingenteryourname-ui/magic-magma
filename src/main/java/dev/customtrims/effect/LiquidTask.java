package dev.customtrims.effect;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.trim.TrimManager;
import dev.customtrims.trim.TrimSettings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws trails and auras as flowing liquid using item display entities with the
 * animated liquid models from the CustomTrims resource pack. Runs every tick.
 */
public final class LiquidTask extends BukkitRunnable {

    public static final String TAG = "customtrims_fx";
    private static final int MAX_PIECES = 600;
    private static final int RING_TINTS = 32;

    private final CustomTrimsPlugin plugin;
    private final boolean glow;
    private final boolean showOwn;
    private final boolean onlyWhenMoving;
    private final boolean hideInvisible;
    private final boolean hideSneaking;
    private final int colorFlowTicks;

    private long tick;
    private final Map<UUID, Aura> auras = new HashMap<>();
    private final Map<UUID, TrimSettings> cache = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Integer> footsteps = new HashMap<>();
    private final List<Piece> pieces = new ArrayList<>();

    public LiquidTask(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
        this.glow = plugin.getConfig().getBoolean("liquid.glow", true);
        this.showOwn = plugin.getConfig().getBoolean("liquid.show-own", true);
        this.onlyWhenMoving = plugin.getConfig().getBoolean("trail-only-when-moving", true);
        this.hideInvisible = plugin.getConfig().getBoolean("hide-when-invisible", true);
        this.hideSneaking = plugin.getConfig().getBoolean("hide-when-sneaking", false);
        this.colorFlowTicks = Math.max(1, plugin.getConfig().getInt("liquid.color-flow-ticks", 3));
    }

    public void invalidate(UUID id) {
        cache.remove(id);
    }

    /** Removes every liquid entity this plugin made, including leftovers from a crash or /reload. */
    public static void removeAllTagged() {
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getScoreboardTags().contains(TAG)) d.remove();
            }
        }
    }

    public void shutdown() {
        cancel();
        for (Aura a : auras.values()) a.remove();
        auras.clear();
        for (Piece p : pieces) p.entity.remove();
        pieces.clear();
    }

    // ===================================================================== tick

    @Override
    public void run() {
        tick++;
        boolean refresh = tick % 10 == 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (refresh || !cache.containsKey(id)) cache.put(id, plugin.getTrimManager().getActive(p));
            TrimSettings s = cache.get(id);

            Location now = p.getLocation();
            Location prev = lastLocation.put(id, now);

            boolean wantLiquid = s != null && s.style != EffectStyle.PARTICLES && visible(p);
            if (!wantLiquid) {
                Aura old = auras.remove(id);
                if (old != null) old.remove();
                continue;
            }

            // ---- aura
            if (s.auraShape == AuraShape.NONE || s.auraParticle == ParticleStyle.NONE) {
                Aura old = auras.remove(id);
                if (old != null) old.remove();
            } else {
                Aura aura = auras.get(id);
                String key = auraKey(s, now.getWorld());
                if (aura == null || !aura.key.equals(key) || !aura.valid()) {
                    if (aura != null) aura.remove();
                    aura = new Aura(key, p, s);
                    auras.put(id, aura);
                }
                aura.update(p, now);
            }

            // ---- trail
            if (s.trailParticle != ParticleStyle.NONE && tick % 2 == 0) {
                boolean moving = prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) > 0.0025;
                if (moving || !onlyWhenMoving) spawnTrail(p, s, now, prev, moving);
            }
        }

        // ---- trail pieces: start their animation, then remove them when done
        Iterator<Piece> it = pieces.iterator();
        while (it.hasNext()) {
            Piece piece = it.next();
            piece.age++;
            if (!piece.entity.isValid()) {
                it.remove();
                continue;
            }
            if (piece.age == 1) {
                animate(piece.entity, piece.end, piece.endTicks);
                if (piece.moveTo != null) {
                    piece.entity.setTeleportDuration(Math.min(59, piece.endTicks));
                    piece.entity.teleport(piece.moveTo);
                }
            }
            if (piece.end2 != null && piece.age == piece.end2At) animate(piece.entity, piece.end2, piece.end2Ticks);
            if (piece.age > piece.life) {
                piece.entity.remove();
                it.remove();
            }
        }

        if (tick % 200 == 0) {
            auras.entrySet().removeIf(e -> {
                if (Bukkit.getPlayer(e.getKey()) != null) return false;
                e.getValue().remove();
                return true;
            });
            cache.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
            lastLocation.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
            footsteps.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
        }
    }

    private boolean visible(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return false;
        if (plugin.isHidden(p.getUniqueId())) return false;
        if (hideInvisible && p.hasPotionEffect(PotionEffectType.INVISIBILITY)) return false;
        return !(hideSneaking && p.isSneaking());
    }

    private static String auraKey(TrimSettings s, World w) {
        StringBuilder sb = new StringBuilder(s.auraShape.name()).append('|').append(s.size).append('|')
                .append(s.density).append('|').append(w.getUID());
        for (Color c : s.colors) sb.append('|').append(c.asRGB());
        return sb.toString();
    }

    // ================================================================= helpers

    private static ItemStack liquidItem(String model, List<Color> colors) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(TrimManager.NAMESPACE, model));
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setColors(colors);
            meta.setCustomModelDataComponent(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemDisplay spawnDisplay(Player owner, Location loc, ItemStack item, Transformation start,
                                     Display.Billboard billboard, int teleportTicks) {
        World w = loc.getWorld();
        if (w == null) return null;
        ItemDisplay d = w.spawn(loc, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(billboard);
            e.setTransformation(start);
            e.setShadowRadius(0f);
            e.setShadowStrength(0f);
            e.setTeleportDuration(teleportTicks);
            e.setViewRange(1.5f);
            if (glow) e.setBrightness(new Display.Brightness(15, 15));
        });
        if (!showOwn) owner.hideEntity(plugin, d);
        return d;
    }

    private static void animate(ItemDisplay d, Transformation to, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(to);
    }

    private static Transformation tf(float tx, float ty, float tz, Quaternionf rot, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(tx, ty, tz), rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private static Transformation tf(float sx, float sy, float sz) {
        return tf(0, 0, 0, new Quaternionf(), sx, sy, sz);
    }

    private static Vector forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    private static Vector right(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.cos(r), 0, -Math.sin(r));
    }

    /** The ring model has 32 color slots around it; this fills them with a smooth loop through the colors. */
    private static List<Color> ringColors(TrimSettings s, double offset, int dir) {
        List<Color> out = new ArrayList<>(RING_TINTS);
        for (int i = 0; i < RING_TINTS; i++) {
            double t = (i / (double) RING_TINTS - dir * offset) % 1.0;
            if (t < 0) t += 1;
            out.add(s.sample(t < 0.5 ? t * 2 : (1 - t) * 2));
        }
        return out;
    }

    private double flowHue() {
        double h = (tick % 120) / 120.0;
        return h < 0.5 ? h * 2 : (1 - h) * 2;   // 0 -> 1 -> 0, so colors flow back and forth smoothly
    }

    // =================================================================== auras

    /** One piece of an aura: a ring, wings or a floating blob. */
    private static final class Part {
        String model;
        double y;          // height above the feet
        float sx = 1, sy = 1, sz = 1;
        Quaternionf tilt = new Quaternionf();
        double spin;       // degrees per tick
        double yawOffset;
        boolean wings;
        boolean pulse;
        boolean firefly;
        double orbitRadius, orbitSpeed, phase;
        boolean headRelative;  // y is measured from the top of the head (follows sneaking)
        int flowDir = 1;       // which way the colors flow around a ring
        ItemDisplay entity;
    }

    private final class Aura {
        final String key;
        final TrimSettings settings;
        final List<Part> parts = new ArrayList<>();

        Aura(String key, Player p, TrimSettings s) {
            this.key = key;
            this.settings = s;
            float size = (float) s.size;
            List<Color> layered = List.of(s.sample(0), s.sample(0.5), s.sample(1));
            switch (s.auraShape) {
                case RING -> parts.add(ring(1.0, size, 1f, 0, 0, 0));
                case DOUBLE_RING -> {
                    parts.add(ring(0.35, size, 1f, 0, 0, 0));
                    Part top = ring(-0.35, size, 1f, 0, 0, 0);
                    top.headRelative = true;
                    top.flowDir = -1;
                    parts.add(top);
                }
                case HALO -> {
                    Part r = ring(0.05, 0.42f * size, 0.6f, 0, 0, 0);
                    r.headRelative = true;
                    parts.add(r);
                }
                case CROWN -> {
                    Part r = ring(0.0, 0.45f * size, 1.5f, 0, 0, 0);
                    r.headRelative = true;
                    parts.add(r);
                }
                case HELIX -> parts.add(ring(1.0, size, 1f, 4, 25, 0));
                case DOUBLE_HELIX -> {
                    parts.add(ring(1.0, size, 1f, 4, 30, 0));
                    parts.add(ring(1.0, size, 1f, 4, -30, 90));
                }
                case ORBIT -> {
                    for (int i = 0; i < 3; i++) parts.add(ring(1.0, 0.95f * size, 0.8f, 2.5, 65, i * 60));
                }
                case PULSE -> {
                    Part r = ring(0.1, size, 1f, 2, 0, 0);
                    r.pulse = true;
                    parts.add(r);
                }
                case TORNADO -> {
                    double[] ys = {0.15, 0.7, 1.25, 1.8};
                    float[] sc = {0.35f, 0.6f, 0.85f, 1.1f};
                    for (int i = 0; i < 4; i++) {
                        Part r = ring(ys[i], sc[i] * size, 0.8f, 0, 0, 0);
                        r.flowDir = i % 2 == 0 ? 1 : -1;
                        parts.add(r);
                    }
                }
                case WINGS -> {
                    Part wpart = new Part();
                    wpart.model = "liquid_wings";
                    wpart.wings = true;
                    wpart.y = 0.75;
                    wpart.sx = wpart.sy = wpart.sz = size;
                    parts.add(wpart);
                }
                case FIREFLIES -> {
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    int n = Math.max(3, (int) Math.round(5 * s.density));
                    for (int i = 0; i < n; i++) {
                        Part f = new Part();
                        f.model = "liquid_blob";
                        f.firefly = true;
                        f.y = rnd.nextDouble(0.3, 2.0);
                        f.orbitRadius = rnd.nextDouble(0.6, 1.1) * size;
                        f.orbitSpeed = rnd.nextDouble(1.5, 3.5) * (rnd.nextBoolean() ? 1 : -1);
                        f.phase = rnd.nextDouble(360);
                        float bs = (float) rnd.nextDouble(0.35, 0.6);
                        f.sx = f.sy = f.sz = bs;
                        parts.add(f);
                    }
                }
                default -> {
                }
            }

            Location base = p.getLocation();
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                List<Color> colors;
                if (part.firefly) colors = List.of(s.sample(parts.size() == 1 ? 0 : (double) i / (parts.size() - 1)));
                else if (part.wings) colors = layered;
                else colors = ringColors(s, 0, part.flowDir);
                Transformation start = tf(0, 0, 0, part.tilt, part.sx, part.sy, part.sz);
                Display.Billboard bb = part.firefly ? Display.Billboard.CENTER : Display.Billboard.FIXED;
                part.entity = spawnDisplay(p, locationFor(part, base, p), liquidItem(part.model, colors), start, bb, 2);
            }
        }

        private Part ring(double y, float scale, float height, double spin, float tiltDeg, double yawOffset) {
            Part r = new Part();
            r.model = "liquid_ring";
            r.y = y;
            r.sx = scale;
            r.sz = scale;
            r.sy = height;
            r.spin = spin;
            r.yawOffset = yawOffset;
            if (tiltDeg != 0) r.tilt = new Quaternionf().rotationX((float) Math.toRadians(tiltDeg));
            return r;
        }

        private Location locationFor(Part part, Location base, Player p) {
            Location l = base.clone();
            double y = part.headRelative ? p.getEyeHeight() + 0.42 + part.y : part.y;
            if (part.wings) {
                Vector back = forward(base.getYaw()).multiply(-0.32);
                l.add(back).add(0, y, 0);
                l.setYaw(base.getYaw());
            } else if (part.firefly) {
                double a = Math.toRadians(part.phase + tick * part.orbitSpeed);
                double bob = Math.sin(Math.toRadians(part.phase * 2 + tick * 4)) * 0.15;
                l.add(Math.cos(a) * part.orbitRadius, y + bob, Math.sin(a) * part.orbitRadius);
                l.setYaw(0);
            } else {
                l.add(0, y, 0);
                l.setYaw((float) ((part.yawOffset + tick * part.spin) % 360));
            }
            l.setPitch(0);
            return l;
        }

        void update(Player p, Location base) {
            boolean flow = settings.colors.size() > 1 && tick % colorFlowTicks == 0;
            for (Part part : parts) {
                if (part.entity == null) continue;
                part.entity.teleport(locationFor(part, base, p));

                // colors flow around the ring like a current
                if (flow && "liquid_ring".equals(part.model)) {
                    double offset = (tick / colorFlowTicks) / (double) RING_TINTS;
                    part.entity.setItemStack(liquidItem("liquid_ring", ringColors(settings, offset, part.flowDir)));
                }

                if (part.pulse) {
                    long phase = tick % 26;
                    if (phase == 0) {
                        animate(part.entity, tf(0, 0, 0, part.tilt, 0.25f * part.sx, 1.2f, 0.25f * part.sz), 0);
                    } else if (phase == 1) {
                        animate(part.entity, tf(0, 0, 0, part.tilt, 1.9f * part.sx, 0.15f, 1.9f * part.sz), 23);
                    }
                } else if (part.wings && tick % 12 == 0) {
                    // gentle flap: wings spread and fold
                    float spread = (tick / 12) % 2 == 0 ? 1.0f : 0.82f;
                    animate(part.entity, tf(0, 0, 0, part.tilt, part.sx * spread, part.sy, part.sz), 12);
                }
            }
        }

        boolean valid() {
            for (Part part : parts) if (part.entity == null || !part.entity.isValid()) return false;
            return true;
        }

        void remove() {
            for (Part part : parts) if (part.entity != null) part.entity.remove();
            parts.clear();
        }
    }

    // ================================================================== trails

    private static final class Piece {
        ItemDisplay entity;
        int age;
        int life;
        Transformation end;
        int endTicks;
        Transformation end2;
        int end2At;
        int end2Ticks;
        Location moveTo;   // optional: glide here (droplets falling / rising)
    }

    private void addPiece(ItemDisplay d, int life, Transformation end, int endTicks) {
        addPiece(d, life, end, endTicks, null);
    }

    private void addPiece(ItemDisplay d, int life, Transformation end, int endTicks, Location moveTo) {
        if (d == null) return;
        Piece piece = new Piece();
        piece.moveTo = moveTo;
        piece.entity = d;
        piece.life = life;
        piece.end = end;
        piece.endTicks = endTicks;
        pieces.add(piece);
    }

    private void spawnTrail(Player p, TrimSettings s, Location now, Location prev, boolean moving) {
        if (pieces.size() >= MAX_PIECES) return;
        float size = (float) s.size;
        Color color = s.sample(flowHue());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Vector move = (prev != null && moving) ? now.toVector().subtract(prev.toVector()).setY(0) : forward(now.getYaw()).multiply(0.1);
        double dist = move.length();
        float moveYaw = (float) Math.toDegrees(Math.atan2(-move.getX(), move.getZ()));

        switch (s.trailShape) {
            case STREAM, COMET -> {
                Location l = (prev != null && moving ? prev.clone().add(move.clone().multiply(0.5)) : now.clone()).add(0, 0.04, 0);
                l.setYaw(moveYaw);
                l.setPitch(0);
                float len = (float) Math.max(0.7, Math.min(2.6, dist * 5)) * size;
                float width = (s.trailShape == TrailShape.COMET ? 1.0f : 0.75f) * size;
                ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_streak", List.of(color)),
                        tf(width, 1, len), Display.Billboard.FIXED, 0);
                addPiece(d, 20, tf(0, -0.02f, 0, new Quaternionf(), 0.08f * width, 1, len * 1.35f), 18);
            }
            case DOUBLE -> {
                Vector right = right(now.getYaw());
                for (int side = -1; side <= 1; side += 2) {
                    Location l = (prev != null && moving ? prev.clone().add(move.clone().multiply(0.5)) : now.clone())
                            .add(right.clone().multiply(0.2 * side)).add(0, 0.04, 0);
                    l.setYaw(moveYaw);
                    l.setPitch(0);
                    float len = (float) Math.max(0.6, Math.min(2.2, dist * 5)) * size;
                    Color c = side < 0 ? s.sample(0) : s.sample(1);
                    ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_streak", List.of(c)),
                            tf(0.5f * size, 1, len), Display.Billboard.FIXED, 0);
                    addPiece(d, 18, tf(0, -0.02f, 0, new Quaternionf(), 0.05f, 1, len * 1.3f), 16);
                }
            }
            case SPARKS, CLOUD -> {
                int n = s.trailShape == TrailShape.CLOUD ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    Location l = now.clone().add(rnd.nextDouble(-0.3, 0.3), rnd.nextDouble(0.4, 1.4), rnd.nextDouble(-0.3, 0.3));
                    float bs = (float) rnd.nextDouble(0.25, s.trailShape == TrailShape.CLOUD ? 0.6 : 0.4) * size;
                    ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_blob", List.of(s.sample(rnd.nextDouble()))),
                            tf(bs, bs, bs), Display.Billboard.CENTER, 0);
                    // droplets fall and shrink, like splashes of liquid
                    Location fall = l.clone().add(rnd.nextDouble(-0.4, 0.4), -0.9, rnd.nextDouble(-0.4, 0.4));
                    addPiece(d, 14, tf(0.02f, 0.02f, 0.02f), 13, fall);
                }
            }
            case SPIRAL -> {
                for (int k = 0; k < 2; k++) {
                    double a = tick * 0.35 + k * Math.PI;
                    double y = 0.2 + ((tick * 0.03 + k * 0.5) % 1.0) * 1.3;
                    Location l = now.clone().add(Math.cos(a) * 0.5 * size, y, Math.sin(a) * 0.5 * size);
                    float bs = 0.32f * size;
                    ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_blob", List.of(s.sample(k == 0 ? 0 : 1))),
                            tf(bs, bs, bs), Display.Billboard.CENTER, 0);
                    addPiece(d, 16, tf(0.02f, 0.02f, 0.02f), 15, l.clone().add(0, 0.35, 0));
                }
            }
            case FOOTSTEPS -> {
                if (!moving || tick % 6 != 0 || !p.isOnGround()) return;
                int step = footsteps.merge(p.getUniqueId(), 1, Integer::sum);
                int side = step % 2 == 0 ? 1 : -1;
                Location l = now.clone().add(right(now.getYaw()).multiply(0.17 * side)).add(0, 0.03, 0);
                l.setYaw(moveYaw);
                l.setPitch(0);
                ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_puddle", List.of(color)),
                        tf(0.15f * size, 1, 0.15f * size), Display.Billboard.FIXED, 0);
                if (d == null) return;
                Piece piece = new Piece();
                piece.entity = d;
                piece.life = 34;
                piece.end = tf(0.75f * size, 1, 0.75f * size);   // splash spreads out
                piece.endTicks = 6;
                piece.end2 = tf(0.02f, 1, 0.02f);                  // then soaks away
                piece.end2At = 18;
                piece.end2Ticks = 14;
                pieces.add(piece);
            }
        }
    }
}
