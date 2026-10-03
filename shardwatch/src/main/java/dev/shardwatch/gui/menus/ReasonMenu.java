package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.VerdictType;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Step 3: a preset reason or a typed one. */
public final class ReasonMenu extends Menu {

    private final OfflinePlayer target;
    private final VerdictType type;
    private final long duration;

    public ReasonMenu(Shardwatch plugin, Player viewer, OfflinePlayer target, VerdictType type, long duration) {
        super(plugin, viewer);
        this.target = target;
        this.type = type;
        this.duration = duration;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.reason.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return Text.pp("type_name", plugin.lang().raw("verdict.names." + type.id()));
    }

    @Override
    protected void build() {
        ConfigurationSection presets = plugin.getConfig().getConfigurationSection("verdicts.reason-presets");
        int slot = 9;
        if (presets != null) {
            for (String id : presets.getKeys(false)) {
                if (slot > 17) {
                    break;
                }
                String reason = presets.getString(id, id);
                set(slot++, plugin.icons().button(type.id(), "reason.preset", Text.p("reason", reason), Text.p("id", id)),
                        e -> confirm(reason));
            }
        }
        set(21, plugin.icons().button("ledger", "reason.custom"), e ->
                plugin.prompts().ask(viewer, "gui.prompt-reason", this::confirm,
                        () -> go(new ReasonMenu(plugin, viewer, target, type, duration))));
        set(23, plugin.icons().button("cancel", "common.back"), e -> go(type.timed()
                ? new DurationMenu(plugin, viewer, target, type) : new VerdictMenu(plugin, viewer, target)));
    }

    private void confirm(String reason) {
        go(new ConfirmMenu(plugin, viewer, target, type, duration, reason, false));
    }

    static String who(OfflinePlayer p) {
        return Players.name(p);
    }
}
