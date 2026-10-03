package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.scope.ScopeViewer;
import dev.shardwatch.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /scope [tab] [window] [filters...] [--chat] and /scope tp &lt;world&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;. */
public final class ScopeCommand extends BaseCommand {

    public ScopeCommand(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] raw) {
        Player p = requirePlayer(sender);
        if (p == null || !has(p, "shardwatch.scope")) {
            return true;
        }
        if (raw.length == 5 && raw[0].equalsIgnoreCase("tp")) {
            if (!has(p, "shardwatch.scope.teleport")) {
                return true;
            }
            World w = Bukkit.getWorld(raw[1]);
            if (w == null) {
                plugin.lang().send(p, "general.unknown-world", Text.p("world", raw[1]));
                return true;
            }
            Location to = new Location(w, intArg(raw, 2, 0) + 0.5, intArg(raw, 3, 64) + 1, intArg(raw, 4, 0) + 0.5,
                    p.getLocation().getYaw(), p.getLocation().getPitch());
            plugin.fx().play("warp-out", p.getLocation().add(0, 1, 0));
            p.teleportAsync(to).thenRun(() -> plugin.fx().play("warp-in", to));
            return true;
        }
        boolean chat = Arrays.asList(raw).contains("--chat") || !plugin.getConfig().getBoolean("scope.book-by-default", true);
        List<String> args = new ArrayList<>(Arrays.stream(raw).filter(a -> !a.equals("--chat")).toList());
        ScopeViewer.Tab tab = ScopeViewer.Tab.OVERVIEW;
        if (!args.isEmpty()) {
            ScopeViewer.Tab parsed = ScopeViewer.Tab.parse(args.get(0));
            if (parsed != null) {
                tab = parsed;
                args.remove(0);
            }
        }
        if (tab == ScopeViewer.Tab.BLOCKS && !has(p, "shardwatch.scope.blocks")
                || tab == ScopeViewer.Tab.CHAT && !has(p, "shardwatch.scope.chat")
                || tab == ScopeViewer.Tab.STAFF && !has(p, "shardwatch.scope.staff")) {
            return true;
        }
        int window = 1;
        if (!args.isEmpty() && args.get(0).matches("\\d+")) {
            window = Math.max(1, Integer.parseInt(args.remove(0)));
        }
        plugin.scope().open(p, tab, window, ScopeViewer.Filter.parse(args.toArray(String[]::new)), chat);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> tabs = new ArrayList<>();
            for (ScopeViewer.Tab t : ScopeViewer.Tab.values()) {
                tabs.add(t.id());
            }
            return filter(tabs, args[0]);
        }
        return filter(List.of("u:", "r:", "t:", "a:", "--chat"), args[args.length - 1]);
    }
}
