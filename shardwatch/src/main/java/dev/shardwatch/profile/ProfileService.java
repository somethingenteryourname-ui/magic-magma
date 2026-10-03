package dev.shardwatch.profile;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Loads profiles at login, keeps online ones in memory and writes changes back. */
public final class ProfileService implements Listener {

    private final Shardwatch plugin;
    private final Map<UUID, Profile> online = new ConcurrentHashMap<>();

    public ProfileService(Shardwatch plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Profile p = load(event.getUniqueId()).join();
        if (p == null) {
            p = new Profile(event.getUniqueId(), event.getName());
        }
        p.name(event.getName());
        online.put(event.getUniqueId(), p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Profile p = online.remove(event.getPlayer().getUniqueId());
        if (p != null) {
            save(p);
        }
    }

    /** Profile of an online player (always present after login). */
    public Profile get(Player player) {
        return online.computeIfAbsent(player.getUniqueId(), u -> new Profile(u, player.getName()));
    }

    public Profile cached(UUID uuid) {
        return online.get(uuid);
    }

    public boolean alertsOn(Player p) {
        return get(p).alertsOn();
    }

    public boolean fxOn(Player p) {
        return get(p).fxOn();
    }

    public CompletableFuture<Profile> load(UUID uuid) {
        return plugin.db().query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT name, facet, lustre, lifetime, refinements, keepsakes, "
                    + "active_aura, active_sigil, alerts_on, fx_on, veiled, daily_day, daily_earned FROM progress WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Profile p = new Profile(uuid, rs.getString(1));
                    p.facet(rs.getString(2));
                    p.lustre(rs.getLong(3));
                    p.lifetime(rs.getLong(4));
                    p.refinementsCsv(rs.getString(5));
                    p.keepsakesCsv(rs.getString(6));
                    p.activeAura(rs.getString(7));
                    p.activeSigil(rs.getString(8));
                    p.alertsOn(rs.getInt(9) == 1);
                    p.fxOn(rs.getInt(10) == 1);
                    p.veiled(rs.getInt(11) == 1);
                    p.daily(rs.getLong(12), rs.getLong(13));
                    return p;
                }
            }
        });
    }

    /** Loads an offline player's profile (or the cached one when online). */
    public CompletableFuture<Profile> loadOrCreate(UUID uuid, String name) {
        Profile cached = online.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return load(uuid).thenApply(p -> p != null ? p : new Profile(uuid, name));
    }

    public CompletableFuture<Void> save(Profile p) {
        Object[] values = {p.uuid().toString(), p.name(), p.facet(), p.lustre(), p.lifetime(), p.refinementsCsv(),
                p.keepsakesCsv(), p.activeAura(), p.activeSigil(), p.alertsOn() ? 1 : 0, p.fxOn() ? 1 : 0,
                p.veiled() ? 1 : 0, p.dailyDay(), p.dailyEarned()};
        return plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO progress(uuid, name, facet, lustre, lifetime, refinements, "
                    + "keepsakes, active_aura, active_sigil, alerts_on, fx_on, veiled, daily_day, daily_earned) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, "
                    + "facet=excluded.facet, lustre=excluded.lustre, lifetime=excluded.lifetime, refinements=excluded.refinements, "
                    + "keepsakes=excluded.keepsakes, active_aura=excluded.active_aura, active_sigil=excluded.active_sigil, "
                    + "alerts_on=excluded.alerts_on, fx_on=excluded.fx_on, veiled=excluded.veiled, "
                    + "daily_day=excluded.daily_day, daily_earned=excluded.daily_earned")) {
                Database.bind(ps, values).executeUpdate();
            }
        });
    }

    /** Everyone with a Facet, for the roster. */
    public CompletableFuture<List<Profile>> staff() {
        return plugin.db().query(c -> {
            List<Profile> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uuid FROM progress WHERE facet IS NOT NULL ORDER BY lifetime DESC");
                 ResultSet rs = ps.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString(1)));
                }
                for (UUID id : ids) {
                    Profile p = online.get(id);
                    out.add(p != null ? p : load(c, id));
                }
            }
            return out;
        });
    }

    private Profile load(java.sql.Connection c, UUID uuid) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name, facet, lustre, lifetime FROM progress WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                Profile p = new Profile(uuid, "?");
                if (rs.next()) {
                    p.name(rs.getString(1));
                    p.facet(rs.getString(2));
                    p.lustre(rs.getLong(3));
                    p.lifetime(rs.getLong(4));
                }
                return p;
            }
        }
    }

    /** Leaderboard by lifetime Lustre. */
    public CompletableFuture<List<String[]>> top(int limit) {
        return plugin.db().query(c -> {
            List<String[]> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, lifetime, facet FROM progress WHERE lifetime > 0 ORDER BY lifetime DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new String[]{rs.getString(1), String.valueOf(rs.getLong(2)), rs.getString(3)});
                    }
                }
            }
            return out;
        });
    }

    public void saveAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            save(get(p));
        }
    }
}
