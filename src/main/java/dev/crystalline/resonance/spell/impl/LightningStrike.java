package dev.crystalline.resonance.spell.impl;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.spell.AbstractSpell;
import dev.crystalline.resonance.spell.SpellSettings;
import dev.crystalline.resonance.spell.SpellType;
import dev.crystalline.resonance.util.Effects;
import dev.crystalline.resonance.util.SoundEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Charges briefly on the targeted spot (tracking a locked-on creature), then calls down lightning that
 * damages everything nearby and arcs to further targets.
 */
public final class LightningStrike extends AbstractSpell {

    private static final Vector DOWN = new Vector(0, -1, 0);

    public LightningStrike(CrystallineResonance plugin) {
        super(plugin, SpellType.LIGHTNING);
    }

    @Override
    public void cast(Player caster, SpellSettings settings) {
        double range = Math.max(1.0, settings.getDouble("range", 40.0));
        int chargeTicks = Math.max(0, settings.getInt("charge-ticks", 10));
        double radius = Math.max(0.5, settings.getDouble("radius", 2.5));

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult aim = world.rayTrace(eye, direction, range, FluidCollisionMode.NEVER, true, 0.6,
                entity -> canHit(caster, entity));

        LivingEntity lockedOn = aim != null && aim.getHitEntity() instanceof LivingEntity living ? living : null;
        Location target;
        if (lockedOn != null) {
            target = lockedOn.getLocation();
        } else if (aim != null) {
            target = aim.getHitPosition().toLocation(world);
        } else {
            // Nothing in range: strike the ground below the furthest point.
            Location far = eye.clone().add(direction.clone().multiply(range));
            RayTraceResult ground = world.rayTraceBlocks(far, DOWN.clone(), 32.0, FluidCollisionMode.NEVER, true);
            target = ground != null ? ground.getHitPosition().toLocation(world) : far;
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!caster.isOnline() || !target.isChunkLoaded()) {
                    cancel();
                    return;
                }
                if (lockedOn != null && lockedOn.isValid() && lockedOn.getWorld().equals(world)) {
                    Location current = lockedOn.getLocation();
                    target.setX(current.getX());
                    target.setY(current.getY());
                    target.setZ(current.getZ());
                }
                if (tick >= chargeTicks) {
                    strike(caster, target, radius, settings);
                    cancel();
                    return;
                }
                drawCharge(target, radius * (1.0 - (double) tick / Math.max(1, chargeTicks)), settings);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawCharge(Location target, double ringRadius, SpellSettings settings) {
        int points = 14;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location point = target.clone().add(Math.cos(angle) * ringRadius, 0.15, Math.sin(angle) * ringRadius);
            Effects.dust(point, settings.secondaryColor(), settings.particleSize() * 0.7f, 1, 0.0);
        }
        Effects.particle(target.clone().add(0, 1.0, 0), Particle.ELECTRIC_SPARK, 6, 0.4, 0.05);
    }

    private void strike(Player caster, Location target, double radius, SpellSettings settings) {
        World world = target.getWorld();
        if (settings.getBoolean("visual-lightning", true)) {
            world.strikeLightningEffect(target);
        }

        double boltHeight = Math.max(2.0, settings.getDouble("bolt-height", 14.0));
        Effects.jaggedLine(target.clone().add(0, boltHeight, 0), target, 1.4, 0.6, point -> {
            Effects.dust(point, settings.primaryColor(), settings.particleSize(), 1, 0.02);
        });
        Effects.particle(target, Particle.ELECTRIC_SPARK, 60, 0.8, 0.3);
        Effects.particle(target, Particle.EXPLOSION, 1, 0.0, 0.0);
        Effects.transition(target.clone().add(0, 0.5, 0), settings.primaryColor(), settings.secondaryColor(),
                settings.particleSize() * 1.4f, 30, radius * 0.5);
        SoundEffect.playAt(plugin, settings.sounds("impact"), target);

        double damage = settings.getDouble("damage", 8.0);
        Set<UUID> struck = new HashSet<>();
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(target, radius, radius, radius, entity -> canHit(caster, entity))) {
            double distanceSquared = entity.getLocation().distanceSquared(target);
            if (distanceSquared > radiusSquared) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            shock(caster, living, damage);
            struck.add(living.getUniqueId());
            if (distanceSquared < closestDistance) {
                closestDistance = distanceSquared;
                closest = living;
            }
        }

        int jumps = Math.max(0, settings.getInt("chain.jumps", 2));
        if (closest != null && jumps > 0) {
            // Chain from where the first target was, so it still jumps if the strike killed it.
            chain(caster, center(closest), struck, jumps, damage * settings.getDouble("chain.damage-multiplier", 0.6), settings);
        }
    }

    private void chain(Player caster, Location from, Set<UUID> struck, int jumpsLeft, double damage, SpellSettings settings) {
        long delay = Math.max(1, settings.getInt("chain.delay-ticks", 3));
        double chainRange = Math.max(1.0, settings.getDouble("chain.range", 6.0));
        double multiplier = settings.getDouble("chain.damage-multiplier", 0.6);
        World world = from.getWorld();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!caster.isOnline() || !from.isChunkLoaded()) {
                return;
            }
            LivingEntity next = null;
            double nextDistance = chainRange * chainRange;
            for (Entity entity : world.getNearbyEntities(from, chainRange, chainRange, chainRange,
                    entity -> !struck.contains(entity.getUniqueId()) && canHit(caster, entity))) {
                LivingEntity living = (LivingEntity) entity;
                double distance = center(living).distanceSquared(from);
                if (distance <= nextDistance && hasClearPath(from, center(living))) {
                    nextDistance = distance;
                    next = living;
                }
            }
            if (next == null) {
                return;
            }

            Location end = center(next);
            Effects.jaggedLine(from, end, 1.0, 0.35, point -> {
                Effects.dust(point, settings.secondaryColor(), settings.particleSize() * 0.8f, 1, 0.0);
            });
            Effects.particle(end, Particle.ELECTRIC_SPARK, 20, 0.3, 0.2);
            SoundEffect.playAt(plugin, settings.sounds("chain"), end);

            struck.add(next.getUniqueId());
            shock(caster, next, damage);
            if (jumpsLeft > 1) {
                chain(caster, end, struck, jumpsLeft - 1, damage * multiplier, settings);
            }
        }, delay);
    }

    private static Location center(LivingEntity entity) {
        return entity.getLocation().add(0, entity.getHeight() / 2, 0);
    }

    private static boolean hasClearPath(Location from, Location to) {
        Vector between = to.toVector().subtract(from.toVector());
        double distance = between.length();
        if (distance < 0.01) {
            return true;
        }
        return from.getWorld().rayTraceBlocks(from, between.normalize(), distance, FluidCollisionMode.NEVER, true) == null;
    }

    private void shock(Player caster, LivingEntity target, double damage) {
        damage(caster, target, damage, DamageType.LIGHTNING_BOLT);
        target.setVelocity(target.getVelocity().add(new Vector(0, 0.25, 0)));
    }
}
