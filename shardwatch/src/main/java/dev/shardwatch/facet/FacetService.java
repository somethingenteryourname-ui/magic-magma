package dev.shardwatch.facet;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.profile.Profile;
import dev.shardwatch.util.Players;
import dev.shardwatch.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff ranks (Facets), their permissions, chat style, the Hum staff channel and Veil vanish. */
public final class FacetService implements Listener {

    public record Facet(String id, String name, int weight, String color, String prefix, String sigil,
                        List<String> permissions, int minLevel) {
    }

    private final Shardwatch plugin;
    private final Map<String, Facet> facets = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Set<UUID> humMode = ConcurrentHashMap.newKeySet();

    public FacetService(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        facets.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("facets.ranks");
        List<Facet> list = new ArrayList<>();
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection f = sec.getConfigurationSection(id);
                if (f == null) {
                    continue;
                }
                list.add(new Facet(id, f.getString("name", id), f.getInt("weight", 10), f.getString("color", "#F59AC8"),
                        f.getString("prefix", ""), f.getString("sigil", ""), f.getStringList("permissions"),
                        f.getInt("min-level", 0)));
            }
        }
        list.sort(Comparator.comparingInt(Facet::weight));
        list.forEach(f -> facets.put(f.id(), f));
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);
        }
    }

    public Map<String, Facet> all() {
        return facets;
    }

    public Facet get(String id) {
        return id == null ? null : facets.get(id.toLowerCase(Locale.ROOT));
    }

    /** The player's Facet: their stored one, or the highest one they hold through {@code shardwatch.facet.<id>}. */
    public Facet of(Player p) {
        Facet stored = get(plugin.profiles().get(p).facet());
        Facet best = stored;
        for (Facet f : facets.values()) {
            if (p.isPermissionSet("shardwatch.facet." + f.id()) && p.hasPermission("shardwatch.facet." + f.id())
                    && (best == null || f.weight() > best.weight())) {
                best = f;
            }
        }
        return best;
    }

    public int weight(Player p) {
        Facet f = of(p);
        return f == null ? (p.hasPermission("shardwatch.*") || p.isOp() ? 1000 : 0) : f.weight();
    }

    public int weight(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return weight(online);
        }
        Profile p = plugin.profiles().cached(uuid);
        Facet f = p == null ? null : get(p.facet());
        return f == null ? 0 : f.weight();
    }

    public boolean isStaff(Player p) {
        return of(p) != null || p.hasPermission("shardwatch.staff");
    }

    // ------------------------------------------------------------------ permissions

    public void apply(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Facet stored = get(plugin.profiles().get(p).facet());
        if (stored == null || !plugin.getConfig().getBoolean("facets.grant-permissions", true)) {
            p.recalculatePermissions();
            return;
        }
        PermissionAttachment att = p.addAttachment(plugin);
        boolean inherit = plugin.getConfig().getBoolean("facets.inherit", true);
        for (Facet f : facets.values()) {
            if (f == stored || inherit && f.weight() < stored.weight()) {
                for (String perm : f.permissions()) {
                    if (perm.startsWith("-")) {
                        att.setPermission(perm.substring(1), false);
                    } else {
                        att.setPermission(perm, true);
                    }
                }
            }
        }
        att.setPermission("shardwatch.facet." + stored.id(), true);
        att.setPermission("shardwatch.staff", true);
        attachments.put(p.getUniqueId(), att);
        p.recalculatePermissions();
        p.updateCommands();
    }

    /** Sets (or clears, with null) a player's Facet. */
    public void set(CommandSender actor, OfflinePlayer target, Facet facet) {
        if (actor instanceof Player a && !a.hasPermission("shardwatch.facet.manage.any")) {
            int mine = weight(a);
            if (facet != null && facet.weight() >= mine || weight(target.getUniqueId()) >= mine) {
                plugin.lang().send(actor, "facet.too-high");
                return;
            }
        }
        plugin.profiles().loadOrCreate(target.getUniqueId(), Players.name(target)).thenAccept(profile -> plugin.sync(() -> {
            Facet before = get(profile.facet());
            profile.facet(facet == null ? null : facet.id());
            plugin.profiles().save(profile);
            Player online = target.getPlayer();
            TagResolver r = TagResolver.resolver(Text.p("player", Players.name(target)),
                    Text.pp("facet", facet == null ? plugin.lang().raw("facet.none") : facet.name()),
                    Text.pp("before", before == null ? plugin.lang().raw("facet.none") : before.name()));
            if (online != null) {
                apply(online);
                plugin.lang().send(online, "facet.changed-target", r);
                if (facet != null && (before == null || facet.weight() > before.weight())) {
                    if (plugin.getConfig().getBoolean("facets.give-sigil", true)) {
                        online.getInventory().addItem(plugin.tools().create(dev.shardwatch.tool.StaffTool.SIGIL, 1, facet.id()))
                                .values().forEach(i -> online.getWorld().dropItem(online.getLocation(), i));
                    }
                    plugin.animations().halo(online, facet.color());
                    plugin.fx().play("facet-promote", online.getLocation().add(0, 1, 0));
                }
            }
            plugin.lang().send(actor, "facet.changed", r);
            plugin.action(actor, StaffAction.FACET_SET, Players.name(target),
                    (before == null ? "none" : before.id()) + " -> " + (facet == null ? "none" : facet.id()));
        }));
    }

    // ------------------------------------------------------------------ chat, hum, logs

    public String sigil(Player p) {
        Facet f = of(p);
        return f == null ? "" : f.sigil();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (humMode.contains(p.getUniqueId()) && p.hasPermission("shardwatch.hum")) {
            event.setCancelled(true);
            String msg = Text.plain(event.message());
            plugin.sync(() -> hum(p, msg));
            return;
        }
        if (!plugin.getConfig().getBoolean("facets.chat-format.enabled", true)) {
            return;
        }
        Facet f = of(p);
        if (f == null) {
            return;
        }
        String format = plugin.getConfig().getString("facets.chat-format.format",
                "<facet_prefix> <name_color><name></name_color> <muted>»</muted> <message>");
        event.renderer((source, displayName, message, viewer) -> plugin.lang().parse(format,
                Text.pc("sigil", Component.empty()),
                Text.pp("facet_prefix", f.prefix()),
                Text.pc("name", displayName),
                TagResolver.resolver("name_color", net.kyori.adventure.text.minimessage.tag.Tag.styling(
                        net.kyori.adventure.text.format.TextColor.fromHexString(f.color()))),
                Text.pc("message", message)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatLog(AsyncChatEvent event) {
        plugin.logs().chat(event.getPlayer(), "CHAT", Text.plain(event.message()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandLog(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        String label = msg.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (plugin.getConfig().getStringList("chat-log.ignored-commands").contains(label)) {
            return;
        }
        plugin.logs().chat(event.getPlayer(), "COMMAND", msg);
    }

    public boolean toggleHum(Player p) {
        if (!humMode.remove(p.getUniqueId())) {
            humMode.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public void hum(CommandSender from, String message) {
        Facet f = from instanceof Player p ? of(p) : null;
        TagResolver r = TagResolver.resolver(Text.p("sender", from.getName()), Text.p("message", message),
                Text.pp("facet_prefix", f == null ? "" : f.prefix()),
                Text.pp("color", f == null ? "<ice>" : "<" + f.color() + ">"));
        Component c = plugin.lang().get("hum.format", r);
        for (Player p : Players.withPermission("shardwatch.hum")) {
            p.sendMessage(c);
            plugin.fx().playFor(p, "hum");
        }
        Bukkit.getConsoleSender().sendMessage(c);
        if (from instanceof Player p) {
            plugin.logs().chat(p, "HUM", message);
        }
    }

    // ------------------------------------------------------------------ veil

    public boolean isVeiled(Player p) {
        return plugin.profiles().get(p).veiled();
    }

    public void setVeil(Player p, boolean on) {
        Profile profile = plugin.profiles().get(p);
        profile.veiled(on);
        plugin.profiles().save(profile);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) {
                continue;
            }
            if (on && !other.hasPermission("shardwatch.veil.see")) {
                other.hidePlayer(plugin, p);
            } else {
                other.showPlayer(plugin, p);
            }
        }
        plugin.fx().play(on ? "veil-on" : "veil-off", p.getLocation().add(0, 1, 0));
        plugin.lang().send(p, on ? "veil.enabled" : "veil.disabled");
        plugin.lang().send(Players.withPermission("shardwatch.veil.see"), on ? "veil.staff-on" : "veil.staff-off",
                Text.p("player", p.getName()));
        plugin.action(p, on ? StaffAction.VEIL_ON : StaffAction.VEIL_OFF, p.getName(), "");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        apply(p);
        boolean canSee = p.hasPermission("shardwatch.veil.see");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p) && isVeiled(other) && !canSee) {
                p.hidePlayer(plugin, other);
            }
        }
        if (isVeiled(p)) {
            if (p.hasPermission("shardwatch.veil")) {
                event.joinMessage(null);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(p) && !other.hasPermission("shardwatch.veil.see")) {
                        other.hidePlayer(plugin, p);
                    }
                }
                plugin.lang().send(p, "veil.still");
            } else {
                plugin.profiles().get(p).veiled(false);
            }
        }
        if (p.hasPermission("shardwatch.notify.flares")) {
            plugin.flares().sendOpenSummary(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (isVeiled(p)) {
            event.quitMessage(null);
        }
        humMode.remove(p.getUniqueId());
        PermissionAttachment att = attachments.remove(p.getUniqueId());
        if (att != null) {
            try {
                p.removeAttachment(att);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public List<String> ids() {
        return new ArrayList<>(facets.keySet());
    }
}
