package dev.magicnuke.fx;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.NukeConfig;
import dev.magicnuke.nuke.NukeManager;
import dev.magicnuke.nuke.NukeSize;
import dev.magicnuke.nuke.PlacedNuke;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runs detonations and keeps track of every effect task and entity they create. */
public final class ExplosionTracker {

    private static final Component FLASH = Component.text("")
            .font(Key.key("magicnuke", "flash")).color(NamedTextColor.WHITE);

    private final MagicNuke plugin;
    private final Set<BukkitRunnable> tasks = new HashSet<>();
    private final Set<UUID> entities = new HashSet<>();

    public ExplosionTracker(MagicNuke plugin) {
        this.plugin = plugin;
    }

    void track(BukkitRunnable task) {
        tasks.add(task);
    }

    void untrack(BukkitRunnable task) {
        tasks.remove(task);
    }

    void track(Entity e) {
        entities.add(e.getUniqueId());
    }

    public void untrack(Entity e) {
        entities.remove(e.getUniqueId());
    }

    public boolean owns(Entity e) {
        return entities.contains(e.getUniqueId());
    }

    public int activeCount() {
        return tasks.size();
    }

    public void shutdown() {
        for (BukkitRunnable t : new ArrayList<>(tasks)) {
            try {
                t.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        tasks.clear();
        for (UUID id : entities) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        entities.clear();
    }

    // ------------------------------------------------------------------ detonation

    public void detonate(Location impact, NukeSize size) {
        NukeConfig c = plugin.settings();
        World world = impact.getWorld();
        double r = size.radius();
        Location at = impact.clone();
        plugin.getLogger().info(String.format("%s (radius %.0f) detonated at %s %.0f %.0f %.0f.",
                dev.magicnuke.Msg.strip(size.name()), r, world.getName(), at.getX(), at.getY(), at.getZ()));

        fireball(at, r);
        flashAndSound(at, r);
        if (c.breakBlocks && c.debris) debris(at, r);
        if (c.breakBlocks) CraterCarver.start(plugin, this, at, r);
        start(new Shockwave(plugin, this, at, r), 0L);
        if (c.mushroomCloud) start(new MushroomCloud(plugin, this, at, r), 0L);
        if (c.fallout && c.falloutTicks > 0) start(new Fallout(plugin, this, at, r), 60L);

        if (c.chainReaction) {
            for (PlacedNuke p : plugin.nukes().near(at, r * 1.25)) {
                plugin.nukes().ignite(p, 10 + Fx.rnd().nextInt(40));
            }
        }
    }

    private void start(BukkitRunnable task, long delay) {
        track(task);
        task.runTaskTimer(plugin, delay, 1L);
    }

    private void fireball(Location at, double r) {
        World w = at.getWorld();
        Location up = at.clone().add(0, Math.min(6, r * 0.2), 0);
        Fx.flash(w, up, Color.WHITE);
        Fx.flash(w, up.clone().add(0, r * 0.3, 0), Color.fromRGB(255, 230, 170));
        Fx.burst(w, Particle.EXPLOSION_EMITTER, up, (int) Math.max(3, r / 3), r * 0.25, r * 0.15, r * 0.25, 0);
        Fx.burst(w, Particle.GUST_EMITTER_LARGE, at, 3, r * 0.2, 1, r * 0.2, 0);
        int n = (int) Math.min(400, 60 + r * 5);
        for (int i = 0; i < n; i++) {
            Vector dir = Vector.getRandom().subtract(new Vector(0.5, 0.2, 0.5)).normalize();
            Fx.shoot(w, i % 4 == 0 ? Particle.LAVA : Particle.FLAME, up, dir, Fx.r(0.4, 1.0) * Math.min(4, r / 10));
        }
        for (int i = 0; i < 6; i++) Fx.burst(w, Particle.SONIC_BOOM, up, 1, r * 0.3, r * 0.1, r * 0.3, 0);
    }

    private void flashAndSound(Location at, double r) {
        NukeConfig c = plugin.settings();
        double hearRange = Math.max(160, r * 12);
        for (Player p : Fx.playersNear(at, hearRange)) {
            double d = p.getLocation().distance(at);
            if (c.flash && d < Math.max(120, r * 10) && plugin.pack().hasPack(p)) {
                p.showTitle(Title.title(FLASH, Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(150), Duration.ofMillis(Math.round(1200 + 1600 * (1 - d / (r * 10)))))));
            }
            if (c.nausea && d < r * 1.5 && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 8, 0, false, false, true));
            }
            // sound travels at ~17 blocks per tick
            long delay = Math.min(160, Math.round(d / 17.0));
            boolean close = d < r * 2.5;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.getWorld() != at.getWorld()) return;
                Fx.distantSound(p, at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                Fx.distantSound(p, at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, close ? 1f : 0.7f, 0.5f);
                Fx.distantSound(p, at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 0.5f);
                if (close) {
                    Fx.distantSound(p, at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                    Fx.distantSound(p, at, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f);
                } else {
                    Fx.distantSound(p, at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f);
                }
            }, delay);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getWorld() == at.getWorld()) {
                    Fx.distantSound(p, at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.5f);
                    Fx.distantSound(p, at, Sound.AMBIENT_BASALT_DELTAS_LOOP, 1f, 0.5f);
                }
            }, delay + 25);
        }
    }

    /** Chunks of the ground flung out of the crater. They vanish in a puff when they land. */
    private void debris(Location at, double r) {
        World w = at.getWorld();
        int n = (int) Math.min(160, 12 + r * 2.5);
        for (int i = 0; i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            double dist = Fx.r(0, r * 0.7);
            int x = at.getBlockX() + (int) Math.round(Math.cos(a) * dist);
            int z = at.getBlockZ() + (int) Math.round(Math.sin(a) * dist);
            Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material m = top.getType();
            if (!m.isSolid() || m.getHardness() < 0 || Math.abs(top.getY() - at.getY()) > r) m = Material.COBBLED_DEEPSLATE;
            Location from = at.clone().add(Fx.r(-2, 2), 1 + Fx.r(0, 2), Fx.r(-2, 2));
            Material type = m;
            FallingBlock fb = w.spawn(from, FallingBlock.class, f -> {
                f.setBlockData(type.createBlockData());
                f.setDropItem(false);
                f.setCancelDrop(true);
                f.setHurtEntities(true);
                f.setPersistent(false);
                NukeManager.tagFx(plugin, f, NukeManager.ROLE_DEBRIS);
            });
            double speed = Fx.r(0.6, 1.6) * Math.min(2.2, 0.6 + r / 30);
            fb.setVelocity(new Vector(Math.cos(a) * speed, Fx.r(0.8, 1.8) * Math.min(1.8, 0.6 + r / 40), Math.sin(a) * speed));
            fb.setFireTicks(200);
            track(fb);
        }
    }
}
