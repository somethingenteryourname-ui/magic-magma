package dev.crystalline.resonance.spell;

import org.bukkit.entity.Player;

/** A castable spell. Mana, cooldowns and permissions are handled by {@link SpellManager}. */
public interface Spell {

    SpellType type();

    void cast(Player caster, SpellSettings settings);
}
