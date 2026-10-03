package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.gui.Menu;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

/** Actions for one Flare: claim, teleport, resolve with a preset outcome, dismiss, judge, Ledger. */
public final class FlareActionMenu extends Menu {

    private final Flare flare;

    public FlareActionMenu(Shardwatch plugin, Player viewer, Flare flare) {
        super(plugin, viewer);
        this.flare = flare;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.flare-action.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return plugin.flares().resolvers(flare);
    }

    @Override
    protected void build() {
        TagResolver r = plugin.flares().resolvers(flare);
        OfflinePlayer target = Bukkit.getOfflinePlayer(flare.target());
        set(4, plugin.icons().head(flare.target(), flare.targetName(), plugin.lang().raw("gui.flare-action.target.name"),
                plugin.lang().rawList("gui.flare-action.target.lore"), r));
        boolean handle = viewer.hasPermission("shardwatch.flares.handle");
        if (handle && flare.status() == Flare.Status.OPEN) {
            set(10, plugin.icons().button("confirm", "flare-action.claim", r), e -> {
                plugin.flares().claim(viewer, flare.id());
                close();
            });
        }
        if (viewer.hasPermission("shardwatch.flares.teleport")) {
            set(11, plugin.icons().button("flare", "flare-action.teleport", r), e -> {
                close();
                plugin.flares().teleport(viewer, flare);
            });
        }
        if (handle && !flare.status().closed()) {
            List<String> outcomes = plugin.getConfig().getStringList("gui.flare-outcomes");
            int slot = 12;
            for (String outcome : outcomes) {
                if (slot > 14) {
                    break;
                }
                set(slot++, plugin.icons().button("refine", "flare-action.resolve", r,
                        dev.shardwatch.util.Text.p("outcome", outcome)), e -> {
                    plugin.flares().close(viewer, flare.id(), true, outcome);
                    plugin.menus().openFlareBoard(viewer, false, 0);
                });
            }
            set(15, plugin.icons().button("info", "flare-action.resolve-custom", r), e ->
                    plugin.prompts().ask(viewer, "gui.prompt-outcome", text -> {
                        plugin.flares().close(viewer, flare.id(), true, text);
                        plugin.menus().openFlareBoard(viewer, false, 0);
                    }, () -> go(new FlareActionMenu(plugin, viewer, flare))));
            set(16, plugin.icons().button("cancel", "flare-action.dismiss", r), e ->
                    plugin.prompts().ask(viewer, "gui.prompt-dismiss", text -> {
                        plugin.flares().close(viewer, flare.id(), false, text);
                        plugin.menus().openFlareBoard(viewer, false, 0);
                    }, () -> go(new FlareActionMenu(plugin, viewer, flare))));
        }
        if (viewer.hasPermission("shardwatch.verdict.chip")) {
            set(20, plugin.icons().model("verdict_gavel", org.bukkit.Material.MACE, plugin.lang().raw("gui.flare-action.judge.name"),
                    plugin.lang().rawList("gui.flare-action.judge.lore"), r), e -> plugin.menus().openVerdict(viewer, target));
        }
        if (viewer.hasPermission("shardwatch.ledger")) {
            set(21, plugin.icons().button("ledger", "flare-action.ledger", r), e -> plugin.menus().openLedger(viewer, target, 0));
        }
        set(22, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openFlareBoard(viewer, false, 0));
    }
}
