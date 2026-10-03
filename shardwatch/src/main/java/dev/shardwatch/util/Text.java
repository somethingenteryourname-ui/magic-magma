package dev.shardwatch.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** MiniMessage helpers. */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String s, TagResolver... resolvers) {
        return MM.deserialize(s == null ? "" : s, resolvers);
    }

    /** Parses text for item names/lore: no default italics. */
    public static Component item(String s, TagResolver... resolvers) {
        return mm(s, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> lore(List<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line, resolvers));
        }
        return out;
    }

    /** Placeholder whose value is inserted literally (no tags parsed) — use for player input. */
    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** Placeholder whose value is parsed as MiniMessage — use only for trusted config text. */
    public static TagResolver pp(String key, String value) {
        return Placeholder.parsed(key, value == null ? "" : value);
    }

    public static TagResolver pc(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String escape(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }

    public static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
