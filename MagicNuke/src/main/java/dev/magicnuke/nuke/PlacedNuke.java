package dev.magicnuke.nuke;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;

import java.util.UUID;

/** A nuke standing on the ground, waiting to be lit. */
public final class PlacedNuke {

    private final ItemDisplay body;
    private final UUID hitboxId;
    private final NukeSize size;

    PlacedNuke(ItemDisplay body, UUID hitboxId, NukeSize size) {
        this.body = body;
        this.hitboxId = hitboxId;
        this.size = size;
    }

    public ItemDisplay body() {
        return body;
    }

    public UUID bodyId() {
        return body.getUniqueId();
    }

    public UUID hitboxId() {
        return hitboxId;
    }

    public Interaction hitbox() {
        if (hitboxId == null) return null;
        Entity e = Bukkit.getEntity(hitboxId);
        return e instanceof Interaction i ? i : null;
    }

    public NukeSize size() {
        return size;
    }

    /** Bottom center of the missile. */
    public Location base() {
        return body.getLocation().subtract(0, size.halfHeight(), 0);
    }
}
