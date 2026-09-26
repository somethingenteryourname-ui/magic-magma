package dev.crystalline.resonance.spell.impl;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.spell.AbstractSpell;
import dev.crystalline.resonance.spell.SpellSettings;
import dev.crystalline.resonance.spell.SpellType;
import dev.crystalline.resonance.util.Effects;
import dev.crystalline.resonance.util.SoundEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** A fast bolt of ice that damages, slows and freezes the first thing it hits. */
public final class FrostBolt extends AbstractSpell {

    private static final BlockData ICE = Material.ICE.createBlockData();

    public FrostBolt(CrystallineResonance plugin) {
        super(plugin, SpellType.FROST);
    }

    @Override
    public void cast(Player caster, SpellSettings settings) {
        double range = Math.max(1.0, settings.getDouble("range", 32.0));
        double speed = Math.max(0.2, Math.min(5.0, settings.getDouble("speed", 1.6)));
        double hitbox = Math.max(0.0, settings.getDouble("hitbox", 0.4));

        World world = caster.getWorld();
        Location start = caster.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        Location muzzle = start.clone().add(direction.clone().multiply(0.8));
        Effects.dust(muzzle, settings.secondaryColor(), settings.particleSize(), 10, 0.15);
        Effects.particle(muzzle, Particle.SNOWFLAKE, 8, 0.1, 0.05);

        new BukkitRunnable() {
            private Location position = start.clone();
            private double travelled;

            @Override
            public void run() {
                if (!caster.isOnline() || travelled >= range || !position.isChunkLoaded()) {
                    fizzle(position, settings);
                    cancel();
                    return;
                }
                double step = Math.min(speed, range - travelled);
                RayTraceResult hit = world.rayTrace(position, direction, step, FluidCollisionMode.NEVER, true, hitbox,
                        entity -> canHit(caster, entity));
                Location end = hit != null
                        ? hit.getHitPosition().toLocation(world)
                        : position.clone().add(direction.clone().multiply(step));

                Effects.line(position, end, 0.3, point -> {
                    Effects.transition(point, settings.primaryColor(), settings.secondaryColor(), settings.particleSize(), 1, 0.03);
                });
                Effects.particle(end, Particle.SNOWFLAKE, 2, 0.08, 0.01);

                if (hit != null) {
                    impact(caster, end, hit.getHitEntity(), settings);
                    cancel();
                    return;
                }
                position = end;
                travelled += step;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Player caster, Location at, Entity hitEntity, SpellSettings settings) {
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 40, 0.3, 0.3, 0.3, 0.15, ICE);
        Effects.particle(at, Particle.SNOWFLAKE, 35, 0.45, 0.08);
        Effects.transition(at, settings.primaryColor(), settings.secondaryColor(), settings.particleSize() * 1.3f, 25, 0.55);
        SoundEffect.playAt(plugin, settings.sounds("impact"), at);

        double damage = settings.getDouble("damage", 6.0);
        if (hitEntity instanceof LivingEntity target) {
            chill(caster, target, damage, settings);
        }

        double splashRadius = settings.getDouble("splash-radius", 1.75);
        double splashDamage = damage * settings.getDouble("splash-damage-multiplier", 0.5);
        if (splashRadius > 0) {
            for (Entity nearby : world.getNearbyEntities(at, splashRadius, splashRadius, splashRadius,
                    entity -> entity != hitEntity && canHit(caster, entity))) {
                chill(caster, (LivingEntity) nearby, splashDamage, settings);
            }
        }
    }

    private void chill(Player caster, LivingEntity target, double damage, SpellSettings settings) {
        damage(caster, target, damage, DamageType.FREEZE);
        int slowTicks = settings.getInt("slowness.duration-ticks", 60);
        if (slowTicks > 0) {
            int amplifier = Math.max(0, settings.getInt("slowness.amplifier", 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, amplifier, false, true, true));
        }
        int freezeTicks = settings.getInt("freeze-ticks", 160);
        if (freezeTicks > 0) {
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
        }
    }

    private void fizzle(Location at, SpellSettings settings) {
        Effects.particle(at, Particle.SNOWFLAKE, 12, 0.25, 0.03);
        Effects.dust(at, settings.primaryColor(), settings.particleSize(), 8, 0.2);
    }
}
