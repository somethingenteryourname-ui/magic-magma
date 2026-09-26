package dev.crystalline.resonance.spell.impl;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.spell.AbstractSpell;
import dev.crystalline.resonance.spell.SpellSettings;
import dev.crystalline.resonance.spell.SpellType;
import dev.crystalline.resonance.util.Effects;
import dev.crystalline.resonance.util.SoundEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** An expanding arc of fire that burns and knocks back everything it passes through. */
public final class InfernoWave extends AbstractSpell {

    private static final double POINT_SPACING = 0.45;

    public InfernoWave(CrystallineResonance plugin) {
        super(plugin, SpellType.INFERNO);
    }

    @Override
    public void cast(Player caster, SpellSettings settings) {
        double range = Math.max(1.0, settings.getDouble("range", 8.0));
        double speed = Math.max(0.1, settings.getDouble("speed", 0.6));
        double arcDegrees = Math.max(10.0, Math.min(360.0, settings.getDouble("arc", 120.0)));
        boolean fullCircle = arcDegrees >= 360.0;
        double halfArc = Math.toRadians(arcDegrees / 2.0);
        double minDot = Math.cos(halfArc);
        double damage = settings.getDouble("damage", 5.0);
        int fireTicks = settings.getInt("fire-ticks", 80);
        double knockback = settings.getDouble("knockback", 0.6);
        double knockbackY = settings.getDouble("knockback-y", 0.25);
        boolean requireSight = settings.getBoolean("require-line-of-sight", true);

        World world = caster.getWorld();
        Location origin = caster.getLocation().add(0, 0.3, 0);
        double yaw = Math.toRadians(origin.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));

        Effects.particle(origin.clone().add(0, 0.7, 0), Particle.FLAME, 30, 0.4, 0.08);
        Effects.particle(origin, Particle.LARGE_SMOKE, 10, 0.4, 0.02);

        new BukkitRunnable() {
            private final Set<UUID> hit = new HashSet<>();
            private double radius = 0.6;
            private int ticks;

            @Override
            public void run() {
                if (!caster.isOnline() || !origin.isChunkLoaded()) {
                    cancel();
                    return;
                }
                double inner = radius;
                radius = Math.min(range, radius + speed);
                drawFront(origin, forward, radius, fullCircle, halfArc, settings);

                if (ticks++ % 4 == 0) {
                    SoundEffect.playAt(plugin, settings.sounds("travel"), origin.clone().add(forward.clone().multiply(radius)));
                }

                for (Entity entity : world.getNearbyEntities(origin, radius + 1.0, 2.5, radius + 1.0,
                        entity -> canHit(caster, entity))) {
                    if (hit.contains(entity.getUniqueId())) {
                        continue;
                    }
                    Vector offset = entity.getLocation().toVector().subtract(origin.toVector()).setY(0);
                    double distance = offset.length();
                    if (distance > radius + 0.6 || distance < inner - 1.0) {
                        continue;
                    }
                    if (!fullCircle && distance > 0.3 && offset.clone().normalize().dot(forward) < minDot) {
                        continue;
                    }
                    if (requireSight && !caster.hasLineOfSight(entity)) {
                        continue;
                    }
                    hit.add(entity.getUniqueId());
                    scorch(caster, (LivingEntity) entity, offset, distance, forward, damage, fireTicks, knockback, knockbackY, settings);
                }

                if (radius >= range) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawFront(Location origin, Vector forward, double radius, boolean fullCircle, double halfArc, SpellSettings settings) {
        double span = fullCircle ? Math.PI * 2 : halfArc * 2;
        int points = Math.max(6, (int) Math.ceil(radius * span / POINT_SPACING));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i <= points; i++) {
            if (fullCircle && i == points) {
                break;
            }
            double angle = -span / 2 + span * i / points;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = forward.getX() * cos - forward.getZ() * sin;
            double z = forward.getX() * sin + forward.getZ() * cos;
            Location point = origin.clone().add(x * radius, 0, z * radius);

            Effects.particle(point, Particle.FLAME, 1, 0.08, 0.02);
            Effects.transition(point.add(0, 0.25, 0), settings.primaryColor(), settings.secondaryColor(), settings.particleSize(), 1, 0.1);
            if (random.nextDouble() < 0.06) {
                Effects.particle(point, Particle.LAVA, 1, 0.0, 0.0);
            } else if (random.nextDouble() < 0.1) {
                Effects.particle(point, Particle.SMOKE, 1, 0.05, 0.01);
            }
        }
    }

    private void scorch(Player caster, LivingEntity target, Vector offset, double distance, Vector forward,
                        double damage, int fireTicks, double knockback, double knockbackY, SpellSettings settings) {
        damage(caster, target, damage, DamageType.IN_FIRE);
        if (fireTicks > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        }
        if (knockback > 0 || knockbackY > 0) {
            Vector push = distance > 0.01 ? offset.clone().normalize() : forward.clone();
            push.multiply(knockback).setY(knockbackY);
            target.setVelocity(target.getVelocity().add(push));
        }
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        Effects.particle(at, Particle.FLAME, 15, 0.3, 0.04);
        SoundEffect.playAt(plugin, settings.sounds("impact"), at);
    }
}
