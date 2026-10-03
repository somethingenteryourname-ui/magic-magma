package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.VerdictType;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Step 4: summary, silent toggle, confirm. */
public final class ConfirmMenu extends Menu {

    private final OfflinePlayer target;
    private final VerdictType type;
    private final long duration;
    private final String reason;
    private boolean silent;

    public ConfirmMenu(Shardwatch plugin, Player viewer, OfflinePlayer target, VerdictType type, long duration,
                       String reason, boolean silent) {
        super(plugin, viewer);
        this.target = target;
        this.type = type;
        this.duration = duration;
        this.reason = reason;
        this.silent = silent;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.confirm.title";
    }

    @Override
    protected void build() {
        TagResolver r = TagResolver.resolver(Text.p("player", Players.name(target)), Text.p("reason", reason),
                Text.pp("type_name", plugin.lang().raw("verdict.names." + type.id())),
                Text.p("duration", !type.timed() ? "-" : duration == 0 ? plugin.lang().raw("general.permanent")
                        : Durations.format(duration)),
                Text.pp("silent", plugin.lang().raw(silent ? "gui.state-on" : "gui.state-off")));
        set(4, plugin.icons().head(target.getUniqueId(), Players.name(target), plugin.lang().raw("gui.confirm.summary.name"),
                plugin.lang().rawList("gui.confirm.summary.lore"), r));
        set(11, plugin.icons().button("confirm", "confirm.accept", r), e -> {
            close();
            plugin.fx().playFor(viewer, "ui-confirm");
            if (plugin.verdicts().canJudge(viewer, target)) {
                plugin.verdicts().issue(viewer, target, type, duration, reason, silent);
            }
        });
        set(13, plugin.icons().button(silent ? "hush" : "flare_chat", "confirm.silent", r), e -> {
            silent = !silent;
            refresh();
        });
        set(15, plugin.icons().button("cancel", "confirm.decline", r), e -> close());
    }
}
