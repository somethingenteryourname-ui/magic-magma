package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.profile.Profile;
import dev.shardwatch.util.Text;
import org.bukkit.entity.Player;

/** Personal staff toggles. */
public final class SettingsMenu extends Menu {

    public SettingsMenu(Shardwatch plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.settings.title";
    }

    private String state(boolean on) {
        return plugin.lang().raw(on ? "gui.state-on" : "gui.state-off");
    }

    @Override
    protected void build() {
        Profile p = plugin.profiles().get(viewer);
        set(11, plugin.icons().button(p.alertsOn() ? "flare" : "locked", "settings.alerts", Text.pp("state", state(p.alertsOn()))), e -> {
            p.alertsOn(!p.alertsOn());
            plugin.profiles().save(p);
            refresh();
        });
        set(13, plugin.icons().button(p.fxOn() ? "lustre" : "locked", "settings.fx", Text.pp("state", state(p.fxOn()))), e -> {
            p.fxOn(!p.fxOn());
            plugin.profiles().save(p);
            refresh();
        });
        if (viewer.hasPermission("shardwatch.echo")) {
            boolean inspecting = plugin.echoes().isInspecting(viewer);
            set(15, plugin.icons().button("glint", "settings.inspect", Text.pp("state", state(inspecting))), e -> {
                plugin.echoes().toggleInspect(viewer);
                refresh();
            });
        }
        set(22, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openConsole(viewer));
    }
}
