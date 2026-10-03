package dev.shardwatch.echo;

import dev.shardwatch.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** SQL for the echoes table. */
public final class EchoStore {

    private static final String COLS = "id, time, actor_uuid, actor_name, action, world, x, y, z, old_data, new_data, rewound";

    private final Database db;

    public EchoStore(Database db) {
        this.db = db;
    }

    public CompletableFuture<Void> insertBatch(Collection<Echo> batch) {
        return db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO echoes(time, actor_uuid, actor_name, action, world, "
                    + "x, y, z, old_data, new_data) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                for (Echo e : batch) {
                    Database.bind(ps, e.time(), e.actorUuid(), e.actorName(), e.action().name(), e.world(), e.x(), e.y(),
                            e.z(), e.oldData(), e.newData());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    /** History of one block, newest first. */
    public CompletableFuture<List<Echo>> at(String world, int x, int y, int z, int limit, int offset) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM echoes WHERE world = ? AND x = ? AND z = ? "
                    + "AND y = ? ORDER BY id DESC LIMIT ? OFFSET ?")) {
                Database.bind(ps, world, x, z, y, limit, offset);
                return read(ps);
            }
        });
    }

    /** Filtered search, newest first. */
    public CompletableFuture<List<Echo>> search(EchoQuery q, int limit, int offset) {
        return db.query(c -> {
            StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM echoes WHERE time >= ?");
            List<Object> args = new ArrayList<>();
            args.add(q.since());
            where(q, sql, args);
            sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
            args.add(limit);
            args.add(offset);
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                Database.bind(ps, args.toArray());
                return read(ps);
            }
        });
    }

    public CompletableFuture<Void> markRewound(Collection<Long> ids, boolean rewound) {
        return db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE echoes SET rewound = ? WHERE id = ?")) {
                for (long id : ids) {
                    Database.bind(ps, rewound ? 1 : 0, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private static void where(EchoQuery q, StringBuilder sql, List<Object> args) {
        if (q.actor() != null) {
            sql.append(" AND actor_name = ? COLLATE NOCASE");
            args.add(q.actor());
        }
        if (q.action() != null) {
            sql.append(" AND action = ?");
            args.add(q.action().name());
        }
        if (q.world() != null) {
            sql.append(" AND world = ?");
            args.add(q.world());
            if (q.radius() >= 0) {
                sql.append(" AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN ? AND ?");
                args.add(q.cx() - q.radius());
                args.add(q.cx() + q.radius());
                args.add(q.cz() - q.radius());
                args.add(q.cz() + q.radius());
                args.add(q.cy() - q.radius());
                args.add(q.cy() + q.radius());
            }
        }
        if (q.text() != null) {
            sql.append(" AND (old_data LIKE ? OR new_data LIKE ? OR actor_name LIKE ?)");
            String l = "%" + q.text() + "%";
            args.add(l);
            args.add(l);
            args.add(l);
        }
        if (q.rewound() != null) {
            sql.append(" AND rewound = ?");
            args.add(q.rewound() ? 1 : 0);
        }
    }

    private static List<Echo> read(PreparedStatement ps) throws SQLException {
        List<Echo> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Echo.Action a = Echo.Action.parse(rs.getString(5));
                if (a == null) {
                    continue;
                }
                out.add(new Echo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), a, rs.getString(6),
                        rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getString(10), rs.getString(11), rs.getInt(12) == 1));
            }
        }
        return out;
    }
}
