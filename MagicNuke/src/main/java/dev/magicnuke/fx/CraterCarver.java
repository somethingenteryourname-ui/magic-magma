package dev.magicnuke.fx;

import dev.magicnuke.MagicNuke;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Blows out a crater, spread over several ticks so the server doesn't freeze.
 * The crater grows outward from ground zero, has a ragged edge, a scorched
 * lining (magma, basalt, blackstone...) and a burnt ring of ground around it.
 */
final class CraterCarver extends BukkitRunnable {

    private static final double EDGE = 1.16;
    private static final int BUCKETS = 128;

    private enum Phase {CARVE, LINING, SCORCH}

    private final MagicNuke plugin;
    private final World world;
    private final int cx, cy, cz;
    private final int radius;
    private final int half;
    private final int side;
    private final int[] offsets;
    private final int[] colMin;
    private final long budgetNanos;
    private final boolean scorch;
    private final boolean fire;
    private final int minY, maxY;
    private final long startedAt = System.currentTimeMillis();

    private Phase phase = Phase.CARVE;
    private int idx;
    private int removed;
    private int ticks;

    private CraterCarver(MagicNuke plugin, Location center, int radius, int[] offsets, int half) {
        this.plugin = plugin;
        this.world = center.getWorld();
        this.cx = center.getBlockX();
        this.cy = center.getBlockY();
        this.cz = center.getBlockZ();
        this.radius = radius;
        this.half = half;
        this.side = half * 2 + 1;
        this.offsets = offsets;
        this.colMin = new int[side * side];
        Arrays.fill(colMin, Integer.MAX_VALUE);
        this.budgetNanos = (long) (plugin.settings().maxMillisPerTick * 1_000_000L);
        this.scorch = plugin.settings().scorch;
        this.fire = plugin.settings().fire;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
    }

    /** Works out the crater shape off the main thread, then carves it on the main thread. */
    static void start(MagicNuke plugin, ExplosionTracker tracker, Location center, double radiusD) {
        int radius = (int) Math.max(2, Math.round(radiusD));
        int depth = (int) Math.max(2, Math.round(radius * plugin.settings().craterDepth));
        int up = (int) Math.max(2, Math.round(radius * plugin.settings().blastHeight));
        int half = (int) Math.ceil(radius * EDGE) + 1;
        long seed = ThreadLocalRandom.current().nextLong();
        Location at = center.clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] offsets = computeOffsets(radius, depth, up, half, seed);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;
                CraterCarver c = new CraterCarver(plugin, at, radius, offsets, half);
                tracker.track(c);
                c.runTaskTimer(plugin, 1L, 1L);
            });
        });
    }

    // ------------------------------------------------------------------ shape

    static int[] computeOffsets(int r, int depth, int up, int half, long seed) {
        int[] counts = new int[BUCKETS];
        for (int pass = 0; pass < 2; pass++) {
            int[] out = null;
            int[] pos = null;
            if (pass == 1) {
                int total = 0;
                pos = new int[BUCKETS];
                for (int b = 0; b < BUCKETS; b++) {
                    pos[b] = total;
                    total += counts[b];
                }
                out = new int[total];
            }
            double freq = 2.2 / r;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    double h2 = ((double) dx * dx + (double) dz * dz) / ((double) r * r);
                    if (h2 > EDGE * EDGE) continue;
                    for (int dy = -depth; dy <= up; dy++) {
                        double vy = dy < 0 ? (double) dy / depth : (double) dy / up;
                        double n = Math.sqrt(h2 + vy * vy);
                        if (n >= EDGE) continue;
                        double thr = 1.0 + 0.13 * (noise(dx * freq, dy * freq * 1.5, dz * freq, seed) * 2 - 1);
                        if (n >= thr) continue;
                        if (n > thr - 0.09 && hash01(dx, dy, dz, seed) >= (thr - n) / 0.09) continue;
                        int bucket = Math.min(BUCKETS - 1, (int) (n / EDGE * BUCKETS));
                        if (pass == 0) {
                            counts[bucket]++;
                        } else {
                            out[pos[bucket]++] = ((dx + 512) << 20) | ((dy + 512) << 10) | (dz + 512);
                        }
                    }
                }
            }
            if (pass == 1) return out;
        }
        throw new IllegalStateException();
    }

    private static double hash01(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double noise(double x, double y, double z, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c000 = hash01(x0, y0, z0, seed), c100 = hash01(x0 + 1, y0, z0, seed);
        double c010 = hash01(x0, y0 + 1, z0, seed), c110 = hash01(x0 + 1, y0 + 1, z0, seed);
        double c001 = hash01(x0, y0, z0 + 1, seed), c101 = hash01(x0 + 1, y0, z0 + 1, seed);
        double c011 = hash01(x0, y0 + 1, z0 + 1, seed), c111 = hash01(x0 + 1, y0 + 1, z0 + 1, seed);
        double a = lerp(lerp(c000, c100, fx), lerp(c010, c110, fx), fy);
        double b = lerp(lerp(c001, c101, fx), lerp(c011, c111, fx), fy);
        return lerp(a, b, fz);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ carving

    @Override
    public void run() {
        ticks++;
        long start = System.nanoTime();
        switch (phase) {
            case CARVE -> carve(start);
            case LINING -> lining(start);
            case SCORCH -> scorchRing(start);
        }
    }

    private void carve(long start) {
        while (idx < offsets.length) {
            if ((idx & 127) == 0 && System.nanoTime() - start > budgetNanos) return;
            int p = offsets[idx++];
            int dx = ((p >>> 20) & 1023) - 512;
            int dy = ((p >>> 10) & 1023) - 512;
            int dz = (p & 1023) - 512;
            int y = cy + dy;
            if (y < minY || y >= maxY) continue;
            int ci = (dx + half) * side + (dz + half);
            if (dy < colMin[ci]) colMin[ci] = dy;
            Block b = world.getBlockAt(cx + dx, y, cz + dz);
            Material m = b.getType();
            if (m.isAir() || m.getHardness() < 0) continue;
            b.setType(Material.AIR, false);
            removed++;
        }
        idx = 0;
        phase = scorch ? Phase.LINING : null;
        if (phase == null) finish();
    }

    private void lining(long start) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (idx < colMin.length) {
            if ((idx & 63) == 0 && System.nanoTime() - start > budgetNanos) return;
            int ci = idx++;
            int min = colMin[ci];
            if (min == Integer.MAX_VALUE) continue;
            int dx = ci / side - half;
            int dz = ci % side - half;
            int y = cy + min - 1;
            if (y < minY || y >= maxY) continue;
            Block b = world.getBlockAt(cx + dx, y, cz + dz);
            Material m = b.getType();
            if (!m.isSolid() || m.getHardness() < 0) continue;
            double hd = Math.sqrt((double) dx * dx + (double) dz * dz) / radius;
            double r = rnd.nextDouble();
            Material to;
            if (hd < 0.35) {
                to = r < 0.4 ? Material.MAGMA_BLOCK : r < 0.55 ? Material.OBSIDIAN : r < 0.75 ? Material.BLACKSTONE : Material.BASALT;
                if (hd < 0.2 && r > 0.985) to = Material.LAVA;
            } else if (hd < 0.75) {
                to = r < 0.35 ? Material.BLACKSTONE : r < 0.6 ? Material.BASALT : r < 0.8 ? Material.COBBLED_DEEPSLATE
                        : r < 0.9 ? Material.MAGMA_BLOCK : Material.TUFF;
            } else {
                to = r < 0.45 ? Material.COARSE_DIRT : r < 0.65 ? Material.BLACKSTONE : r < 0.85 ? Material.TUFF : Material.SOUL_SOIL;
            }
            b.setType(to, false);
            if (fire && hd > 0.25 && rnd.nextDouble() < 0.06) {
                Block above = b.getRelative(0, 1, 0);
                if (above.getType().isAir()) above.setType(Material.FIRE, false);
            }
        }
        idx = 0;
        phase = Phase.SCORCH;
    }

    private void scorchRing(long start) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int outer = (int) Math.ceil(radius * 1.8);
        int w = outer * 2 + 1;
        int total = w * w;
        while (idx < total) {
            if ((idx & 63) == 0 && System.nanoTime() - start > budgetNanos) return;
            int i = idx++;
            int dx = i / w - outer;
            int dz = i % w - outer;
            double hd = Math.sqrt((double) dx * dx + (double) dz * dz) / radius;
            if (hd < 0.95 || hd > 1.8) continue;
            double p = 1 - (hd - 0.95) / 0.85;
            if (rnd.nextDouble() > p * 0.9) continue;
            int x = cx + dx, z = cz + dz;
            Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            if (Math.abs(top.getY() - cy) > radius) continue;
            Material m = top.getType();
            Material to = scorched(m, rnd);
            if (to != null) {
                top.setType(to, false);
                if (to.isAir()) top = top.getRelative(0, -1, 0);
            }
            Block above = top.getRelative(0, 1, 0);
            Material am = above.getType();
            if (!am.isAir() && !am.isSolid() && !above.isLiquid() && am != Material.FIRE) {
                above.setType(Material.AIR, false);
                am = Material.AIR;
            }
            if (fire && am.isAir() && top.getType().isSolid() && rnd.nextDouble() < p * 0.3) {
                above.setType(Material.FIRE, false);
            }
        }
        finish();
    }

    private static Material scorched(Material m, ThreadLocalRandom rnd) {
        if (Tag.LEAVES.isTagged(m)) return Material.AIR;
        double r = rnd.nextDouble();
        return switch (m) {
            case GRASS_BLOCK, DIRT, PODZOL, MYCELIUM, ROOTED_DIRT, MOSS_BLOCK, DIRT_PATH, FARMLAND, MUD ->
                    r < 0.65 ? Material.COARSE_DIRT : r < 0.85 ? Material.SOUL_SOIL : Material.DIRT;
            case SAND, RED_SAND -> r < 0.35 ? Material.GLASS : null;
            case STONE, ANDESITE, DIORITE, GRANITE, COBBLESTONE, DEEPSLATE, GRAVEL ->
                    r < 0.35 ? Material.BASALT : r < 0.6 ? Material.BLACKSTONE : r < 0.8 ? Material.TUFF : null;
            case SNOW, SNOW_BLOCK, POWDER_SNOW -> Material.AIR;
            case ICE, PACKED_ICE -> Material.WATER;
            default -> null;
        };
    }

    private void finish() {
        cancel();
        plugin.explosions().untrack(this);
        plugin.getLogger().info(String.format("Crater complete at %s %d %d %d: %d blocks removed in %d ticks (%.1fs).",
                world.getName(), cx, cy, cz, removed, ticks, (System.currentTimeMillis() - startedAt) / 1000.0));
    }
}
