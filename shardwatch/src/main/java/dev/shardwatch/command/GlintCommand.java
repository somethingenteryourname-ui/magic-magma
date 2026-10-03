package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.glint.GlintAlert;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** /glint — x-ray alerts. */
public final class GlintCommand extends BaseCommand {

    public GlintCommand(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!has(sender, "shardwatch.glint")) {
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "check" -> {
                if (args.length < 2) {
                    plugin.lang().send(sender, "usage.glint", Text.p("label", label));
                    return true;
                }
                Player t = online(sender, args[1]);
                if (t != null) {
                    plugin.lang().send(sender, "glint.check", plugin.glint().check(t));
                }
            }
            case "suspects" -> {
                List<Map.Entry<Player, Double>> list = plugin.glint().suspects();
                plugin.lang().send(sender, "glint.suspects-header");
                if (list.isEmpty()) {
                    plugin.lang().send(sender, "glint.none");
                }
                for (Map.Entry<Player, Double> e : list.subList(0, Math.min(10, list.size()))) {
                    sender.sendMessage(plugin.lang().get("glint.suspect-row", Text.p("player", e.getKey().getName()),
                            Text.p("score", String.format("%.0f", e.getValue())))
                            .clickEvent(ClickEvent.runCommand("/glint watch " + e.getKey().getName())));
                }
            }
            case "tp" -> {
                Player p = requirePlayer(sender);
                Player t = args.length > 1 ? online(sender, args[1]) : null;
                if (p != null && t != null) {
                    p.teleportAsync(t.getLocation());
                    plugin.fx().play("warp-in", t.getLocation().add(0, 1, 0));
                    plugin.action(p, StaffAction.GLINT_HANDLE, t.getName(), "teleport");
                }
            }
            case "watch" -> {
                Player p = requirePlayer(sender);
                Player t = args.length > 1 ? online(sender, args[1]) : null;
                if (p != null && t != null && has(p, "shardwatch.glint.watch")) {
                    plugin.glint().watch(p, t);
                }
            }
            case "unwatch" -> {
                Player p = requirePlayer(sender);
                if (p != null) {
                    plugin.lang().send(p, plugin.glint().unwatch(p) ? "glint.unwatched" : "glint.not-watching");
                }
            }
            case "handle" -> {
                long id = args.length > 1 ? longArg(args[1], -1) : -1;
                plugin.glint().store().markHandled(id, sender.getName()).thenAccept(ok -> plugin.sync(() -> {
                    plugin.lang().send(sender, ok ? "glint.handled" : "glint.already-handled", Text.p("id", id));
                    if (ok) {
                        plugin.action(sender, StaffAction.GLINT_HANDLE, "", "#" + id);
                    }
                }));
            }
            default -> {
                int page = Math.max(1, intArg(args, sub.equals("list") ? 1 : 0, 1));
                int per = 8;
                plugin.glint().store().recent(null, 0, per, (page - 1) * per).thenAccept(rows -> plugin.sync(() -> list(sender, rows, page)));
            }
        }
        return true;
    }

    private void list(CommandSender sender, List<GlintAlert> rows, int page) {
        plugin.lang().send(sender, "glint.header", Text.p("page", page));
        if (rows.isEmpty()) {
            plugin.lang().send(sender, "glint.none");
        }
        for (GlintAlert g : rows) {
            TagResolver r = TagResolver.resolver(Text.p("id", g.id()), Text.p("ago", Durations.ago(g.time())),
                    Text.p("player", g.name()), Text.p("ore", g.ore()), Text.p("score", String.format("%.0f", g.score())),
                    Text.p("x", g.x()), Text.p("y", g.y()), Text.p("z", g.z()), Text.p("world", g.world()),
                    Text.p("handled", g.handledBy() == null ? "-" : g.handledBy()));
            sender.sendMessage(plugin.lang().get(g.handledBy() == null ? "glint.row" : "glint.row-handled", r)
                    .hoverEvent(HoverEvent.showText(plugin.lang().get("glint.row-hover", r)))
                    .clickEvent(ClickEvent.runCommand("/scope tp " + g.world() + " " + g.x() + " " + g.y() + " " + g.z())));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            return filter(List.of("list", "suspects", "check", "tp", "watch", "unwatch", "handle"), args[0]);
        }
        if (args.length == 2 && List.of("check", "tp", "watch").contains(args[0].toLowerCase())) {
            return Players.onlineNames(args[1]);
        }
        return List.of();
    }
}
