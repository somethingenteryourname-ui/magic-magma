package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The Crystal Console: the staff hub. */
public final class ConsoleMenu extends Menu {

    private final int openFlares;

    public ConsoleMenu(Shardwatch plugin, Player viewer, int openFlares) {
        super(plugin, viewer);
        this.openFlares = openFlares;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected String titleKey() {
        return "gui.console.title";
    }

    @Override
    protected void build() {
        var menus = plugin.menus();
        FacetService.Facet facet = plugin.facets().of(viewer);
        var profile = plugin.profiles().get(viewer);
        set(4, plugin.icons().head(viewer.getUniqueId(), viewer.getName(), plugin.lang().raw("gui.console.me.name"),
                plugin.lang().rawList("gui.console.me.lore"), Text.p("player", viewer.getName()),
                Text.pp("facet", facet == null ? plugin.lang().raw("facet.none") : facet.name()),
                Text.p("lustre", profile.lustre()), Text.p("lifetime", profile.lifetime())));
        if (viewer.hasPermission("shardwatch.flares")) {
            set(19, plugin.icons().amount(plugin.icons().button("flare", "console.flares", Text.p("count", openFlares)),
                    openFlares), e -> menus.openFlareBoard(viewer, false, 0));
        }
        if (viewer.hasPermission("shardwatch.verdict.chip")) {
            set(20, plugin.icons().model("verdict_gavel", Material.MACE, plugin.lang().raw("gui.console.judge.name"),
                    plugin.lang().rawList("gui.console.judge.lore")), e -> menus.openPlayerPick(viewer, PlayerPickMenu.Purpose.JUDGE, 0));
        }
        if (viewer.hasPermission("shardwatch.ledger")) {
            set(21, plugin.icons().button("ledger", "console.ledger"),
                    e -> menus.openPlayerPick(viewer, PlayerPickMenu.Purpose.LEDGER, 0));
        }
        if (viewer.hasPermission("shardwatch.glint")) {
            set(22, plugin.icons().button("glint", "console.glint"), e -> menus.openGlint(viewer, 0));
        }
        if (viewer.hasPermission("shardwatch.rewind")) {
            set(23, plugin.icons().button("rewind", "console.rewind"), e -> menus.openRewind(viewer));
        }
        if (viewer.hasPermission("shardwatch.scope")) {
            set(24, plugin.icons().button("scope", "console.scope"), e -> {
                close();
                viewer.performCommand("scope");
            });
        }
        set(25, plugin.icons().button("facet", "console.roster"), e -> menus.openRoster(viewer, 0));
        if (viewer.hasPermission("shardwatch.kit")) {
            set(30, plugin.icons().model("echo_lens", Material.SPYGLASS, plugin.lang().raw("gui.console.kit.name"),
                    plugin.lang().rawList("gui.console.kit.lore")), e -> {
                close();
                viewer.performCommand("shardwatch kit");
            });
        }
        if (viewer.hasPermission("shardwatch.veil")) {
            boolean veiled = plugin.facets().isVeiled(viewer);
            var lantern = plugin.icons().model("veil_lantern", Material.LANTERN, plugin.lang().raw("gui.console.veil.name"),
                    plugin.lang().rawList("gui.console.veil.lore"), Text.pp("state", plugin.lang().raw(veiled ? "gui.state-on" : "gui.state-off")));
            plugin.tools().setLanternLit(lantern, veiled);
            set(31, lantern, e -> {
                plugin.facets().setVeil(viewer, !plugin.facets().isVeiled(viewer));
                refresh();
            });
        }
        if (viewer.hasPermission("shardwatch.hum")) {
            set(32, plugin.icons().button("flare_chat", "console.hum"), e -> {
                plugin.lang().send(viewer, plugin.facets().toggleHum(viewer) ? "hum.mode-on" : "hum.mode-off");
                refresh();
            });
        }
        extra();
        set(40, plugin.icons().button("settings", "console.settings"), e -> menus.openSettings(viewer));
        set(49, plugin.icons().button("close", "common.close"), e -> close());
    }

    /** Extension point for Stage 4 (Lustre and Keepsakes buttons). */
    protected void extra() {
        plugin.menus().consoleExtras(this, viewer);
    }

    public void place(int slot, org.bukkit.inventory.ItemStack icon, Runnable action) {
        set(slot, icon, e -> action.run());
    }
}
