package dev.customtrims.effect;

import org.bukkit.Particle;

/**
 * Every particle look a trail or aura can use.
 * The first group uses your trim colors, the rest are vanilla particles.
 */
public enum ParticleStyle {
    NONE(null, false),

    // ---- Colored (use your trim colors, 1 to 8 of them) ----
    DUST(null, true),        // solid first color
    DUAL(null, true),        // steps through each of your colors
    GRADIENT(null, true),    // particles fade from one color into the next
    BLEND(null, true),       // smoothly blends through all your colors along the shape
    RAINBOW(null, false),    // cycling rainbow
    SPARKLE(null, true),     // your colors with glittering end-rod sparks

    // ---- Vanilla particles ----
    FLAME(Particle.FLAME),
    SOUL_FLAME(Particle.SOUL_FIRE_FLAME),
    END_ROD(Particle.END_ROD),
    HEART(Particle.HEART),
    ENCHANT(Particle.ENCHANT),
    WITCH(Particle.WITCH),
    ELECTRIC(Particle.ELECTRIC_SPARK),
    SNOW(Particle.SNOWFLAKE),
    TOTEM(Particle.TOTEM_OF_UNDYING),
    PORTAL(Particle.PORTAL),
    REVERSE_PORTAL(Particle.REVERSE_PORTAL),
    NOTE(Particle.NOTE),
    SCULK(Particle.SCULK_SOUL),
    SOUL(Particle.SOUL),
    GLOW(Particle.GLOW),
    FIREWORK(Particle.FIREWORK),
    CLOUD(Particle.CLOUD),
    CRIT(Particle.CRIT),
    MAGIC_CRIT(Particle.ENCHANTED_HIT),
    HAPPY(Particle.HAPPY_VILLAGER),
    ASH(Particle.ASH),
    WHITE_ASH(Particle.WHITE_ASH),
    CRIMSON(Particle.CRIMSON_SPORE),
    WARPED(Particle.WARPED_SPORE),
    NAUTILUS(Particle.NAUTILUS),
    DOLPHIN(Particle.DOLPHIN),
    SMOKE(Particle.SMOKE),
    WAX(Particle.WAX_ON),
    SCRAPE(Particle.SCRAPE),
    CHERRY(Particle.CHERRY_LEAVES),
    OBSIDIAN_TEAR(Particle.FALLING_OBSIDIAN_TEAR),
    LAVA(Particle.LAVA);

    private final Particle particle;
    private final boolean usesColor;

    ParticleStyle(Particle particle) {
        this(particle, false);
    }

    ParticleStyle(Particle particle, boolean usesColor) {
        this.particle = particle;
        this.usesColor = usesColor;
    }

    public Particle particle() {
        return particle;
    }

    public boolean usesColor() {
        return usesColor;
    }
}
