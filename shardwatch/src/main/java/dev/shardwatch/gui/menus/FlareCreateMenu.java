package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.flare.FlareService;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Lets a player pick a Flare category (and optionally type details) for a report. */
public final class FlareCreateMenu extends Menu {

    private final OfflinePlayer target;
    private String details;

    public FlareCreateMenu(Shardwatch plugin, Player viewer, OfflinePlayer target, String details) {
        super(plugin, viewer);
        this.target = target;
        this.details = details;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.flare-create.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return Text.p("player", Players.name(target));
    }

    @Override
    protected void build() {
        TagResolver who = TagResolver.resolver(Text.p("player", Players.name(target)),
                Text.p("details", details == null ? "-" : details));
        set(4, plugin.icons().head(target.getUniqueId(), Players.name(target), plugin.lang().raw("gui.flare-create.target.name"),
                plugin.lang().rawList("gui.flare-create.target.lore"), who));
        int slot = 10;
        for (FlareService.Category cat : plugin.flares().categories().values()) {
            if (slot > 16) {
                break;
            }
            java.util.List<String> lore = new java.util.ArrayList<>(cat.description());
            lore.addAll(plugin.lang().rawList("gui.flare-create.category-lore"));
            set(slot++, plugin.icons().icon(cat.icon(), cat.name(), lore, who), e -> {
                close();
                plugin.flares().file(viewer, target, cat.id(), details == null ? "" : details);
            });
        }
        set(22, plugin.icons().button("ledger", "flare-create.details", who), e ->
                plugin.prompts().ask(viewer, "gui.prompt-details", text -> {
                    details = text;
                    go(new FlareCreateMenu(plugin, viewer, target, details));
                }, () -> go(new FlareCreateMenu(plugin, viewer, target, details))));
        set(26, plugin.icons().button("close", "common.close"), e -> close());
    }
}
