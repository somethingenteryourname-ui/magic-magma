package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.glint.GlintAlert;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Glint Watch: recent x-ray alerts. Left: teleport · right: watch · shift: mark handled. */
public final class GlintMenu extends PagedMenu<GlintAlert> {

    public GlintMenu(Shardwatch plugin, Player viewer, List<GlintAlert> alerts, int page) {
        super(plugin, viewer, alerts, page);
    }

    @Override
    protected String titleKey() {
        return "gui.glint.title";
    }

    @Override
    protected ItemStack entryIcon(GlintAlert g) {
        TagResolver r = TagResolver.resolver(Text.p("id", g.id()), Text.p("player", g.name()), Text.p("ore", g.ore()),
                Text.p("score", String.format("%.0f", g.score())), Text.p("ago", Durations.ago(g.time())),
                Text.p("world", g.world()), Text.p("x", g.x()), Text.p("y", g.y()), Text.p("z", g.z()),
                Text.p("handled", g.handledBy() == null ? "-" : g.handledBy()));
        ItemStack icon = plugin.icons().icon(g.handledBy() == null ? "glint" : "confirm", plugin.lang().raw("gui.glint.entry-name"),
                plugin.lang().rawList("gui.glint.entry"), r);
        return plugin.icons().amount(icon, (int) Math.min(64, Math.max(1, g.score())));
    }

    @Override
    protected void onEntry(GlintAlert g, InventoryClickEvent e) {
        if (e.isShiftClick()) {
            plugin.glint().store().markHandled(g.id(), viewer.getName()).thenAccept(ok -> plugin.sync(() -> {
                if (ok) {
                    plugin.action(viewer, StaffAction.GLINT_HANDLE, g.name(), "#" + g.id());
                }
                plugin.menus().openGlint(viewer, page);
            }));
            return;
        }
        if (e.isRightClick()) {
            Player suspect = Bukkit.getPlayer(g.uuid());
            if (suspect == null) {
                plugin.lang().send(viewer, "general.not-online", Text.p("player", g.name()));
                return;
            }
            close();
            plugin.glint().watch(viewer, suspect);
            return;
        }
        World w = Bukkit.getWorld(g.world());
        if (w != null) {
            close();
            viewer.teleportAsync(new Location(w, g.x() + 0.5, g.y() + 1, g.z() + 0.5));
            plugin.fx().play("warp-in", viewer.getLocation());
        }
    }

    @Override
    protected Runnable back() {
        return () -> plugin.menus().openConsole(viewer);
    }

    @Override
    protected void navigation() {
        set(46, plugin.icons().button("glint", "glint.suspects"), e -> plugin.menus().openPlayerPick(viewer,
                PlayerPickMenu.Purpose.WATCH, 0));
    }
}
