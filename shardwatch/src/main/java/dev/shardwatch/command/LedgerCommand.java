package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.Verdict;
import dev.shardwatch.verdict.VerdictType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** /ledger &lt;player&gt; [page] — verdict history; /ledger revoke &lt;id&gt; [reason]. */
public final class LedgerCommand extends BaseCommand {

    public LedgerCommand(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!has(sender, "shardwatch.ledger")) {
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(sender, "usage.ledger", Text.p("label", label));
            return true;
        }
        if (args[0].equalsIgnoreCase("revoke")) {
            if (!has(sender, "shardwatch.ledger.revoke") || args.length < 2) {
                return true;
            }
            long id = longArg(args[1], -1);
            String reason = args.length > 2 ? join(args, 2) : "Revoked from Ledger";
            plugin.verdicts().store().byId(id).thenAccept(v -> plugin.sync(() -> {
                if (v == null) {
                    plugin.lang().send(sender, "ledger.no-entry", Text.p("id", id));
                    return;
                }
                plugin.verdicts().revokeById(sender, v, reason).thenAccept(ok -> plugin.sync(() ->
                        plugin.lang().send(sender, ok ? "ledger.revoked" : "ledger.already-revoked", Text.p("id", id))));
            }));
            return true;
        }
        OfflinePlayer target = target(sender, args[0]);
        if (target == null) {
            return true;
        }
        boolean chat = args[args.length - 1].equalsIgnoreCase("--chat");
        if (sender instanceof org.bukkit.entity.Player p && !chat && plugin.getConfig().getBoolean("gui.enabled", true)) {
            plugin.menus().openLedger(p, target, 0);
            return true;
        }
        int page = Math.max(1, intArg(args, 1, 1));
        int per = plugin.getConfig().getInt("ledger.chat-page-size", 8);
        String name = Players.name(target);
        plugin.verdicts().store().counts(target.getUniqueId()).thenCombine(
                plugin.verdicts().store().history(target.getUniqueId(), per + 1, (page - 1) * per), (counts, rows) -> {
                    plugin.sync(() -> show(sender, name, page, per, counts, rows));
                    return null;
                });
        return true;
    }

    private void show(CommandSender sender, String name, int page, int per, int[] counts, List<Verdict> rows) {
        TagResolver head = TagResolver.resolver(Text.p("player", name), Text.p("page", page),
                Text.p("chips", counts[VerdictType.CHIP.ordinal()]), Text.p("hushes", counts[VerdictType.HUSH.ordinal()]),
                Text.p("ejects", counts[VerdictType.EJECT.ordinal()]), Text.p("encases", counts[VerdictType.ENCASE.ordinal()]),
                Text.p("petrifies", counts[VerdictType.PETRIFY.ordinal()]));
        plugin.lang().send(sender, "ledger.header", head);
        if (rows.isEmpty()) {
            plugin.lang().send(sender, "ledger.empty", head);
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(per, rows.size()); i++) {
            Verdict v = rows.get(i);
            TagResolver r = TagResolver.resolver(plugin.verdicts().resolvers(v), Text.p("ago", Durations.ago(v.created())),
                    Text.pp("status_tag", plugin.lang().raw("ledger.status." + v.status(now))));
            Component line = plugin.lang().get("ledger.row", r).hoverEvent(HoverEvent.showText(plugin.lang().get("ledger.row-hover", r)));
            if (v.inForce(now) && sender.hasPermission("shardwatch.ledger.revoke")) {
                line = line.append(plugin.lang().get("ledger.revoke-button", r)
                        .clickEvent(ClickEvent.suggestCommand("/ledger revoke " + v.id() + " ")));
            }
            sender.sendMessage(line);
        }
        Component nav = Component.empty();
        if (page > 1) {
            nav = nav.append(plugin.lang().get("ledger.prev").clickEvent(ClickEvent.runCommand("/ledger " + name + " " + (page - 1) + " --chat")));
        }
        if (rows.size() > per) {
            nav = nav.append(plugin.lang().get("ledger.next").clickEvent(ClickEvent.runCommand("/ledger " + name + " " + (page + 1) + " --chat")));
        }
        if (!nav.equals(Component.empty())) {
            sender.sendMessage(nav);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> names = new java.util.ArrayList<>(Players.onlineNames(args[0]));
            if ("revoke".startsWith(args[0].toLowerCase())) {
                names.add("revoke");
            }
            return names;
        }
        return List.of();
    }
}
