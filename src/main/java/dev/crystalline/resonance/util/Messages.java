package dev.crystalline.resonance.util;

import dev.crystalline.resonance.CrystallineResonance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.List;

/** MiniMessage-backed messages from the {@code messages} section of config.yml. */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final CrystallineResonance plugin;
    private ConfigurationSection section = new MemoryConfiguration();
    private Component prefix = Component.empty();

    public Messages(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ConfigurationSection loaded = plugin.getConfig().getConfigurationSection("messages");
        section = loaded != null ? loaded : new MemoryConfiguration();
        prefix = parse(section.getString("prefix", ""));
    }

    /** Parses a MiniMessage string. */
    public static Component parse(String raw, TagResolver... resolvers) {
        return MINI.deserialize(raw == null ? "" : raw, resolvers);
    }

    /** Parses a MiniMessage string for use as an item name or lore line (not italic by default). */
    public static Component parseItemText(String raw, TagResolver... resolvers) {
        return parse(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** The message with the given key, without the prefix. */
    public Component get(String key, TagResolver... resolvers) {
        return parse(section.getString(key, key), resolvers);
    }

    /** Sends a prefixed chat message. Empty messages are not sent. */
    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String raw = section.getString(key, key);
        if (raw.isEmpty()) {
            return;
        }
        sender.sendMessage(prefix.append(parse(raw, resolvers)));
    }

    /** Sends every line of a message list, without the prefix. */
    public void sendLines(CommandSender sender, String key, TagResolver... resolvers) {
        List<String> lines = section.getStringList(key);
        for (String line : lines) {
            sender.sendMessage(parse(line, resolvers));
        }
    }
}
