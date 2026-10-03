package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /facet, /hum and /veil. */
public final class FacetCommands extends BaseCommand {

    public FacetCommands(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "hum" -> {
                if (!has(sender, "shardwatch.hum")) {
                    return true;
                }
                if (args.length == 0) {
                    Player p = requirePlayer(sender);
                    if (p != null) {
                        plugin.lang().send(p, plugin.facets().toggleHum(p) ? "hum.mode-on" : "hum.mode-off");
                    }
                } else {
                    plugin.facets().hum(sender, join(args, 0));
                }
            }
            case "veil" -> {
                if (!has(sender, "shardwatch.veil")) {
                    return true;
                }
                Player target = args.length > 0 && sender.hasPermission("shardwatch.veil.others") ? online(sender, args[0])
                        : requirePlayer(sender);
                if (target != null) {
                    plugin.facets().setVeil(target, !plugin.facets().isVeiled(target));
                }
            }
            default -> facet(sender, label, args);
        }
        return true;
    }

    private void facet(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (!has(sender, "shardwatch.facet.manage")) {
                    return;
                }
                if (args.length < 3) {
                    plugin.lang().send(sender, "usage.facet", Text.p("label", label));
                    return;
                }
                OfflinePlayer target = target(sender, args[1]);
                if (target == null) {
                    return;
                }
                FacetService.Facet f = args[2].equalsIgnoreCase("none") ? null : plugin.facets().get(args[2]);
                if (f == null && !args[2].equalsIgnoreCase("none")) {
                    plugin.lang().send(sender, "facet.unknown", Text.p("facet", args[2]));
                    return;
                }
                plugin.facets().set(sender, target, f);
            }
            case "info" -> {
                OfflinePlayer target = args.length > 1 ? target(sender, args[1]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    return;
                }
                plugin.profiles().loadOrCreate(target.getUniqueId(), Players.name(target)).thenAccept(profile -> plugin.sync(() -> {
                    FacetService.Facet f = target.getPlayer() != null ? plugin.facets().of(target.getPlayer())
                            : plugin.facets().get(profile.facet());
                    plugin.lang().send(sender, "facet.info", Text.p("player", Players.name(target)),
                            Text.pp("facet", f == null ? plugin.lang().raw("facet.none") : f.name()),
                            Text.p("weight", f == null ? 0 : f.weight()), Text.p("lustre", profile.lustre()),
                            Text.p("lifetime", profile.lifetime()));
                }));
            }
            default -> {
                plugin.lang().send(sender, "facet.list-header");
                for (FacetService.Facet f : plugin.facets().all().values()) {
                    long online = Bukkit.getOnlinePlayers().stream().filter(p -> plugin.facets().of(p) == f).count();
                    plugin.lang().send(sender, "facet.list-row", Text.pp("facet", f.name()), Text.p("id", f.id()),
                            Text.p("weight", f.weight()), Text.p("online", online),
                            Text.p("perms", f.permissions().size()));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("veil")) {
            return args.length == 1 && sender.hasPermission("shardwatch.veil.others") ? Players.onlineNames(args[0]) : List.of();
        }
        if (!name.equals("facet")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "set", "info"), args[0]);
        }
        if (args.length == 2) {
            return Players.onlineNames(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            List<String> ids = new ArrayList<>(plugin.facets().ids());
            ids.add("none");
            return filter(ids, args[2]);
        }
        return List.of();
    }
}
