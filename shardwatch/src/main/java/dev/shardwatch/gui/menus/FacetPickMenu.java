package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Choose a Facet for a staff member. Each Facet shows its sigil model. */
public final class FacetPickMenu extends Menu {

    private final OfflinePlayer target;
    private final String name;

    public FacetPickMenu(Shardwatch plugin, Player viewer, OfflinePlayer target, String name) {
        super(plugin, viewer);
        this.target = target;
        this.name = name;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.facet-pick.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return Text.p("player", name);
    }

    @Override
    protected void build() {
        int slot = 10;
        for (FacetService.Facet f : plugin.facets().all().values()) {
            if (slot > 15) {
                break;
            }
            set(slot++, plugin.icons().model("sigil_" + f.sigil(), Material.AMETHYST_SHARD, f.name(),
                    plugin.lang().rawList("gui.facet-pick.entry"), Text.p("player", name), Text.p("weight", f.weight()),
                    Text.p("perms", f.permissions().size())), e -> {
                close();
                plugin.facets().set(viewer, target, f);
            });
        }
        set(16, plugin.icons().button("cancel", "facet-pick.none", Text.p("player", name)), e -> {
            close();
            plugin.facets().set(viewer, target, null);
        });
        set(22, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openRoster(viewer, 0));
    }
}
