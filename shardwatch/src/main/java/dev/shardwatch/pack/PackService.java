package dev.shardwatch.pack;

import com.sun.net.httpserver.HttpServer;
import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Optional resource-pack delivery by the plugin. Most servers just use server.properties (see README); this adds:
 * <ul>
 *   <li>{@code pack.send}: push the pack on join with a prompt (and a SHA-1 that can be computed automatically),</li>
 *   <li>{@code pack.host}: serve {@code plugins/Shardwatch/Shardwatch-pack.zip} from a tiny built-in web server, for
 *       servers whose GitHub repository is private.</li>
 * </ul>
 */
public final class PackService implements Listener {

    private final Shardwatch plugin;
    private HttpServer server;
    private volatile String url;
    private volatile String sha1;
    private UUID packId;

    public PackService(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        stopHost();
        url = plugin.getConfig().getString("pack.url", "");
        sha1 = plugin.getConfig().getString("pack.sha1", "auto");
        packId = UUID.nameUUIDFromBytes(("shardwatch-pack").getBytes(StandardCharsets.UTF_8));
        if (plugin.getConfig().getBoolean("pack.host.enabled", false)) {
            startHost();
        }
        if ("auto".equalsIgnoreCase(sha1) && url != null && !url.isBlank()) {
            sha1 = null;
            computeRemoteSha1(url).thenAccept(h -> {
                sha1 = h;
                plugin.getLogger().info("Resource pack SHA-1: " + h);
            }).exceptionally(err -> {
                plugin.getLogger().warning("Could not hash the resource pack at " + url + ": " + err.getMessage());
                return null;
            });
        }
    }

    public boolean ready() {
        return url != null && !url.isBlank() && sha1 != null && sha1.matches("[0-9a-fA-F]{40}");
    }

    public String url() {
        return url;
    }

    public String sha1() {
        return sha1;
    }

    // ------------------------------------------------------------------ sending

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("pack.send", false)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(event.getPlayer()),
                    plugin.getConfig().getLong("pack.delay-ticks", 20));
        }
    }

    public boolean send(Player p) {
        if (!p.isOnline()) {
            return false;
        }
        if (!ready()) {
            plugin.lang().send(p, "pack.not-ready");
            return false;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), sha1.toLowerCase());
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(plugin.getConfig().getBoolean("pack.required", false))
                .prompt(plugin.lang().get("pack.prompt"))
                .replace(false));
        return true;
    }

    // ------------------------------------------------------------------ hashing

    private CompletableFuture<String> computeRemoteSha1(String from) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(from)).timeout(Duration.ofSeconds(60)).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenApply(resp -> {
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body()) {
                return sha1(in);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static String sha1(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    // ------------------------------------------------------------------ built-in host

    private void startHost() {
        File zip = new File(plugin.getDataFolder(), plugin.getConfig().getString("pack.host.file", "Shardwatch-pack.zip"));
        if (!zip.isFile()) {
            plugin.getLogger().warning("pack.host is on but " + zip + " does not exist. Copy the pack zip there and /sw reload.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(zip.toPath());
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            int port = plugin.getConfig().getInt("pack.host.port", 8163);
            String bind = plugin.getConfig().getString("pack.host.bind", "0.0.0.0");
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext("/Shardwatch-pack.zip", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Shardwatch-PackHost");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            String publicUrl = plugin.getConfig().getString("pack.host.public-url", "");
            if (publicUrl.isBlank()) {
                String host = Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
                url = "http://" + host + ":" + port + "/Shardwatch-pack.zip";
                plugin.getLogger().warning("pack.host.public-url is empty; players need a public address. Using " + url);
            } else {
                url = publicUrl;
            }
            plugin.getLogger().info("Serving the resource pack at " + url + " (SHA-1 " + sha1 + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Could not start the pack host: " + e.getMessage());
        }
    }

    public void stopHost() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void status(org.bukkit.command.CommandSender to) {
        plugin.lang().send(to, "pack.status", Text.p("url", url == null || url.isBlank() ? "-" : url),
                Text.p("sha1", sha1 == null ? "computing…" : sha1),
                Text.pp("send", plugin.lang().raw(plugin.getConfig().getBoolean("pack.send", false) ? "gui.state-on" : "gui.state-off")),
                Text.pp("host", plugin.lang().raw(server != null ? "gui.state-on" : "gui.state-off")));
    }
}
