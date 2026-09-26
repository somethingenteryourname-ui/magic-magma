package dev.crystalline.resonance.spell;

import dev.crystalline.resonance.CrystallineResonance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Shared helpers for the built-in spells. */
public abstract class AbstractSpell implements Spell {

    protected final CrystallineResonance plugin;
    private final SpellType type;

    protected AbstractSpell(CrystallineResonance plugin, SpellType type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public final SpellType type() {
        return type;
    }

    protected boolean canHit(Player caster, Entity entity) {
        return plugin.spells().targets().canHit(caster, entity);
    }

    /**
     * Damages the target on behalf of the caster, so kill credit, death messages, PvP rules and
     * protection plugins all behave as if the caster attacked.
     */
    protected void damage(Player caster, LivingEntity target, double amount, DamageType damageType) {
        if (amount <= 0) {
            return;
        }
        DamageSource source = DamageSource.builder(damageType)
                .withCausingEntity(caster)
                .withDirectEntity(caster)
                .build();
        target.damage(amount, source);
    }
}
