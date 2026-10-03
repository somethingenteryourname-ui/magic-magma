package dev.shardwatch.echo;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records block changes into a queue that is flushed to SQLite once per second, and answers block-history lookups
 * (inspect mode and the Echo Lens).
 */
public final class EchoRecorder implements Listener {

    private final Shardwatch plugin;
    private final EchoStore store;
    private final ConcurrentLinkedQueue<Echo> queue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> inspecting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> lastInspected = new ConcurrentHashMap<>();
    private Set<String> disabledWorlds = new HashSet<>();
    private Set<Material> ignored = new HashSet<>();
    private boolean enabled;
    private BukkitTask flushTask;

    public EchoRecorder(Shardwatch plugin) {
        this.plugin = plugin;
        this.store = new EchoStore(plugin.db());
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("echoes.enabled", true);
        disabledWorlds = new HashSet<>(plugin.getConfig().getStringList("echoes.disabled-worlds"));
        ignored = new HashSet<>();
        for (String m : plugin.getConfig().getStringList("echoes.ignored-blocks")) {
            Material mat = Material.matchMaterial(m);
            if (mat != null) {
                ignored.add(mat);
            }
        }
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 20L, 20L);
    }

    public EchoStore store() {
        return store;
    }

    public void flush() {
        if (queue.isEmpty()) {
            return;
        }
        List<Echo> batch = new ArrayList<>();
        Echo e;
        while ((e = queue.poll()) != null && batch.size() < 5000) {
            batch.add(e);
        }
        store.insertBatch(batch);
    }

    // ------------------------------------------------------------------ recording

    private boolean skip(Block b) {
        return !enabled || disabledWorlds.contains(b.getWorld().getName()) || ignored.contains(b.getType());
    }

    public void record(String actorUuid, String actorName, Echo.Action action, Block b, String oldData, String newData) {
        if (oldData.equals(newData)) {
            return;
        }
        queue.add(new Echo(0, System.currentTimeMillis(), actorUuid, actorName, action, b.getWorld().getName(),
                b.getX(), b.getY(), b.getZ(), oldData, newData, false));
    }

    private void record(Player p, Echo.Action action, Block b, String oldData, String newData) {
        record(p.getUniqueId().toString(), p.getName(), action, b, oldData, newData);
    }

    private static final String AIR = Material.AIR.createBlockData().getAsString();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (skip(b)) {
            return;
        }
        record(event.getPlayer(), Echo.Action.PLACE, b, event.getBlockReplacedState().getBlockData().getAsString(),
                b.getBlockData().getAsString());
        // Second half of beds/doors is placed by the game without its own event.
        Block other = otherHalf(b, b.getBlockData());
        if (other != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!other.getType().isAir()) {
                    record(event.getPlayer(), Echo.Action.PLACE, other, AIR, other.getBlockData().getAsString());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (skip(b)) {
            return;
        }
        BlockData data = b.getBlockData();
        String after = data instanceof Waterlogged w && w.isWaterlogged() ? Material.WATER.createBlockData().getAsString() : AIR;
        record(event.getPlayer(), Echo.Action.BREAK, b, data.getAsString(), after);
        Block other = otherHalf(b, data);
        if (other != null && other.getType() == b.getType()) {
            record(event.getPlayer(), Echo.Action.BREAK, other, other.getBlockData().getAsString(), AIR);
        }
    }

    private static Block otherHalf(Block b, BlockData data) {
        if (data instanceof Bed bed) {
            BlockFace f = bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace();
            return b.getRelative(f);
        }
        if (data instanceof Bisected bis && !(data instanceof org.bukkit.block.data.type.Stairs)
                && !(data instanceof org.bukkit.block.data.type.TrapDoor)) {
            return b.getRelative(bis.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        String[] actor = blame(event.getEntity());
        for (Block b : event.blockList()) {
            if (!skip(b)) {
                record(actor[0], actor[1], Echo.Action.EXPLODE, b, b.getBlockData().getAsString(), AIR);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            if (!skip(b)) {
                record(null, "#block-explosion", Echo.Action.EXPLODE, b, b.getBlockData().getAsString(), AIR);
            }
        }
    }

    /** Works out who to credit for an explosion: the player who lit the TNT, the creeper's target, or a #tag. */
    private static String[] blame(Entity e) {
        if (e instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return new String[]{p.getUniqueId().toString(), p.getName()};
        }
        if (e instanceof Creeper creeper && creeper.getTarget() instanceof Player p) {
            return new String[]{p.getUniqueId().toString(), p.getName()};
        }
        if (e instanceof TNTPrimed) {
            return new String[]{null, "#tnt"};
        }
        return new String[]{null, "#" + e.getType().name().toLowerCase(Locale.ROOT)};
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block b = event.getBlock();
        if (!skip(b)) {
            record(null, "#fire", Echo.Action.BURN, b, b.getBlockData().getAsString(), AIR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block b = event.getBlock();
        if (skip(b)) {
            return;
        }
        BlockData before = b.getBlockData();
        String after;
        if (event.getBucket() == Material.LAVA_BUCKET) {
            after = Material.LAVA.createBlockData().getAsString();
        } else if (before instanceof Waterlogged w) {
            Waterlogged copy = (Waterlogged) w.clone();
            copy.setWaterlogged(true);
            after = copy.getAsString();
        } else if (event.getBucket() == Material.POWDER_SNOW_BUCKET) {
            after = Material.POWDER_SNOW.createBlockData().getAsString();
        } else {
            after = Material.WATER.createBlockData().getAsString();
        }
        record(event.getPlayer(), Echo.Action.BUCKET_EMPTY, b, before.getAsString(), after);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block b = event.getBlock();
        if (skip(b)) {
            return;
        }
        BlockData before = b.getBlockData();
        String after;
        if (before instanceof Waterlogged w && w.isWaterlogged()) {
            Waterlogged copy = (Waterlogged) w.clone();
            copy.setWaterlogged(false);
            after = copy.getAsString();
        } else {
            after = AIR;
        }
        record(event.getPlayer(), Echo.Action.BUCKET_FILL, b, before.getAsString(), after);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        Entity e = event.getEntity();
        if (e instanceof Player || !(e instanceof LivingEntity) || skip(event.getBlock())) {
            return;
        }
        if (!plugin.getConfig().getStringList("echoes.log-entities").contains(e.getType().name().toLowerCase(Locale.ROOT))) {
            return;
        }
        record(null, "#" + e.getType().name().toLowerCase(Locale.ROOT), Echo.Action.ENTITY, event.getBlock(),
                event.getBlock().getBlockData().getAsString(), event.getBlockData().getAsString());
    }

    // ------------------------------------------------------------------ inspect

    public boolean toggleInspect(Player p) {
        if (!inspecting.remove(p.getUniqueId())) {
            inspecting.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public boolean isInspecting(Player p) {
        return inspecting.contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInspectClick(PlayerInteractEvent event) {
        if (!isInspecting(event.getPlayer()) || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        Block b = event.getAction() == Action.RIGHT_CLICK_BLOCK
                ? event.getClickedBlock().getRelative(event.getBlockFace()) : event.getClickedBlock();
        inspect(event.getPlayer(), b.getLocation(), 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspecting.remove(event.getPlayer().getUniqueId());
        lastInspected.remove(event.getPlayer().getUniqueId());
    }

    /** Shows one page of a block's history in chat. */
    public void inspect(Player p, Location loc, int page) {
        int perPage = plugin.getConfig().getInt("echoes.inspect-page-size", 7);
        lastInspected.put(p.getUniqueId(), loc.clone());
        int pg = Math.max(1, page);
        store.at(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), perPage + 1, (pg - 1) * perPage)
                .whenComplete((rows, err) -> plugin.sync(() -> {
                    if (err != null) {
                        plugin.lang().send(p, "general.db-error");
                        return;
                    }
                    TagResolver pos = TagResolver.resolver(Text.p("x", loc.getBlockX()), Text.p("y", loc.getBlockY()),
                            Text.p("z", loc.getBlockZ()), Text.p("world", loc.getWorld().getName()), Text.p("page", pg));
                    plugin.lang().send(p, "echo.header", pos);
                    if (rows.isEmpty()) {
                        plugin.lang().send(p, "echo.empty", pos);
                    }
                    for (int i = 0; i < Math.min(perPage, rows.size()); i++) {
                        p.sendMessage(row(rows.get(i)));
                    }
                    Component nav = Component.empty();
                    if (pg > 1) {
                        nav = nav.append(plugin.lang().get("echo.prev").clickEvent(ClickEvent.runCommand("/echo page " + (pg - 1))));
                    }
                    if (rows.size() > perPage) {
                        nav = nav.append(plugin.lang().get("echo.next").clickEvent(ClickEvent.runCommand("/echo page " + (pg + 1))));
                    }
                    if (!nav.equals(Component.empty())) {
                        p.sendMessage(nav);
                    }
                    plugin.fx().playFor(p, "echo-inspect", loc.clone().add(0.5, 0.5, 0.5));
                    plugin.action(p, StaffAction.ECHO_INSPECT, "", loc.getWorld().getName() + " " + loc.getBlockX() + " "
                            + loc.getBlockY() + " " + loc.getBlockZ());
                }));
    }

    public void inspectPage(Player p, int page) {
        Location loc = lastInspected.get(p.getUniqueId());
        if (loc == null) {
            plugin.lang().send(p, "echo.nothing-inspected");
            return;
        }
        inspect(p, loc, page);
    }

    public Component row(Echo e) {
        TagResolver r = resolvers(e);
        return plugin.lang().get("echo.row." + e.action().name().toLowerCase(Locale.ROOT), r)
                .hoverEvent(HoverEvent.showText(plugin.lang().get("echo.row-hover", r)))
                .clickEvent(ClickEvent.suggestCommand("/rewind " + e.actorName() + " "
                        + Durations.format(System.currentTimeMillis() - e.time() + 60_000, 1).replace(" ", "") + " 10"));
    }

    public TagResolver resolvers(Echo e) {
        return TagResolver.resolver(
                Text.p("id", e.id()),
                Text.p("ago", Durations.ago(e.time())),
                Text.p("ago_s", Durations.format(System.currentTimeMillis() - e.time(), 1)),
                Text.p("actor", e.actorName()),
                Text.p("actor_s", Text.shorten(e.actorName(), 12)),
                Text.p("old_s", Text.shorten(Echo.shortId(e.oldData()), 14)),
                Text.p("new_s", Text.shorten(Echo.shortId(e.newData()), 14)),
                Text.p("old", Echo.shortId(e.oldData())),
                Text.p("new", Echo.shortId(e.newData())),
                Text.p("old_full", e.oldData()),
                Text.p("new_full", e.newData()),
                Text.p("action", e.action().name().toLowerCase(Locale.ROOT)),
                Text.p("world", e.world()),
                Text.p("x", e.x()), Text.p("y", e.y()), Text.p("z", e.z()),
                Text.pp("rewound", e.rewound() ? plugin.lang().raw("echo.rewound-tag") : ""));
    }
}
