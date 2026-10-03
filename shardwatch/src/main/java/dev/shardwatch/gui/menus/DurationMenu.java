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

import java.util.List;

/** Step 2 for Hush and Encase: how long. */
public final class DurationMenu extends Menu {

    private final OfflinePlayer target;
    private final VerdictType type;

    public DurationMenu(Shardwatch plugin, Player viewer, OfflinePlayer target, VerdictType type) {
        super(plugin, viewer);
        this.target = target;
        this.type = type;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.duration.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return TagResolver.resolver(Text.p("player", Players.name(target)),
                Text.pp("type_name", plugin.lang().raw("verdict.names." + type.id())));
    }

    @Override
    protected void build() {
        List<String> presets = plugin.getConfig().getStringList("gui.durations." + type.id());
        int slot = 9;
        for (String preset : presets) {
            Long d = Durations.parse(preset);
            if (d == null || slot > 17) {
                continue;
            }
            boolean perm = d == Durations.PERMANENT;
            if (perm && !viewer.hasPermission(type.permission() + ".permanent")) {
                continue;
            }
            String label = perm ? plugin.lang().raw("general.permanent") : Durations.format(d);
            set(slot++, plugin.icons().amount(plugin.icons().button(perm ? "encase" : "clock",
                    perm ? "duration.permanent" : "duration.preset", Text.p("duration", label)),
                    perm ? 1 : (int) Math.min(64, Math.max(1, d / 3_600_000L))),
                    e -> go(new ReasonMenu(plugin, viewer, target, type, d)));
        }
        set(22, plugin.icons().button("cancel", "common.back"), e -> go(new VerdictMenu(plugin, viewer, target)));
    }
}
