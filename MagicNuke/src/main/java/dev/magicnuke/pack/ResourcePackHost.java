package dev.magicnuke.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.magicnuke.MagicNuke;
import dev.magicnuke.Msg;
import dev.magicnuke.NukeConfig;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the resource pack that ships inside the plugin jar on a tiny built-in
 * web server, and sends it to players when they join. The pack is also copied
 * to plugins/MagicNuke/MagicNuke-ResourcePack.zip for anyone who wants to host
 * it elsewhere or install it by hand.
 */
public final class ResourcePackHost implements Listener {

    public static final String PACK_FILE = "MagicNuke-ResourcePack.zip";

    private final MagicNuke plugin;
    private final Set<UUID> loaded = new HashSet<>();
    private byte[] pack;
    private String sha1;
    private UUID packId;
    private HttpServer server;
    private ExecutorService executor;
    private String path;

    public ResourcePackHost(MagicNuke plugin) {
        this.plugin = plugin;
    }

    public void start() {
        NukeConfig c = plugin.settings();
        if (pack == null && !loadPack()) return;
        if (!c.packEnabled || !c.packExternalUrl.isEmpty()) return;
        try {
            server = HttpServer.create(new InetSocketAddress(c.packBindAddress, c.packPort), 16);
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread th = new Thread(r, "MagicNuke-PackHost");
                th.setDaemon(true);
                return th;
            });
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            plugin.getLogger().info("Hosting the resource pack on port " + c.packPort
                    + ". Players must be able to reach this port (TCP) - open or forward it like your game port.");
        } catch (IOException | RuntimeException e) {
            server = null;
            if (executor != null) executor.shutdownNow();
            plugin.getLogger().warning("Couldn't start the resource pack server on port " + c.packPort + ": " + e.getMessage());
            plugin.getLogger().warning("Change pack.port in config.yml, or upload " + PACK_FILE
                    + " somewhere and set pack.external-url.");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private boolean loadPack() {
        try (InputStream in = plugin.getResource("magicnuke-pack.zip")) {
            if (in == null) {
                plugin.getLogger().severe("The resource pack is missing from the plugin jar!");
                return false;
            }
            pack = in.readAllBytes();
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            packId = UUID.nameUUIDFromBytes(("magicnuke:" + sha1).getBytes(StandardCharsets.UTF_8));
            path = "/magicnuke-" + sha1.substring(0, 12) + ".zip";
            Path out = plugin.getDataFolder().toPath().resolve(PACK_FILE);
            Files.createDirectories(out.getParent());
            if (!Files.exists(out) || Files.size(out) != pack.length || !java.util.Arrays.equals(Files.readAllBytes(out), pack)) {
                Files.write(out, pack);
            }
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Couldn't load the resource pack: " + e);
            return false;
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String p = ex.getRequestURI().getPath();
            if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            if (!p.endsWith(".zip")) {
                byte[] body = "MagicNuke resource pack server".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + PACK_FILE + "\"");
            if ("HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, pack.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(pack);
            }
        }
    }

    /** The download URL this player should use, or null if the pack can't be offered. */
    public String urlFor(Player player) {
        NukeConfig c = plugin.settings();
        if (pack == null || !c.packEnabled) return null;
        if (!c.packExternalUrl.isEmpty()) return c.packExternalUrl;
        if (server == null) return null;
        String host = c.packPublicAddress;
        if (host.isEmpty()) {
            InetSocketAddress vh = player.getVirtualHost();
            if (vh != null) host = vh.getHostString();
        }
        if (host == null || host.isEmpty()) host = Bukkit.getIp();
        if (host == null || host.isEmpty()) host = "localhost";
        int nul = host.indexOf('\0');
        if (nul >= 0) host = host.substring(0, nul);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return "http://" + host + ":" + c.packPort + path;
    }

    public void send(Player player) {
        String url = urlFor(player);
        if (url == null) return;
        NukeConfig c = plugin.settings();
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), sha1);
        ResourcePackRequest.Builder req = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(c.packRequired)
                .replace(false);
        if (c.packPrompt != null && !c.packPrompt.isEmpty()) req.prompt(Msg.mm(c.packPrompt));
        player.sendResourcePacks(req.build());
    }

    public boolean hasPack(Player player) {
        return loaded.contains(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.settings().packSendOnJoin) return;
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!event.getID().equals(packId)) return;
        Player p = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(p.getUniqueId());
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                loaded.remove(p.getUniqueId());
                plugin.getLogger().warning(p.getName() + " couldn't download the MagicNuke resource pack from "
                        + urlFor(p) + " (" + event.getStatus() + "). Check that port "
                        + plugin.settings().packPort + " is open, or set pack.public-address / pack.external-url.");
                if (p.hasPermission("magicnuke.admin")) {
                    Msg.send(p, "<red>Your resource pack download failed. <gray>Check the server console for how to fix it.");
                }
            }
            case DECLINED -> loaded.remove(p.getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    public String sha1() {
        return sha1;
    }

    public boolean isHosting() {
        return server != null;
    }
}
