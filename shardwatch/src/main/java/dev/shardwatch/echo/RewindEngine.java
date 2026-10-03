package dev.shardwatch.echo;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rewinds logged block changes. Plans are read async, then applied on the main thread a few hundred blocks per tick.
 * A block is only restored if it still looks the way the echo left it, so later legitimate changes survive.
 */
public final class RewindEngine {

    /** A block this engine changed: {@code before} is what was there, {@code after} what the rewind put there. */
    public record Change(String world, int x, int y, int z, String before, String after, long echoId) {
    }

    /** What to rewind. {@code actor == null} rewinds everyone in the area. Box bounds are inclusive. */
    public record Request(String actor, World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          boolean global, long since) {

        static Request around(String actor, Location c, int radius, long since) {
            return new Request(actor, c.getWorld(), c.getBlockX() - radius, c.getBlockY() - radius, c.getBlockZ() - radius,
                    c.getBlockX() + radius, c.getBlockY() + radius, c.getBlockZ() + radius, radius < 0, since);
        }
    }

    private final Shardwatch plugin;
    private final Map<String, Deque<List<Change>>> undo = new ConcurrentHashMap<>();
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();

    public RewindEngine(Shardwatch plugin) {
        this.plugin = plugin;
    }

    /** Validates limits and starts a rewind (or a preview) around a point. */
    public void rewind(CommandSender sender, String actor, long since, int radius, Location center, boolean preview) {
        int maxRadius = plugin.getConfig().getInt("rewind.max-radius", 60);
        if (radius < 0 && !sender.hasPermission("shardwatch.rewind.global")) {
            plugin.lang().send(sender, "rewind.need-radius");
            return;
        }
        if (radius > maxRadius && !sender.hasPermission("shardwatch.rewind.global")) {
            plugin.lang().send(sender, "rewind.radius-too-big", Text.p("max", maxRadius));
            return;
        }
        if (actor == null && radius < 0) {
            plugin.lang().send(sender, "rewind.need-radius");
            return;
        }
        run(sender, Request.around(actor, center, radius, since), preview);
    }

    public void run(CommandSender sender, Request req, boolean preview) {
        Long maxTime = Durations.parse(plugin.getConfig().getString("rewind.max-time", "14d"));
        if (maxTime != null && maxTime > 0 && req.since() > maxTime && !sender.hasPermission("shardwatch.rewind.global")) {
            plugin.lang().send(sender, "rewind.time-too-long", Text.p("max", Durations.format(maxTime)));
            return;
        }
        if (running.putIfAbsent(sender.getName(), true) != null) {
            plugin.lang().send(sender, "rewind.busy");
            return;
        }
        int maxBlocks = plugin.getConfig().getInt("rewind.max-blocks", 50000);
        int cx = (req.minX() + req.maxX()) / 2;
        int cy = (req.minY() + req.maxY()) / 2;
        int cz = (req.minZ() + req.maxZ()) / 2;
        int r = Math.max(Math.max(req.maxX() - cx, req.maxY() - cy), req.maxZ() - cz);
        EchoQuery q = new EchoQuery(req.actor(), null, req.global() && req.actor() != null ? null : req.world().getName(),
                cx, cy, cz, req.global() ? -1 : r, System.currentTimeMillis() - req.since(), null, false);
        TagResolver who = TagResolver.resolver(Text.p("actor", req.actor() == null ? "everyone" : req.actor()),
                Text.p("time", Durations.format(req.since())), Text.p("radius", req.global() ? "∞" : String.valueOf(r)));
        plugin.lang().send(sender, preview ? "rewind.preview-start" : "rewind.start", who);
        plugin.echoes().flush();
        plugin.echoes().store().search(q, maxBlocks, 0).whenComplete((rows, err) -> plugin.sync(() -> {
            if (err != null) {
                running.remove(sender.getName());
                plugin.lang().send(sender, "general.db-error");
                return;
            }
            List<Echo> plan = new ArrayList<>();
            for (Echo e : rows) {
                if (req.global() || e.x() >= req.minX() && e.x() <= req.maxX() && e.y() >= req.minY()
                        && e.y() <= req.maxY() && e.z() >= req.minZ() && e.z() <= req.maxZ()) {
                    plan.add(e);
                }
            }
            if (plan.isEmpty()) {
                running.remove(sender.getName());
                plugin.lang().send(sender, "rewind.nothing", who);
                return;
            }
            if (preview) {
                running.remove(sender.getName());
                preview(sender, plan);
                plugin.lang().send(sender, "rewind.preview", TagResolver.resolver(who, Text.p("count", plan.size())));
                return;
            }
            if (sender instanceof Player p) {
                plugin.animations().rewindSweep(p.getLocation(), r);
                plugin.fx().play("rewind-start", p.getLocation().add(0, 1, 0));
            }
            apply(sender, plan, who);
        }));
    }

    private void preview(CommandSender sender, List<Echo> plan) {
        if (!(sender instanceof Player p)) {
            return;
        }
        int seconds = plugin.getConfig().getInt("rewind.preview-seconds", 10);
        List<Location> spots = new ArrayList<>();
        for (Echo e : plan) {
            World w = Bukkit.getWorld(e.world());
            if (w != null && spots.size() < 2000) {
                spots.add(new Location(w, e.x() + 0.5, e.y() + 0.5, e.z() + 0.5));
            }
        }
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!p.isOnline() || (ticks += 10) > seconds * 20) {
                    cancel();
                    return;
                }
                for (Location l : spots) {
                    p.spawnParticle(Particle.DUST, l, 1, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(0x7FE8E0), 1.1f));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void apply(CommandSender sender, List<Echo> plan, TagResolver who) {
        int perTick = Math.max(1, plugin.getConfig().getInt("rewind.blocks-per-tick", 400));
        boolean onlyMatching = plugin.getConfig().getBoolean("rewind.only-matching", true);
        List<Change> changes = new ArrayList<>();
        int[] skipped = {0};
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                int end = Math.min(plan.size(), index + perTick);
                for (; index < end; index++) {
                    Echo e = plan.get(index);
                    World w = Bukkit.getWorld(e.world());
                    if (w == null) {
                        skipped[0]++;
                        continue;
                    }
                    Block b = w.getBlockAt(e.x(), e.y(), e.z());
                    String current = b.getBlockData().getAsString();
                    if (onlyMatching && !current.equals(e.newData())) {
                        skipped[0]++;
                        continue;
                    }
                    BlockData restored;
                    try {
                        restored = Bukkit.createBlockData(e.oldData());
                    } catch (IllegalArgumentException ex) {
                        skipped[0]++;
                        continue;
                    }
                    b.setBlockData(restored, false);
                    changes.add(new Change(e.world(), e.x(), e.y(), e.z(), current, e.oldData(), e.id()));
                    if (changes.size() % 12 == 0) {
                        plugin.fx().play("rewind-block", b.getLocation().add(0.5, 0.5, 0.5));
                    }
                }
                if (index >= plan.size()) {
                    cancel();
                    finish(sender, changes, skipped[0], who);
                } else if (sender instanceof Player p) {
                    plugin.lang().sendActionBar(p, "rewind.progress", Text.p("done", index), Text.p("total", plan.size()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finish(CommandSender sender, List<Change> changes, int skipped, TagResolver who) {
        running.remove(sender.getName());
        List<Long> ids = new ArrayList<>(changes.size());
        changes.forEach(c -> ids.add(c.echoId()));
        plugin.echoes().store().markRewound(ids, true);
        Deque<List<Change>> stack = undo.computeIfAbsent(sender.getName(), k -> new ArrayDeque<>());
        stack.push(changes);
        while (stack.size() > plugin.getConfig().getInt("rewind.undo-depth", 5)) {
            stack.removeLast();
        }
        TagResolver r = TagResolver.resolver(who, Text.p("count", changes.size()), Text.p("skipped", skipped));
        plugin.lang().send(sender, "rewind.done", r);
        if (sender instanceof Player p) {
            plugin.fx().play("rewind-done", p.getLocation().add(0, 1, 0));
        }
        plugin.action(sender, StaffAction.REWIND, Text.plain(plugin.lang().parse("<actor>", who)),
                changes.size() + " blocks, " + skipped + " skipped (" + Text.plain(plugin.lang().parse("<time> r<radius>", who)) + ")");
    }

    /** Puts back what the sender's last rewind changed. */
    public void undo(CommandSender sender) {
        Deque<List<Change>> stack = undo.get(sender.getName());
        List<Change> last = stack == null ? null : stack.poll();
        if (last == null) {
            plugin.lang().send(sender, "rewind.nothing-to-undo");
            return;
        }
        int restored = 0;
        List<Long> ids = new ArrayList<>();
        for (int i = last.size() - 1; i >= 0; i--) {
            Change c = last.get(i);
            World w = Bukkit.getWorld(c.world());
            if (w == null) {
                continue;
            }
            Block b = w.getBlockAt(c.x(), c.y(), c.z());
            if (!b.getBlockData().getAsString().equals(c.after())) {
                continue;
            }
            try {
                b.setBlockData(Bukkit.createBlockData(c.before()), false);
                restored++;
                ids.add(c.echoId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.echoes().store().markRewound(ids, false);
        plugin.lang().send(sender, "rewind.undone", Text.p("count", restored));
        if (sender instanceof Player p) {
            plugin.fx().play("rewind-undo", p.getLocation().add(0, 1, 0));
        }
        plugin.action(sender, StaffAction.REWIND_UNDO, "", restored + " blocks");
    }

    public boolean isRunning(CommandSender sender) {
        return running.containsKey(sender.getName());
    }
}
