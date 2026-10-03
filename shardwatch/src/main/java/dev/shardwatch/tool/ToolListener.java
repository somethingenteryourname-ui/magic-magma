package dev.shardwatch.tool;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.echo.RewindEngine;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What every staff tool does, plus the rules that keep tools in staff hands. */
public final class ToolListener implements Listener {

    private final Shardwatch plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, Integer> compassIndex = new HashMap<>();
    private final Map<UUID, Integer> monocleIndex = new HashMap<>();
    private final Map<UUID, UUID> lastGavelTarget = new HashMap<>();
    private final Map<UUID, List<ItemStack>> keptOnDeath = new HashMap<>();

    public ToolListener(Shardwatch plugin) {
        this.plugin = plugin;
        // Keep Flare Compasses pointed at the current Flare.
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshCompasses, 100L,
                Math.max(20L, plugin.getConfig().getLong("tools.flare_compass.refresh-ticks", 200L)));
    }

    private ToolService tools() {
        return plugin.tools();
    }

    private boolean allowed(Player p, StaffTool tool) {
        if (p.hasPermission(tool.permission())) {
            return true;
        }
        plugin.lang().send(p, "tools.no-permission");
        return false;
    }

    // ------------------------------------------------------------------ clicks on blocks / air

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        StaffTool tool = tools().toolOf(item);
        if (tool == null) {
            return;
        }
        Action a = event.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        Block clicked = event.getClickedBlock();
        if (tool != StaffTool.LUSTRE_SHARD && tool != StaffTool.SIGIL) {
            event.setCancelled(true);
        }
        switch (tool) {
            case ECHO_LENS -> {
                if (clicked == null || !allowed(p, tool) || tools().onCooldown(p, tool)) {
                    return;
                }
                Block target = right ? clicked.getRelative(event.getBlockFace()) : clicked;
                tools().startCooldown(p, tool, tools().cooldown(tool));
                plugin.fx().play("lens-focus", target.getLocation().add(0.5, 0.5, 0.5));
                plugin.echoes().inspect(p, target.getLocation(), 1);
            }
            case TIMEGLASS -> {
                if (!allowed(p, tool)) {
                    return;
                }
                if (right && p.isSneaking()) {
                    offerAreaRewind(p);
                } else if (clicked != null) {
                    select(p, clicked.getLocation(), right ? 1 : 0);
                }
            }
            case VEIL_LANTERN -> {
                if (!right || !allowed(p, tool) || tools().onCooldown(p, tool)) {
                    return;
                }
                tools().startCooldown(p, tool, tools().cooldown(tool));
                boolean on = !plugin.facets().isVeiled(p);
                plugin.facets().setVeil(p, on);
                tools().setLanternLit(item, on);
            }
            case FLARE_COMPASS -> {
                if (!right || !allowed(p, tool) || tools().onCooldown(p, tool)) {
                    return;
                }
                tools().startCooldown(p, tool, tools().cooldown(tool));
                useCompass(p, item, p.isSneaking());
            }
            case GLINT_MONOCLE -> {
                if (!right || !allowed(p, tool) || tools().onCooldown(p, tool)) {
                    return;
                }
                tools().startCooldown(p, tool, tools().cooldown(tool));
                if (p.isSneaking()) {
                    plugin.lang().send(p, plugin.glint().unwatch(p) ? "glint.unwatched" : "glint.not-watching");
                } else {
                    cycleSuspects(p);
                }
            }
            case VERDICT_GAVEL -> {
                if (right && p.isSneaking() && allowed(p, tool)) {
                    UUID last = lastGavelTarget.get(p.getUniqueId());
                    Player t = last == null ? null : Bukkit.getPlayer(last);
                    if (t == null) {
                        plugin.lang().send(p, "tools.gavel-no-target");
                    } else {
                        plugin.menus().openLedger(p, t, 0);
                    }
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        StaffTool tool = tools().toolOf(event.getPlayer().getInventory().getItemInMainHand());
        if (tool == StaffTool.ECHO_LENS || tool == StaffTool.TIMEGLASS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tools().isTool(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ clicks on players

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player p = event.getPlayer();
        StaffTool tool = tools().toolOf(p.getInventory().getItemInMainHand());
        if (tool == StaffTool.PETRIFY_PRISM) {
            event.setCancelled(true);
            if (!allowed(p, tool) || tools().onCooldown(p, tool)) {
                return;
            }
            tools().startCooldown(p, tool, tools().cooldown(tool));
            plugin.verdicts().togglePetrify(p, target);
        } else if (tool == StaffTool.VERDICT_GAVEL) {
            event.setCancelled(true);
            gavel(p, target);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        StaffTool tool = tools().toolOf(p.getInventory().getItemInMainHand());
        if (tool == null) {
            return;
        }
        event.setCancelled(true); // staff tools never deal damage
        if (tool == StaffTool.VERDICT_GAVEL && event.getEntity() instanceof Player target) {
            gavel(p, target);
        }
    }

    private void gavel(Player p, Player target) {
        if (!allowed(p, StaffTool.VERDICT_GAVEL) || tools().onCooldown(p, StaffTool.VERDICT_GAVEL)) {
            return;
        }
        tools().startCooldown(p, StaffTool.VERDICT_GAVEL, tools().cooldown(StaffTool.VERDICT_GAVEL));
        lastGavelTarget.put(p.getUniqueId(), target.getUniqueId());
        plugin.fx().play("gavel-hit", target.getLocation().add(0, 1.2, 0));
        if (plugin.getConfig().getBoolean("gui.enabled", true)) {
            plugin.menus().openVerdict(p, target);
        } else {
            openVerdicts(p, target);
        }
    }

    /** Chat-based verdict picker (replaced by the Verdict menu in Stage 3). */
    void openVerdicts(Player p, Player target) {
        plugin.lang().send(p, "tools.gavel-header", Text.p("player", target.getName()));
        Component row = Component.empty();
        for (String type : new String[]{"chip", "hush", "eject", "encase", "petrify"}) {
            if (!p.hasPermission("shardwatch.verdict." + type)) {
                continue;
            }
            String cmd = type.equals("petrify") ? "/petrify " + target.getName() : "/" + type + " " + target.getName() + " ";
            row = row.append(plugin.lang().get("tools.gavel-button-" + type)
                    .clickEvent(type.equals("petrify") ? ClickEvent.runCommand(cmd) : ClickEvent.suggestCommand(cmd)))
                    .append(Component.space());
        }
        row = row.append(plugin.lang().get("tools.gavel-button-ledger")
                .clickEvent(ClickEvent.runCommand("/ledger " + target.getName())));
        p.sendMessage(row);
    }

    // ------------------------------------------------------------------ Timeglass

    private void select(Player p, Location loc, int corner) {
        Location[] sel = selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
        if (sel[1 - corner] != null && !sel[1 - corner].getWorld().equals(loc.getWorld())) {
            sel[1 - corner] = null;
        }
        sel[corner] = loc.clone();
        plugin.lang().send(p, corner == 0 ? "tools.timeglass-pos1" : "tools.timeglass-pos2",
                Text.p("x", loc.getBlockX()), Text.p("y", loc.getBlockY()), Text.p("z", loc.getBlockZ()));
        plugin.fx().playFor(p, "timeglass-mark", loc.clone().add(0.5, 1, 0.5));
        if (sel[0] != null && sel[1] != null) {
            int max = plugin.getConfig().getInt("rewind.max-radius", 60) * 2 + 1;
            int dx = Math.abs(sel[0].getBlockX() - sel[1].getBlockX()) + 1;
            int dy = Math.abs(sel[0].getBlockY() - sel[1].getBlockY()) + 1;
            int dz = Math.abs(sel[0].getBlockZ() - sel[1].getBlockZ()) + 1;
            plugin.lang().send(p, "tools.timeglass-size", Text.p("dx", dx), Text.p("dy", dy), Text.p("dz", dz),
                    Text.p("max", max));
            outline(p, sel[0], sel[1]);
        }
    }

    /** Draws the selection box edges for a few seconds. */
    private void outline(Player p, Location a, Location b) {
        int x0 = Math.min(a.getBlockX(), b.getBlockX()), x1 = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        int y0 = Math.min(a.getBlockY(), b.getBlockY()), y1 = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        int z0 = Math.min(a.getBlockZ(), b.getBlockZ()), z1 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        World w = a.getWorld();
        List<Location> points = new ArrayList<>();
        double step = Math.max(0.5, Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0) / 40.0);
        for (double t = 0; t <= 1.0001; t += step / Math.max(1, Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0))) {
            for (int i = 0; i < 4; i++) {
                double yy = i < 2 ? y0 : y1, zz = i % 2 == 0 ? z0 : z1;
                points.add(new Location(w, x0 + t * (x1 - x0), yy, zz));
                double xx = i < 2 ? x0 : x1;
                points.add(new Location(w, xx, y0 + t * (y1 - y0), zz));
                points.add(new Location(w, xx, i % 2 == 0 ? y0 : y1, z0 + t * (z1 - z0)));
            }
        }
        Particle.DustTransition dust = new Particle.DustTransition(Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0), 0.8f);
        new BukkitRunnable() {
            int runs;

            @Override
            public void run() {
                if (!p.isOnline() || runs++ > 12) {
                    cancel();
                    return;
                }
                for (Location l : points) {
                    p.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, dust);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void offerAreaRewind(Player p) {
        Location[] sel = selections.get(p.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) {
            plugin.lang().send(p, "tools.timeglass-no-selection");
            return;
        }
        plugin.lang().send(p, "tools.timeglass-offer");
        Component row = Component.text("  ");
        for (String time : plugin.getConfig().getStringList("tools.timeglass.presets")) {
            row = row.append(plugin.lang().get("tools.timeglass-preset", Text.p("time", time))
                    .clickEvent(ClickEvent.runCommand("/rewind area " + time))).append(Component.space());
        }
        p.sendMessage(row);
    }

    /** The two corners as a rewind request, or null. */
    public RewindEngine.Request selection(Player p, String actor, long since) {
        Location[] sel = selections.get(p.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) {
            return null;
        }
        return new RewindEngine.Request(actor, sel[0].getWorld(),
                Math.min(sel[0].getBlockX(), sel[1].getBlockX()), Math.min(sel[0].getBlockY(), sel[1].getBlockY()),
                Math.min(sel[0].getBlockZ(), sel[1].getBlockZ()), Math.max(sel[0].getBlockX(), sel[1].getBlockX()),
                Math.max(sel[0].getBlockY(), sel[1].getBlockY()), Math.max(sel[0].getBlockZ(), sel[1].getBlockZ()),
                false, since);
    }

    /** Puts the Timeglass on cooldown so its model drains while a rewind runs. */
    public void drainTimeglass(Player p, int ticks) {
        tools().startCooldown(p, StaffTool.TIMEGLASS, ticks);
    }

    // ------------------------------------------------------------------ Flare Compass

    private void useCompass(Player p, ItemStack item, boolean cycle) {
        plugin.flares().open(50, 0).thenAccept(open -> plugin.sync(() -> {
            if (open.isEmpty()) {
                plugin.lang().send(p, "tools.compass-none");
                tools().pointCompass(item, null);
                return;
            }
            int idx = compassIndex.getOrDefault(p.getUniqueId(), 0);
            if (cycle) {
                idx = (idx + 1) % open.size();
                compassIndex.put(p.getUniqueId(), idx);
                Flare f = open.get(idx);
                tools().pointCompass(item, location(f));
                plugin.lang().send(p, "tools.compass-cycle", plugin.flares().resolvers(f), Text.p("index", idx + 1),
                        Text.p("total", open.size()));
                plugin.fx().playFor(p, "ui-page");
                return;
            }
            Flare f = open.get(Math.min(idx, open.size() - 1));
            if (f.status() == Flare.Status.OPEN && p.hasPermission("shardwatch.flares.handle")
                    && plugin.getConfig().getBoolean("tools.flare_compass.claim-on-warp", true)) {
                plugin.flares().claim(p, f.id());
            }
            if (p.hasPermission("shardwatch.flares.teleport")) {
                plugin.fx().play("compass-warp", p.getLocation().add(0, 1, 0));
                plugin.flares().teleport(p, f);
            }
        }));
    }

    private static Location location(Flare f) {
        World w = f.world() == null ? null : Bukkit.getWorld(f.world());
        Player target = Bukkit.getPlayer(f.target());
        if (target != null) {
            return target.getLocation();
        }
        return w == null ? null : new Location(w, f.x(), f.y(), f.z());
    }

    private void refreshCompasses() {
        List<Player> holders = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (tools().has(p, StaffTool.FLARE_COMPASS)) {
                holders.add(p);
            }
        }
        if (holders.isEmpty()) {
            return;
        }
        plugin.flares().open(50, 0).thenAccept(open -> plugin.sync(() -> {
            for (Player p : holders) {
                if (!p.isOnline()) {
                    continue;
                }
                int idx = compassIndex.getOrDefault(p.getUniqueId(), 0);
                Location target = open.isEmpty() ? null : location(open.get(Math.min(idx, open.size() - 1)));
                for (ItemStack i : p.getInventory().getContents()) {
                    if (tools().toolOf(i) == StaffTool.FLARE_COMPASS) {
                        tools().pointCompass(i, target != null && target.getWorld() == p.getWorld() ? target : null);
                    }
                }
            }
        }));
    }

    // ------------------------------------------------------------------ Glint Monocle

    private void cycleSuspects(Player p) {
        var suspects = plugin.glint().suspects();
        suspects.removeIf(e -> e.getKey().equals(p));
        if (suspects.isEmpty()) {
            plugin.lang().send(p, "glint.none");
            return;
        }
        int idx = (monocleIndex.getOrDefault(p.getUniqueId(), -1) + 1) % suspects.size();
        monocleIndex.put(p.getUniqueId(), idx);
        Player target = suspects.get(idx).getKey();
        plugin.fx().playFor(p, "monocle-focus");
        plugin.glint().watch(p, target);
        plugin.lang().send(p, "glint.check", plugin.glint().check(target));
    }

    // ------------------------------------------------------------------ keep tools in staff hands

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        StaffTool t = tools().toolOf(event.getItemDrop().getItemStack());
        if (t != null && t.kit() && plugin.getConfig().getBoolean("tools.prevent-drop", true)) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "tools.cannot-drop");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("tools.prevent-storing", true)) {
            return;
        }
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                || event.getView().getTopInventory().getHolder() instanceof dev.shardwatch.gui.MenuHolder) {
            return;
        }
        ItemStack moving = event.isShiftClick() ? event.getCurrentItem() : event.getCursor();
        boolean intoTop = event.isShiftClick() ? event.getClickedInventory() != event.getView().getTopInventory()
                : event.getClickedInventory() == event.getView().getTopInventory();
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && event.getClickedInventory() == event.getView().getTopInventory()) {
            moving = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            intoTop = true;
        }
        StaffTool t = tools().toolOf(moving);
        if (t != null && t.kit() && intoTop) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        StaffTool t = tools().toolOf(event.getOldCursor());
        if (t == null || !t.kit() || !plugin.getConfig().getBoolean("tools.prevent-storing", true)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && event.getRawSlots().stream().anyMatch(s -> s < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack i : event.getInventory().getMatrix()) {
            if (tools().isTool(i)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("tools.keep-on-death", true)) {
            return;
        }
        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack i = it.next();
            StaffTool t = tools().toolOf(i);
            if (t != null && t != StaffTool.LUSTRE_SHARD) {
                kept.add(i);
                it.remove();
            }
        }
        if (!kept.isEmpty()) {
            keptOnDeath.put(event.getEntity().getUniqueId(), kept);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> kept = keptOnDeath.remove(event.getPlayer().getUniqueId());
        if (kept != null) {
            Bukkit.getScheduler().runTask(plugin, () -> kept.forEach(i -> event.getPlayer().getInventory().addItem(i)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            boolean veiled = plugin.facets().isVeiled(p);
            for (ItemStack i : p.getInventory().getContents()) {
                StaffTool t = tools().toolOf(i);
                if (t == null) {
                    continue;
                }
                if (t.kit() && !p.hasPermission(t.permission())
                        && plugin.getConfig().getBoolean("tools.remove-from-unauthorised", true)) {
                    i.setAmount(0);
                } else if (t == StaffTool.VEIL_LANTERN) {
                    tools().setLanternLit(i, veiled);
                }
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        selections.remove(id);
        compassIndex.remove(id);
        monocleIndex.remove(id);
        lastGavelTarget.remove(id);
    }
}
