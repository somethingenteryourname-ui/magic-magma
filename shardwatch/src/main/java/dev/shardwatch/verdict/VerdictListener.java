package dev.shardwatch.verdict;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

/** Enforces Encase at login, Hush in chat and Petrify everywhere. */
public final class VerdictListener implements Listener {

    private final Shardwatch plugin;

    public VerdictListener(Shardwatch plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            Verdict encase = plugin.verdicts().loadOnLogin(event.getUniqueId());
            if (encase != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        plugin.verdicts().screen("screens.encase", plugin.verdicts().resolvers(encase)));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Could not check Encase for " + event.getName() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean("verdicts.encase.fail-closed", false)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.lang().get("verdict.encase.check-failed"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Verdict hush = plugin.verdicts().hush(event.getPlayer().getUniqueId());
        if (hush != null) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "verdict.hush.blocked", plugin.verdicts().resolvers(hush));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        Verdict hush = plugin.verdicts().hush(p.getUniqueId());
        if (hush != null && plugin.getConfig().getStringList("verdicts.hush.blocked-commands").contains(label)) {
            event.setCancelled(true);
            plugin.lang().send(p, "verdict.hush.blocked", plugin.verdicts().resolvers(hush));
            return;
        }
        if (plugin.verdicts().isPetrified(p.getUniqueId()) && !p.hasPermission("shardwatch.verdict.petrify")) {
            List<String> allowed = plugin.getConfig().getStringList("verdicts.petrify.allowed-commands");
            if (!allowed.contains(label)) {
                event.setCancelled(true);
                plugin.lang().send(p, "verdict.petrify.no-commands");
            }
        }
    }

    // ------------------------------------------------------------------ Petrify

    private boolean petrified(Player p) {
        return plugin.verdicts().isPetrified(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!petrified(event.getPlayer()) || !event.hasChangedPosition()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        // Allow falling so players can't be held in mid-air, but nothing else.
        boolean falling = to.getY() < from.getY() && from.clone().subtract(0, 0.05, 0).getBlock().isPassable()
                && plugin.getConfig().getBoolean("verdicts.petrify.allow-falling", true);
        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        if (falling) {
            locked.setY(to.getY());
        }
        event.setTo(locked);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (petrified(event.getPlayer()) && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || petrified(event.getPlayer()) && event.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (petrified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (petrified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (petrified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (petrified(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("verdicts.petrify.invulnerable", true)) {
            return;
        }
        if (event.getEntity() instanceof Player p && petrified(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && petrified(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (petrified(p)) {
            plugin.lang().send(p, "verdict.petrify.still");
            plugin.animations().petrify(p, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (petrified(p)) {
            plugin.lang().send(Players.withPermission("shardwatch.notify.verdicts"), "verdict.petrify.logout",
                    Text.p("player", p.getName()));
            plugin.animations().petrify(p, false);
        }
        plugin.verdicts().forget(p.getUniqueId());
    }
}
