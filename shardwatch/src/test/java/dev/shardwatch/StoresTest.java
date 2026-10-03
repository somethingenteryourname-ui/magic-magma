package dev.shardwatch;

import dev.shardwatch.echo.Echo;
import dev.shardwatch.echo.EchoQuery;
import dev.shardwatch.echo.EchoStore;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.flare.FlareStore;
import dev.shardwatch.glint.GlintStore;
import dev.shardwatch.storage.Database;
import dev.shardwatch.verdict.Verdict;
import dev.shardwatch.verdict.VerdictStore;
import dev.shardwatch.verdict.VerdictType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs every store against a real SQLite file. */
class StoresTest {

    @TempDir
    File dir;
    Database db;

    @BeforeEach
    void open() throws Exception {
        db = new Database(dir, Logger.getLogger("test"));
        db.open("test.db");
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void verdictLifecycle() {
        VerdictStore store = new VerdictStore(db);
        UUID target = UUID.randomUUID();
        long now = System.currentTimeMillis();
        store.insert(VerdictType.CHIP, target, "Steve", null, "CONSOLE", "spam", now, 0, true).join();
        Verdict hush1 = store.insert(VerdictType.HUSH, target, "Steve", null, "Mod", "caps", now, now + 60_000, true).join();
        Verdict hush2 = store.insert(VerdictType.HUSH, target, "Steve", null, "Mod", "again", now, now + 120_000, true).join();
        List<Verdict> active = store.active(target, VerdictType.HUSH).join();
        assertEquals(1, active.size(), "a new Hush replaces the old one");
        assertEquals(hush2.id(), active.get(0).id());
        assertTrue(hush2.inForce(now));
        assertFalse(store.history(target, 10, 0).join().stream().filter(v -> v.id() == hush1.id()).findFirst().orElseThrow().active());
        // Chips are logged, never "active".
        assertEquals(0, store.active(target, VerdictType.CHIP).join().size());
        assertEquals(1, store.counts(target).join()[VerdictType.CHIP.ordinal()]);
        // Expired timed verdicts are deactivated lazily.
        store.insert(VerdictType.ENCASE, target, "Steve", null, "Mod", "old", now - 10_000, now - 1, true).join();
        assertEquals(0, store.active(target, VerdictType.ENCASE).join().size());
        assertEquals(1, store.revoke(target, VerdictType.HUSH, "Admin", "appeal").join());
        assertEquals(0, store.active(target, VerdictType.HUSH).join().size());
        assertEquals("Admin", store.byId(hush2.id()).join().revokedBy());
        assertEquals(4, store.recent(null, 0, 50, 0).join().size());
        assertEquals(1, store.recent("caps", 0, 50, 0).join().size());
    }

    @Test
    void flareWorkflow() {
        FlareStore store = new FlareStore(db);
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Flare f = store.insert(reporter, "Alex", target, "Steve", "griefing", "broke my house", "world", 1, 64, 2).join();
        assertTrue(store.hasOpen(reporter, target).join());
        assertEquals(1, store.countOpen().join());
        assertTrue(store.transition(f.id(), List.of(Flare.Status.OPEN), Flare.Status.CLAIMED, "u", "Mod", null).join());
        assertFalse(store.transition(f.id(), List.of(Flare.Status.OPEN), Flare.Status.CLAIMED, "u", "Mod2", null).join(),
                "can't claim twice");
        assertTrue(store.transition(f.id(), List.of(Flare.Status.OPEN, Flare.Status.CLAIMED), Flare.Status.RESOLVED,
                "u", "Mod", "rolled back").join());
        Flare done = store.byId(f.id()).join();
        assertEquals(Flare.Status.RESOLVED, done.status());
        assertEquals("rolled back", done.resolution());
        assertTrue(done.closedAt() > 0);
        assertEquals(0, store.countOpen().join());
        assertFalse(store.hasOpen(reporter, target).join());
        assertEquals(1, store.list(null, "house", 10, 0).join().size());
        assertNull(store.byId(999).join());
    }

    @Test
    void echoSearchAndRewindFlags() {
        EchoStore store = new EchoStore(db);
        long now = System.currentTimeMillis();
        store.insertBatch(List.of(
                new Echo(0, now - 5000, "u1", "Griefer", Echo.Action.BREAK, "world", 10, 64, 10, "minecraft:oak_planks", "minecraft:air", false),
                new Echo(0, now - 4000, "u1", "Griefer", Echo.Action.PLACE, "world", 11, 64, 10, "minecraft:air", "minecraft:lava", false),
                new Echo(0, now - 3000, "u2", "Builder", Echo.Action.PLACE, "world", 50, 64, 50, "minecraft:air", "minecraft:stone", false),
                new Echo(0, now - 2000, null, "#tnt", Echo.Action.EXPLODE, "world_nether", 10, 64, 10, "minecraft:netherrack", "minecraft:air", false)
        )).join();
        assertEquals(2, store.search(new EchoQuery("griefer", null, null, 0, 0, 0, -1, 0, null, false), 100, 0).join().size(),
                "actor match is case-insensitive");
        List<Echo> near = store.search(new EchoQuery(null, null, "world", 10, 64, 10, 3, 0, null, null), 100, 0).join();
        assertEquals(2, near.size(), "radius stays inside one world");
        assertEquals(11, near.get(0).x(), "newest first");
        assertEquals(1, store.search(new EchoQuery(null, Echo.Action.EXPLODE, null, 0, 0, 0, -1, 0, null, null), 100, 0).join().size());
        assertEquals(1, store.search(new EchoQuery(null, null, null, 0, 0, 0, -1, 0, "lava", null), 100, 0).join().size());
        store.markRewound(List.of(near.get(0).id()), true).join();
        assertEquals(1, store.search(new EchoQuery("Griefer", null, null, 0, 0, 0, -1, 0, null, false), 100, 0).join().size());
        assertEquals(1, store.at("world", 10, 64, 10, 10, 0).join().size());
    }

    @Test
    void glintHandledOnce() {
        GlintStore store = new GlintStore(db);
        long id = store.insert(UUID.randomUUID(), "Miner", "diamond ore", "world", 0, -50, 0, 55.0).join();
        assertTrue(store.markHandled(id, "Mod").join());
        assertFalse(store.markHandled(id, "Mod2").join());
        assertEquals("Mod", store.recent(null, 0, 10, 0).join().get(0).handledBy());
    }
}
