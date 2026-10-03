package dev.shardwatch.verdict;

import dev.shardwatch.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** SQL for the verdicts table. */
public final class VerdictStore {

    private static final String COLS = "id, type, target_uuid, target_name, actor_uuid, actor_name, reason, created, "
            + "expires, active, revoked_by, revoked_at, revoke_reason";

    private final Database db;

    public VerdictStore(Database db) {
        this.db = db;
    }

    public CompletableFuture<Verdict> insert(VerdictType type, UUID target, String targetName, String actorUuid,
                                             String actorName, String reason, long created, long expires,
                                             boolean replaceActive) {
        return db.query(c -> {
            if (replaceActive && type.lasting()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE verdicts SET active = 0 WHERE target_uuid = ? AND type = ? AND active = 1")) {
                    Database.bind(ps, target.toString(), type.name()).executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO verdicts(type, target_uuid, target_name, actor_uuid, actor_name, reason, created, expires, active)"
                            + " VALUES (?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                Database.bind(ps, type.name(), target.toString(), targetName, actorUuid, actorName, reason, created,
                        expires, type.lasting() ? 1 : 0).executeUpdate();
                long id = Database.insertId(ps);
                return new Verdict(id, type, target, targetName, actorUuid, actorName, reason, created, expires,
                        type.lasting(), null, 0, null);
            }
        });
    }

    /** Active, unexpired verdicts of a type for a player (newest first). Expired rows are deactivated on the way. */
    public CompletableFuture<List<Verdict>> active(UUID target, VerdictType type) {
        return db.query(c -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE verdicts SET active = 0 WHERE target_uuid = ? AND active = 1 AND expires > 0 AND expires <= ?")) {
                Database.bind(ps, target.toString(), now).executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS
                    + " FROM verdicts WHERE target_uuid = ? AND type = ? AND active = 1 ORDER BY id DESC")) {
                Database.bind(ps, target.toString(), type.name());
                return read(ps);
            }
        });
    }

    public CompletableFuture<List<Verdict>> history(UUID target, int limit, int offset) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS
                    + " FROM verdicts WHERE target_uuid = ? ORDER BY id DESC LIMIT ? OFFSET ?")) {
                Database.bind(ps, target.toString(), limit, offset);
                return read(ps);
            }
        });
    }

    public CompletableFuture<List<Verdict>> recent(String like, long since, int limit, int offset) {
        return db.query(c -> {
            String l = like == null ? null : "%" + like + "%";
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM verdicts WHERE created >= ? AND "
                    + "(? IS NULL OR target_name LIKE ? OR actor_name LIKE ? OR reason LIKE ? OR type LIKE ?) "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                Database.bind(ps, since, l, l, l, l, l, limit, offset);
                return read(ps);
            }
        });
    }

    public CompletableFuture<Verdict> byId(long id) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM verdicts WHERE id = ?")) {
                ps.setLong(1, id);
                List<Verdict> l = read(ps);
                return l.isEmpty() ? null : l.get(0);
            }
        });
    }

    /** Revokes every active verdict of a type for a player; returns how many were revoked. */
    public CompletableFuture<Integer> revoke(UUID target, VerdictType type, String by, String reason) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE verdicts SET active = 0, revoked_by = ?, revoked_at = ?, "
                    + "revoke_reason = ? WHERE target_uuid = ? AND type = ? AND active = 1")) {
                return Database.bind(ps, by, System.currentTimeMillis(), reason, target.toString(), type.name())
                        .executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> revokeById(long id, String by, String reason) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE verdicts SET active = 0, revoked_by = ?, revoked_at = ?, "
                    + "revoke_reason = ? WHERE id = ? AND revoked_by IS NULL")) {
                return Database.bind(ps, by, System.currentTimeMillis(), reason, id).executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<int[]> counts(UUID target) {
        return db.query(c -> {
            int[] out = new int[VerdictType.values().length];
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type, COUNT(*) FROM verdicts WHERE target_uuid = ? GROUP BY type")) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        VerdictType t = VerdictType.parse(rs.getString(1));
                        if (t != null) {
                            out[t.ordinal()] = rs.getInt(2);
                        }
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<Integer> countActive(VerdictType type) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM verdicts WHERE type = ? AND active = 1 AND (expires = 0 OR expires > ?)")) {
                Database.bind(ps, type.name(), System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    private static List<Verdict> read(PreparedStatement ps) throws SQLException {
        List<Verdict> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VerdictType type = VerdictType.parse(rs.getString(2));
                if (type == null) {
                    continue;
                }
                out.add(new Verdict(rs.getLong(1), type, UUID.fromString(rs.getString(3)), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getLong(9),
                        rs.getInt(10) == 1, rs.getString(11), rs.getLong(12), rs.getString(13)));
            }
        }
        return out;
    }
}
