package dev.magicnuke.nuke;

import dev.magicnuke.Keys;
import dev.magicnuke.MagicNuke;
import dev.magicnuke.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and recognises nuke items. They are TNT with the 3D missile item model. */
public final class NukeItems {

    private final MagicNuke plugin;

    public NukeItems(MagicNuke plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(NukeSize size, int amount) {
        ItemStack item = new ItemStack(Material.TNT, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item("<red>☢</red> " + size.name() + " <red>☢</red>"));
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.item("<dark_gray>Thermonuclear device"));
        lore.add(Component.empty());
        lore.add(Msg.item("<gray>Blast radius: <gold>" + fmt(size.radius()) + " blocks"));
        lore.add(Msg.item("<gray>Flight height: <gold>" + fmt(size.flightHeight()) + " blocks"));
        lore.add(Msg.item("<gray>Fuse: <gold>" + fmt(size.fuseTicks() / 20.0) + "s"));
        lore.add(Component.empty());
        lore.add(Msg.item("<yellow>Right-click <gray>the ground to set it up."));
        lore.add(Msg.item("<yellow>Flint and Steel<gray>, a fire charge or"));
        lore.add(Msg.item("<gray>redstone launches it. <yellow>Punch <gray>to pick up."));
        meta.lore(lore);
        meta.setItemModel(Keys.MODEL_NUKE);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.keys().size, PersistentDataType.STRING, size.id());
        pdc.set(plugin.keys().radius, PersistentDataType.DOUBLE, size.radius());
        item.setItemMeta(meta);
        return item;
    }

    /** The plain item shown by the display entity. */
    public ItemStack displayStack() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(Keys.MODEL_NUKE);
        item.setItemMeta(meta);
        return item;
    }

    /** An item showing one of the explosion effect models (fx_fireball, fx_smoke...). */
    public static ItemStack fxStack(String model) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(Keys.model(model));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isNuke(ItemStack item) {
        if (item == null || item.getType() != Material.TNT || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.keys().size, PersistentDataType.STRING);
    }

    /** The size of a nuke item, or null if it isn't one. */
    public NukeSize sizeOf(ItemStack item) {
        if (!isNuke(item)) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return sizeOf(pdc.get(plugin.keys().size, PersistentDataType.STRING),
                pdc.getOrDefault(plugin.keys().radius, PersistentDataType.DOUBLE, 16.0));
    }

    /** Looks a size up by id; falls back to a custom size if the preset no longer exists. */
    public NukeSize sizeOf(String id, double radius) {
        if (id != null && !NukeSize.CUSTOM_ID.equals(id)) {
            NukeSize preset = plugin.settings().sizes.get(id);
            if (preset != null) return preset;
        }
        return NukeSize.custom(radius);
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
