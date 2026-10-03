package dev.shardwatch.progress;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.profile.Profile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;

/** Chat sigils: little crystal glyphs from the "shardwatch:sigils" font, shown before a staff member's name. */
public final class SigilService {

    public static final Key FONT = Key.key("shardwatch", "sigils");

    /** Glyph for each Facet sigil and Keepsake sigil (must match tools/art/fonts.py). */
    public static final Map<String, String> GLYPHS = Map.of(
            "shardling", "", "prismkeeper", "", "lumenwarden", "", "crownfacet", "",
            "sigil_heart", "", "sigil_star", "", "sigil_moon", "", "sigil_bloom", "");

    private final Shardwatch plugin;

    public SigilService(Shardwatch plugin) {
        this.plugin = plugin;
    }

    /** The active Keepsake sigil, else the Facet's own sigil, else nothing. Ends with a space when present. */
    public Component component(Player p) {
        if (!plugin.getConfig().getBoolean("facets.chat-sigils", true)) {
            return Component.empty();
        }
        Profile prof = plugin.profiles().get(p);
        String id = prof.activeSigil();
        if (id == null) {
            FacetService.Facet f = plugin.facets().of(p);
            id = f == null ? null : f.sigil();
        }
        String glyph = id == null ? null : GLYPHS.get(id);
        if (glyph == null) {
            return Component.empty();
        }
        return Component.text(glyph).font(FONT).color(NamedTextColor.WHITE).append(Component.text(" ").font(Key.key("minecraft", "default")));
    }
}
