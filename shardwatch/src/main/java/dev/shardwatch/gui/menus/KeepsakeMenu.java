package dev.shardwatch.gui.menus;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.profile.Profile;
import dev.shardwatch.progress.LustreService;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Keepsakes: auras, chat sigils and shard bundles. Locked, claimable, owned or active. */
public final class KeepsakeMenu extends PagedMenu<LustreService.Keepsake> {

    public KeepsakeMenu(Shardwatch plugin, Player viewer, List<LustreService.Keepsake> keepsakes) {
        super(plugin, viewer, keepsakes, 0);
    }

    @Override
    protected String titleKey() {
        return "gui.keepsakes.title";
    }

    @Override
    protected ItemStack entryIcon(LustreService.Keepsake k) {
        Profile p = plugin.profiles().get(viewer);
        boolean owned = p.keepsakes().contains(k.id());
        boolean active = k.id().equals(p.activeAura()) || k.id().equals(p.activeSigil());
        String state = active ? "active" : owned ? "owned" : plugin.lustre().level(viewer) < k.level() ? "locked" : "claimable";
        TagResolver r = TagResolver.resolver(Text.p("cost", k.cost()), Text.p("level", k.level()),
                Text.pp("type", plugin.lang().raw("gui.keepsakes.types." + k.type())));
        List<String> lore = new ArrayList<>(k.lore());
        lore.addAll(plugin.lang().rawList("gui.keepsakes." + state));
        String model = state.equals("locked") ? "icon_locked" : k.icon();
        ItemStack icon = plugin.icons().model(model, Material.AMETHYST_SHARD, k.name(), lore, r);
        if (active) {
            icon.setData(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return icon;
    }

    @Override
    protected void onEntry(LustreService.Keepsake k, InventoryClickEvent e) {
        plugin.lustre().useKeepsake(viewer, k);
        refresh();
    }

    @Override
    protected Runnable back() {
        return () -> plugin.menus().openLustre(viewer);
    }

    @Override
    protected void navigation() {
        Profile p = plugin.profiles().get(viewer);
        set(46, plugin.icons().button("lustre", "keepsakes.balance", Text.p("lustre", p.lustre())));
    }
}
