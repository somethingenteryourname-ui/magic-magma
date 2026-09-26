package dev.crystalline.resonance.util;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * One layer of a spell's sound: any vanilla or resource-pack sound key, with volume, pitch and delay.
 * Parsed from {@code "<key> [volume] [pitch] [delay-ticks]"}.
 */
public record SoundEffect(Sound sound, long delayTicks) {

    public static SoundEffect parse(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("missing sound key");
        }
        Key key;
        try {
            key = Key.key(parts[0].toLowerCase(Locale.ROOT));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("invalid sound key '" + parts[0] + "'");
        }
        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
        long delay = parts.length > 3 ? Long.parseLong(parts[3]) : 0L;
        return new SoundEffect(Sound.sound(key, Sound.Source.PLAYER, volume, pitch), Math.max(0L, delay));
    }

    /** Parses a list of sound strings, logging and skipping invalid entries. */
    public static List<SoundEffect> parseAll(List<String> raw, String path, Logger logger) {
        List<SoundEffect> effects = new ArrayList<>();
        for (String line : raw) {
            try {
                effects.add(parse(line));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid sound '" + line + "' at " + path + ": " + e.getMessage());
            }
        }
        return List.copyOf(effects);
    }

    /** Plays every sound at a location, audible to nearby players. */
    public static void playAt(Plugin plugin, List<SoundEffect> effects, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        for (SoundEffect effect : effects) {
            if (effect.delayTicks() <= 0) {
                world.playSound(effect.sound(), x, y, z);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> world.playSound(effect.sound(), x, y, z), effect.delayTicks());
            }
        }
    }

    /** Plays every sound only to one player. */
    public static void playTo(Plugin plugin, List<SoundEffect> effects, Player player) {
        for (SoundEffect effect : effects) {
            if (effect.delayTicks() <= 0) {
                player.playSound(effect.sound());
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(effect.sound());
                    }
                }, effect.delayTicks());
            }
        }
    }
}
