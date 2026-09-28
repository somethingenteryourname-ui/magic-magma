package dev.magicnuke.nuke;

import dev.magicnuke.Keys;
import dev.magicnuke.MagicNuke;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Tracks placed nukes (persistent display entities) and nukes in flight. */
public final class NukeManager {

    public static final String ROLE_NUKE = "nuke";
    public static final String ROLE_HITBOX = "hitbox";
    public static final String ROLE_FLIGHT = "flight";
    public static final String ROLE_FX = "fx";
    public static final String ROLE_DEBRIS = "debris";

    private final MagicNuke plugin;
    private final Map<UUID, PlacedNuke> byBody = new HashMap<>();
    private final Map<UUID, PlacedNuke> byHitbox = new HashMap<>();
    private final Set<NukeFlight> flights = new java.util.HashSet<>();
    private BukkitTask task;

    public NukeManager(MagicNuke plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) track(e);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (NukeFlight f : new ArrayList<>(flights)) f.abort();
        flights.clear();
    }

    // ---------------------------------------------------------------- entities

    /** Called for every loaded entity: picks up placed nukes, cleans up leftovers. */
    public void track(Entity e) {
        String role = roleOf(e);
        if (role == null) return;
        switch (role) {
            case ROLE_NUKE -> {
                if (e instanceof ItemDisplay d && !byBody.containsKey(d.getUniqueId())) {
                    PersistentDataContainer pdc = d.getPersistentDataContainer();
                    NukeSize size = plugin.items().sizeOf(pdc.get(plugin.keys().size, PersistentDataType.STRING),
                            pdc.getOrDefault(plugin.keys().radius, PersistentDataType.DOUBLE, 16.0));
                    String link = pdc.get(plugin.keys().link, PersistentDataType.STRING);
                    UUID hitbox = null;
                    try {
                        if (link != null) hitbox = UUID.fromString(link);
                    } catch (IllegalArgumentException ignored) {
                    }
                    register(new PlacedNuke(d, hitbox, size));
                }
            }
            // effects and flying nukes never survive a restart
            case ROLE_FLIGHT, ROLE_FX, ROLE_DEBRIS -> {
                if (!isActiveEffect(e)) e.remove();
            }
            default -> {
            }
        }
    }

    public void untrack(Entity e) {
        PlacedNuke p = byBody.remove(e.getUniqueId());
        if (p != null && p.hitboxId() != null) byHitbox.remove(p.hitboxId());
    }

    private boolean isActiveEffect(Entity e) {
        // Effects created this session are tracked by their tasks; anything found while
        // loading chunks is a leftover from a crash or reload.
        return plugin.explosions().owns(e) || flights.stream().anyMatch(f -> f.owns(e));
    }

    public String roleOf(Entity e) {
        return e.getPersistentDataContainer().get(plugin.keys().role, PersistentDataType.STRING);
    }

    private void register(PlacedNuke p) {
        byBody.put(p.bodyId(), p);
        if (p.hitboxId() != null) byHitbox.put(p.hitboxId(), p);
    }

    private void unregister(PlacedNuke p) {
        byBody.remove(p.bodyId());
        if (p.hitboxId() != null) byHitbox.remove(p.hitboxId());
    }

    public PlacedNuke byHitbox(Entity e) {
        return e instanceof Interaction ? byHitbox.get(e.getUniqueId()) : null;
    }

    public PlacedNuke byAnyPart(Entity e) {
        PlacedNuke p = byHitbox.get(e.getUniqueId());
        return p != null ? p : byBody.get(e.getUniqueId());
    }

    public Collection<PlacedNuke> placed() {
        return new ArrayList<>(byBody.values());
    }

    public int flightCount() {
        return flights.size();
    }

    public List<PlacedNuke> near(Location loc, double radius) {
        List<PlacedNuke> out = new ArrayList<>();
        double r2 = radius * radius;
        for (PlacedNuke p : byBody.values()) {
            if (!p.body().isValid() || p.body().getWorld() != loc.getWorld()) continue;
            if (p.base().distanceSquared(loc) <= r2) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- actions

    /** Whether there is room to stand a nuke with its base at this block. */
    public boolean canPlaceAt(Block block, NukeSize size) {
        if (!block.isPassable() || block.isLiquid()) return false;
        Block below = block.getRelative(0, -1, 0);
        if (below.isPassable()) return false;
        Location center = block.getLocation().add(0.5, 0, 0.5);
        return near(center, Math.max(0.9, size.modelScale() * 0.9)).isEmpty();
    }

    public PlacedNuke place(Location base, NukeSize size) {
        World world = base.getWorld();
        float s = (float) size.modelScale();
        Location center = base.clone().add(0, size.halfHeight(), 0);
        center.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        center.setPitch(0);
        ItemDisplay body = world.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(plugin.items().displayStack());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(4f);
            d.setShadowRadius(0.45f * s);
            d.setShadowStrength(0.9f);
            d.setPersistent(true);
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            pdc.set(plugin.keys().role, PersistentDataType.STRING, ROLE_NUKE);
            pdc.set(plugin.keys().size, PersistentDataType.STRING, size.id());
            pdc.set(plugin.keys().radius, PersistentDataType.DOUBLE, size.radius());
        });
        Interaction hitbox = world.spawn(base.clone(), Interaction.class, i -> {
            i.setInteractionWidth((float) (NukeSize.MODEL_WIDTH * 0.8 * s));
            i.setInteractionHeight((float) size.height());
            i.setResponsive(true);
            i.setPersistent(true);
            PersistentDataContainer pdc = i.getPersistentDataContainer();
            pdc.set(plugin.keys().role, PersistentDataType.STRING, ROLE_HITBOX);
            pdc.set(plugin.keys().link, PersistentDataType.STRING, body.getUniqueId().toString());
        });
        body.getPersistentDataContainer().set(plugin.keys().link, PersistentDataType.STRING, hitbox.getUniqueId().toString());
        PlacedNuke p = new PlacedNuke(body, hitbox.getUniqueId(), size);
        register(p);

        world.playSound(base, Sound.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.8f, 0.6f);
        world.playSound(base, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 0.5f);
        world.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.2, 0), 20, 0.5 * s, 0.05, 0.5 * s, 0.02);
        return p;
    }

    /** Removes a placed nuke without launching it. */
    public void remove(PlacedNuke p) {
        unregister(p);
        Interaction hit = p.hitbox();
        if (hit != null) hit.remove();
        p.body().remove();
    }

    /** Lights a placed nuke: starts the countdown and launch. */
    public void ignite(PlacedNuke p, int fuseTicks) {
        if (!byBody.containsKey(p.bodyId())) return;
        unregister(p);
        Interaction hit = p.hitbox();
        if (hit != null) hit.remove();
        ItemDisplay body = p.body();
        body.setPersistent(false);
        body.getPersistentDataContainer().set(plugin.keys().role, PersistentDataType.STRING, ROLE_FLIGHT);
        Location base = p.base();
        NukeFlight flight = new NukeFlight(plugin, body, p.size(), base, fuseTicks);
        flights.add(flight);
        flight.start();
    }

    /** Spawns a nuke at a spot and lights it straight away. */
    public void launchAt(Location base, NukeSize size) {
        PlacedNuke p = place(base, size);
        ignite(p, size.fuseTicks());
    }

    /** A nuke that skips the launch and drops out of the sky onto a spot. */
    public void strike(Location target, NukeSize size) {
        World world = target.getWorld();
        float s = (float) size.modelScale();
        Location start = target.clone().add(0, Math.max(size.flightHeight(), 60), 0);
        ItemDisplay body = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(plugin.items().displayStack());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.PI),
                    new Vector3f(s, s, s), new Quaternionf()));
            d.setViewRange(8f);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(plugin.keys().role, PersistentDataType.STRING, ROLE_FLIGHT);
        });
        NukeFlight flight = NukeFlight.strike(plugin, body, size, target);
        flights.add(flight);
        flight.start();
    }

    void flightEnded(NukeFlight f) {
        flights.remove(f);
    }

    public boolean isDebris(Entity e) {
        return e instanceof FallingBlock && ROLE_DEBRIS.equals(roleOf(e));
    }

    // ---------------------------------------------------------------- tick

    private void tick() {
        for (PlacedNuke p : placed()) {
            ItemDisplay body = p.body();
            if (!body.isValid()) {
                if (body.isDead()) {
                    // killed by a command or another plugin
                    unregister(p);
                    Interaction hit = p.hitbox();
                    if (hit != null) hit.remove();
                }
                continue;
            }
            if (plugin.settings().allowRedstone) {
                Block b = p.base().getBlock();
                if (b.isBlockIndirectlyPowered() || b.getRelative(0, -1, 0).isBlockPowered()) {
                    ignite(p, p.size().fuseTicks());
                }
            }
        }
    }

    public static void tagFx(MagicNuke plugin, Entity e, String role) {
        e.getPersistentDataContainer().set(plugin.keys().role, PersistentDataType.STRING, role);
    }

    public Keys keys() {
        return plugin.keys();
    }
}
