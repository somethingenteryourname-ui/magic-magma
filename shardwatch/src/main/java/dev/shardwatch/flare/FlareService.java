package dev.shardwatch.flare;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Player reports ("Flares"): filing, staff alerts and the claim/resolve workflow. */
public final class FlareService {

    public record Category(String id, String name, String icon, List<String> description) {
    }

    private static final List<Flare.Status> OPEN = List.of(Flare.Status.OPEN, Flare.Status.CLAIMED);

    private final Shardwatch plugin;
    private final FlareStore store;
    private final Map<UUID, Long> lastFiled = new ConcurrentHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public FlareService(Shardwatch plugin) {
        this.plugin = plugin;
        this.store = new FlareStore(plugin.db());
        reload();
    }

    public void reload() {
        categories.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("flares.categories");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                categories.put(id, new Category(id, sec.getString(id + ".name", id), sec.getString(id + ".icon", "flare"),
                        sec.getStringList(id + ".description")));
            }
        }
        if (categories.isEmpty()) {
            categories.put("other", new Category("other", "Other", "flare", List.of()));
        }
    }

    public FlareStore store() {
        return store;
    }

    public Map<String, Category> categories() {
        return categories;
    }

    public Category category(String id) {
        Category c = categories.get(id);
        return c != null ? c : categories.values().iterator().next();
    }

    /** Seconds left on the reporter's cooldown, or 0. */
    public long cooldownLeft(Player reporter) {
        if (reporter.hasPermission("shardwatch.flare.bypass-cooldown")) {
            return 0;
        }
        long cd = plugin.getConfig().getLong("flares.cooldown-seconds", 60) * 1000L;
        Long last = lastFiled.get(reporter.getUniqueId());
        return last == null ? 0 : Math.max(0, (last + cd - System.currentTimeMillis()) / 1000L);
    }

    public void file(Player reporter, OfflinePlayer target, String categoryId, String reason) {
        if (reporter.getUniqueId().equals(target.getUniqueId())) {
            plugin.lang().send(reporter, "flare.self");
            return;
        }
        long left = cooldownLeft(reporter);
        if (left > 0) {
            plugin.lang().send(reporter, "flare.cooldown", Text.p("seconds", left));
            return;
        }
        int min = plugin.getConfig().getInt("flares.min-reason-length", 0);
        String why = reason == null ? "" : reason.trim();
        if (why.length() < min) {
            plugin.lang().send(reporter, "flare.reason-too-short", Text.p("min", min));
            return;
        }
        int max = plugin.getConfig().getInt("flares.max-reason-length", 200);
        if (why.length() > max) {
            why = why.substring(0, max);
        }
        Category cat = category(categoryId);
        String finalWhy = why.isEmpty() ? cat.name() : why;
        lastFiled.put(reporter.getUniqueId(), System.currentTimeMillis());
        Player onlineTarget = target.getPlayer();
        Location loc = onlineTarget != null ? onlineTarget.getLocation() : reporter.getLocation();
        String targetName = Players.name(target);
        store.hasOpen(reporter.getUniqueId(), target.getUniqueId()).thenCompose(dup -> {
            if (dup && !plugin.getConfig().getBoolean("flares.allow-duplicates", false)) {
                return CompletableFuture.completedFuture((Flare) null);
            }
            return store.insert(reporter.getUniqueId(), reporter.getName(), target.getUniqueId(), targetName, cat.id(),
                    finalWhy, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }).whenComplete((flare, err) -> plugin.sync(() -> {
            if (err != null) {
                plugin.lang().send(reporter, "general.db-error");
                return;
            }
            if (flare == null) {
                lastFiled.remove(reporter.getUniqueId());
                plugin.lang().send(reporter, "flare.duplicate", Text.p("player", targetName));
                return;
            }
            TagResolver r = resolvers(flare);
            plugin.lang().send(reporter, "flare.filed", r);
            plugin.fx().playFor(reporter, "flare-send");
            if (onlineTarget != null) {
                plugin.animations().flareBeacon(onlineTarget.getLocation());
            }
            alertStaff(flare, r);
            plugin.action(reporter, StaffAction.FLARE_CREATE, targetName, "#" + flare.id() + " " + cat.id() + ": " + finalWhy);
        }));
    }

    private void alertStaff(Flare flare, TagResolver r) {
        for (Player staff : Players.withPermission("shardwatch.notify.flares")) {
            if (!plugin.profiles().alertsOn(staff)) {
                continue;
            }
            plugin.lang().send(staff, "flare.alert", r);
            plugin.fx().playFor(staff, "flare-alert");
        }
        plugin.lang().send(Bukkit.getConsoleSender(), "flare.alert", r);
    }

    public void claim(Player staff, long id) {
        store.transition(id, List.of(Flare.Status.OPEN), Flare.Status.CLAIMED, staff.getUniqueId().toString(),
                staff.getName(), null).thenCompose(ok -> store.byId(id).thenApply(f -> ok ? f : null))
                .whenComplete((flare, err) -> plugin.sync(() -> {
                    if (flare == null) {
                        plugin.lang().send(staff, "flare.cannot-claim", Text.p("id", id));
                        return;
                    }
                    TagResolver r = resolvers(flare);
                    plugin.lang().send(Players.withPermission("shardwatch.notify.flares"), "flare.claimed", r);
                    plugin.fx().playFor(staff, "flare-claim");
                    plugin.action(staff, StaffAction.FLARE_CLAIM, flare.targetName(), "#" + id);
                }));
    }

    public void close(CommandSender staff, long id, boolean resolved, String note) {
        Flare.Status to = resolved ? Flare.Status.RESOLVED : Flare.Status.DISMISSED;
        String text = note == null || note.isBlank() ? (resolved ? "Handled" : "No action needed") : note;
        store.transition(id, OPEN, to, Players.uuidOf(staff), staff.getName(), text)
                .thenCompose(ok -> store.byId(id).thenApply(f -> ok ? f : null))
                .whenComplete((flare, err) -> plugin.sync(() -> {
                    if (flare == null) {
                        plugin.lang().send(staff, "flare.cannot-close", Text.p("id", id));
                        return;
                    }
                    TagResolver r = resolvers(flare);
                    plugin.lang().send(staff, resolved ? "flare.resolved" : "flare.dismissed", r);
                    if (staff instanceof Player p) {
                        plugin.fx().playFor(p, resolved ? "flare-resolve" : "flare-dismiss");
                    }
                    Player reporter = Bukkit.getPlayer(flare.reporter());
                    if (reporter != null && plugin.getConfig().getBoolean("flares.notify-reporter", true)) {
                        plugin.lang().send(reporter, resolved ? "flare.reporter-resolved" : "flare.reporter-dismissed", r);
                    }
                    plugin.action(staff, resolved ? StaffAction.FLARE_RESOLVE : StaffAction.FLARE_DISMISS,
                            flare.targetName(), "#" + id + ": " + text);
                }));
    }

    /** Teleports staff to where the flare was filed. */
    public void teleport(Player staff, Flare flare) {
        World w = Bukkit.getWorld(flare.world() == null ? "" : flare.world());
        Player target = Bukkit.getPlayer(flare.target());
        Location dest = target != null && plugin.getConfig().getBoolean("flares.teleport-to-target", true)
                ? target.getLocation()
                : w == null ? null : new Location(w, flare.x() + 0.5, flare.y(), flare.z() + 0.5);
        if (dest == null) {
            plugin.lang().send(staff, "flare.no-location");
            return;
        }
        plugin.fx().play("warp-out", staff.getLocation().add(0, 1, 0));
        staff.teleportAsync(dest).thenRun(() -> plugin.fx().play("warp-in", staff.getLocation().add(0, 1, 0)));
    }

    public CompletableFuture<List<Flare>> open(int limit, int offset) {
        return store.list(OPEN, null, limit, offset);
    }

    public void sendOpenSummary(Player staff) {
        store.countOpen().thenAccept(n -> plugin.sync(() -> {
            if (n > 0 && staff.isOnline()) {
                plugin.lang().send(staff, "flare.join-summary", Text.p("count", n));
            }
        }));
    }

    public TagResolver resolvers(Flare f) {
        Category cat = category(f.category());
        return TagResolver.resolver(
                Text.p("id", f.id()),
                Text.p("reporter", f.reporterName()),
                Text.p("player", f.targetName()),
                Text.p("reason", f.reason()),
                Text.pp("category", cat.name()),
                Text.p("status", f.status().name().toLowerCase()),
                Text.p("handler", f.handlerName() == null ? "-" : f.handlerName()),
                Text.p("resolution", f.resolution() == null ? "-" : f.resolution()),
                Text.p("ago", Durations.ago(f.created())),
                Text.p("world", f.world() == null ? "?" : f.world()),
                Text.p("x", f.x()), Text.p("y", f.y()), Text.p("z", f.z()));
    }

    public List<String> categoryIds() {
        return new ArrayList<>(categories.keySet());
    }
}
