package dev.shardwatch.gui;

import dev.shardwatch.Shardwatch;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds menu icons: a vanilla fallback item (for players without the pack) carrying a Shardwatch item_model,
 * the crystal tooltip style, a name and lore from lang.yml.
 */
public final class Icons {

    public static final Key TOOLTIP = Key.key("shardwatch", "crystal");

    /** What players without the resource pack see. */
    private static final Map<String, Material> FALLBACK = Map.ofEntries(
            Map.entry("flare", Material.FIREWORK_STAR), Map.entry("chip", Material.PINK_DYE),
            Map.entry("hush", Material.CYAN_DYE), Map.entry("eject", Material.FEATHER),
            Map.entry("encase", Material.AMETHYST_BLOCK), Map.entry("petrify", Material.PACKED_ICE),
            Map.entry("ledger", Material.WRITABLE_BOOK), Map.entry("rewind", Material.CLOCK),
            Map.entry("glint", Material.ENDER_EYE), Map.entry("facet", Material.GOLDEN_HELMET),
            Map.entry("scope", Material.MAP), Map.entry("lustre", Material.NETHER_STAR),
            Map.entry("refine", Material.ANVIL), Map.entry("keepsake", Material.CHEST),
            Map.entry("locked", Material.BARRIER), Map.entry("confirm", Material.LIME_DYE),
            Map.entry("cancel", Material.RED_DYE), Map.entry("prev", Material.ARROW),
            Map.entry("next", Material.SPECTRAL_ARROW), Map.entry("close", Material.BARRIER),
            Map.entry("filter", Material.HOPPER), Map.entry("info", Material.BOOK),
            Map.entry("settings", Material.COMPARATOR), Map.entry("clock", Material.CLOCK),
            Map.entry("pane", Material.GRAY_STAINED_GLASS_PANE));

    private final Shardwatch plugin;
    private ItemStack pane;

    public Icons(Shardwatch plugin) {
        this.plugin = plugin;
    }

    /** An icon from {@code assets/shardwatch/items/icon_<id>.json}. */
    public ItemStack icon(String id, String nameRaw, List<String> loreRaw, TagResolver... r) {
        String root = id.startsWith("flare_") ? "flare" : id;
        return model("icon_" + id, FALLBACK.getOrDefault(root, Material.PAPER), nameRaw, loreRaw, r);
    }

    /** An icon whose name and lore come from {@code gui.<key>.name / .lore}. */
    public ItemStack button(String id, String langKey, TagResolver... r) {
        return icon(id, plugin.lang().raw("gui." + langKey + ".name"), plugin.lang().rawList("gui." + langKey + ".lore"), r);
    }

    /** Any Shardwatch model (tools and sigils work as icons too). */
    public ItemStack model(String modelId, Material fallback, String nameRaw, List<String> loreRaw, TagResolver... r) {
        ItemStack item = ItemStack.of(fallback);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("shardwatch", modelId));
        return dress(item, nameRaw, loreRaw, r);
    }

    public ItemStack head(UUID uuid, String name, String nameRaw, List<String> loreRaw, TagResolver... r) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(uuid)
                .name(name != null && name.matches("^[!-~]{0,16}$") ? name : null).build());
        return dress(item, nameRaw, loreRaw, r);
    }

    private ItemStack dress(ItemStack item, String nameRaw, List<String> loreRaw, TagResolver... r) {
        item.setData(DataComponentTypes.ITEM_NAME, plugin.lang().item(nameRaw, r));
        if (loreRaw != null && !loreRaw.isEmpty()) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(plugin.lang().itemLines(loreRaw, r)));
        }
        style(item);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.PROFILE).build());
        return item;
    }

    public void style(ItemStack item) {
        if (plugin.getConfig().getBoolean("gui.tooltip-style", true)) {
            item.setData(DataComponentTypes.TOOLTIP_STYLE, TOOLTIP);
        }
    }

    public ItemStack amount(ItemStack item, int n) {
        item.setAmount(Math.max(1, Math.min(99, n)));
        if (n > 1) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 99);
        }
        return item;
    }

    /** Background filler: no tooltip at all. */
    public ItemStack pane() {
        if (pane == null) {
            pane = ItemStack.of(FALLBACK.get("pane"));
            pane.setData(DataComponentTypes.ITEM_MODEL, Key.key("shardwatch", "icon_pane"));
            pane.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        }
        return pane;
    }
}
