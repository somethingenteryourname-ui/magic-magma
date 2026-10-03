package dev.shardwatch.gui;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Asks a player to type one line in chat (a reason, a note…). "cancel" aborts. */
public final class ChatPrompt implements Listener {

    private record Pending(Consumer<String> answer, Runnable cancelled, BukkitTask timeout) {
    }

    private final Shardwatch plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    public ChatPrompt(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String langKey, Consumer<String> answer, Runnable cancelled) {
        p.closeInventory();
        Pending old = waiting.remove(p.getUniqueId());
        if (old != null) {
            old.timeout().cancel();
        }
        int seconds = plugin.getConfig().getInt("gui.prompt-seconds", 60);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending p2 = waiting.remove(p.getUniqueId());
            if (p2 != null && p.isOnline()) {
                plugin.lang().send(p, "gui.prompt-timeout");
                if (p2.cancelled() != null) {
                    p2.cancelled().run();
                }
            }
        }, seconds * 20L);
        waiting.put(p.getUniqueId(), new Pending(answer, cancelled, timeout));
        plugin.lang().send(p, langKey, Text.p("seconds", seconds));
        plugin.fx().playFor(p, "ui-page");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending pending = waiting.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String text = Text.plain(event.message()).trim();
        pending.timeout().cancel();
        plugin.sync(() -> {
            if (text.equalsIgnoreCase("cancel")) {
                plugin.lang().send(event.getPlayer(), "gui.prompt-cancelled");
                if (pending.cancelled() != null) {
                    pending.cancelled().run();
                }
            } else {
                pending.answer().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pending p = waiting.remove(event.getPlayer().getUniqueId());
        if (p != null) {
            p.timeout().cancel();
        }
    }
}
