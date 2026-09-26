package dev.crystalline.resonance.spell;

import dev.crystalline.resonance.util.Effects;
import dev.crystalline.resonance.util.Messages;
import dev.crystalline.resonance.util.SoundEffect;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * A spell's settings from {@code spells.<id>}. Common values are parsed up front; spell-specific
 * values (damage, range, ...) are read from {@link #section()}.
 */
public record SpellSettings(
        boolean enabled,
        Component displayName,
        double manaCost,
        long cooldownMillis,
        Color primaryColor,
        Color secondaryColor,
        float particleSize,
        Map<String, List<SoundEffect>> sounds,
        ConfigurationSection section
) {

    public static SpellSettings load(ConfigurationSection root, SpellType type, Logger logger) {
        String path = "spells." + type.id();
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            logger.warning("Missing config section " + path + "; the spell uses built-in defaults.");
            section = new MemoryConfiguration();
        }
        Map<String, List<SoundEffect>> sounds = new HashMap<>();
        ConfigurationSection soundSection = section.getConfigurationSection("sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, SoundEffect.parseAll(soundSection.getStringList(key), path + ".sounds." + key, logger));
            }
        }
        return new SpellSettings(
                section.getBoolean("enabled", true),
                Messages.parse(section.getString("name", type.id())),
                Math.max(0.0, section.getDouble("mana-cost", 20.0)),
                Math.max(0L, (long) (section.getDouble("cooldown", 1.0) * 1000)),
                Effects.parseColor(section.getString("particles.primary"), Color.WHITE),
                Effects.parseColor(section.getString("particles.secondary"), Color.WHITE),
                Effects.clampSize(section.getDouble("particles.size", 1.0)),
                Map.copyOf(sounds),
                section
        );
    }

    public double getDouble(String key, double fallback) {
        return section.getDouble(key, fallback);
    }

    public int getInt(String key, int fallback) {
        return section.getInt(key, fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return section.getBoolean(key, fallback);
    }

    /** Sounds from {@code sounds.<key>}. */
    public List<SoundEffect> sounds(String key) {
        return sounds.getOrDefault(key, List.of());
    }

    /** Formats a number without a trailing ".0" (e.g. 20 or 1.5). */
    public static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
