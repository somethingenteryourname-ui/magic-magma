package dev.customtrims.pack;

import com.sun.net.httpserver.HttpServer;
import dev.customtrims.CustomTrimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Hosts the resource pack (optional) and sends it to players when they join. */
public final class PackServer implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("customtrims-pack".getBytes(StandardCharsets.UTF_8));

    private final CustomTrimsPlugin plugin;
    private HttpServer server;

    public PackServer(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private boolean enabled() {
        return cfg().getBoolean("resource-pack.enabled", true);
    }

    private String externalUrl() {
        return cfg().getString("resource-pack.url", "").trim();
    }

    public void start() {
        stop();
        if (!enabled() || !externalUrl().isEmpty()) return;
        int port = cfg().getInt("resource-pack.port", 8164);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                byte[] pack = plugin.getPackBuilder().getResourcePack();
                String path = exchange.getRequestURI().getPath();
                if (pack == null || !path.endsWith(".zip")) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, pack.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(pack);
                }
            });
            server.start();
            plugin.getLogger().info("Hosting the trim resource pack on port " + port + ".");
        } catch (IOException e) {
            server = null;
            plugin.getLogger().severe("Could not host the resource pack on port " + port + " (" + e.getMessage()
                    + "). Change resource-pack.port, or upload plugins/CustomTrims/CustomTrims-ResourcePack.zip"
                    + " somewhere and put the link in resource-pack.url");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** The download link for this player, or null if the pack can't be sent. */
    public String urlFor(Player p) {
        String url = externalUrl();
        if (!url.isEmpty()) return url;
        if (server == null) return null;
        String host = cfg().getString("resource-pack.public-address", "").trim();
        if (host.isEmpty()) {
            InetSocketAddress virtual = p.getVirtualHost(); // the address the player typed to join
            host = virtual != null ? virtual.getHostString() : Bukkit.getIp();
        }
        if (host == null || host.isEmpty()) host = "localhost";
        String hash = plugin.getPackBuilder().getSha1Hex();
        return "http://" + host + ":" + cfg().getInt("resource-pack.port", 8164)
                + "/customtrims-" + (hash.length() >= 10 ? hash.substring(0, 10) : "pack") + ".zip";
    }

    public boolean send(Player p) {
        if (!enabled()) return false;
        String url = urlFor(p);
        byte[] hash = plugin.getPackBuilder().getSha1();
        if (url == null || hash == null) return false;
        String prompt = cfg().getString("resource-pack.prompt", "This adds the custom armor trims!");
        p.addResourcePack(PACK_ID, url, hash, prompt, cfg().getBoolean("resource-pack.force", false));
        return true;
    }

    public void sendToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) send(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            send(p);
            if (plugin.getPackBuilder().isRestartNeeded() && p.hasPermission("customtrims.admin")) {
                p.sendMessage("\u00A7b\u00A7lTrims \u00A78\u00BB \u00A7eRestart the server once so the new trims and materials load.");
            }
        }, 40L);
    }
}
