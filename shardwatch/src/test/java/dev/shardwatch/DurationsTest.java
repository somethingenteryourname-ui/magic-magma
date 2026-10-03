package dev.shardwatch;

import dev.shardwatch.scope.ScopeViewer;
import dev.shardwatch.util.Durations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {

    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(30_000L, Durations.parse("30s"));
        assertEquals(600_000L, Durations.parse("10m"));
        assertEquals(7_200_000L, Durations.parse("2h"));
        assertEquals(86_400_000L * 9, Durations.parse("1w2d"));
        assertEquals(2_592_000_000L, Durations.parse("1mo"));
        assertEquals(Durations.PERMANENT, Durations.parse("perm"));
        assertEquals(Durations.PERMANENT, Durations.parse("Permanent"));
    }

    @Test
    void rejectsGarbage() {
        assertNull(Durations.parse("griefing"));
        assertNull(Durations.parse("10"));
        assertNull(Durations.parse("2h stuff"));
        assertNull(Durations.parse("0m"));
        assertNull(Durations.parse(""));
    }

    @Test
    void formats() {
        assertEquals("1d 2h", Durations.format(93_600_000L));
        assertEquals("1w", Durations.format(604_800_000L, 1));
        assertEquals("0s", Durations.format(0));
    }

    @Test
    void scopeFilterSplitsTokens() {
        ScopeViewer.Filter f = ScopeViewer.Filter.parse(new String[]{"u:Steve", "r:12", "a:break", "diamond", "block"});
        assertEquals("Steve", f.user());
        assertEquals(12, f.radius());
        assertEquals("break", f.action());
        assertEquals("diamond block", f.text());
        ScopeViewer.Filter t = ScopeViewer.Filter.parse(new String[]{"t:1h"});
        assertTrue(t.since() > System.currentTimeMillis() - 3_700_000L);
    }
}
