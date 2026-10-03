package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.VerdictType;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /chip /hush /unhush /eject /encase /unencase /petrify.
 * <p>Reasons may be a preset ({@code #spam}); add {@code -s} anywhere to keep the verdict silent.
 */
public final class VerdictCommands extends BaseCommand {

    public VerdictCommands(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] raw) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        boolean silent = Arrays.asList(raw).contains("-s");
        String[] args = Arrays.stream(raw).filter(a -> !a.equals("-s")).toArray(String[]::new);
        switch (name) {
            case "unhush" -> {
                return revoke(sender, args, VerdictType.HUSH, label);
            }
            case "unencase" -> {
                return revoke(sender, args, VerdictType.ENCASE, label);
            }
            case "petrify" -> {
                if (!has(sender, VerdictType.PETRIFY.permission())) {
                    return true;
                }
                if (args.length < 1) {
                    plugin.lang().send(sender, "usage.petrify", Text.p("label", label));
                    return true;
                }
                Player t = online(sender, args[0]);
                if (t != null) {
                    plugin.verdicts().togglePetrify(sender, t);
                }
                return true;
            }
            default -> {
                VerdictType type = switch (name) {
                    case "chip" -> VerdictType.CHIP;
                    case "hush" -> VerdictType.HUSH;
                    case "eject" -> VerdictType.EJECT;
                    default -> VerdictType.ENCASE;
                };
                return issue(sender, args, type, label, silent);
            }
        }
    }

    private boolean issue(CommandSender sender, String[] args, VerdictType type, String label, boolean silent) {
        if (!has(sender, type.permission())) {
            return true;
        }
        if (args.length < 1) {
            plugin.lang().send(sender, "usage." + type.id(), Text.p("label", label));
            return true;
        }
        OfflinePlayer target = type == VerdictType.EJECT ? online(sender, args[0]) : target(sender, args[0]);
        if (target == null || !plugin.verdicts().canJudge(sender, target)) {
            return true;
        }
        int reasonFrom = 1;
        long duration = 0;
        if (type.timed()) {
            Long parsed = args.length > 1 ? Durations.parse(args[1]) : null;
            if (parsed != null) {
                duration = parsed;
                reasonFrom = 2;
            } else {
                Long def = Durations.parse(plugin.getConfig().getString("verdicts." + type.id() + ".default-duration", "perm"));
                duration = def == null ? 0 : def;
            }
            if (duration == Durations.PERMANENT && !sender.hasPermission(type.permission() + ".permanent")) {
                plugin.lang().send(sender, "verdict.no-permanent", Text.p("type", type.id()));
                return true;
            }
            Long max = Durations.parse(plugin.getConfig().getString("verdicts." + type.id() + ".max-duration", "perm"));
            if (max != null && max > 0 && (duration == 0 || duration > max) && !sender.hasPermission(type.permission() + ".unlimited")) {
                plugin.lang().send(sender, "verdict.too-long", Text.p("max", Durations.format(max)));
                return true;
            }
        }
        String reason = preset(join(args, reasonFrom));
        if (reason.isBlank() && plugin.getConfig().getBoolean("verdicts.require-reason", true) && type != VerdictType.PETRIFY) {
            plugin.lang().send(sender, "verdict.need-reason");
            return true;
        }
        plugin.verdicts().issue(sender, target, type, duration, reason, silent);
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args, VerdictType type, String label) {
        if (!has(sender, type.permission() + ".revoke")) {
            return true;
        }
        if (args.length < 1) {
            plugin.lang().send(sender, "usage.un" + type.id(), Text.p("label", label));
            return true;
        }
        OfflinePlayer target = target(sender, args[0]);
        if (target != null) {
            plugin.verdicts().revoke(sender, target, type, join(args, 1));
        }
        return true;
    }

    /** Expands {@code #id} into the configured preset reason. */
    private String preset(String reason) {
        if (!reason.startsWith("#")) {
            return reason;
        }
        String id = reason.substring(1).split(" ")[0];
        String text = plugin.getConfig().getString("verdicts.reason-presets." + id);
        return text == null ? reason : text + reason.substring(1 + id.length());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return Players.onlineNames(args[0]);
        }
        if (args.length == 2 && (name.equals("hush") || name.equals("encase"))) {
            return filter(List.of("10m", "30m", "1h", "6h", "1d", "3d", "7d", "30d", "perm"), args[1]);
        }
        if (args.length >= 2 && !name.equals("petrify") && args[args.length - 1].startsWith("#")) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("verdicts.reason-presets");
            List<String> out = new ArrayList<>();
            if (sec != null) {
                sec.getKeys(false).forEach(k -> out.add("#" + k));
            }
            return filter(out, args[args.length - 1]);
        }
        return List.of();
    }
}
