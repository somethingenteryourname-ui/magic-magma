package dev.magicnuke.fx;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * After the blast: ash falls around the site, the sky darkens for players nearby,
 * and near ground zero a Geiger counter clicks and radiation makes you sick.
 */
final class Fallout extends BukkitRunnable {

    private final MagicNuke plugin;
    private final ExplosionTracker tracker;
    private final Location center;
    private final World world;
    private final double radius;
    private final int duration;
    private final Set<UUID> darkened = new HashSet<>();
    private int t;

    Fallout(MagicNuke plugin, ExplosionTracker tracker, Location center, double radius) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.center = center.clone();
        this.world = center.getWorld();
        this.radius = radius;
        this.duration = plugin.settings().falloutTicks;
    }

    @Override
    public void run() {
        if (t++ >= duration) {
            end();
            return;
        }
        double fade = 1 - (double) t / duration;
        double area = Math.max(48, radius * 5);
        for (Player p : world.getPlayers()) {
            double d = horizontal(p.getLocation());
            if (d > area) {
                if (darkened.remove(p.getUniqueId())) p.resetPlayerWeather();
                continue;
            }
            if (darkened.add(p.getUniqueId())) p.setPlayerWeather(WeatherType.DOWNFALL);
            if (t % 4 == 0) {
                Location around = p.getLocation().add(0, 6, 0);
                int n = (int) (10 + 40 * fade * (1 - d / area));
                p.spawnParticle(Particle.WHITE_ASH, around, n * 2, 10, 5, 10, 0);
                p.spawnParticle(Particle.ASH, around, n, 10, 5, 10, 0);
            }
            if (plugin.settings().radiation) radiation(p, d);
        }
        if (t % 6 == 0) {
            Fx.burst(world, Particle.LARGE_SMOKE, center.clone().add(0, 1, 0), (int) Math.min(30, 4 + radius / 3), radius * 0.4, 0.5, radius * 0.4, 0.02);
        }
    }

    private void radiation(Player p, double d) {
        double hot = 1 - d / (radius * 1.6);
        if (hot <= 0 || p.getGameMode() == GameMode.SPECTATOR) return;
        // Geiger counter: more clicks the closer you get
        if (Fx.rnd().nextDouble() < 0.15 + 0.75 * hot) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.35f + 0.3f * (float) hot, (float) Fx.r(1.6, 2.0));
        }
        if (t % 40 == 0 && p.getGameMode() != GameMode.CREATIVE) {
            p.sendActionBar(Msg.mm("<green>☢ <yellow>Radiation level: <red>" + (int) (hot * 100) + "%</red> <green>☢"));
            if (hot > 0.25) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, hot > 0.7 ? 1 : 0, false, true, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 0, false, false, true));
            }
        }
    }

    private double horizontal(Location l) {
        double dx = l.getX() - center.getX();
        double dz = l.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void end() {
        for (Player p : world.getPlayers()) {
            if (darkened.contains(p.getUniqueId())) p.resetPlayerWeather();
        }
        darkened.clear();
        cancel();
        tracker.untrack(this);
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        // restore the sky if the plugin shuts down mid-fallout
        for (UUID id : darkened) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.resetPlayerWeather();
        }
        darkened.clear();
        super.cancel();
    }
}
