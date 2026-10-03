package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /echo (inspect mode) and /rewind. */
public final class EchoCommands extends BaseCommand {

    public EchoCommands(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("echo")) {
            Player p = requirePlayer(sender);
            if (p == null || !has(p, "shardwatch.echo")) {
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("page")) {
                plugin.echoes().inspectPage(p, intArg(args, 1, 1));
                return true;
            }
            boolean on = plugin.echoes().toggleInspect(p);
            plugin.lang().send(p, on ? "echo.inspect-on" : "echo.inspect-off");
            plugin.fx().playFor(p, on ? "tool-equip" : "ui-back");
            return true;
        }
        return rewind(sender, label, args);
    }

    private boolean rewind(CommandSender sender, String label, String[] raw) {
        if (!has(sender, "shardwatch.rewind")) {
            return true;
        }
        boolean preview = Arrays.asList(raw).contains("-p");
        String[] args = Arrays.stream(raw).filter(a -> !a.equals("-p")).toArray(String[]::new);
        if (args.length >= 1 && args[0].equalsIgnoreCase("undo")) {
            plugin.rewind().undo(sender);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("area")) {
            Player p = requirePlayer(sender);
            Long since = Durations.parse(args[1]);
            if (p == null) {
                return true;
            }
            if (since == null || since == 0) {
                plugin.lang().send(sender, "general.bad-duration", Text.p("input", args[1]));
                return true;
            }
            var req = plugin.toolListener().selection(p, args.length > 2 ? args[2] : null, since);
            if (req == null) {
                plugin.lang().send(p, "tools.timeglass-no-selection");
                return true;
            }
            int max = plugin.getConfig().getInt("rewind.max-radius", 60) * 2 + 1 + plugin.lustre().bonus(p, "timeglass", "area-bonus");
            if ((req.maxX() - req.minX() + 1 > max || req.maxY() - req.minY() + 1 > max || req.maxZ() - req.minZ() + 1 > max)
                    && !p.hasPermission("shardwatch.rewind.global")) {
                plugin.lang().send(p, "rewind.radius-too-big", Text.p("max", max));
                return true;
            }
            if (!preview) {
                plugin.toolListener().drainTimeglass(p, plugin.tools().cooldown(p, dev.shardwatch.tool.StaffTool.TIMEGLASS));
            }
            plugin.rewind().run(p, req, preview);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("preview")) {
            preview = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length < 2) {
            plugin.lang().send(sender, "usage.rewind", Text.p("label", label));
            return true;
        }
        Player p = requirePlayer(sender);
        if (p == null) {
            return true;
        }
        String actor = args[0].equals("*") ? null : args[0];
        Long since = Durations.parse(args[1]);
        if (since == null || since == 0) {
            plugin.lang().send(sender, "general.bad-duration", Text.p("input", args[1]));
            return true;
        }
        int radius = plugin.getConfig().getInt("rewind.default-radius", 15);
        if (args.length >= 3) {
            radius = args[2].equalsIgnoreCase("global") ? -1 : intArg(args, 2, radius);
        }
        plugin.rewind().rewind(sender, actor, since, radius, p.getLocation(), preview);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("echo")) {
            return args.length == 1 ? filter(List.of("page"), args[0]) : List.of();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>(Players.onlineNames(args[0]));
            out.addAll(filter(List.of("undo", "preview", "area", "*", "#tnt", "#creeper", "#fire", "#enderman", "#wither",
                    "#block-explosion"), args[0]));
            return out;
        }
        if (args.length == 2) {
            return filter(List.of("10m", "30m", "1h", "3h", "12h", "1d", "3d", "7d"), args[1]);
        }
        if (args.length == 3) {
            return filter(List.of("5", "10", "15", "25", "40", "global", "-p"), args[2]);
        }
        return List.of("-p");
    }
}
