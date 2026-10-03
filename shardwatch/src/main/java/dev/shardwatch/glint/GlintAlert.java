package dev.shardwatch.glint;

import java.util.UUID;

/** A saved x-ray alert. */
public record GlintAlert(long id, long time, UUID uuid, String name, String ore, String world, int x, int y, int z,
                         double score, String handledBy) {
}
