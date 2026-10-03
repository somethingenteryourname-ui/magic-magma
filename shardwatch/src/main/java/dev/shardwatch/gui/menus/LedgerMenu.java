package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.Verdict;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** A player's verdict history; click an active entry to revoke it. */
public final class LedgerMenu extends PagedMenu<Verdict> {

    private final OfflinePlayer target;

    public LedgerMenu(Shardwatch plugin, Player viewer, OfflinePlayer target, List<Verdict> verdicts, int page) {
        super(plugin, viewer, verdicts, page);
        this.target = target;
    }

    @Override
    protected String titleKey() {
        return "gui.ledger.title";
    }

    @Override
    protected TagResolver titleResolvers() {
        return Text.p("player", Players.name(target));
    }

    @Override
    protected ItemStack entryIcon(Verdict v) {
        long now = System.currentTimeMillis();
        TagResolver r = TagResolver.resolver(plugin.verdicts().resolvers(v), Text.p("ago", Durations.ago(v.created())),
                Text.pp("status_tag", plugin.lang().raw("ledger.status." + v.status(now))));
        boolean revocable = v.inForce(now) && viewer.hasPermission("shardwatch.ledger.revoke");
        ItemStack icon = plugin.icons().icon(v.type().id(), plugin.lang().raw("gui.ledger.entry-name"),
                plugin.lang().rawList(revocable ? "gui.ledger.entry-active" : "gui.ledger.entry"), r);
        if (v.inForce(now)) {
            icon.setData(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return icon;
    }

    @Override
    protected void onEntry(Verdict v, InventoryClickEvent e) {
        if (!v.inForce(System.currentTimeMillis()) || !viewer.hasPermission("shardwatch.ledger.revoke")) {
            return;
        }
        plugin.prompts().ask(viewer, "gui.prompt-revoke", reason -> plugin.verdicts().revokeById(viewer, v, reason)
                .thenAccept(ok -> plugin.sync(() -> {
                    plugin.lang().send(viewer, ok ? "ledger.revoked" : "ledger.already-revoked", Text.p("id", v.id()));
                    plugin.menus().openLedger(viewer, target, page);
                })), () -> plugin.menus().openLedger(viewer, target, page));
    }

    @Override
    protected Runnable back() {
        return () -> plugin.menus().openVerdict(viewer, target);
    }

    @Override
    protected void navigation() {
        long active = entries.stream().filter(v -> v.inForce(System.currentTimeMillis())).count();
        set(46, plugin.icons().head(target.getUniqueId(), Players.name(target), plugin.lang().raw("gui.ledger.summary.name"),
                plugin.lang().rawList("gui.ledger.summary.lore"), Text.p("player", Players.name(target)),
                Text.p("total", entries.size()), Text.p("active", active)));
    }
}
