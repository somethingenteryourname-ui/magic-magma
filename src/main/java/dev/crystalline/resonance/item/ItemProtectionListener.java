package dev.crystalline.resonance.item;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.crystal.CrystalType;
import dev.crystalline.resonance.recipe.RecipeManager.TomeRecipe;
import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.EnumMap;
import java.util.Map;

/**
 * Validates tome recipes (real crystals, craft permission) and stops crystals and tomes from being
 * used up as their plain vanilla materials in other recipes, smithing tables or enchanting tables.
 */
public final class ItemProtectionListener implements Listener {

    private final CrystallineResonance plugin;

    public ItemProtectionListener(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }
        ItemStack[] matrix = inventory.getMatrix();

        TomeRecipe tomeRecipe = recipe instanceof Keyed keyed ? plugin.recipes().recipe(keyed.getKey()) : null;
        if (tomeRecipe == null) {
            // Any other recipe: never let custom items be consumed as vanilla materials.
            for (ItemStack item : matrix) {
                if (plugin.items().isCustom(item)) {
                    inventory.setResult(null);
                    return;
                }
            }
            return;
        }

        HumanEntity crafter = event.getView().getPlayer();
        if (!crafter.hasPermission(tomeRecipe.spell().craftPermission())) {
            inventory.setResult(null);
            return;
        }

        // The recipe matches on base materials; make sure the right number of real crystals is used.
        Map<CrystalType, Integer> found = new EnumMap<>(CrystalType.class);
        for (ItemStack item : matrix) {
            if (plugin.items().tomeSpell(item) != null) {
                inventory.setResult(null);
                return;
            }
            CrystalType type = plugin.items().crystalType(item);
            if (type != null) {
                found.merge(type, 1, Integer::sum);
            }
        }
        if (!found.equals(tomeRecipe.crystals())) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (plugin.items().isCustom(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (plugin.items().isCustom(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
