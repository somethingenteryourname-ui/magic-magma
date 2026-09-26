package dev.crystalline.resonance.spell;

import dev.crystalline.resonance.CrystallineResonance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Casts a tome's spell when a player right-clicks with it in their main hand. */
public final class CastListener implements Listener {

    private final CrystallineResonance plugin;

    public CastListener(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    // Right-clicking air arrives already "cancelled", so cancelled events must not be ignored here.
    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // Material#isInteractable has no replacement for this check
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        SpellType spell = plugin.items().tomeSpell(event.getItem());
        if (spell == null) {
            return;
        }

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        // Let players still open doors, chests, etc. while holding a tome (sneak to cast anyway).
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        plugin.spells().tryCast(player, spell);
    }
}
