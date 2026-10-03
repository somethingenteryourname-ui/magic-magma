package dev.shardwatch.config;

import dev.shardwatch.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Messages from lang.yml. Every message understands {@code <prefix>} and the palette tags
 * {@code <pink> <rose> <teal> <ice> <muted> <ok> <bad>}.
 */
public final class Lang {

    public static final TextColor PINK = TextColor.fromHexString("#F59AC8");
    public static final TextColor ROSE = TextColor.fromHexString("#D9468F");
    public static final TextColor TEAL = TextColor.fromHexString("#3FD0C9");
    public static final TextColor ICE = TextColor.fromHexString("#B7F2FF");
    public static final TextColor MUTED = TextColor.fromHexString("#8A9BA8");
    public static final TextColor OK = TextColor.fromHexString("#7FE8B0");
    public static final TextColor BAD = TextColor.fromHexString("#FF7A9C");

    private static final TagResolver PALETTE = TagResolver.resolver(
            TagResolver.resolver("pink", Tag.styling(PINK)),
            TagResolver.resolver("rose", Tag.styling(ROSE)),
            TagResolver.resolver("teal", Tag.styling(TEAL)),
            TagResolver.resolver("ice", Tag.styling(ICE)),
            TagResolver.resolver("muted", Tag.styling(MUTED)),
            TagResolver.resolver("ok", Tag.styling(OK)),
            TagResolver.resolver("bad", Tag.styling(BAD)));

    private final JavaPlugin plugin;
    private YamlConfiguration yaml;
    private TagResolver base;

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("lang.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default lang.yml: " + e.getMessage());
        }
        Component prefix = Text.mm(yaml.getString("prefix", ""), PALETTE);
        base = TagResolver.resolver(PALETTE, TagResolver.resolver("prefix", Tag.inserting(prefix)));
    }

    public static TagResolver palette() {
        return PALETTE;
    }

    public TagResolver base() {
        return base;
    }

    public String raw(String key) {
        return yaml.getString(key, "<bad>missing lang: " + key);
    }

    public List<String> rawList(String key) {
        return yaml.getStringList(key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return parse(raw(key), resolvers);
    }

    /** Parses any MiniMessage string with the palette, prefix and the given placeholders. */
    public Component parse(String text, TagResolver... resolvers) {
        return Text.mm(text, all(resolvers));
    }

    public Component item(String text, TagResolver... resolvers) {
        return Text.item(text, all(resolvers));
    }

    public List<Component> itemLines(List<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        TagResolver r = all(resolvers);
        for (String line : lines) {
            out.add(Text.item(line, r));
        }
        return out;
    }

    public List<Component> getLines(String key, TagResolver... resolvers) {
        return itemLines(rawList(key), resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (!raw.isEmpty()) {
            to.sendMessage(parse(raw, resolvers));
        }
    }

    public void sendActionBar(org.bukkit.entity.Player to, String key, TagResolver... resolvers) {
        to.sendActionBar(get(key, resolvers));
    }

    public void send(Collection<? extends CommandSender> to, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        Component c = parse(raw, resolvers);
        for (CommandSender s : to) {
            s.sendMessage(c);
        }
    }

    private TagResolver all(TagResolver... resolvers) {
        TagResolver.Builder b = TagResolver.builder().resolver(base);
        for (TagResolver r : resolvers) {
            b.resolver(r);
        }
        return b.build();
    }
}
