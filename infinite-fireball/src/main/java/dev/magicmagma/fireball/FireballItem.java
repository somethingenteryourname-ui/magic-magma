package dev.magicmagma.fireball;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and recognizes the Infinite Fire Charge item. */
public final class FireballItem {

    private final InfiniteFireball plugin;
    private final NamespacedKey key;

    public FireballItem(InfiniteFireball plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "infinite_fire_charge");
    }

    public ItemStack create() {
        MiniMessage mm = MiniMessage.miniMessage();
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(plugin.getConfig().getString("messages.item-name", "Infinite Fire Charge")));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("messages.item-lore")) {
            lore.add(mm.deserialize(line));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFireballItem(ItemStack item) {
        if (item == null || item.getType() != Material.FIRE_CHARGE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
