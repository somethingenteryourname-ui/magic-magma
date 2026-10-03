package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.profile.Profile;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Lustre overview: level, progress bar, leaderboard, and the way to Refinements and Keepsakes. */
public final class LustreMenu extends Menu {

    private final List<String[]> top;

    public LustreMenu(Shardwatch plugin, Player viewer, List<String[]> top) {
        super(plugin, viewer);
        this.top = top;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.lustre.title";
    }

    @Override
    protected void build() {
        var lustre = plugin.lustre();
        Profile p = plugin.profiles().get(viewer);
        int level = lustre.level(p.lifetime());
        long toNext = lustre.toNext(p.lifetime());
        TagResolver r = TagResolver.resolver(Text.p("lustre", p.lustre()), Text.p("lifetime", p.lifetime()),
                Text.p("level", level), Text.p("clarity", lustre.clarity(level)), Text.pp("bar", lustre.bar(p.lifetime())),
                Text.p("next", toNext < 0 ? "-" : String.valueOf(toNext)), Text.p("today", p.dailyEarned()),
                Text.p("cap", plugin.getConfig().getLong("progression.daily-cap", 400)));
        set(4, plugin.icons().amount(plugin.icons().button("lustre", "lustre.overview", r), level));
        set(11, plugin.icons().button("refine", "lustre.refine", r), e -> plugin.menus().openRefine(viewer));
        set(13, plugin.icons().model("lustre_shard", Material.AMETHYST_SHARD, plugin.lang().raw("gui.lustre.shards.name"),
                plugin.lang().rawList("gui.lustre.shards.lore"), Text.p("value", plugin.getConfig().getInt("progression.shard-value", 25))));
        set(15, plugin.icons().button("keepsake", "lustre.keepsakes", r), e -> plugin.menus().openKeepsakes(viewer));
        List<String> lore = new java.util.ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            lore.add("<muted>" + (i + 1) + ".</muted> <white>" + Text.escape(top.get(i)[0]) + "</white> <pink>" + top.get(i)[1]);
        }
        if (lore.isEmpty()) {
            lore.add("<muted>No Lustre earned yet.");
        }
        set(22, plugin.icons().icon("facet", plugin.lang().raw("gui.lustre.top-name"), lore));
        set(18, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openConsole(viewer));
        set(26, plugin.icons().button("close", "common.close"), e -> close());
    }
}
