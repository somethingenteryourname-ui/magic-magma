package dev.magicnuke;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** MiniMessage helpers. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#ff5e3a:#ffcc33><bold>☢ MagicNuke</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    /** Parsed text with italics turned off (for item names and lore). */
    public static Component item(String text) {
        return MM.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void send(Audience to, String text) {
        to.sendMessage(MM.deserialize(PREFIX + text));
    }

    public static String strip(String text) {
        return MM.stripTags(text);
    }

    public static String escape(String text) {
        return MM.escapeTags(text);
    }
}
