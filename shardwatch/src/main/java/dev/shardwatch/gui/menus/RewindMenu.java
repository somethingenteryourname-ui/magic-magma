package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.List;

/** Rewind around you: pick a source, a time and a radius, then preview or rewind. */
public final class RewindMenu extends Menu {

    private int source;
    private int time;
    private int radius;

    public RewindMenu(Shardwatch plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected String titleKey() {
        return "gui.rewind.title";
    }

    private List<String> sources() {
        return plugin.getConfig().getStringList("gui.rewind.sources");
    }

    private List<String> times() {
        return plugin.getConfig().getStringList("gui.rewind.times");
    }

    private List<Integer> radii() {
        return plugin.getConfig().getIntegerList("gui.rewind.radii");
    }

    @Override
    protected void build() {
        List<String> sources = sources();
        List<String> times = times();
        List<Integer> radii = radii();
        for (int i = 0; i < Math.min(7, sources.size()); i++) {
            int idx = i;
            String s = sources.get(i);
            set(10 + i, plugin.icons().button(i == source ? "confirm" : "flare_griefing", "rewind.source",
                    Text.p("source", s.equals("*") ? "everyone" : s), Text.pp("chosen", plugin.lang().raw(i == source ? "gui.chosen" : "gui.blank"))),
                    e -> {
                        source = idx;
                        refresh();
                    });
        }
        for (int i = 0; i < Math.min(7, times.size()); i++) {
            int idx = i;
            set(19 + i, plugin.icons().amount(plugin.icons().button(i == time ? "confirm" : "clock", "rewind.time",
                    Text.p("time", times.get(i)), Text.pp("chosen", plugin.lang().raw(i == time ? "gui.chosen" : "gui.blank"))), i + 1),
                    e -> {
                        time = idx;
                        refresh();
                    });
        }
        for (int i = 0; i < Math.min(7, radii.size()); i++) {
            int idx = i;
            set(28 + i, plugin.icons().amount(plugin.icons().button(i == radius ? "confirm" : "filter", "rewind.radius",
                    Text.p("radius", radii.get(i)), Text.pp("chosen", plugin.lang().raw(i == radius ? "gui.chosen" : "gui.blank"))),
                    Math.min(64, radii.get(i))), e -> {
                radius = idx;
                refresh();
            });
        }
        TagResolver r = summary();
        set(39, plugin.icons().button("glint", "rewind.preview", r), e -> run(true));
        set(41, plugin.icons().button("rewind", "rewind.run", r), e -> run(false));
        set(45, plugin.icons().button("cancel", "rewind.undo"), e -> {
            close();
            plugin.rewind().undo(viewer);
        });
        set(48, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openConsole(viewer));
        set(49, plugin.icons().button("close", "common.close"), e -> close());
    }

    private TagResolver summary() {
        String s = sources().isEmpty() ? "*" : sources().get(source);
        return TagResolver.resolver(Text.p("source", s.equals("*") ? "everyone" : s),
                Text.p("time", times().isEmpty() ? "1h" : times().get(time)),
                Text.p("radius", radii().isEmpty() ? 15 : radii().get(radius)));
    }

    private void run(boolean preview) {
        String s = sources().isEmpty() ? "*" : sources().get(source);
        Long since = Durations.parse(times().isEmpty() ? "1h" : times().get(time));
        int r = radii().isEmpty() ? 15 : radii().get(radius);
        close();
        plugin.rewind().rewind(viewer, s.equals("*") ? null : s, since == null ? 3_600_000L : since, r,
                viewer.getLocation(), preview);
    }
}
