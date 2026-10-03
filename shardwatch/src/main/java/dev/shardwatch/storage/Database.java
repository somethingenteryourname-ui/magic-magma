package dev.shardwatch.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage. Every query runs on a single dedicated thread, so SQLite never sees two writers at once and the
 * server thread never blocks on disk.
 */
public final class Database {

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection c) throws SQLException;
    }

    private static final int SCHEMA_VERSION = 1;

    private final File folder;
    private final Logger logger;
    private final ExecutorService executor;
    private Connection connection;

    public Database(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Shardwatch-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public void open(String fileName) throws Exception {
        File file = new File(folder, fileName);
        file.getParentFile().mkdirs();
        executor.submit(() -> {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            migrate();
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }
        if (version < 1) {
            try (Statement st = connection.createStatement()) {
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS verdicts (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          type TEXT NOT NULL,
                          target_uuid TEXT NOT NULL, target_name TEXT NOT NULL,
                          actor_uuid TEXT, actor_name TEXT NOT NULL,
                          reason TEXT NOT NULL,
                          created INTEGER NOT NULL, expires INTEGER NOT NULL DEFAULT 0,
                          active INTEGER NOT NULL DEFAULT 1,
                          revoked_by TEXT, revoked_at INTEGER, revoke_reason TEXT)""");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_verdicts_target ON verdicts(target_uuid)");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_verdicts_active ON verdicts(active, type)");
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS flares (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          reporter_uuid TEXT NOT NULL, reporter_name TEXT NOT NULL,
                          target_uuid TEXT NOT NULL, target_name TEXT NOT NULL,
                          category TEXT NOT NULL, reason TEXT NOT NULL,
                          world TEXT, x INTEGER, y INTEGER, z INTEGER,
                          created INTEGER NOT NULL,
                          status TEXT NOT NULL DEFAULT 'OPEN',
                          handler_uuid TEXT, handler_name TEXT, resolution TEXT, closed_at INTEGER)""");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_flares_status ON flares(status)");
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS echoes (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          time INTEGER NOT NULL,
                          actor_uuid TEXT, actor_name TEXT NOT NULL,
                          action TEXT NOT NULL,
                          world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                          old_data TEXT NOT NULL, new_data TEXT NOT NULL,
                          rewound INTEGER NOT NULL DEFAULT 0)""");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_echoes_pos ON echoes(world, x, z, y)");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_echoes_time ON echoes(time)");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_echoes_actor ON echoes(actor_name, time)");
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS chat_log (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          time INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL,
                          kind TEXT NOT NULL, message TEXT NOT NULL)""");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_chat_time ON chat_log(time)");
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS audit (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          time INTEGER NOT NULL, actor_uuid TEXT, actor_name TEXT NOT NULL,
                          action TEXT NOT NULL, detail TEXT NOT NULL)""");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_audit_time ON audit(time)");
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS glint (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          time INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL,
                          ore TEXT NOT NULL, world TEXT NOT NULL, x INTEGER, y INTEGER, z INTEGER,
                          score REAL NOT NULL, handled_by TEXT)""");
                st.addBatch("CREATE INDEX IF NOT EXISTS idx_glint_time ON glint(time)");
                st.addBatch("""
                        CREATE TABLE IF NOT EXISTS progress (
                          uuid TEXT PRIMARY KEY, name TEXT NOT NULL,
                          facet TEXT, lustre INTEGER NOT NULL DEFAULT 0, lifetime INTEGER NOT NULL DEFAULT 0,
                          refinements TEXT NOT NULL DEFAULT '', keepsakes TEXT NOT NULL DEFAULT '',
                          active_aura TEXT, active_sigil TEXT,
                          alerts_on INTEGER NOT NULL DEFAULT 1, fx_on INTEGER NOT NULL DEFAULT 1,
                          veiled INTEGER NOT NULL DEFAULT 0,
                          daily_day INTEGER NOT NULL DEFAULT 0, daily_earned INTEGER NOT NULL DEFAULT 0)""");
                st.addBatch("PRAGMA user_version = " + SCHEMA_VERSION);
                st.executeBatch();
            }
        }
    }

    public <T> CompletableFuture<T> query(SqlFunction<T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.apply(connection);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database error", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> run(SqlConsumer fn) {
        return query(c -> {
            fn.accept(c);
            return null;
        });
    }

    /** Runs several statements in one transaction. */
    public CompletableFuture<Void> transaction(SqlConsumer fn) {
        return query(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                fn.accept(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            return null;
        });
    }

    public static PreparedStatement bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
        return ps;
    }

    public static long insertId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            return keys.next() ? keys.getLong(1) : -1;
        }
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Database queue did not drain in 15s.");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
