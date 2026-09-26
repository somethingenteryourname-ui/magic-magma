package dev.crystalline.resonance.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Particle helpers shared by the spells. */
public final class Effects {

    private Effects() {
    }

    /** Parses {@code #RRGGBB} (or {@code RRGGBB}); returns the fallback when invalid. */
    public static Color parseColor(String raw, Color fallback) {
        if (raw == null) {
            return fallback;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static float clampSize(double size) {
        return (float) Math.max(0.1, Math.min(4.0, size));
    }

    public static void dust(Location at, Color color, float size, int count, double spread) {
        World world = at.getWorld();
        world.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
    }

    public static void transition(Location at, Color from, Color to, float size, int count, double spread) {
        World world = at.getWorld();
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, count, spread, spread, spread, 0,
                new Particle.DustTransition(from, to, size));
    }

    public static void particle(Location at, Particle particle, int count, double spread, double speed) {
        at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, speed);
    }

    /** Calls the painter at evenly spaced points from {@code from} to {@code to} (inclusive). */
    public static void line(Location from, Location to, double spacing, Consumer<Location> painter) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        int steps = Math.max(1, (int) Math.ceil(length / spacing));
        Vector step = delta.multiply(1.0 / steps);
        Location point = from.clone();
        for (int i = 0; i <= steps; i++) {
            painter.accept(point.clone());
            point.add(step);
        }
    }

    /** Draws a jagged, lightning-like line between two points. */
    public static void jaggedLine(Location from, Location to, double segmentLength, double jitter, Consumer<Location> painter) {
        Vector delta = to.toVector().subtract(from.toVector());
        int segments = Math.max(1, (int) Math.ceil(delta.length() / segmentLength));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Location> points = new ArrayList<>(segments + 1);
        points.add(from.clone());
        for (int i = 1; i < segments; i++) {
            Location point = from.clone().add(delta.clone().multiply((double) i / segments));
            point.add(random.nextDouble(-jitter, jitter), random.nextDouble(-jitter, jitter) * 0.5, random.nextDouble(-jitter, jitter));
            points.add(point);
        }
        points.add(to.clone());

        for (int i = 0; i < points.size() - 1; i++) {
            line(points.get(i), points.get(i + 1), 0.25, painter);
        }
    }
}
