package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Shared helpers for every Shardwatch command. */
public abstract class BaseCommand implements TabExecutor {

    protected final Shardwatch plugin;

    protected BaseCommand(Shardwatch plugin) {
        this.plugin = plugin;
    }

    protected boolean has(CommandSender s, String perm) {
        if (s.hasPermission(perm)) {
            return true;
        }
        plugin.lang().send(s, "general.no-permission", Text.p("permission", perm));
        return false;
    }

    protected Player requirePlayer(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        plugin.lang().send(s, "general.players-only");
        return null;
    }

    protected OfflinePlayer target(CommandSender s, String name) {
        OfflinePlayer p = Players.resolve(name);
        if (p == null) {
            plugin.lang().send(s, "general.unknown-player", Text.p("player", name));
        }
        return p;
    }

    protected Player online(CommandSender s, String name) {
        Player p = org.bukkit.Bukkit.getPlayerExact(name);
        if (p == null) {
            plugin.lang().send(s, "general.not-online", Text.p("player", name));
        }
        return p;
    }

    protected static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    protected static int intArg(String[] args, int index, int def) {
        if (index >= args.length) {
            return def;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    protected static long longArg(String s, long def) {
        try {
            return Long.parseLong(s.startsWith("#") ? s.substring(1) : s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    protected static List<String> filter(Collection<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) {
                out.add(o);
            }
        }
        return out;
    }
}
