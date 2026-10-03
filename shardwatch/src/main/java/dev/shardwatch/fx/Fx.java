package dev.shardwatch.fx;

import dev.shardwatch.Shardwatch;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Named effect presets from config: a list of sound layers and a list of particle layers.
 * <pre>
 * sounds:    "namespace:key volume pitch [delayTicks]"
 * particles: "TYPE count dx dy dz speed [#color] [#color2|size] [size]"
 * </pre>
 */
public class Fx {

    public record SoundLayer(Key key, float volume, float pitch, int delay) {
    }

    public record ParticleLayer(Particle type, int count, double dx, double dy, double dz, double speed,
                                Color color, Color color2, float size) {
    }

    public record Preset(List<SoundLayer> sounds, List<ParticleLayer> particles) {
    }

    protected final Shardwatch plugin;
    protected final Map<String, Preset> presets = new HashMap<>();
    protected boolean enabled;

    public Fx(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        presets.clear();
        enabled = plugin.getConfig().getBoolean("fx.enabled", true);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("fx.presets");
        if (sec == null) {
            return;
        }
        for (String name : sec.getKeys(false)) {
            List<SoundLayer> sounds = new ArrayList<>();
            for (String line : sec.getStringList(name + ".sounds")) {
                SoundLayer s = parseSound(line);
                if (s != null) {
                    sounds.add(s);
                } else {
                    plugin.getLogger().warning("fx.presets." + name + ": bad sound '" + line + "'");
                }
            }
            List<ParticleLayer> particles = new ArrayList<>();
            for (String line : sec.getStringList(name + ".particles")) {
                ParticleLayer p = parseParticle(line);
                if (p != null) {
                    particles.add(p);
                } else {
                    plugin.getLogger().warning("fx.presets." + name + ": bad particle '" + line + "'");
                }
            }
            presets.put(name, new Preset(sounds, particles));
        }
    }

    /** Plays a preset for everyone near the location. */
    public void play(String preset, Location at) {
        Preset p = presets.get(preset);
        if (!enabled || p == null || at.getWorld() == null) {
            return;
        }
        for (SoundLayer s : p.sounds()) {
            later(s.delay(), () -> at.getWorld().playSound(sound(s), at.getX(), at.getY(), at.getZ()));
        }
        for (ParticleLayer l : p.particles()) {
            spawn(l, at, null);
        }
    }

    /** Plays a preset that only one player sees and hears (UI feedback). */
    public void playFor(Player player, String preset) {
        playFor(player, preset, player.getLocation());
    }

    public void playFor(Player player, String preset, Location at) {
        Preset p = presets.get(preset);
        if (!enabled || p == null) {
            return;
        }
        for (SoundLayer s : p.sounds()) {
            later(s.delay(), () -> player.playSound(sound(s), at.getX(), at.getY(), at.getZ()));
        }
        for (ParticleLayer l : p.particles()) {
            spawn(l, at, player);
        }
    }

    public boolean has(String preset) {
        return presets.containsKey(preset);
    }

    public java.util.Set<String> presetNames() {
        return presets.keySet();
    }

    protected Sound sound(SoundLayer s) {
        return Sound.sound(s.key(), Sound.Source.PLAYER, s.volume(), s.pitch());
    }

    protected void spawn(ParticleLayer l, Location at, Player only) {
        Object data = switch (l.type().getDataType().getSimpleName()) {
            case "DustOptions" -> new Particle.DustOptions(l.color() == null ? Color.WHITE : l.color(), l.size());
            case "DustTransition" -> new Particle.DustTransition(l.color() == null ? Color.WHITE : l.color(),
                    l.color2() == null ? Color.WHITE : l.color2(), l.size());
            case "Color" -> l.color() == null ? Color.WHITE : l.color();
            case "Float" -> l.size();
            case "Void" -> null;
            default -> null;
        };
        if (data == null && l.type().getDataType() != Void.class) {
            return;
        }
        if (only != null) {
            only.spawnParticle(l.type(), at, l.count(), l.dx(), l.dy(), l.dz(), l.speed(), data);
        } else {
            at.getWorld().spawnParticle(l.type(), at, l.count(), l.dx(), l.dy(), l.dz(), l.speed(), data, true);
        }
    }

    protected void later(int ticks, Runnable r) {
        if (ticks <= 0) {
            r.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
        }
    }

    public static SoundLayer parseSound(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        try {
            Key key = Key.key(parts[0].toLowerCase(Locale.ROOT));
            float vol = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            int delay = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
            return new SoundLayer(key, vol, pitch, delay);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ParticleLayer parseParticle(String line) {
        String[] parts = line.trim().split("\\s+");
        try {
            Particle type = Particle.valueOf(parts[0].toUpperCase(Locale.ROOT));
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            double dx = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;
            double dy = parts.length > 3 ? Double.parseDouble(parts[3]) : 0;
            double dz = parts.length > 4 ? Double.parseDouble(parts[4]) : 0;
            double speed = parts.length > 5 ? Double.parseDouble(parts[5]) : 0;
            Color c1 = null;
            Color c2 = null;
            float size = 1f;
            for (int i = 6; i < parts.length; i++) {
                String p = parts[i];
                if (p.startsWith("#")) {
                    Color c = hex(p);
                    if (c1 == null) {
                        c1 = c;
                    } else {
                        c2 = c;
                    }
                } else {
                    size = Float.parseFloat(p);
                }
            }
            return new ParticleLayer(type, count, dx, dy, dz, speed, c1, c2, size);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Color hex(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        return Color.fromRGB(Integer.parseInt(h, 16));
    }
}
