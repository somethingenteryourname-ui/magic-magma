package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.flare.FlareService;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Flare Board: open (or all) Flares; click one for actions. */
public final class FlareBoardMenu extends PagedMenu<Flare> {

    private final boolean all;

    public FlareBoardMenu(Shardwatch plugin, Player viewer, List<Flare> flares, boolean all, int page) {
        super(plugin, viewer, flares, page);
        this.all = all;
    }

    @Override
    protected String titleKey() {
        return all ? "gui.flares.title-all" : "gui.flares.title";
    }

    @Override
    protected ItemStack entryIcon(Flare f) {
        FlareService.Category cat = plugin.flares().category(f.category());
        TagResolver r = plugin.flares().resolvers(f);
        String key = "gui.flares.entry-" + f.status().name().toLowerCase();
        return plugin.icons().icon(cat.icon(), plugin.lang().raw("gui.flares.entry.name"), plugin.lang().rawList(key), r);
    }

    @Override
    protected void onEntry(Flare f, InventoryClickEvent e) {
        go(new FlareActionMenu(plugin, viewer, f));
    }

    @Override
    protected Runnable back() {
        return () -> plugin.menus().openConsole(viewer);
    }

    @Override
    protected void navigation() {
        set(46, plugin.icons().button("filter", all ? "flares.filter-all" : "flares.filter-open"),
                e -> plugin.menus().openFlareBoard(viewer, !all, 0));
    }
}
