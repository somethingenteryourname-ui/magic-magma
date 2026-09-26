package dev.crystalline.resonance.crystal;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.crystal.CrystalSettings.CrystalDefinition;
import dev.crystalline.resonance.util.Effects;
import dev.crystalline.resonance.util.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Drops crystals from Nether blocks and adds them to Nether structure chests. */
public final class CrystalDropListener implements Listener {

    private static final String GATHER_PERMISSION = "crystalline.gather";

    private final CrystallineResonance plugin;

    public CrystalDropListener(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        CrystalSettings settings = plugin.crystalSettings();
        Block block = event.getBlockPlaced();
        if (settings.ignorePlacedBlocks() && settings.canDropFrom(block.getType())) {
            plugin.placedBlocks().markPlaced(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        CrystalSettings settings = plugin.crystalSettings();
        Block block = event.getBlock();
        if (!settings.canDropFrom(block.getType())) {
            return;
        }
        // Always clear the placed marker, even if nothing else happens.
        boolean placedByPlayer = plugin.placedBlocks().consume(block);
        if (placedByPlayer && settings.ignorePlacedBlocks()) {
            return;
        }

        Player player = event.getPlayer();
        if (!event.isDropItems() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (settings.requireNether() && block.getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }
        if (!player.hasPermission(GATHER_PERMISSION)) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!settings.silkTouchDrops() && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return;
        }
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        double multiplier = settings.globalMultiplier() * (1.0 + fortune * settings.fortuneBonus());

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (CrystalType type : CrystalType.values()) {
            CrystalDefinition definition = settings.definition(type);
            Double chance = definition.drops().get(block.getType());
            if (chance == null || random.nextDouble() >= chance * multiplier) {
                continue;
            }
            ItemStack crystal = plugin.items().createCrystal(type, 1);
            block.getWorld().dropItemNaturally(center, crystal);
            announceFind(player, center, definition);
        }
    }

    private void announceFind(Player player, Location at, CrystalDefinition definition) {
        Effects.dust(at, definition.color(), 1.3f, 24, 0.45);
        Effects.particle(at, Particle.END_ROD, 8, 0.3, 0.05);
        plugin.mana().notify(player, plugin.messages().get("crystal-found",
                Placeholder.component("crystal", Messages.parse(definition.name()))));
        plugin.mana().playFeedback(player, "crystal-found");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        CrystalSettings settings = plugin.crystalSettings();
        if (!settings.lootEnabled() || event.getInventoryHolder() == null) {
            return;
        }
        if (settings.requireNether() && event.getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }
        if (!settings.lootTableAllowed(event.getLootTable().getKey().getKey())) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= settings.lootChance()) {
            return;
        }

        int count = random.nextInt(settings.lootMin(), settings.lootMax() + 1);
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        for (int i = 0; i < count; i++) {
            CrystalType type = pickWeighted(settings, random);
            if (type == null) {
                break;
            }
            loot.add(plugin.items().createCrystal(type, 1));
        }
        event.setLoot(loot);
    }

    private static CrystalType pickWeighted(CrystalSettings settings, ThreadLocalRandom random) {
        int total = 0;
        for (CrystalType type : CrystalType.values()) {
            total += settings.definition(type).lootWeight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (CrystalType type : CrystalType.values()) {
            roll -= settings.definition(type).lootWeight();
            if (roll < 0) {
                return type;
            }
        }
        return null;
    }
}
