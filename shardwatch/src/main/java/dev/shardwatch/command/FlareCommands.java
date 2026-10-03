package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /flare (players) and /flares (staff). */
public final class FlareCommands extends BaseCommand {

    public FlareCommands(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("flare")) {
            return flare(sender, label, args);
        }
        return board(sender, label, args);
    }

    private boolean flare(CommandSender sender, String label, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null || !has(p, "shardwatch.flare")) {
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(p, "usage.flare", Text.p("label", label));
            return true;
        }
        OfflinePlayer target = target(p, args[0]);
        if (target == null) {
            return true;
        }
        if (args.length == 1 && plugin.getConfig().getBoolean("gui.enabled", true)) {
            plugin.menus().openFlareCreate(p, target);
            return true;
        }
        String category = "other";
        int from = 1;
        if (args.length > 1 && plugin.flares().categories().containsKey(args[1].toLowerCase(Locale.ROOT))) {
            category = args[1].toLowerCase(Locale.ROOT);
            from = 2;
        }
        plugin.flares().file(p, target, category, join(args, from));
        return true;
    }

    private boolean board(CommandSender sender, String label, String[] args) {
        if (!has(sender, "shardwatch.flares")) {
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        long id = args.length > 1 ? longArg(args[1], -1) : -1;
        switch (sub) {
            case "claim" -> {
                Player p = requirePlayer(sender);
                if (p != null && has(p, "shardwatch.flares.handle")) {
                    plugin.flares().claim(p, id);
                }
            }
            case "resolve", "dismiss" -> {
                if (has(sender, "shardwatch.flares.handle")) {
                    plugin.flares().close(sender, id, sub.equals("resolve"), join(args, 2));
                }
            }
            case "tp" -> {
                Player p = requirePlayer(sender);
                if (p != null && has(p, "shardwatch.flares.teleport")) {
                    plugin.flares().store().byId(id).thenAccept(f -> plugin.sync(() -> {
                        if (f == null) {
                            plugin.lang().send(p, "flare.not-found", Text.p("id", id));
                        } else {
                            plugin.flares().teleport(p, f);
                        }
                    }));
                }
            }
            case "view" -> plugin.flares().store().byId(id).thenAccept(f -> plugin.sync(() -> {
                if (f == null) {
                    plugin.lang().send(sender, "flare.not-found", Text.p("id", id));
                } else {
                    plugin.lang().getLines("flare.view", plugin.flares().resolvers(f)).forEach(sender::sendMessage);
                    sender.sendMessage(buttons(f));
                }
            }));
            case "all" -> list(sender, null, Math.max(1, intArg(args, 1, 1)));
            default -> {
                if (sender instanceof Player p && !sub.equals("list") && plugin.getConfig().getBoolean("gui.enabled", true)) {
                    plugin.menus().openFlareBoard(p, false, 0);
                    return true;
                }
                list(sender, List.of(Flare.Status.OPEN, Flare.Status.CLAIMED), Math.max(1, intArg(args, 1, 1)));
            }
        }
        return true;
    }

    private void list(CommandSender sender, List<Flare.Status> statuses, int page) {
        int per = 8;
        plugin.flares().store().list(statuses, null, per + 1, (page - 1) * per).thenAccept(rows -> plugin.sync(() -> {
            plugin.lang().send(sender, "flare.board-header", Text.p("page", page),
                    Text.pp("filter", plugin.lang().raw(statuses == null ? "flare.filter-all" : "flare.filter-open")));
            if (rows.isEmpty()) {
                plugin.lang().send(sender, "flare.board-empty");
            }
            for (int i = 0; i < Math.min(per, rows.size()); i++) {
                Flare f = rows.get(i);
                TagResolver r = plugin.flares().resolvers(f);
                sender.sendMessage(plugin.lang().get("flare.board-row", r)
                        .hoverEvent(HoverEvent.showText(plugin.lang().get("flare.board-hover", r)))
                        .clickEvent(ClickEvent.runCommand("/flares view " + f.id())).append(Component.space()).append(buttons(f)));
            }
            String base = statuses == null ? "/flares all " : "/flares list ";
            Component nav = Component.empty();
            if (page > 1) {
                nav = nav.append(plugin.lang().get("flare.prev").clickEvent(ClickEvent.runCommand(base + (page - 1))));
            }
            if (rows.size() > per) {
                nav = nav.append(plugin.lang().get("flare.next").clickEvent(ClickEvent.runCommand(base + (page + 1))));
            }
            if (!nav.equals(Component.empty())) {
                sender.sendMessage(nav);
            }
        }));
    }

    private Component buttons(Flare f) {
        if (f.status().closed()) {
            return Component.empty();
        }
        Component b = Component.empty();
        if (f.status() == Flare.Status.OPEN) {
            b = b.append(plugin.lang().get("flare.button-claim").clickEvent(ClickEvent.runCommand("/flares claim " + f.id())));
        }
        return b.append(plugin.lang().get("flare.button-tp").clickEvent(ClickEvent.runCommand("/flares tp " + f.id())))
                .append(plugin.lang().get("flare.button-resolve").clickEvent(ClickEvent.suggestCommand("/flares resolve " + f.id() + " ")))
                .append(plugin.lang().get("flare.button-dismiss").clickEvent(ClickEvent.suggestCommand("/flares dismiss " + f.id() + " ")));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("flare")) {
            if (args.length == 1) {
                return Players.onlineNames(args[0]);
            }
            if (args.length == 2) {
                return filter(plugin.flares().categoryIds(), args[1]);
            }
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "all", "claim", "resolve", "dismiss", "tp", "view"), args[0]);
        }
        return List.of();
    }
}
