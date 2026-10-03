package dev.shardwatch.gui;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.gui.menus.ConsoleMenu;
import dev.shardwatch.gui.menus.FlareBoardMenu;
import dev.shardwatch.gui.menus.FlareCreateMenu;
import dev.shardwatch.gui.menus.GlintMenu;
import dev.shardwatch.gui.menus.LedgerMenu;
import dev.shardwatch.gui.menus.PlayerPickMenu;
import dev.shardwatch.gui.menus.RewindMenu;
import dev.shardwatch.gui.menus.RosterMenu;
import dev.shardwatch.gui.menus.SettingsMenu;
import dev.shardwatch.gui.menus.VerdictMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Opens menus, loading their data first. Every open happens on the next tick, outside click handlers. */
public class MenuService {

    protected final Shardwatch plugin;

    public MenuService(Shardwatch plugin) {
        this.plugin = plugin;
    }

    protected void show(Menu menu) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (menu.viewer.isOnline()) {
                menu.open();
            }
        });
    }

    public void openConsole(Player p) {
        plugin.flares().store().countOpen().thenAccept(n -> show(new ConsoleMenu(plugin, p, n)));
    }

    public void openFlareBoard(Player p, boolean all, int page) {
        List<Flare.Status> statuses = all ? null : List.of(Flare.Status.OPEN, Flare.Status.CLAIMED);
        plugin.flares().store().list(statuses, null, 45 * 6, 0)
                .thenAccept(list -> show(new FlareBoardMenu(plugin, p, list, all, page)));
    }

    public void openFlareCreate(Player p, OfflinePlayer target) {
        show(new FlareCreateMenu(plugin, p, target, null));
    }

    public void openVerdict(Player p, OfflinePlayer target) {
        show(new VerdictMenu(plugin, p, target));
    }

    public void openLedger(Player p, OfflinePlayer target, int page) {
        plugin.verdicts().store().history(target.getUniqueId(), 45 * 8, 0)
                .thenAccept(list -> show(new LedgerMenu(plugin, p, target, list, page)));
    }

    public void openGlint(Player p, int page) {
        plugin.glint().store().recent(null, 0, 45 * 4, 0).thenAccept(list -> show(new GlintMenu(plugin, p, list, page)));
    }

    public void openRoster(Player p, int page) {
        plugin.profiles().staff().thenAccept(list -> show(new RosterMenu(plugin, p, list, page)));
    }

    public void openPlayerPick(Player p, PlayerPickMenu.Purpose purpose, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.remove(p);
        players.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        show(new PlayerPickMenu(plugin, p, players, purpose, page));
    }

    public void openSettings(Player p) {
        show(new SettingsMenu(plugin, p));
    }

    public void openRewind(Player p) {
        show(new RewindMenu(plugin, p));
    }

    /** Buttons other stages add to the Crystal Console. */
    public void consoleExtras(ConsoleMenu menu, Player viewer) {
    }
}
