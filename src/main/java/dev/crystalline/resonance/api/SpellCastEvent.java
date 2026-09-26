package dev.crystalline.resonance.api;

import dev.crystalline.resonance.spell.SpellType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called after permission and cooldown checks pass, before mana is spent and the spell is cast.
 * Other plugins can cancel the cast or change its mana cost.
 */
public final class SpellCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SpellType spell;
    private double manaCost;
    private boolean cancelled;

    public SpellCastEvent(@NotNull Player caster, @NotNull SpellType spell, double manaCost) {
        super(caster);
        this.spell = spell;
        this.manaCost = manaCost;
    }

    public @NotNull SpellType getSpell() {
        return spell;
    }

    public double getManaCost() {
        return manaCost;
    }

    public void setManaCost(double manaCost) {
        this.manaCost = Math.max(0.0, manaCost);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
