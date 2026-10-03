package dev.shardwatch.flare;

import dev.shardwatch.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** SQL for the flares table. */
public final class FlareStore {

    private static final String COLS = "id, reporter_uuid, reporter_name, target_uuid, target_name, category, reason, "
            + "world, x, y, z, created, status, handler_uuid, handler_name, resolution, closed_at";

    private final Database db;

    public FlareStore(Database db) {
        this.db = db;
    }

    public CompletableFuture<Flare> insert(UUID reporter, String reporterName, UUID target, String targetName,
                                           String category, String reason, String world, int x, int y, int z) {
        long now = System.currentTimeMillis();
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO flares(reporter_uuid, reporter_name, target_uuid, "
                    + "target_name, category, reason, world, x, y, z, created) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                Database.bind(ps, reporter.toString(), reporterName, target.toString(), targetName, category, reason,
                        world, x, y, z, now).executeUpdate();
                return new Flare(Database.insertId(ps), reporter, reporterName, target, targetName, category, reason,
                        world, x, y, z, now, Flare.Status.OPEN, null, null, null, 0);
            }
        });
    }

    public CompletableFuture<Boolean> hasOpen(UUID reporter, UUID target) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM flares WHERE reporter_uuid = ? AND target_uuid = ? "
                    + "AND status IN ('OPEN','CLAIMED') LIMIT 1")) {
                Database.bind(ps, reporter.toString(), target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public CompletableFuture<Flare> byId(long id) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM flares WHERE id = ?")) {
                ps.setLong(1, id);
                List<Flare> l = read(ps);
                return l.isEmpty() ? null : l.get(0);
            }
        });
    }

    /** Lists flares; {@code statuses} null means every status. Open ones come oldest-first, closed ones newest-first. */
    public CompletableFuture<List<Flare>> list(List<Flare.Status> statuses, String like, int limit, int offset) {
        return db.query(c -> {
            StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM flares WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                sql.append(" AND status IN (").append("?,".repeat(statuses.size() - 1)).append("?)");
                statuses.forEach(s -> args.add(s.name()));
            }
            if (like != null) {
                sql.append(" AND (target_name LIKE ? OR reporter_name LIKE ? OR reason LIKE ? OR category LIKE ?)");
                String l = "%" + like + "%";
                args.add(l);
                args.add(l);
                args.add(l);
                args.add(l);
            }
            boolean openOnly = statuses != null && statuses.stream().noneMatch(Flare.Status::closed);
            sql.append(openOnly ? " ORDER BY id ASC" : " ORDER BY id DESC").append(" LIMIT ? OFFSET ?");
            args.add(limit);
            args.add(offset);
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                Database.bind(ps, args.toArray());
                return read(ps);
            }
        });
    }

    public CompletableFuture<Integer> countOpen() {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM flares WHERE status IN ('OPEN','CLAIMED')");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    /** Moves a flare to a new status if it is currently in one of {@code from}. Returns true on success. */
    public CompletableFuture<Boolean> transition(long id, List<Flare.Status> from, Flare.Status to, String handlerUuid,
                                                 String handlerName, String resolution) {
        return db.query(c -> {
            String in = "?,".repeat(from.size() - 1) + "?";
            try (PreparedStatement ps = c.prepareStatement("UPDATE flares SET status = ?, handler_uuid = ?, handler_name = ?, "
                    + "resolution = COALESCE(?, resolution), closed_at = ? WHERE id = ? AND status IN (" + in + ")")) {
                List<Object> args = new ArrayList<>(List.of(to.name()));
                args.add(handlerUuid);
                args.add(handlerName);
                args.add(resolution);
                args.add(to.closed() ? System.currentTimeMillis() : 0L);
                args.add(id);
                from.forEach(s -> args.add(s.name()));
                return Database.bind(ps, args.toArray()).executeUpdate() > 0;
            }
        });
    }

    private static List<Flare> read(PreparedStatement ps) throws SQLException {
        List<Flare> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Flare(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                        UUID.fromString(rs.getString(4)), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getLong(12),
                        Flare.Status.valueOf(rs.getString(13)), rs.getString(14), rs.getString(15), rs.getString(16),
                        rs.getLong(17)));
            }
        }
        return out;
    }
}
