package dev.customtrims.effect;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.trim.TrimSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Repeating task that draws trails and auras for everyone wearing a custom trim. */
public final class EffectTask extends BukkitRunnable {

    private static final double TAU = Math.PI * 2;

    private final CustomTrimsPlugin plugin;
    private final int interval;
    private final int refreshEvery;
    private final boolean onlyWhenMoving;
    private final boolean hideInvisible;
    private final boolean hideSneaking;

    private long tick;
    private long runs;

    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, TrimSettings> cache = new HashMap<>();
    private final Map<UUID, Integer> footsteps = new HashMap<>();

    public EffectTask(CustomTrimsPlugin plugin, int interval) {
        this.plugin = plugin;
        this.interval = interval;
        this.refreshEvery = Math.max(1, 10 / interval); // re-read armor about every half second
        this.onlyWhenMoving = plugin.getConfig().getBoolean("trail-only-when-moving", true);
        this.hideInvisible = plugin.getConfig().getBoolean("hide-when-invisible", true);
        this.hideSneaking = plugin.getConfig().getBoolean("hide-when-sneaking", false);
    }

    public void invalidate(UUID id) {
        cache.remove(id);
    }

    @Override
    public void run() {
        tick += interval;
        runs++;
        boolean refresh = runs % refreshEvery == 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (refresh || !cache.containsKey(id)) {
                cache.put(id, plugin.getTrimManager().getActive(p));
            }
            TrimSettings s = cache.get(id);

            Location now = p.getLocation();
            Location prev = lastLocation.put(id, now);
            if (s == null || s.style == EffectStyle.LIQUID || !visible(p)) continue;

            boolean moving = prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) > 0.0016;

            if (s.trailParticle != ParticleStyle.NONE && (moving || !onlyWhenMoving)) {
                drawTrail(p, s, now, prev, moving);
            }
            if (s.auraParticle != ParticleStyle.NONE && s.auraShape != AuraShape.NONE) {
                drawAura(p, s, now);
            }
        }

        if (runs % 300 == 0) {
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

    private double baseHue() {
        return (tick % 200) / 200.0;
    }

    private static Vector forward(Location l) {
        double r = Math.toRadians(l.getYaw());
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    private static Vector right(Location l) {
        double r = Math.toRadians(l.getYaw());
        return new Vector(-Math.cos(r), 0, -Math.sin(r));
    }

    private static void draw(Location loc, ParticleStyle st, TrimSettings s, int index, double hue,
                             int count, double spread, float size) {
        ParticleRenderer.draw(loc, st, s, index, hue, count, spread, size);
    }

    // =================================================================== TRAILS

    private void drawTrail(Player p, TrimSettings s, Location now, Location prev, boolean moving) {
        ParticleStyle st = s.trailParticle;
        int count = Math.max(1, (int) Math.round(2 * s.density));
        double hue = baseHue();
        Vector fwd = forward(now);
        Vector right = right(now);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        switch (s.trailShape) {
            case STREAM -> draw(now.clone().add(fwd.clone().multiply(-0.25)).add(0, 0.15, 0),
                    st, s, 0, hue, count, 0.07, 0.8f);

            case COMET -> {
                if (prev != null && moving) {
                    Vector d = now.toVector().subtract(prev.toVector());
                    int pts = (int) Math.min(14, Math.max(1, Math.ceil(d.length() / 0.12 * s.density)));
                    for (int i = 0; i < pts; i++) {
                        double f = (double) i / pts;
                        Location l = prev.clone().add(d.clone().multiply(f)).add(0, 0.2, 0);
                        draw(l, st, s, i, hue + f * 0.15, 1, 0.03, 1.0f);
                    }
                } else {
                    draw(now.clone().add(0, 0.2, 0), st, s, 0, hue, 1, 0.05, 1.0f);
                }
            }

            case DOUBLE -> {
                for (int side = -1; side <= 1; side += 2) {
                    Location l = now.clone()
                            .add(right.clone().multiply(0.18 * side))
                            .add(fwd.clone().multiply(-0.2))
                            .add(0, 0.08, 0);
                    draw(l, st, s, side > 0 ? 1 : 0, hue + (side > 0 ? 0.5 : 0), count, 0.02, 0.8f);
                }
            }

            case SPIRAL -> {
                for (int k = 0; k < 2; k++) {
                    double a = tick * 0.5 + k * Math.PI;
                    double y = 0.15 + ((tick * 0.05 + k * 0.5) % 1.0);
                    draw(now.clone().add(Math.cos(a) * 0.4, y, Math.sin(a) * 0.4), st, s, k, hue + k * 0.5, 1, 0, 0.9f);
                }
            }

            case SPARKS -> {
                for (int i = 0; i <= count; i++) {
                    Location l = now.clone().add(rnd.nextDouble(-0.3, 0.3), rnd.nextDouble(0.1, 1.6), rnd.nextDouble(-0.3, 0.3));
                    draw(l, st, s, i, hue + rnd.nextDouble(0.25), 1, 0, 0.7f);
                }
            }

            case CLOUD -> draw(now.clone().add(fwd.clone().multiply(-0.3)).add(0, 0.25, 0),
                    st, s, 0, hue, count * 3, 0.22, 1.2f);

            case FOOTSTEPS -> {
                if (moving && runs % Math.max(1, 6 / interval) == 0 && p.isOnGround()) {
                    int step = footsteps.merge(p.getUniqueId(), 1, Integer::sum);
                    int side = (step % 2 == 0) ? 1 : -1;
                    Location l = now.clone().add(right.clone().multiply(0.17 * side)).add(0, 0.04, 0);
                    draw(l, st, s, side > 0 ? 1 : 0, hue, 3, 0.04, 0.7f);
                }
            }
        }
    }

    // ==================================================================== AURAS

    private void drawAura(Player p, TrimSettings s, Location c) {
        ParticleStyle st = s.auraParticle;
        double r = 0.85 * s.size;
        int n = Math.max(4, (int) Math.round(18 * s.density));
        double t = tick;
        double hue = baseHue();
        double head = p.getEyeHeight() + 0.45; // top of head, lower while sneaking

        switch (s.auraShape) {
            case RING -> ring(c, st, s, 1.0, r, n, t * 0.06, hue);

            case DOUBLE_RING -> {
                ring(c, st, s, 0.3, r, n, t * 0.06, hue);
                ring(c, st, s, head - 0.35, r, n, -t * 0.06, hue + 0.5);
            }

            case HALO -> ring(c, st, s, head + 0.15, 0.32 * s.size,
                    Math.max(6, (int) Math.round(10 * s.density)), t * 0.1, hue);

            case HELIX -> helix(c, st, s, r, 1, t, hue);

            case DOUBLE_HELIX -> helix(c, st, s, r, 2, t, hue);

            case ORBIT -> {
                for (int k = 0; k < 3; k++) {
                    for (int j = 0; j < 4; j++) {
                        double tt = t - j;
                        double a = tt * 0.18 + k * TAU / 3;
                        double y = 1.0 + 0.55 * Math.sin(tt * 0.08 + k * 2.1);
                        draw(c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), st, s, k, hue + k / 3.0,
                                1, 0, Math.max(0.3f, 1.1f - j * 0.2f));
                    }
                }
            }

            case PULSE -> {
                double period = 24;
                for (int wave = 0; wave < 2; wave++) {
                    double ph = ((t + wave * period / 2) % period) / period;
                    double rr = 0.2 + ph * r * 1.8;
                    int m = Math.max(6, (int) Math.round(n * (0.5 + ph)));
                    ring(c, st, s, 0.1, rr, m, 0, hue + ph);
                }
            }

            case CROWN -> {
                double cr = 0.36 * s.size;
                double y = head + 0.05;
                double rot = t * 0.04;
                ring(c, st, s, y, cr, Math.max(8, (int) Math.round(12 * s.density)), rot, hue);
                for (int k = 0; k < 6; k++) {
                    double a = rot + k * TAU / 6;
                    for (int h = 1; h <= 3; h++) {
                        draw(c.clone().add(Math.cos(a) * cr, y + h * 0.09, Math.sin(a) * cr), st, s, k,
                                hue + k / 6.0, 1, 0, 0.8f);
                    }
                }
            }

            case TORNADO -> {
                int layers = 7;
                int perLayer = Math.max(2, (int) Math.round(3 * s.density));
                for (int i = 0; i < layers; i++) {
                    double f = (double) i / (layers - 1);
                    double y = f * 2.2;
                    double rr = 0.15 + f * r * 1.1;
                    for (int k = 0; k < perLayer; k++) {
                        double a = t * 0.25 + i * 0.7 + k * TAU / perLayer;
                        draw(c.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr), st, s, k, hue + f, 1, 0, 0.9f);
                    }
                }
            }

            case WINGS -> wings(p, c, st, s, t, hue);

            case FIREFLIES -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                int m = Math.max(2, (int) Math.round(4 * s.density));
                for (int i = 0; i < m; i++) {
                    double a = rnd.nextDouble(TAU);
                    double rr = rnd.nextDouble(0.3, Math.max(0.31, r * 1.4));
                    double y = rnd.nextDouble(0.1, 2.2);
                    draw(c.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr), st, s, i, rnd.nextDouble(), 1, 0, 0.7f);
                }
            }

            default -> {
            }
        }
    }

    private void ring(Location c, ParticleStyle st, TrimSettings s, double y, double r, int n, double rot, double hue) {
        for (int i = 0; i < n; i++) {
            double f = (double) i / n;
            double a = rot + f * TAU;
            draw(c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), st, s, i, hue + f, 1, 0, 1f);
        }
    }

    private void helix(Location c, ParticleStyle st, TrimSettings s, double r, int strands, double t, double hue) {
        int tail = Math.max(3, (int) Math.round(7 * s.density));
        for (int k = 0; k < strands; k++) {
            for (int j = 0; j < tail; j++) {
                double tt = t - j * 1.2;
                double a = tt * 0.22 + k * Math.PI;
                double y = 1.05 + Math.sin(tt * 0.05) * 1.0;
                draw(c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), st, s, k,
                        hue + j * 0.03 + k * 0.5, 1, 0, Math.max(0.3f, 1f - j * 0.08f));
            }
        }
    }

    private static final double[][] WING_TIPS = {
            {0.55, 2.25}, {0.95, 2.05}, {1.20, 1.75}, {1.20, 1.40}, {0.95, 1.05}
    };

    private void wings(Player p, Location c, ParticleStyle st, TrimSettings s, double t, double hue) {
        // wings are dense, so draw them a little less often
        if (runs % Math.max(1, 4 / interval) != 0) return;

        Vector fwd = forward(c);
        Vector right = right(c);
        double fold = 0.35 + 0.3 * Math.sin(t * 0.15);  // flapping
        double drop = p.isSneaking() ? -0.3 : 0;
        double rootX = 0.1;
        double rootY = 1.45 + drop;
        double step = 0.12 / Math.max(0.5, s.density);

        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < WING_TIPS.length; f++) {
                double[] tip = WING_TIPS[f];
                for (double u = 0; u <= 1.0001; u += step) {
                    double x = (rootX + (tip[0] - rootX) * u) * s.size;
                    double y = rootY + (tip[1] + drop - rootY) * u * s.size;
                    double lateral = x * Math.cos(fold);
                    double back = 0.28 + x * Math.sin(fold);
                    Location l = c.clone()
                            .add(right.clone().multiply(side * lateral))
                            .add(fwd.clone().multiply(-back))
                            .add(0, y, 0);
                    draw(l, st, s, f, hue + u * 0.3, 1, 0, 0.75f);
                }
            }
        }
    }
}
