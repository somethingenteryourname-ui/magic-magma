package dev.crystalline.resonance.spell;

import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/** Decides which entities a caster's spells may hit. */
public record SpellTargets(boolean affectPlayers, boolean hitOwnPets) {

    public boolean canHit(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.equals(caster)) {
            return false;
        }
        if (!living.isValid() || living.isDead() || living.isInvulnerable() || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player player) {
            GameMode mode = player.getGameMode();
            if (!affectPlayers || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                return false;
            }
        }
        if (!hitOwnPets && entity instanceof Tameable pet && pet.isTamed()
                && caster.getUniqueId().equals(pet.getOwnerUniqueId())) {
            return false;
        }
        return true;
    }
}
