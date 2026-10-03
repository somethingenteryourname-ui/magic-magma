package dev.shardwatch.tool;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds and recognises staff tool items. */
public final class ToolService {

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private final Shardwatch plugin;
    private final NamespacedKey toolKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey extraKey;

    public ToolService(Shardwatch plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.tierKey = new NamespacedKey(plugin, "tier");
        this.extraKey = new NamespacedKey(plugin, "extra");
    }

    public static String roman(int n) {
        return n >= 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }

    public Material baseMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("tools.base-material", "AMETHYST_SHARD"));
        return m == null || !m.isItem() ? Material.AMETHYST_SHARD : m;
    }

    /** Creates a tool. For {@link StaffTool#SIGIL} pass the Facet id as {@code extra}. */
    public ItemStack create(StaffTool tool, int tier, String extra) {
        int t = tool.tiered() ? Math.max(1, Math.min(3, tier)) : 1;
        String modelId = tool == StaffTool.SIGIL ? "sigil_" + (extra == null ? "shardling" : extra) : tool.id();
        ItemStack item = ItemStack.of(baseMaterial());
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("shardwatch", modelId));
        TagResolver r = TagResolver.resolver(Text.p("tier", roman(t)), Text.p("tier_num", t),
                Text.pp("facet", sigilFacetName(extra)));
        String base = "tools." + tool.id();
        item.setData(DataComponentTypes.ITEM_NAME, plugin.lang().item(plugin.lang().raw(base + ".name"), r));
        List<Component> lore = new ArrayList<>(plugin.lang().getLines(base + ".lore", r));
        if (tool.tiered()) {
            lore.addAll(plugin.lang().getLines("tools.tier-line", r));
        }
        lore.addAll(plugin.lang().getLines("tools.footer", r));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        CustomModelData.Builder cmd = CustomModelData.customModelData().addString("t" + t);
        if (tool == StaffTool.VEIL_LANTERN) {
            cmd.addFlag(true); // unlit until the holder is Veiled
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd.build());
        if (tool != StaffTool.LUSTRE_SHARD) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        }
        item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(1f)
                .cooldownGroup(Key.key("shardwatch", modelId)).build());
        applyStyle(item);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(toolKey, PersistentDataType.STRING, tool.id());
            pdc.set(tierKey, PersistentDataType.INTEGER, t);
            if (extra != null) {
                pdc.set(extraKey, PersistentDataType.STRING, extra);
            }
        });
        return item;
    }

    public ItemStack create(StaffTool tool, int tier) {
        return create(tool, tier, null);
    }

    /** Crystal tooltip frame (Stage 3 sprites). */
    void applyStyle(ItemStack item) {
        plugin.icons().style(item);
    }

    private String sigilFacetName(String facetId) {
        FacetService.Facet f = facetId == null ? null : plugin.facets().get(facetId);
        return f == null ? "" : f.name();
    }

    public StaffTool toolOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return id == null ? null : StaffTool.parse(id);
    }

    public boolean isTool(ItemStack item) {
        return toolOf(item) != null;
    }

    public int tierOf(ItemStack item) {
        Integer t = item.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return t == null ? 1 : t;
    }

    public String extraOf(ItemStack item) {
        return item.getPersistentDataContainer().get(extraKey, PersistentDataType.STRING);
    }

    /** Ticks of the tool's cooldown from config ({@code tools.<id>.cooldown-ticks}). */
    public int cooldown(StaffTool tool) {
        return plugin.getConfig().getInt("tools." + tool.id() + ".cooldown-ticks", 10);
    }

    public Key cooldownGroup(StaffTool tool) {
        return Key.key("shardwatch", tool.id());
    }

    public boolean onCooldown(Player p, StaffTool tool) {
        return p.getCooldown(cooldownGroup(tool)) > 0;
    }

    public void startCooldown(Player p, StaffTool tool, int ticks) {
        p.setCooldown(cooldownGroup(tool), ticks);
    }

    /** Lantern model: flag 0 = unlit. */
    public void setLanternLit(ItemStack item, boolean lit) {
        CustomModelData old = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        CustomModelData.Builder b = CustomModelData.customModelData().addFlag(!lit);
        if (old != null) {
            b.addStrings(old.strings());
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, b.build());
    }

    public void pointCompass(ItemStack item, Location target) {
        if (target == null) {
            item.unsetData(DataComponentTypes.LODESTONE_TRACKER);
        } else {
            item.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker(target, false));
        }
    }

    /** Gives every kit tool the player may use (at their Refinement tier), skipping ones they already carry. */
    public int giveKit(Player p) {
        int given = 0;
        for (StaffTool tool : StaffTool.values()) {
            if (!tool.kit() || !p.hasPermission(tool.permission()) || has(p, tool)) {
                continue;
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(create(tool, tierFor(p, tool)));
            left.values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
            given++;
        }
        return given;
    }

    /** The tier this player has unlocked for a tool (Refinements arrive in Stage 4; until then every tool is tier I). */
    public int tierFor(Player p, StaffTool tool) {
        return tool.tiered() ? plugin.profiles().get(p).tier(tool.id()) : 1;
    }

    public boolean has(Player p, StaffTool tool) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (toolOf(i) == tool) {
                return true;
            }
        }
        return false;
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (StaffTool t : StaffTool.values()) {
            out.add(t.id().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
