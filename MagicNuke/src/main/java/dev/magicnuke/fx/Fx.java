package dev.magicnuke.fx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Particle and sound shortcuts. Particles are "forced" so they show from far away. */
public final class Fx {

    private Fx() {
    }

    public static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    public static double r(double min, double max) {
        return min + rnd().nextDouble() * (max - min);
    }

    public static void burst(World w, Particle p, Location at, int count, double spread, double speed) {
        w.spawnParticle(p, at, count, spread, spread, spread, speed, null, true);
    }

    public static void burst(World w, Particle p, Location at, int count, double sx, double sy, double sz, double speed) {
        w.spawnParticle(p, at, count, sx, sy, sz, speed, null, true);
    }

    /** One particle moving in a direction (count 0 makes the offset a velocity). */
    public static void shoot(World w, Particle p, Location at, Vector dir, double speed) {
        w.spawnParticle(p, at, 0, dir.getX(), dir.getY(), dir.getZ(), speed, null, true);
    }

    public static void flash(World w, Location at, Color color) {
        w.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, color, true);
    }

    public static void dust(World w, Location at, int count, double spread, Color color, float size) {
        w.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(color, Math.min(4f, size)), true);
    }

    public static void dust(World w, Location at, int count, double sx, double sy, double sz, Color color, float size) {
        w.spawnParticle(Particle.DUST, at, count, sx, sy, sz, 0, new Particle.DustOptions(color, Math.min(4f, size)), true);
    }

    public static void dustFade(World w, Location at, int count, double spread, Color from, Color to, float size) {
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, count, spread, spread, spread, 0,
                new Particle.DustTransition(from, to, Math.min(4f, size)), true);
    }

    public static void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, SoundCategory.MASTER, volume, pitch);
    }

    public static List<Player> playersNear(Location at, double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= r2) out.add(p);
        }
        return out;
    }

    /**
     * Plays a sound for one player as if it came from a far-away spot: it's placed a few
     * blocks from the player in the right direction, so it's audible at any distance.
     */
    public static void distantSound(Player p, Location source, Sound sound, float volume, float pitch) {
        Location eye = p.getEyeLocation();
        Vector dir = source.toVector().subtract(eye.toVector());
        double d = dir.length();
        Location at = d < 8 ? source : eye.add(dir.multiply(8 / d));
        p.playSound(at, sound, SoundCategory.MASTER, volume, pitch);
    }

    public static float clampPitch(double v) {
        return (float) Math.max(0.5, Math.min(2.0, v));
    }
}
