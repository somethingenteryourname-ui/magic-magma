package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.VerdictType;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Step 1 of passing a verdict: choose the verdict. */
public final class VerdictMenu extends Menu {

    private final OfflinePlayer target;

    public VerdictMenu(Shardwatch plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.verdict.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return Text.p("player", Players.name(target));
    }

    @Override
    protected void build() {
        TagResolver who = Text.p("player", Players.name(target));
        boolean petrified = plugin.verdicts().isPetrified(target.getUniqueId());
        set(4, plugin.icons().head(target.getUniqueId(), Players.name(target), plugin.lang().raw("gui.verdict.target.name"),
                plugin.lang().rawList(target.isOnline() ? "gui.verdict.target.lore-online" : "gui.verdict.target.lore-offline"), who));
        int slot = 10;
        for (VerdictType type : VerdictType.values()) {
            if (!viewer.hasPermission(type.permission())) {
                slot++;
                continue;
            }
            if (type == VerdictType.EJECT && !target.isOnline() || type == VerdictType.PETRIFY && !target.isOnline()) {
                set(slot++, plugin.icons().button("locked", "verdict.offline", Text.pp("type_name",
                        plugin.lang().raw("verdict.names." + type.id()))));
                continue;
            }
            String key = type == VerdictType.PETRIFY && petrified ? "verdict.release" : "verdict." + type.id();
            set(slot++, plugin.icons().button(type.id(), key, who), e -> next(type));
        }
        if (viewer.hasPermission("shardwatch.ledger")) {
            set(16, plugin.icons().button("ledger", "verdict.ledger", who), e -> plugin.menus().openLedger(viewer, target, 0));
        }
        set(22, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openConsole(viewer));
    }

    private void next(VerdictType type) {
        if (type == VerdictType.PETRIFY) {
            close();
            Player online = target.getPlayer();
            if (online != null) {
                plugin.verdicts().togglePetrify(viewer, online);
            }
        } else if (type.timed()) {
            go(new DurationMenu(plugin, viewer, target, type));
        } else {
            go(new ReasonMenu(plugin, viewer, target, type, 0));
        }
    }
}
