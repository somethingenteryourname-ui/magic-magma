package dev.shardwatch.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Player lookup helpers that never hit the network on the main thread. */
public final class Players {

    private Players() {
    }

    /** Online player by exact name, else a cached offline player who has joined before, else null. */
    public static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return cached;
        }
        return null;
    }

    public static String name(OfflinePlayer p) {
        String n = p.getName();
        return n == null ? p.getUniqueId().toString().substring(0, 8) : n;
    }

    public static String uuidOf(CommandSender s) {
        return s instanceof Player p ? p.getUniqueId().toString() : null;
    }

    public static List<String> onlineNames(String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(low)) {
                out.add(p.getName());
            }
        }
        return out;
    }

    public static List<Player> withPermission(String perm) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm)) {
                out.add(p);
            }
        }
        return out;
    }
}
