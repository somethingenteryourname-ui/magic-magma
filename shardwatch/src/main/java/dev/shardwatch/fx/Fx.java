package dev.shardwatch.fx;

import dev.shardwatch.Shardwatch;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named effect presets from config. Each preset has:
 * <pre>
 * sounds:    custom pack sounds   "shardwatch:event volume pitch [delayTicks]"
 * fallback:  vanilla sounds for players without the pack (same format)
 * particles: "PARTICLE count dx dy dz speed [#color] [#color2] [size]"
 *            "ITEM count dx dy dz speed shardwatch:particle_shard"   (custom crystal particles)
 * </pre>
 * Who hears what follows {@code fx.sound-mode}: auto (custom if the player's pack loaded, else fallback),
 * custom, vanilla or both.
 */
public class Fx implements Listener {

    public record SoundLayer(Key key, float volume, float pitch, int delay) {
    }

    public record ParticleLayer(Particle type, int count, double dx, double dy, double dz, double speed,
                                Color color, Color color2, float size, Key model) {
    }

    public record Preset(List<SoundLayer> sounds, List<SoundLayer> fallback, List<ParticleLayer> particles) {
    }

    protected final Shardwatch plugin;
    protected final Map<String, Preset> presets = new HashMap<>();
    private final Set<UUID> packLoaded = ConcurrentHashMap.newKeySet();
    private final Map<Key, ItemStack> particleItems = new HashMap<>();
    protected boolean enabled;
    private String mode;
    private double radius;

    public Fx(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        presets.clear();
        enabled = plugin.getConfig().getBoolean("fx.enabled", true);
        mode = plugin.getConfig().getString("fx.sound-mode", "auto").toLowerCase(Locale.ROOT);
        radius = plugin.getConfig().getDouble("fx.hear-radius", 24);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("fx.presets");
        if (sec == null) {
            return;
        }
        for (String name : sec.getKeys(false)) {
            presets.put(name, new Preset(sounds(sec, name + ".sounds"), sounds(sec, name + ".fallback"),
                    particles(sec, name + ".particles")));
        }
    }

    private List<SoundLayer> sounds(ConfigurationSection sec, String path) {
        List<SoundLayer> out = new ArrayList<>();
        for (String line : sec.getStringList(path)) {
            SoundLayer s = parseSound(line);
            if (s != null) {
                out.add(s);
            } else {
                plugin.getLogger().warning("fx.presets." + path + ": bad sound '" + line + "'");
            }
        }
        return out;
    }

    private List<ParticleLayer> particles(ConfigurationSection sec, String path) {
        List<ParticleLayer> out = new ArrayList<>();
        for (String line : sec.getStringList(path)) {
            ParticleLayer p = parseParticle(line);
            if (p != null) {
                out.add(p);
            } else {
                plugin.getLogger().warning("fx.presets." + path + ": bad particle '" + line + "'");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ pack status

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> packLoaded.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> packLoaded.remove(event.getPlayer().getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packLoaded.remove(event.getPlayer().getUniqueId());
    }

    public boolean hasPack(Player p) {
        return packLoaded.contains(p.getUniqueId());
    }

    private List<SoundLayer> layersFor(Player listener, Preset p) {
        return switch (mode) {
            case "custom" -> p.sounds();
            case "vanilla" -> p.fallback().isEmpty() ? p.sounds() : p.fallback();
            case "both" -> {
                List<SoundLayer> all = new ArrayList<>(p.sounds());
                all.addAll(p.fallback());
                yield all;
            }
            default -> hasPack(listener) || p.fallback().isEmpty() ? p.sounds() : p.fallback();
        };
    }

    private boolean wants(Player p) {
        return plugin.profiles() == null || plugin.profiles().get(p).fxOn();
    }

    // ------------------------------------------------------------------ playing

    /** Plays a preset at a location: everyone in range hears it (their own variant) and sees the particles. */
    public void play(String preset, Location at) {
        Preset p = presets.get(preset);
        if (!enabled || p == null || at.getWorld() == null) {
            return;
        }
        for (Player listener : at.getWorld().getPlayers()) {
            if (listener.getLocation().distanceSquared(at) > radius * radius) {
                continue;
            }
            for (SoundLayer s : layersFor(listener, p)) {
                later(s.delay(), () -> listener.playSound(sound(s), at.getX(), at.getY(), at.getZ()));
            }
        }
        for (ParticleLayer l : p.particles()) {
            spawn(l, at, null);
        }
    }

    /** Plays a preset only this player sees and hears (UI feedback). Respects their Effects setting for particles. */
    public void playFor(Player player, String preset) {
        playFor(player, preset, player.getLocation());
    }

    public void playFor(Player player, String preset, Location at) {
        Preset p = presets.get(preset);
        if (!enabled || p == null) {
            return;
        }
        for (SoundLayer s : layersFor(player, p)) {
            later(s.delay(), () -> player.playSound(sound(s), at.getX(), at.getY(), at.getZ()));
        }
        if (wants(player)) {
            for (ParticleLayer l : p.particles()) {
                spawn(l, at, player);
            }
        }
    }

    public boolean has(String preset) {
        return presets.containsKey(preset);
    }

    public Set<String> presetNames() {
        return presets.keySet();
    }

    protected Sound sound(SoundLayer s) {
        return Sound.sound(s.key(), Sound.Source.PLAYER, s.volume(), s.pitch());
    }

    /** An item stack that renders as a Shardwatch model (for ITEM particles and display animations). */
    public ItemStack modelItem(Key model) {
        return particleItems.computeIfAbsent(model, k -> {
            ItemStack item = ItemStack.of(Material.AMETHYST_SHARD);
            item.setData(DataComponentTypes.ITEM_MODEL, k);
            return item;
        }).clone();
    }

    protected void spawn(ParticleLayer l, Location at, Player only) {
        Object data = switch (l.type().getDataType().getSimpleName()) {
            case "DustOptions" -> new Particle.DustOptions(l.color() == null ? Color.WHITE : l.color(), l.size());
            case "DustTransition" -> new Particle.DustTransition(l.color() == null ? Color.WHITE : l.color(),
                    l.color2() == null ? Color.WHITE : l.color2(), l.size());
            case "Color" -> l.color() == null ? Color.WHITE : l.color();
            case "Float" -> l.size();
            case "ItemStack" -> modelItem(l.model() == null ? Key.key("shardwatch", "particle_shard") : l.model());
            default -> null;
        };
        if (data == null && l.type().getDataType() != Void.class) {
            return;
        }
        if (only != null) {
            only.spawnParticle(l.type(), at, l.count(), l.dx(), l.dy(), l.dz(), l.speed(), data);
            return;
        }
        for (Player viewer : at.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) <= 64 * 64 && wants(viewer)) {
                viewer.spawnParticle(l.type(), at, l.count(), l.dx(), l.dy(), l.dz(), l.speed(), data);
            }
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
            Key model = null;
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
                } else if (p.contains(":")) {
                    model = Key.key(p.toLowerCase(Locale.ROOT));
                } else {
                    size = Float.parseFloat(p);
                }
            }
            return new ParticleLayer(type, count, dx, dy, dz, speed, c1, c2, size, model);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Color hex(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        return Color.fromRGB(Integer.parseInt(h, 16));
    }
}
