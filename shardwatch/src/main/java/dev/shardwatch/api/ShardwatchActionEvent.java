package dev.shardwatch.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on the main thread after a staff action has been applied and saved. Other plugins can listen to it;
 * Shardwatch itself uses it to grant Lustre.
 */
public final class ShardwatchActionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable Player actor;
    private final StaffAction action;
    private final String target;
    private final String detail;

    public ShardwatchActionEvent(@Nullable Player actor, StaffAction action, String target, String detail) {
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.detail = detail;
    }

    /** The staff member, or null when the console did it. */
    public @Nullable Player getActor() {
        return actor;
    }

    public StaffAction getAction() {
        return action;
    }

    /** Target name, or an empty string. */
    public String getTarget() {
        return target;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
