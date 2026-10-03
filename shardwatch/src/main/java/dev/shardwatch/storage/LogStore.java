package dev.shardwatch.storage;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Audit trail of staff actions and the chat/command log. */
public final class LogStore {

    public record AuditRow(long id, long time, String actor, String action, String detail) {
    }

    public record ChatRow(long id, long time, String uuid, String name, String kind, String message) {
    }

    private final Database db;

    public LogStore(Database db) {
        this.db = db;
    }

    public void audit(CommandSender actor, String action, String detail) {
        String uuid = actor instanceof Player p ? p.getUniqueId().toString() : null;
        long now = System.currentTimeMillis();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO audit(time, actor_uuid, actor_name, action, detail) VALUES (?,?,?,?,?)")) {
                Database.bind(ps, now, uuid, actor.getName(), action, detail).executeUpdate();
            }
        });
    }

    public void chat(Player p, String kind, String message) {
        long now = System.currentTimeMillis();
        String uuid = p.getUniqueId().toString();
        String name = p.getName();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO chat_log(time, uuid, name, kind, message) VALUES (?,?,?,?,?)")) {
                Database.bind(ps, now, uuid, name, kind, message).executeUpdate();
            }
        });
    }

    public CompletableFuture<List<AuditRow>> audits(String like, long since, int limit, int offset) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, time, actor_name, action, detail FROM audit
                    WHERE time >= ? AND (? IS NULL OR actor_name LIKE ? OR action LIKE ? OR detail LIKE ?)
                    ORDER BY id DESC LIMIT ? OFFSET ?""")) {
                String l = like == null ? null : "%" + like + "%";
                Database.bind(ps, since, l, l, l, l, limit, offset);
                List<AuditRow> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new AuditRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                    }
                }
                return out;
            }
        });
    }

    public CompletableFuture<List<ChatRow>> chats(String user, String like, long since, int limit, int offset) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, time, uuid, name, kind, message FROM chat_log
                    WHERE time >= ? AND (? IS NULL OR name = ? COLLATE NOCASE) AND (? IS NULL OR message LIKE ?)
                    ORDER BY id DESC LIMIT ? OFFSET ?""")) {
                String l = like == null ? null : "%" + like + "%";
                Database.bind(ps, since, user, user, l, l, limit, offset);
                List<ChatRow> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ChatRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                                rs.getString(5), rs.getString(6)));
                    }
                }
                return out;
            }
        });
    }

    /** Counts rows newer than {@code since} in a table that has a {@code time} column. */
    public CompletableFuture<Integer> countSince(String table, long since) {
        if (!table.matches("[a-z_]+")) {
            throw new IllegalArgumentException(table);
        }
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE time >= ?")) {
                ps.setLong(1, since);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    /** Deletes old echo/chat/audit rows according to retention settings. */
    public CompletableFuture<Void> prune(long echoBefore, long chatBefore, long auditBefore) {
        return db.transaction(c -> {
            delete(c, "echoes", echoBefore);
            delete(c, "chat_log", chatBefore);
            delete(c, "audit", auditBefore);
        });
    }

    private static void delete(java.sql.Connection c, String table, long before) throws SQLException {
        if (before <= 0) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE time < ?")) {
            ps.setLong(1, before);
            ps.executeUpdate();
        }
    }
}
