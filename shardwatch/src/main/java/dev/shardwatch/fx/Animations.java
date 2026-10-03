package dev.shardwatch.fx;

import dev.shardwatch.Shardwatch;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Particle-drawn animations for the big moments. */
public class Animations {

    protected final Shardwatch plugin;

    public Animations(Shardwatch plugin) {
        this.plugin = plugin;
    }

    protected boolean on() {
        return plugin.getConfig().getBoolean("fx.enabled", true) && plugin.getConfig().getBoolean("fx.animations", true);
    }

    /** Crystal rings close in around a player before an Encase kick. */
    public void encase(Player p, int ticks) {
        if (!on()) {
            return;
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > ticks) {
                    cancel();
                    return;
                }
                double progress = t / (double) Math.max(1, ticks);
                double radius = 1.6 - progress * 1.1;
                ring(p.getLocation().add(0, progress * 2.0, 0), radius, 14, Color.fromRGB(0xD9468F), Color.fromRGB(0x7FE8E0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A burst when Petrify starts or ends. */
    public void petrify(Player p, boolean on) {
        if (!on()) {
            return;
        }
        Location l = p.getLocation();
        for (int i = 0; i < 4; i++) {
            ring(l.clone().add(0, 0.5 * i, 0), 0.8, 12, Color.fromRGB(on ? 0xB7F2FF : 0xF59AC8), Color.WHITE);
        }
    }

    /** A rising column where a Flare was filed. */
    public void flareBeacon(Location at) {
        if (!on()) {
            return;
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (t++ > 20) {
                    cancel();
                    return;
                }
                at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0, 0.2 * t, 0), 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustTransition(Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0), 1.2f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A ring above the head on promotion. */
    public void halo(Player p, String hex) {
        if (!on()) {
            return;
        }
        Color c = Fx.hex(hex);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 40) {
                    cancel();
                    return;
                }
                ring(p.getLocation().add(0, 2.3, 0), 0.55, 10, c, Color.WHITE);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** An expanding ring when a Rewind starts. */
    public void rewindSweep(Location center, int radius) {
        if (!on()) {
            return;
        }
        int max = Math.max(3, Math.min(radius, 30));
        new BukkitRunnable() {
            int r = 1;

            @Override
            public void run() {
                if (r > max) {
                    cancel();
                    return;
                }
                ring(center.clone().add(0, 0.2, 0), r, r * 6, Color.fromRGB(0x7FE8E0), Color.fromRGB(0xF59AC8));
                r++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Rising rings on a Clarity level-up. */
    public void levelUp(Player p) {
        if (!on()) {
            return;
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 30) {
                    cancel();
                    return;
                }
                ring(p.getLocation().add(0, t * 0.08, 0), 0.9 - t * 0.02, 12, Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A quick double ring when a tool is refined. */
    public void refine(Player p) {
        if (!on()) {
            return;
        }
        ring(p.getLocation().add(0, 1.0, 0), 0.7, 16, Color.fromRGB(0x7FE8E0), Color.WHITE);
        ring(p.getLocation().add(0, 1.4, 0), 0.5, 12, Color.fromRGB(0xF59AC8), Color.WHITE);
    }

    /** Sparkles bursting from a redeemed Lustre Shard. */
    public void shardBurst(Player p) {
        if (!on()) {
            return;
        }
        p.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, p.getLocation().add(0, 1.2, 0), 24, 0.4, 0.4, 0.4, 0,
                new Particle.DustTransition(Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0), 1.1f));
    }

    /** Removes anything left behind (display entities in later stages). */
    public void shutdown() {
    }

    protected static void ring(Location c, double radius, int points, Color from, Color to) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            c.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, c.getX() + Math.cos(a) * radius, c.getY(),
                    c.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, new Particle.DustTransition(from, to, 1.0f), true);
        }
    }
}
