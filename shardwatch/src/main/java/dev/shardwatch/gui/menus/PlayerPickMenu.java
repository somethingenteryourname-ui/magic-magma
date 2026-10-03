package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Online players as heads; what clicking does depends on the purpose. */
public final class PlayerPickMenu extends PagedMenu<Player> {

    public enum Purpose { JUDGE, LEDGER, WATCH }

    private final Purpose purpose;

    public PlayerPickMenu(Shardwatch plugin, Player viewer, List<Player> players, Purpose purpose, int page) {
        super(plugin, viewer, players, page);
        this.purpose = purpose;
    }

    @Override
    protected String titleKey() {
        return "gui.pick.title-" + purpose.name().toLowerCase();
    }

    @Override
    protected ItemStack entryIcon(Player p) {
        return plugin.icons().head(p.getUniqueId(), p.getName(), plugin.lang().raw("gui.pick.entry.name"),
                plugin.lang().rawList("gui.pick.entry-" + purpose.name().toLowerCase()), Text.p("player", p.getName()),
                Text.p("world", p.getWorld().getName()), Text.p("ping", p.getPing()));
    }

    @Override
    protected void onEntry(Player p, InventoryClickEvent e) {
        switch (purpose) {
            case JUDGE -> plugin.menus().openVerdict(viewer, p);
            case LEDGER -> plugin.menus().openLedger(viewer, p, 0);
            case WATCH -> {
                close();
                plugin.glint().watch(viewer, p);
            }
        }
    }

    @Override
    protected Runnable back() {
        return () -> plugin.menus().openConsole(viewer);
    }
}
