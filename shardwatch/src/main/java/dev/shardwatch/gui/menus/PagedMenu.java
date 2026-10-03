package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** A six-row list: 45 entry slots and a navigation row. */
abstract class PagedMenu<T> extends Menu {

    static final int PER_PAGE = 45;

    protected final List<T> entries;
    protected int page;

    PagedMenu(Shardwatch plugin, Player viewer, List<T> entries, int page) {
        super(plugin, viewer);
        this.entries = entries;
        this.page = page;
    }

    @Override
    protected int rows() {
        return 6;
    }

    protected abstract ItemStack entryIcon(T entry);

    protected abstract void onEntry(T entry, org.bukkit.event.inventory.InventoryClickEvent event);

    /** Extra buttons in the navigation row (slots 46-52 except 48/49). */
    protected void navigation() {
    }

    protected Runnable back() {
        return null;
    }

    @Override
    protected void build() {
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= entries.size()) {
                break;
            }
            T entry = entries.get(idx);
            set(i, entryIcon(entry), e -> onEntry(entry, e));
        }
        if (entries.isEmpty()) {
            set(22, plugin.icons().button("info", "common.empty"));
        }
        if (page > 0) {
            set(45, plugin.icons().button("prev", "common.prev", Text.p("page", page), Text.p("pages", pages)), e -> {
                page--;
                refresh();
                plugin.fx().playFor(viewer, "ui-page");
            });
        }
        if (page < pages - 1) {
            set(53, plugin.icons().button("next", "common.next", Text.p("page", page + 2), Text.p("pages", pages)), e -> {
                page++;
                refresh();
                plugin.fx().playFor(viewer, "ui-page");
            });
        }
        Runnable back = back();
        if (back != null) {
            set(48, plugin.icons().button("cancel", "common.back"), e -> back.run());
        }
        set(49, plugin.icons().button("close", "common.close"), e -> close());
        navigation();
    }

    protected Consumer<org.bukkit.event.inventory.InventoryClickEvent> reopen(Menu next) {
        return e -> go(next);
    }
}
