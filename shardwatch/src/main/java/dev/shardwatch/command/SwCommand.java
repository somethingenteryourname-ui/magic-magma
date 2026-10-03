package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.tool.StaffTool;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /shardwatch hub: help, kit, give, reload, version. */
public final class SwCommand extends BaseCommand {

    public SwCommand(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? (sender instanceof Player ? "menu" : "help") : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "console" -> {
                Player p = requirePlayer(sender);
                if (p != null && p.hasPermission("shardwatch.staff")) {
                    plugin.menus().openConsole(p);
                } else {
                    plugin.lang().getLines("help").forEach(sender::sendMessage);
                }
            }
            case "settings" -> {
                Player p = requirePlayer(sender);
                if (p != null && has(p, "shardwatch.staff")) {
                    plugin.menus().openSettings(p);
                }
            }
            case "reload" -> {
                if (!has(sender, "shardwatch.admin.reload")) {
                    return true;
                }
                long start = System.nanoTime();
                plugin.reload();
                plugin.lang().send(sender, "admin.reloaded", Text.p("ms", (System.nanoTime() - start) / 1_000_000));
                plugin.action(sender, StaffAction.RELOAD, "", "config, lang, facets");
            }
            case "kit" -> {
                Player p = requirePlayer(sender);
                if (p == null || !has(p, "shardwatch.kit")) {
                    return true;
                }
                int n = plugin.tools().giveKit(p);
                plugin.lang().send(p, n == 0 ? "tools.kit-none" : "tools.kit-given", Text.p("count", n));
                if (n > 0) {
                    plugin.fx().playFor(p, "tool-equip");
                }
            }
            case "give" -> give(sender, label, args);
            case "version" -> plugin.lang().send(sender, "admin.version",
                    Text.p("version", plugin.getPluginMeta().getVersion()));
            default -> plugin.lang().getLines("help").forEach(sender::sendMessage);
        }
        return true;
    }

    /** /sw give &lt;player&gt; &lt;tool&gt; [tier|facet] [amount]. */
    private void give(CommandSender sender, String label, String[] args) {
        if (!has(sender, "shardwatch.admin.give")) {
            return;
        }
        if (args.length < 3) {
            plugin.lang().send(sender, "usage.give", Text.p("label", label));
            return;
        }
        Player target = online(sender, args[1]);
        StaffTool tool = StaffTool.parse(args[2]);
        if (target == null) {
            return;
        }
        if (tool == null) {
            plugin.lang().send(sender, "tools.unknown", Text.p("tool", args[2]));
            return;
        }
        String extra = null;
        int tier = 1;
        if (tool == StaffTool.SIGIL) {
            extra = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "shardling";
            if (plugin.facets().get(extra) == null) {
                plugin.lang().send(sender, "facet.unknown", Text.p("facet", extra));
                return;
            }
        } else if (args.length > 3) {
            tier = intArg(args, 3, 1);
        }
        int amount = Math.max(1, Math.min(64, intArg(args, 4, 1)));
        ItemStack item = plugin.tools().create(tool, tier, extra);
        if (tool == StaffTool.LUSTRE_SHARD) {
            item.setAmount(amount);
            amount = 1;
        }
        for (int i = 0; i < amount; i++) {
            target.getInventory().addItem(item.clone()).values()
                    .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        }
        plugin.lang().send(sender, "tools.given", Text.p("player", target.getName()), Text.p("tool", tool.id()),
                Text.p("tier", tier));
        plugin.fx().playFor(target, "tool-equip");
        plugin.action(sender, StaffAction.GIVE, target.getName(), tool.id() + " t" + tier);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            return filter(List.of("menu", "settings", "help", "kit", "give", "reload", "version"), args[0]);
        }
        if (!args[0].equalsIgnoreCase("give")) {
            return List.of();
        }
        if (args.length == 2) {
            return Players.onlineNames(args[1]);
        }
        if (args.length == 3) {
            return filter(plugin.tools().ids(), args[2]);
        }
        if (args.length == 4) {
            StaffTool t = StaffTool.parse(args[2]);
            if (t == StaffTool.SIGIL) {
                return filter(plugin.facets().ids(), args[3]);
            }
            return t != null && t.tiered() ? filter(List.of("1", "2", "3"), args[3]) : List.of();
        }
        if (args.length == 5) {
            return new ArrayList<>(filter(List.of("1", "8", "16", "64"), args[4]));
        }
        return List.of();
    }
}
