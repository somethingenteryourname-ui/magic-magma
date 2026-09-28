package dev.magicmagma.fireball;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Throws fireballs from the Infinite Fire Charge and controls what they do when they hit.
 *
 * <p>Each fireball remembers the settings it was thrown with, so changing a setting doesn't affect
 * fireballs that are already flying.
 */
public final class FireballListener implements Listener {

    private static final String USE_PERMISSION = "infinitefireball.use";

    private final InfiniteFireball plugin;
    private final FireballSettings settings;
    private final FireballItem items;

    private final NamespacedKey markerKey;
    private final NamespacedKey sizeKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey totemKey;
    private final NamespacedKey blocksKey;
    private final NamespacedKey fireKey;
    private final NamespacedKey hurtSelfKey;

    /** Fireballs in flight, for the trail and the flight time limit. */
    private final Map<UUID, Fireball> active = new HashMap<>();
    /** Creatures each fireball has already hit, so a direct hit plus the blast only counts once. */
    private final Map<UUID, Set<UUID>> alreadyHit = new HashMap<>();
    private final Map<UUID, Long> lastThrow = new HashMap<>();

    /** Set while a totem-mode hit is being applied, so its death message can be replaced. */
    private UUID finishingVictim;
    private String finishingKiller;

    private BukkitTask tickTask;

    public FireballListener(InfiniteFireball plugin, FireballSettings settings, FireballItem items) {
        this.plugin = plugin;
        this.settings = settings;
        this.items = items;
        this.markerKey = new NamespacedKey(plugin, "fireball");
        this.sizeKey = new NamespacedKey(plugin, "size");
        this.damageKey = new NamespacedKey(plugin, "damage");
        this.totemKey = new NamespacedKey(plugin, "totem_mode");
        this.blocksKey = new NamespacedKey(plugin, "break_blocks");
        this.fireKey = new NamespacedKey(plugin, "set_fire");
        this.hurtSelfKey = new NamespacedKey(plugin, "hurt_self");
    }

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    // ------------------------------------------------------------------ throwing

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!items.isFireballItem(item)) {
            return;
        }
        // Another plugin (e.g. a region protection plugin) blocked using items here.
        boolean blocked = event.useItemInHand() == Event.Result.DENY;

        // Never let the vanilla fire charge behavior run: it would use up the item and light a fire.
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (blocked || !player.hasPermission(USE_PERMISSION)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastThrow.get(player.getUniqueId());
        if (last != null && now - last < settings.cooldownMillis()) {
            return;
        }
        lastThrow.put(player.getUniqueId(), now);

        launch(player);
    }

    private void launch(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        LargeFireball fireball = player.launchProjectile(LargeFireball.class, direction.clone().multiply(settings.speed()));
        fireball.setDirection(direction);
        fireball.setVelocity(direction.clone().multiply(settings.speed()));
        fireball.setYield((float) settings.size());
        fireball.setIsIncendiary(settings.setFire());

        PersistentDataContainer data = fireball.getPersistentDataContainer();
        data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        data.set(sizeKey, PersistentDataType.DOUBLE, settings.size());
        data.set(damageKey, PersistentDataType.DOUBLE, settings.damageHearts());
        data.set(totemKey, PersistentDataType.BYTE, flag(settings.totemMode()));
        data.set(blocksKey, PersistentDataType.BYTE, flag(settings.breakBlocks()));
        data.set(fireKey, PersistentDataType.BYTE, flag(settings.setFire()));
        data.set(hurtSelfKey, PersistentDataType.BYTE, flag(settings.hurtSelf()));

        active.put(fireball.getUniqueId(), fireball);
        player.swingMainHand();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
    }

    private void tick() {
        int maxTicks = settings.maxFlightTicks();
        Iterator<Map.Entry<UUID, Fireball>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Fireball> entry = it.next();
            Fireball fireball = entry.getValue();
            if (!fireball.isValid()) {
                it.remove();
                alreadyHit.remove(entry.getKey());
                continue;
            }
            if (fireball.getTicksLived() > maxTicks) {
                fireball.remove();
                it.remove();
                alreadyHit.remove(entry.getKey());
                continue;
            }
            if (settings.trail()) {
                double size = sizeOf(fireball);
                double spread = Math.min(0.15 + size * 0.08, 1.5);
                int count = (int) Math.min(2 + size, 12);
                Location at = fireball.getLocation();
                fireball.getWorld().spawnParticle(Particle.FLAME, at, count, spread, spread, spread, 0.01);
                fireball.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, Math.max(1, count / 3), spread, spread, spread, 0.0);
            }
        }
        // Fireballs that were saved with a chunk and loaded again aren't in 'active'; forget their hits too.
        alreadyHit.keySet().removeIf(id -> !active.containsKey(id));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastThrow.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ explosion

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball) || !isOurs(fireball)) {
            return;
        }
        event.setRadius((float) sizeOf(fireball));
        event.setFire(getFlag(fireball, fireKey));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball) || !isOurs(fireball)) {
            return;
        }
        if (getFlag(fireball, blocksKey)) {
            return;
        }
        event.blockList().clear();
        // Vanilla only puts fire where blocks were blown up, so place it ourselves when blocks are kept.
        if (getFlag(fireball, fireKey)) {
            ignite(event.getLocation(), Math.min(sizeOf(fireball), 16.0), fireball);
        }
    }

    private void ignite(Location center, double radius, Entity igniter) {
        World world = center.getWorld();
        int r = (int) Math.ceil(radius);
        double radiusSquared = radius * radius;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > radiusSquared || random.nextInt(3) != 0) {
                        continue;
                    }
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (!block.getType().isAir() || !block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                        continue;
                    }
                    BlockIgniteEvent ignite = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.FIREBALL, igniter);
                    if (ignite.callEvent()) {
                        block.setType(Material.FIRE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();

        // Someone punching or shooting the fireball to send it back.
        if (victim instanceof Fireball fireball && isOurs(fireball)) {
            if (!settings.canBeDeflected()) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Fireball fireball)
                || !isOurs(fireball)
                || !(victim instanceof LivingEntity target)) {
            return;
        }

        Entity shooter = shooterOf(fireball);
        if (shooter != null && shooter.getUniqueId().equals(target.getUniqueId()) && !getFlag(fireball, hurtSelfKey)) {
            event.setCancelled(true);
            return;
        }

        // The direct hit and the blast are separate damage events; only the first one counts.
        Set<UUID> hit = alreadyHit.computeIfAbsent(fireball.getUniqueId(), id -> new HashSet<>());
        if (!hit.add(target.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Armor stands aren't really creatures; let the blast knock them over as usual.
        if (getFlag(fireball, totemKey) && !(target instanceof ArmorStand)) {
            // Cancelling the explosion damage is what keeps armor and shields from losing durability.
            event.setCancelled(true);
            // Apply the finishing hit next tick, outside the explosion's own damage handling.
            plugin.getServer().getScheduler().runTask(plugin, () -> popTotemOrKill(target, shooter));
            return;
        }

        double hearts = fireball.getPersistentDataContainer().getOrDefault(damageKey, PersistentDataType.DOUBLE, settings.damageHearts());
        event.setDamage(hearts * 2.0);
    }

    /**
     * Hits the target hard enough to kill it through anything, with a damage type that vanilla lets a
     * Totem of Undying save you from. Starvation damage skips armor (so armor isn't damaged), shields,
     * Protection and Resistance, but unlike /kill it still respects totems.
     */
    private void popTotemOrKill(LivingEntity target, Entity shooter) {
        if (!target.isValid() || target.isDead()) {
            return;
        }
        if (target instanceof Player player
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }

        DamageSource.Builder source = DamageSource.builder(DamageType.STARVE);
        if (shooter != null && shooter.isValid()) {
            source = source.withCausingEntity(shooter).withDirectEntity(shooter);
        }
        double amount = target.getHealth() + target.getAbsorptionAmount() + 1000.0;

        target.setNoDamageTicks(0);
        finishingVictim = target.getUniqueId();
        finishingKiller = shooter != null ? shooter.getName() : null;
        try {
            target.damage(amount, source.build());
        } finally {
            finishingVictim = null;
            finishingKiller = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (finishingVictim == null || !finishingVictim.equals(event.getEntity().getUniqueId())) {
            return;
        }
        String template = plugin.getConfig().getString("messages.death-message", "");
        if (template.isEmpty()) {
            return;
        }
        event.deathMessage(MiniMessage.miniMessage().deserialize(template,
                Placeholder.unparsed("victim", event.getEntity().getName()),
                Placeholder.unparsed("killer", finishingKiller != null ? finishingKiller : "someone")));
    }

    // ------------------------------------------------------------------ item protection

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (items.isFireballItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (items.isFireballItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean isOurs(Fireball fireball) {
        return fireball.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private double sizeOf(Fireball fireball) {
        return fireball.getPersistentDataContainer().getOrDefault(sizeKey, PersistentDataType.DOUBLE, settings.size());
    }

    private boolean getFlag(Fireball fireball, NamespacedKey key) {
        Byte value = fireball.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    private static byte flag(boolean value) {
        return value ? (byte) 1 : (byte) 0;
    }

    private static Entity shooterOf(Fireball fireball) {
        ProjectileSource source = fireball.getShooter();
        return source instanceof Entity entity ? entity : null;
    }
}
