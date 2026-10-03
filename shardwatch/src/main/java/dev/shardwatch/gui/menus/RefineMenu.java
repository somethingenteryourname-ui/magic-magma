package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.gui.Menu;
import dev.shardwatch.tool.StaffTool;
import dev.shardwatch.tool.ToolService;
import dev.shardwatch.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Refinements: each tool at its current tier, with the cost and level for the next one. */
public final class RefineMenu extends Menu {

    public RefineMenu(Shardwatch plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected String titleKey() {
        return "gui.refine.title";
    }

    @Override
    protected void build() {
        long lustre = plugin.profiles().get(viewer).lustre();
        int level = plugin.lustre().level(viewer);
        int slot = 10;
        for (StaffTool tool : StaffTool.values()) {
            if (!tool.tiered()) {
                continue;
            }
            int tier = plugin.profiles().get(viewer).tier(tool.id());
            long[] next = plugin.lustre().nextTier(viewer, tool);
            ItemStack icon = plugin.tools().create(tool, tier);
            TagResolver r = TagResolver.resolver(Text.p("tier", ToolService.roman(tier)),
                    Text.p("next", next == null ? "-" : ToolService.roman((int) next[0])),
                    Text.p("cost", next == null ? 0 : next[1]), Text.p("level", next == null ? 0 : next[2]),
                    Text.p("lustre", lustre));
            String key = next == null ? "gui.refine.max" : !viewer.hasPermission(tool.permission()) ? "gui.refine.no-access"
                    : level < next[2] ? "gui.refine.locked" : lustre < next[1] ? "gui.refine.poor" : "gui.refine.ready";
            List<Component> lore = new ArrayList<>(plugin.lang().getLines(key, r));
            icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
            set(slot++, icon, e -> {
                if (next != null && viewer.hasPermission(tool.permission()) && plugin.lustre().refine(viewer, tool)) {
                    refresh();
                }
            });
        }
        set(22, plugin.icons().button("cancel", "common.back"), e -> plugin.menus().openLustre(viewer));
    }
}
