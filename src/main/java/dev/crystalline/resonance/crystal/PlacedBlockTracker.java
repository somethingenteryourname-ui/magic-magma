package dev.crystalline.resonance.crystal;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Remembers which crystal-dropping blocks were placed by players, so they can't be farmed by
 * placing and breaking them. Positions are stored in the chunk's persistent data.
 */
public final class PlacedBlockTracker {

    private static final int MAX_PER_CHUNK = 4096;

    private final NamespacedKey key;

    public PlacedBlockTracker(NamespacedKey key) {
        this.key = key;
    }

    public void markPlaced(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        long[] positions = data.getOrDefault(key, PersistentDataType.LONG_ARRAY, new long[0]);
        long packed = pack(block);
        if (indexOf(positions, packed) >= 0) {
            return;
        }
        long[] next;
        if (positions.length >= MAX_PER_CHUNK) {
            // Drop the oldest entry to keep chunk data bounded.
            next = Arrays.copyOfRange(positions, 1, positions.length + 1);
        } else {
            next = Arrays.copyOf(positions, positions.length + 1);
        }
        next[next.length - 1] = packed;
        data.set(key, PersistentDataType.LONG_ARRAY, next);
    }

    /** Forgets the block and returns whether it had been placed by a player. */
    public boolean consume(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        long[] positions = data.get(key, PersistentDataType.LONG_ARRAY);
        if (positions == null) {
            return false;
        }
        int index = indexOf(positions, pack(block));
        if (index < 0) {
            return false;
        }
        if (positions.length == 1) {
            data.remove(key);
        } else {
            long[] next = new long[positions.length - 1];
            System.arraycopy(positions, 0, next, 0, index);
            System.arraycopy(positions, index + 1, next, index, positions.length - index - 1);
            data.set(key, PersistentDataType.LONG_ARRAY, next);
        }
        return true;
    }

    private static long pack(Block block) {
        return ((long) block.getY() << 8) | ((block.getX() & 15L) << 4) | (block.getZ() & 15L);
    }

    private static int indexOf(long[] values, long value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
