package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.profile.Profile;
import dev.shardwatch.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Every staff member with their Facet and Lustre. Managers can click to change a Facet. */
public final class RosterMenu extends PagedMenu<Profile> {

    public RosterMenu(Shardwatch plugin, Player viewer, List<Profile> staff, int page) {
        super(plugin, viewer, staff, page);
    }

    @Override
    protected String titleKey() {
        return "gui.roster.title";
    }

    @Override
    protected ItemStack entryIcon(Profile p) {
        FacetService.Facet f = plugin.facets().get(p.facet());
        boolean online = Bukkit.getPlayer(p.uuid()) != null;
        return plugin.icons().head(p.uuid(), p.name(), plugin.lang().raw("gui.roster.entry-name"),
                plugin.lang().rawList(viewer.hasPermission("shardwatch.facet.manage") ? "gui.roster.entry-manage" : "gui.roster.entry"),
                Text.p("player", p.name()), Text.pp("facet", f == null ? plugin.lang().raw("facet.none") : f.name()),
                Text.p("lustre", p.lustre()), Text.p("lifetime", p.lifetime()),
                Text.pp("online", plugin.lang().raw(online ? "gui.online" : "gui.offline")));
    }

    @Override
    protected void onEntry(Profile p, InventoryClickEvent e) {
        if (viewer.hasPermission("shardwatch.facet.manage")) {
            go(new FacetPickMenu(plugin, viewer, Bukkit.getOfflinePlayer(p.uuid()), p.name()));
        }
    }

    @Override
    protected Runnable back() {
        return () -> plugin.menus().openConsole(viewer);
    }
}
