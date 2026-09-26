package dev.crystalline.resonance.item;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.crystal.CrystalSettings.CrystalDefinition;
import dev.crystalline.resonance.crystal.CrystalType;
import dev.crystalline.resonance.spell.SpellSettings;
import dev.crystalline.resonance.spell.SpellType;
import dev.crystalline.resonance.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and identifies crystal and spell tome items. */
public final class CustomItems {

    private final CrystallineResonance plugin;

    public CustomItems(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    public ItemStack createCrystal(CrystalType type, int amount) {
        CrystalDefinition definition = plugin.crystalSettings().definition(type);
        ItemStack item = new ItemStack(definition.material(), amount);
        item.editMeta(meta -> {
            meta.displayName(Messages.parseItemText(definition.name()));
            meta.lore(parseLore(definition.lore(), TagResolver.empty()));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(plugin.keys().crystal, PersistentDataType.STRING, type.id());
        });
        return item;
    }

    public ItemStack createTome(SpellType spell) {
        SpellSettings settings = plugin.spells().settings(spell);
        ConfigurationSection tome = settings.section().getConfigurationSection("tome");
        String materialName = tome != null ? tome.getString("material", "BOOK") : "BOOK";
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem() || material.isAir()) {
            plugin.getLogger().warning("Invalid tome material '" + materialName + "' for spell " + spell.id() + "; using BOOK.");
            material = Material.BOOK;
        }

        TagResolver placeholders = TagResolver.resolver(
                Placeholder.component("spell", settings.displayName()),
                Placeholder.unparsed("cost", SpellSettings.formatNumber(settings.manaCost())),
                Placeholder.unparsed("cooldown", SpellSettings.formatNumber(settings.cooldownMillis() / 1000.0))
        );
        String name = tome != null ? tome.getString("name", "<spell>") : "<spell>";
        List<String> lore = tome != null ? tome.getStringList("lore") : List.of();

        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Messages.parseItemText(name, placeholders));
            meta.lore(parseLore(lore, placeholders));
            meta.setEnchantmentGlintOverride(true);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(plugin.keys().tome, PersistentDataType.STRING, spell.id());
        });
        return item;
    }

    /** The crystal type of an item, or {@code null} if it isn't a crystal. */
    public CrystalType crystalType(ItemStack item) {
        return CrystalType.fromId(readTag(item, plugin.keys().crystal));
    }

    /** The spell of a tome, or {@code null} if the item isn't a tome. */
    public SpellType tomeSpell(ItemStack item) {
        return SpellType.fromId(readTag(item, plugin.keys().tome));
    }

    /** Whether the item is any CrystallineResonance item. */
    public boolean isCustom(ItemStack item) {
        return crystalType(item) != null || tomeSpell(item) != null;
    }

    private static String readTag(ItemStack item, NamespacedKey key) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static List<Component> parseLore(List<String> lines, TagResolver placeholders) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(Messages.parseItemText(line, placeholders));
        }
        return lore;
    }
}
