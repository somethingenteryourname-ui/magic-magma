package dev.shardwatch.flare;

import java.util.UUID;

/** One player report. */
public record Flare(long id, UUID reporter, String reporterName, UUID target, String targetName, String category,
                    String reason, String world, int x, int y, int z, long created, Status status, String handlerUuid,
                    String handlerName, String resolution, long closedAt) {

    public enum Status {
        OPEN, CLAIMED, RESOLVED, DISMISSED;

        public boolean closed() {
            return this == RESOLVED || this == DISMISSED;
        }
    }
}
