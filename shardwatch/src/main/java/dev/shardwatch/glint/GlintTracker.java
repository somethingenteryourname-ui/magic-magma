package dev.shardwatch.glint;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * X-ray detection. For each player it keeps a sliding window of mined blocks and of ore <em>veins</em> (connected ores
 * mined close together count once). Ores that were fully enclosed in stone when found weigh more, because a player who
 * follows caves finds exposed ores while an x-ray user tunnels straight to hidden ones.
 * <pre>score = Σ(vein weight × hidden bonus) × 100 / max(blocks mined in window, min-sample)</pre>
 */
public final class GlintTracker implements Listener {

    private record Hit(long time, int x, int y, int z, String family) {
    }

    private record Vein(long time, double weight, String ore) {
    }

    /** Per-player sliding window. Only touched on the main thread. */
    private static final class State {
        final Deque<Long> mined = new ArrayDeque<>();
        final Deque<Vein> veins = new ArrayDeque<>();
        final Deque<Hit> recentOres = new ArrayDeque<>();
        long lastAlert;
        double lastScore;
    }

    private final Shardwatch plugin;
    private final GlintStore store;
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<Long> placedOres = new LinkedHashSet<>();
    private final Map<Material, Double> weights = new EnumMap<>(Material.class);
    private final Set<Material> counted = EnumSet.noneOf(Material.class);
    private final Map<UUID, Location> watchReturn = new HashMap<>();
    private final Map<UUID, GameMode> watchMode = new HashMap<>();

    public GlintTracker(Shardwatch plugin) {
        this.plugin = plugin;
        this.store = new GlintStore(plugin.db());
        reload();
    }

    public void reload() {
        weights.clear();
        counted.clear();
        ConfigurationSection ores = plugin.getConfig().getConfigurationSection("glint.ores");
        if (ores != null) {
            for (String key : ores.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) {
                    weights.put(m, ores.getDouble(key));
                }
            }
        }
        for (String key : plugin.getConfig().getStringList("glint.counted-blocks")) {
            Material m = Material.matchMaterial(key);
            if (m != null) {
                counted.add(m);
            }
        }
        counted.addAll(weights.keySet());
    }

    public GlintStore store() {
        return store;
    }

    private static long pack(Block b) {
        return ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | (b.getY() & 0xFFF)
                ^ (long) b.getWorld().getName().hashCode() << 52;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (weights.containsKey(event.getBlockPlaced().getType())) {
            placedOres.add(pack(event.getBlockPlaced()));
            if (placedOres.size() > 20000) {
                Iterator<Long> it = placedOres.iterator();
                it.next();
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        Material type = b.getType();
        if (!plugin.getConfig().getBoolean("glint.enabled", true) || !counted.contains(type)
                || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                || p.hasPermission("shardwatch.glint.exempt")
                || plugin.getConfig().getStringList("glint.disabled-worlds").contains(b.getWorld().getName())) {
            return;
        }
        long now = System.currentTimeMillis();
        long window = plugin.getConfig().getLong("glint.window-minutes", 15) * 60_000L;
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        s.mined.addLast(now);
        while (!s.mined.isEmpty() && s.mined.peekFirst() < now - window) {
            s.mined.pollFirst();
        }
        while (!s.veins.isEmpty() && s.veins.peekFirst().time() < now - window) {
            s.veins.pollFirst();
        }
        Double weight = weights.get(type);
        if (weight == null || placedOres.remove(pack(b))) {
            return;
        }
        String family = type.name().replace("DEEPSLATE_", "");
        long veinGap = plugin.getConfig().getLong("glint.vein-seconds", 60) * 1000L;
        while (!s.recentOres.isEmpty() && s.recentOres.peekFirst().time() < now - veinGap) {
            s.recentOres.pollFirst();
        }
        boolean sameVein = false;
        for (Hit h : s.recentOres) {
            if (h.family().equals(family) && Math.abs(h.x() - b.getX()) <= 2 && Math.abs(h.y() - b.getY()) <= 2
                    && Math.abs(h.z() - b.getZ()) <= 2) {
                sameVein = true;
                break;
            }
        }
        s.recentOres.addLast(new Hit(now, b.getX(), b.getY(), b.getZ(), family));
        if (sameVein) {
            return;
        }
        double w = weight * (exposedFaces(b) <= 1 ? plugin.getConfig().getDouble("glint.hidden-bonus", 1.5) : 1.0);
        s.veins.addLast(new Vein(now, w, family));
        double score = score(s);
        s.lastScore = score;
        int minVeins = plugin.getConfig().getInt("glint.min-veins", 3);
        long cooldown = plugin.getConfig().getLong("glint.alert-cooldown-seconds", 120) * 1000L;
        if (score >= plugin.getConfig().getDouble("glint.threshold", 40) && s.veins.size() >= minVeins
                && now - s.lastAlert >= cooldown) {
            s.lastAlert = now;
            alert(p, b, family, score, s);
        }
    }

    private int exposedFaces(Block b) {
        int open = 0;
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material m = b.getRelative(f).getType();
            if (m.isAir() || m == Material.WATER || m == Material.LAVA) {
                open++;
            }
        }
        return open;
    }

    private double score(State s) {
        double sum = 0;
        for (Vein v : s.veins) {
            sum += v.weight();
        }
        int sample = Math.max(s.mined.size(), plugin.getConfig().getInt("glint.min-sample", 60));
        return sum * 100.0 / sample;
    }

    private void alert(Player p, Block b, String ore, double score, State s) {
        String pretty = ore.toLowerCase().replace('_', ' ');
        store.insert(p.getUniqueId(), p.getName(), pretty, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), score)
                .whenComplete((id, err) -> plugin.sync(() -> {
                    TagResolver r = TagResolver.resolver(Text.p("player", p.getName()), Text.p("ore", pretty),
                            Text.p("score", String.format("%.0f", score)), Text.p("veins", s.veins.size()),
                            Text.p("mined", s.mined.size()), Text.p("x", b.getX()), Text.p("y", b.getY()),
                            Text.p("z", b.getZ()), Text.p("world", b.getWorld().getName()),
                            Text.p("id", id == null ? 0 : id));
                    for (Player staff : Players.withPermission("shardwatch.notify.glint")) {
                        if (plugin.profiles().alertsOn(staff)) {
                            plugin.lang().send(staff, "glint.alert", r);
                            plugin.fx().playFor(staff, "glint-alert");
                        }
                    }
                    plugin.lang().send(org.bukkit.Bukkit.getConsoleSender(), "glint.alert", r);
                    plugin.action(null, StaffAction.GLINT_ALERT, p.getName(),
                            pretty + " score " + String.format("%.0f", score));
                }));
    }

    /** Current score and window info for /glint check. */
    public TagResolver check(Player target) {
        State s = states.get(target.getUniqueId());
        double score = s == null ? 0 : score(s);
        return TagResolver.resolver(Text.p("player", target.getName()), Text.p("score", String.format("%.0f", score)),
                Text.p("veins", s == null ? 0 : s.veins.size()), Text.p("mined", s == null ? 0 : s.mined.size()),
                Text.p("threshold", String.format("%.0f", plugin.getConfig().getDouble("glint.threshold", 40))),
                Text.p("window", Durations.format(plugin.getConfig().getLong("glint.window-minutes", 15) * 60_000L)));
    }

    /** Online players with a non-zero score, highest first. */
    public List<Map.Entry<Player, Double>> suspects() {
        List<Map.Entry<Player, Double>> out = new ArrayList<>();
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(e.getKey());
            if (p != null) {
                double sc = score(e.getValue());
                if (sc > 0) {
                    out.add(Map.entry(p, sc));
                }
            }
        }
        out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return out;
    }

    // ------------------------------------------------------------------ watch (spectate)

    public void watch(Player staff, Player target) {
        if (!watchReturn.containsKey(staff.getUniqueId())) {
            watchReturn.put(staff.getUniqueId(), staff.getLocation());
            watchMode.put(staff.getUniqueId(), staff.getGameMode());
        }
        staff.setGameMode(GameMode.SPECTATOR);
        staff.teleportAsync(target.getLocation()).thenRun(() -> plugin.sync(() -> {
            if (staff.isOnline() && target.isOnline()) {
                staff.setSpectatorTarget(target);
            }
        }));
        plugin.lang().send(staff, "glint.watching", Text.p("player", target.getName()));
        plugin.action(staff, StaffAction.GLINT_HANDLE, target.getName(), "watch");
    }

    public boolean unwatch(Player staff) {
        Location back = watchReturn.remove(staff.getUniqueId());
        GameMode mode = watchMode.remove(staff.getUniqueId());
        if (back == null) {
            return false;
        }
        staff.setSpectatorTarget(null);
        staff.teleport(back);
        staff.setGameMode(mode == null ? GameMode.SURVIVAL : mode);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
        unwatch(event.getPlayer());
    }
}
