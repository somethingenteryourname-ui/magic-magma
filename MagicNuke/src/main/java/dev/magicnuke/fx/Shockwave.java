package dev.magicnuke.fx;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.NukeConfig;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The blast wave: a ring of dust and smoke racing across the ground, plus a
 * white condensation dome. Things are hurt and thrown when the ring reaches them.
 */
final class Shockwave extends BukkitRunnable {

    private final MagicNuke plugin;
    private final ExplosionTracker tracker;
    private final Location center;
    private final World world;
    private final double radius;
    private final double speed;
    private final double maxRadius;
    private final DamageSource source;
    private double ring;
    private int t;

    Shockwave(MagicNuke plugin, ExplosionTracker tracker, Location center, double radius) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.center = center.clone();
        this.world = center.getWorld();
        this.radius = radius;
        this.speed = Math.max(1.3, radius / 9.0);
        this.maxRadius = radius * 3.0;
        this.source = DamageSource.builder(DamageType.EXPLOSION).withDamageLocation(center).build();
    }

    @Override
    public void run() {
        double prev = ring;
        ring += speed;
        t++;
        particles();
        hitEntities(prev, ring);
        if (ring >= maxRadius) {
            cancel();
            tracker.untrack(this);
        }
    }

    private void particles() {
        int n = (int) Math.min(170, Math.max(16, ring * Math.PI * 2 / 1.6));
        double fade = 1 - ring / maxRadius;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + Fx.r(-0.02, 0.02);
            double x = center.getX() + Math.cos(a) * ring;
            double z = center.getZ() + Math.sin(a) * ring;
            double y = ground(x, z);
            Location at = new Location(world, x, y + 0.6, z);
            Vector out = new Vector(Math.cos(a), 0.05, Math.sin(a));
            Fx.shoot(world, Particle.CLOUD, at, out, 0.35 + 0.5 * fade);
            if (i % 2 == 0) Fx.dust(world, at, 1, 0.4, Color.fromRGB(205, 190, 170), (float) (2 + 2 * fade));
            if (i % 3 == 0) Fx.shoot(world, Particle.CAMPFIRE_COSY_SMOKE, at, out, 0.12 * fade);
            if (fade > 0.5 && i % 9 == 0) Fx.burst(world, Particle.EXPLOSION, at, 1, 0.3, 0);
            if (i % 14 == 0) Fx.burst(world, Particle.GUST, at, 1, 0.2, 0);
        }
        // condensation dome early on
        if (t < 24) {
            double shell = ring * 0.9;
            int m = (int) Math.min(220, Math.max(30, shell * shell * 0.5));
            for (int i = 0; i < m; i++) {
                double u = Fx.r(0, 1);
                double phi = Fx.r(0, Math.PI * 2);
                double cosT = u;
                double sinT = Math.sqrt(1 - cosT * cosT);
                Location at = center.clone().add(Math.cos(phi) * sinT * shell, cosT * shell * 0.8, Math.sin(phi) * sinT * shell);
                Fx.dustFade(world, at, 1, 0.3, Color.WHITE, Color.fromRGB(200, 200, 210), 3.5f);
            }
        }
    }

    private double ground(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return center.getY();
        int y = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        if (Math.abs(y - center.getY()) > radius * 1.5) return center.getY();
        return y;
    }

    private void hitEntities(double from, double to) {
        NukeConfig c = plugin.settings();
        double hurtRange = radius * c.damageRadius;
        double pushRange = Math.max(hurtRange, radius * 2.5);
        if (from > pushRange) return;
        double to2 = to * to;
        double from2 = from * from;
        for (Entity e : world.getNearbyEntities(center, to, to, to)) {
            if (e instanceof Display || e instanceof Interaction || tracker.owns(e)) continue;
            if (plugin.nukes().roleOf(e) != null) continue;
            double d2 = e.getLocation().distanceSquared(center);
            if (d2 <= from2 || d2 > to2) continue;
            double d = Math.sqrt(d2);
            if (e instanceof Player p) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                if (c.screenShake) shake(p, d);
                Fx.distantSound(p, center, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f);
            }
            if (!c.damageEntities) continue;
            double hurt = 1 - d / hurtRange;
            if (hurt > 0 && e instanceof LivingEntity le) {
                le.damage(c.maxDamage * Math.pow(hurt, 1.6), source);
                if (d < radius * 1.2) le.setFireTicks(Math.max(le.getFireTicks(), (int) (40 + 160 * hurt)));
            }
            double push = 1 - d / pushRange;
            if (push > 0) {
                Vector dir = e.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (dir.lengthSquared() < 1e-4) dir = new Vector(Fx.r(-1, 1), 0, Fx.r(-1, 1));
                dir.normalize().multiply(0.6 + 2.6 * push).setY(0.35 + 0.9 * push);
                e.setVelocity(e.getVelocity().add(dir));
            }
        }
    }

    private void shake(Player p, double d) {
        int shakes = (int) Math.max(2, 12 * (1 - d / (radius * 3)));
        for (int i = 0; i < shakes; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.sendHurtAnimation(Fx.rnd().nextFloat() * 360f);
            }, i * 3L);
        }
    }
}
