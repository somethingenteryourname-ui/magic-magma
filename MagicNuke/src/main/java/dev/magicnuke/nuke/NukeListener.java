package dev.magicnuke.nuke;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class NukeListener implements Listener {

    private final MagicNuke plugin;

    public NukeListener(MagicNuke plugin) {
        this.plugin = plugin;
    }

    private NukeManager nukes() {
        return plugin.nukes();
    }

    // ------------------------------------------------------------------ placing

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) return;
        ItemStack item = event.getItem();
        NukeSize size = plugin.items().sizeOf(item);
        if (size == null) return;
        // never let the TNT itself get placed
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!player.hasPermission("magicnuke.use")) {
            Msg.send(player, "<red>You aren't allowed to use nukes.");
            return;
        }
        if (plugin.settings().isDisabled(clicked.getWorld())) {
            Msg.send(player, "<red>Nukes are disabled in this world.");
            return;
        }
        Block target = clicked.isReplaceable() && !clicked.isLiquid() ? clicked : clicked.getRelative(event.getBlockFace());
        if (!nukes().canPlaceAt(target, size)) {
            player.sendActionBar(Msg.mm("<red>Not enough room to set up the nuke here."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        // respect protection plugins: ask whether a block could be placed here
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, item, player, true, event.getHand());
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled() || !check.canBuild()) return;

        nukes().place(target.getLocation().add(0.5, 0, 0.5), size);
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.swingHand(event.getHand());
        player.sendActionBar(Msg.mm("<gold>☢ Nuke ready. <yellow>Light it with Flint and Steel<gray>, or <yellow>punch <gray>to pick it up."));
    }

    /** The nuke item is TNT underneath: make sure it's never placed as a block. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getClass() != BlockPlaceEvent.class) return;
        if (plugin.items().isNuke(event.getItemInHand()) && event.getBlockPlaced().getType() == Material.TNT) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ lighting & picking up

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractNuke(PlayerInteractEntityEvent event) {
        PlacedNuke nuke = nukes().byHitbox(event.getRightClicked());
        if (nuke == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material type = hand.getType();
        if (type != Material.FLINT_AND_STEEL && type != Material.FIRE_CHARGE) {
            hand = player.getInventory().getItemInOffHand();
            type = hand.getType();
        }
        if (type != Material.FLINT_AND_STEEL && type != Material.FIRE_CHARGE) {
            player.sendActionBar(Msg.mm("<yellow>Use Flint and Steel <gray>or a fire charge to launch this nuke."));
            return;
        }
        if (!player.hasPermission("magicnuke.use")) {
            Msg.send(player, "<red>You aren't allowed to launch nukes.");
            return;
        }
        if (plugin.settings().isDisabled(player.getWorld())) {
            Msg.send(player, "<red>Nukes are disabled in this world.");
            return;
        }
        Location at = nuke.base();
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (type == Material.FIRE_CHARGE) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                ItemMeta meta = hand.getItemMeta();
                if (meta instanceof Damageable dmg) {
                    dmg.setDamage(dmg.getDamage() + 1);
                    if (dmg.getDamage() >= hand.getType().getMaxDurability()) {
                        hand.setAmount(0);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    } else {
                        hand.setItemMeta(meta);
                    }
                }
            }
        }
        at.getWorld().playSound(at, type == Material.FIRE_CHARGE ? Sound.ITEM_FIRECHARGE_USE : Sound.ITEM_FLINTANDSTEEL_USE,
                SoundCategory.PLAYERS, 1f, 1f);
        player.swingMainHand();
        plugin.getLogger().info(player.getName() + " lit a " + Msg.strip(nuke.size().name()) + " at "
                + at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
        nukes().ignite(nuke, nuke.size().fuseTicks());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunchNuke(PrePlayerAttackEntityEvent event) {
        PlacedNuke nuke = nukes().byHitbox(event.getAttacked());
        if (nuke == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.settings().pickupWithPunch || !player.hasPermission("magicnuke.use")) return;
        Location at = nuke.base();
        nukes().remove(nuke);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = plugin.items().create(nuke.size(), 1);
            player.getInventory().addItem(item).values()
                    .forEach(left -> at.getWorld().dropItemNaturally(at, left));
        }
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1f, 0.6f);
        at.getWorld().playSound(at, Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.PLAYERS, 0.8f, 0.6f);
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.02);
    }

    /** The hitbox and model can't be hurt or killed by anything. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (nukes().byAnyPart(event.getEntity()) != null) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ other triggers

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explosionNear(event.getLocation(), 3 + event.getYield() * 2.5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explosionNear(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 3 + event.getYield() * 2.5);
    }

    private void explosionNear(Location at, double range) {
        if (!plugin.settings().igniteFromExplosions) return;
        for (PlacedNuke p : nukes().near(at, Math.min(12, range))) {
            nukes().ignite(p, Math.min(30, p.size().fuseTicks()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        NukeSize size = plugin.items().sizeOf(event.getItem());
        if (size == null) return;
        event.setCancelled(true);
        if (!plugin.settings().dispensersLaunch || plugin.settings().isDisabled(event.getBlock().getWorld())) return;
        Block dispenser = event.getBlock();
        if (!(dispenser.getBlockData() instanceof Directional dir)) return;
        BlockFace face = dir.getFacing();
        Block target = dispenser.getRelative(face);
        if (face == BlockFace.UP || face == BlockFace.DOWN || !nukes().canPlaceAt(target, size)) return;
        ItemStack dispensed = event.getItem().clone();
        // take the item out next tick, once the cancelled dispense has settled
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(dispenser.getState() instanceof Container container)) return;
            Inventory inv = container.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && s.isSimilar(dispensed)) {
                    s.setAmount(s.getAmount() - 1);
                    inv.setItem(i, s.getAmount() > 0 ? s : null);
                    if (nukes().canPlaceAt(target, size)) nukes().launchAt(target.getLocation().add(0.5, 0, 0.5), size);
                    return;
                }
            }
        });
    }

    /** Nukes can't be used as crafting ingredients (e.g. a TNT minecart). */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack s : event.getInventory().getMatrix()) {
            if (plugin.items().isNuke(s)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Debris blocks vanish in a puff of smoke when they land. */
    @EventHandler
    public void onDebrisLand(EntityChangeBlockEvent event) {
        Entity e = event.getEntity();
        if (!nukes().isDebris(e)) return;
        event.setCancelled(true);
        Location at = e.getLocation();
        at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 3, 0.3, 0.2, 0.3, 0.01, null, true);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 12, 0.3, 0.2, 0.3, 0, event.getBlockData());
        plugin.explosions().untrack(e);
        e.remove();
    }

    // ------------------------------------------------------------------ persistence

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) nukes().track(e);
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) nukes().untrack(e);
    }
}
