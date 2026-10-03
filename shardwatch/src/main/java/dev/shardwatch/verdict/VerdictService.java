package dev.shardwatch.verdict;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.config.Lang;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Issues, enforces and revokes verdicts. */
public final class VerdictService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Shardwatch plugin;
    private final VerdictStore store;
    private final Map<UUID, Verdict> hushed = new ConcurrentHashMap<>();
    private final Set<UUID> petrified = ConcurrentHashMap.newKeySet();

    public VerdictService(Shardwatch plugin) {
        this.plugin = plugin;
        this.store = new VerdictStore(plugin.db());
    }

    public VerdictStore store() {
        return store;
    }

    // ------------------------------------------------------------------ issue

    /**
     * Checks whether {@code actor} may judge {@code target}. Sends the reason to the actor when not.
     */
    public boolean canJudge(CommandSender actor, OfflinePlayer target) {
        if (!(actor instanceof Player a)) {
            return true;
        }
        if (a.getUniqueId().equals(target.getUniqueId())) {
            plugin.lang().send(actor, "verdict.self");
            return false;
        }
        Player online = target.getPlayer();
        if (online != null && online.hasPermission("shardwatch.exempt") && !a.hasPermission("shardwatch.exempt.bypass")) {
            plugin.lang().send(actor, "verdict.exempt", Text.p("player", online.getName()));
            return false;
        }
        int mine = plugin.facets().weight(a);
        int theirs = plugin.facets().weight(target.getUniqueId());
        if (theirs > 0 && theirs >= mine && !a.hasPermission("shardwatch.exempt.bypass")) {
            plugin.lang().send(actor, "verdict.outranked", Text.p("player", Players.name(target)));
            return false;
        }
        return true;
    }

    public CompletableFuture<Verdict> issue(CommandSender actor, OfflinePlayer target, VerdictType type, long duration,
                                            String reason, boolean silent) {
        long now = System.currentTimeMillis();
        long expires = type.timed() && duration > 0 ? now + duration : 0;
        String targetName = Players.name(target);
        String finalReason = reason == null || reason.isBlank()
                ? plugin.getConfig().getString("verdicts.default-reason", "No reason given") : reason;
        return store.insert(type, target.getUniqueId(), targetName, Players.uuidOf(actor), actor.getName(), finalReason,
                now, expires, true).whenComplete((v, err) -> plugin.sync(() -> {
            if (err != null) {
                plugin.lang().send(actor, "general.db-error");
                return;
            }
            apply(actor, target, v, silent);
        }));
    }

    private void apply(CommandSender actor, OfflinePlayer target, Verdict v, boolean silent) {
        Lang lang = plugin.lang();
        TagResolver r = resolvers(v);
        Player online = target.getPlayer();
        switch (v.type()) {
            case CHIP -> {
                if (online != null) {
                    lang.send(online, "verdict.chip.target", r);
                    online.showTitle(Title.title(lang.get("verdict.chip.title", r), lang.get("verdict.chip.subtitle", r),
                            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(600))));
                }
            }
            case HUSH -> {
                hushed.put(v.target(), v);
                if (online != null) {
                    lang.send(online, "verdict.hush.target", r);
                }
            }
            case EJECT -> {
                if (online != null) {
                    online.kick(screen("screens.eject", r));
                }
            }
            case ENCASE -> {
                if (online != null) {
                    Component screen = screen("screens.encase", r);
                    int delay = plugin.getConfig().getInt("verdicts.encase.kick-delay-ticks", 30);
                    plugin.animations().encase(online, delay);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (online.isOnline()) {
                            online.kick(screen);
                        }
                    }, delay);
                }
            }
            case PETRIFY -> {
                petrified.add(v.target());
                if (online != null) {
                    lang.send(online, "verdict.petrify.target", r);
                    online.showTitle(Title.title(lang.get("verdict.petrify.title", r), lang.get("verdict.petrify.subtitle", r),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))));
                    plugin.animations().petrify(online, true);
                }
            }
        }
        if (online != null) {
            plugin.fx().play("verdict-" + v.type().id(), online.getLocation().add(0, 1, 0));
        } else if (actor instanceof Player p) {
            plugin.fx().playFor(p, "verdict-" + v.type().id());
        }
        announce(actor, v, silent, r);
        plugin.action(actor, actionOf(v.type()), v.targetName(),
                "#" + v.id() + " " + v.type().id() + (v.type().timed() ? " " + durationText(v) : "") + ": " + v.reason());
    }

    private void announce(CommandSender actor, Verdict v, boolean silent, TagResolver r) {
        String mode = plugin.getConfig().getString("verdicts.broadcast." + v.type().id(), "staff");
        Lang lang = plugin.lang();
        String key = "verdict." + v.type().id() + ".announce";
        if (!silent && mode.equalsIgnoreCase("all")) {
            lang.send(Bukkit.getOnlinePlayers(), key, r);
            lang.send(Bukkit.getConsoleSender(), key, r);
        } else if (!mode.equalsIgnoreCase("none") || silent) {
            Component msg = lang.get(key, r).append(silent ? lang.get("verdict.silent-tag") : Component.empty());
            for (Player p : Players.withPermission("shardwatch.notify.verdicts")) {
                p.sendMessage(msg);
            }
            Bukkit.getConsoleSender().sendMessage(msg);
        }
        if (actor instanceof Player p && !p.hasPermission("shardwatch.notify.verdicts")) {
            lang.send(p, key, r);
        }
    }

    private static StaffAction actionOf(VerdictType t) {
        return switch (t) {
            case CHIP -> StaffAction.CHIP;
            case HUSH -> StaffAction.HUSH;
            case EJECT -> StaffAction.EJECT;
            case ENCASE -> StaffAction.ENCASE;
            case PETRIFY -> StaffAction.PETRIFY;
        };
    }

    // ------------------------------------------------------------------ revoke

    public void revoke(CommandSender actor, OfflinePlayer target, VerdictType type, String reason) {
        String why = reason == null || reason.isBlank() ? "Revoked" : reason;
        store.revoke(target.getUniqueId(), type, actor.getName(), why).whenComplete((n, err) -> plugin.sync(() -> {
            if (err != null) {
                plugin.lang().send(actor, "general.db-error");
                return;
            }
            TagResolver r = TagResolver.resolver(Text.p("player", Players.name(target)), Text.p("type", type.id()),
                    Text.p("reason", why), Text.p("actor", actor.getName()));
            if (n == 0) {
                plugin.lang().send(actor, "verdict.revoke.none", r);
                return;
            }
            afterRevoke(target.getUniqueId(), type);
            plugin.lang().send(Players.withPermission("shardwatch.notify.verdicts"), "verdict.revoke.announce", r);
            if (!actor.hasPermission("shardwatch.notify.verdicts")) {
                plugin.lang().send(actor, "verdict.revoke.announce", r);
            }
            Player online = target.getPlayer();
            if (online != null) {
                plugin.lang().send(online, "verdict.revoke.target", r);
            }
            plugin.action(actor, type == VerdictType.PETRIFY ? StaffAction.UNPETRIFY : StaffAction.REVOKE,
                    Players.name(target), type.id() + ": " + why);
        }));
    }

    /** Revokes one Ledger entry by id. */
    public CompletableFuture<Boolean> revokeById(CommandSender actor, Verdict v, String reason) {
        return store.revokeById(v.id(), actor.getName(), reason).whenComplete((ok, err) -> plugin.sync(() -> {
            if (Boolean.TRUE.equals(ok)) {
                afterRevoke(v.target(), v.type());
                plugin.action(actor, StaffAction.REVOKE, v.targetName(), "#" + v.id() + " " + v.type().id() + ": " + reason);
            }
        }));
    }

    private void afterRevoke(UUID target, VerdictType type) {
        if (type == VerdictType.HUSH) {
            hushed.remove(target);
        } else if (type == VerdictType.PETRIFY) {
            petrified.remove(target);
            Player p = Bukkit.getPlayer(target);
            if (p != null) {
                plugin.animations().petrify(p, false);
                plugin.fx().play("verdict-unpetrify", p.getLocation().add(0, 1, 0));
            }
        }
    }

    /** Toggles Petrify on an online player. */
    public void togglePetrify(CommandSender actor, Player target) {
        if (isPetrified(target.getUniqueId())) {
            revoke(actor, target, VerdictType.PETRIFY, "Released");
        } else if (canJudge(actor, target)) {
            issue(actor, target, VerdictType.PETRIFY, 0, plugin.getConfig().getString("verdicts.petrify.reason",
                    "Held for questioning"), true);
        }
    }

    // ------------------------------------------------------------------ enforcement state

    /** Called from the async pre-login thread. Returns the active Encase, if any, and warms the caches. */
    public Verdict loadOnLogin(UUID uuid) {
        List<Verdict> encased = store.active(uuid, VerdictType.ENCASE).join();
        if (!encased.isEmpty()) {
            return encased.get(0);
        }
        List<Verdict> hush = store.active(uuid, VerdictType.HUSH).join();
        if (!hush.isEmpty()) {
            hushed.put(uuid, hush.get(0));
        }
        if (!store.active(uuid, VerdictType.PETRIFY).join().isEmpty()) {
            petrified.add(uuid);
        }
        return null;
    }

    public void forget(UUID uuid) {
        hushed.remove(uuid);
        if (!plugin.getConfig().getBoolean("verdicts.petrify.persist", true)) {
            petrified.remove(uuid);
        }
    }

    /** The active Hush, or null. Expired Hushes are dropped. */
    public Verdict hush(UUID uuid) {
        Verdict v = hushed.get(uuid);
        if (v != null && !v.inForce(System.currentTimeMillis())) {
            hushed.remove(uuid);
            return null;
        }
        return v;
    }

    public boolean isPetrified(UUID uuid) {
        return petrified.contains(uuid);
    }

    public Set<UUID> petrifiedPlayers() {
        return petrified;
    }

    // ------------------------------------------------------------------ text

    public Component screen(String key, TagResolver r) {
        return plugin.lang().parse(String.join("\n", plugin.lang().rawList(key)), r);
    }

    public TagResolver resolvers(Verdict v) {
        long now = System.currentTimeMillis();
        return TagResolver.resolver(
                Text.p("id", v.id()),
                Text.p("player", v.targetName()),
                Text.p("actor", v.actorName()),
                Text.p("reason", v.reason()),
                Text.p("type", v.type().id()),
                Text.pp("type_name", plugin.lang().raw("verdict.names." + v.type().id())),
                Text.pp("type_color", "<" + v.type().color() + ">"),
                Text.p("duration", durationText(v)),
                Text.p("remaining", v.permanent() ? plugin.lang().raw("general.permanent")
                        : Durations.format(v.remaining(now))),
                Text.p("date", DATE.format(Instant.ofEpochMilli(v.created()))),
                Text.p("expires", v.permanent() ? plugin.lang().raw("general.never") : DATE.format(Instant.ofEpochMilli(v.expires()))),
                Text.pp("appeal", plugin.getConfig().getString("verdicts.appeal-url", "")),
                Text.p("status", v.status(now)));
    }

    public String durationText(Verdict v) {
        return v.permanent() ? plugin.lang().raw("general.permanent") : Durations.format(v.expires() - v.created());
    }

    public static String formatDate(long time) {
        return DATE.format(Instant.ofEpochMilli(time));
    }
}
