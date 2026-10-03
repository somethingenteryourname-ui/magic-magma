package dev.shardwatch.glint;

import dev.shardwatch.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** SQL for the glint table. */
public final class GlintStore {

    private final Database db;

    public GlintStore(Database db) {
        this.db = db;
    }

    public CompletableFuture<Long> insert(UUID uuid, String name, String ore, String world, int x, int y, int z, double score) {
        long now = System.currentTimeMillis();
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO glint(time, uuid, name, ore, world, x, y, z, score) "
                    + "VALUES (?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                Database.bind(ps, now, uuid.toString(), name, ore, world, x, y, z, score).executeUpdate();
                return Database.insertId(ps);
            }
        });
    }

    public CompletableFuture<List<GlintAlert>> recent(String like, long since, int limit, int offset) {
        return db.query(c -> {
            String l = like == null ? null : "%" + like + "%";
            try (PreparedStatement ps = c.prepareStatement("SELECT id, time, uuid, name, ore, world, x, y, z, score, handled_by "
                    + "FROM glint WHERE time >= ? AND (? IS NULL OR name LIKE ? OR ore LIKE ?) ORDER BY id DESC LIMIT ? OFFSET ?")) {
                Database.bind(ps, since, l, l, l, limit, offset);
                List<GlintAlert> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new GlintAlert(rs.getLong(1), rs.getLong(2), UUID.fromString(rs.getString(3)), rs.getString(4),
                                rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getDouble(10),
                                rs.getString(11)));
                    }
                }
                return out;
            }
        });
    }

    public CompletableFuture<Boolean> markHandled(long id, String by) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE glint SET handled_by = ? WHERE id = ? AND handled_by IS NULL")) {
                return Database.bind(ps, by, id).executeUpdate() > 0;
            }
        });
    }
}
