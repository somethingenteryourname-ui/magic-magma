package dev.shardwatch.verdict;

import java.util.UUID;

/** One stored verdict. {@code expires == 0} means permanent. */
public record Verdict(long id, VerdictType type, UUID target, String targetName, String actorUuid, String actorName,
                      String reason, long created, long expires, boolean active, String revokedBy, long revokedAt,
                      String revokeReason) {

    public boolean permanent() {
        return expires == 0;
    }

    public boolean inForce(long now) {
        return active && type.lasting() && (expires == 0 || expires > now);
    }

    public long remaining(long now) {
        return expires == 0 ? Long.MAX_VALUE : Math.max(0, expires - now);
    }

    /** Status word shown in the Ledger. */
    public String status(long now) {
        if (revokedBy != null) {
            return "revoked";
        }
        if (!type.lasting()) {
            return "logged";
        }
        return inForce(now) ? "active" : "expired";
    }
}
