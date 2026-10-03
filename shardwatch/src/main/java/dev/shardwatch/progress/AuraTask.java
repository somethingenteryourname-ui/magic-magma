package dev.shardwatch.progress;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.fx.Fx;
import dev.shardwatch.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Draws Keepsake auras around staff who have one active. Patterns: orbit, halo, spiral, trail.
 * A Veiled staff member's aura is only shown to players who can see through the Veil.
 */
public final class AuraTask implements Runnable {

    private final Shardwatch plugin;
    private long tick;

    public AuraTask(Shardwatch plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tick++;
        if (!plugin.getConfig().getBoolean("fx.enabled", true) || !plugin.lustre().enabled()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile prof = plugin.profiles().get(p);
            if (prof.activeAura() == null || !prof.fxOn() || p.isDead()) {
                continue;
            }
            String base = "progression.keepsakes." + prof.activeAura();
            String pattern = plugin.getConfig().getString(base + ".pattern", "orbit");
            List<String> lines = plugin.getConfig().getStringList(base + ".particles");
            if (lines.isEmpty()) {
                continue;
            }
            Fx.ParticleLayer layer = Fx.parseParticle(lines.get((int) (tick % lines.size())));
            if (layer == null) {
                continue;
            }
            boolean veiled = plugin.facets().isVeiled(p);
            for (Location at : points(p.getLocation(), pattern)) {
                for (Player viewer : p.getWorld().getPlayers()) {
                    if (viewer.getLocation().distanceSquared(at) > 48 * 48) {
                        continue;
                    }
                    if (veiled && !viewer.hasPermission("shardwatch.veil.see") && !viewer.equals(p)) {
                        continue;
                    }
                    spawn(viewer, layer, at);
                }
            }
        }
    }

    private List<Location> points(Location feet, String pattern) {
        double t = tick * 0.35;
        return switch (pattern) {
            case "halo" -> List.of(feet.clone().add(Math.cos(t) * 0.45, 2.25, Math.sin(t) * 0.45),
                    feet.clone().add(Math.cos(t + Math.PI) * 0.45, 2.25, Math.sin(t + Math.PI) * 0.45));
            case "spiral" -> List.of(feet.clone().add(Math.cos(t) * 0.7, (tick % 20) / 10.0, Math.sin(t) * 0.7));
            case "trail" -> List.of(feet.clone().add(0, 0.1, 0));
            default -> List.of(feet.clone().add(Math.cos(t) * 0.8, 1.0 + Math.sin(t * 0.5) * 0.3, Math.sin(t) * 0.8),
                    feet.clone().add(Math.cos(t + Math.PI) * 0.8, 1.0 - Math.sin(t * 0.5) * 0.3, Math.sin(t + Math.PI) * 0.8));
        };
    }

    private static void spawn(Player viewer, Fx.ParticleLayer l, Location at) {
        Object data = switch (l.type().getDataType().getSimpleName()) {
            case "DustOptions" -> new Particle.DustOptions(l.color() == null ? org.bukkit.Color.WHITE : l.color(), l.size());
            case "DustTransition" -> new Particle.DustTransition(l.color() == null ? org.bukkit.Color.WHITE : l.color(),
                    l.color2() == null ? org.bukkit.Color.WHITE : l.color2(), l.size());
            default -> null;
        };
        if (data == null && l.type().getDataType() != Void.class) {
            return;
        }
        viewer.spawnParticle(l.type(), at, l.count(), l.dx(), l.dy(), l.dz(), l.speed(), data);
    }
}
