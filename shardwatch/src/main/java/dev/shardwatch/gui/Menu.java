package dev.shardwatch.gui;

import dev.shardwatch.Shardwatch;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for every menu. Subclasses lay out buttons in {@link #build()}; {@link #refresh()} rebuilds in place.
 * When {@code gui.custom-backgrounds} is on, the title carries a font glyph that draws the crystal panel behind the slots.
 */
public abstract class Menu {

    private static final Key GUI_FONT = Key.key("shardwatch", "gui");

    protected final Shardwatch plugin;
    protected final Player viewer;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private MenuHolder holder;

    protected Menu(Shardwatch plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    /** 3 or 6 (the background glyphs exist for those two sizes). */
    protected abstract int rows();

    protected abstract String titleKey();

    protected TagResolver titleResolvers() {
        return TagResolver.empty();
    }

    protected abstract void build();

    public void open() {
        holder = new MenuHolder(this);
        holder.inventory = Bukkit.createInventory(holder, rows() * 9, fullTitle());
        render();
        viewer.openInventory(holder.inventory);
        plugin.fx().playFor(viewer, "ui-open");
    }

    /** Rebuilds the buttons without reopening (keeps the cursor where it is). */
    public void refresh() {
        if (holder != null) {
            render();
        }
    }

    private void render() {
        actions.clear();
        holder.inventory.clear();
        build();
        if (plugin.getConfig().getBoolean("gui.filler", true)) {
            ItemStack pane = plugin.icons().pane();
            for (int i = 0; i < holder.inventory.getSize(); i++) {
                if (holder.inventory.getItem(i) == null) {
                    holder.inventory.setItem(i, pane);
                }
            }
        }
    }

    protected void set(int slot, ItemStack icon, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= rows() * 9) {
            return;
        }
        holder.inventory.setItem(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void set(int slot, ItemStack icon) {
        set(slot, icon, null);
    }

    void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action == null) {
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        plugin.fx().playFor(viewer, "ui-click");
        action.accept(event);
    }

    protected void close() {
        viewer.closeInventory();
    }

    protected Component fullTitle() {
        Component title = plugin.lang().get(titleKey(), titleResolvers());
        if (!plugin.getConfig().getBoolean("gui.custom-backgrounds", true)) {
            return title;
        }
        char glyph = rows() >= 6 ? '' : '';
        Component bg = Component.text(shift(-8) + glyph + shift(-169)).font(GUI_FONT).color(NamedTextColor.WHITE);
        return Component.text().append(bg).append(title).build();
    }

    /** Builds a run of space glyphs that moves the text cursor by {@code px} GUI pixels. */
    public static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        int left = Math.abs(px);
        char base = px < 0 ? '' : '';
        for (int bit = 7; bit >= 0; bit--) {
            int size = 1 << bit;
            while (left >= size) {
                sb.append((char) (base + bit));
                left -= size;
            }
        }
        return sb.toString();
    }

    /** Opens another menu next tick (opening from inside a click handler is not allowed). */
    protected void go(Menu next) {
        Bukkit.getScheduler().runTask(plugin, next::open);
    }
}
