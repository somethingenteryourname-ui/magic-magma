package dev.shardwatch.progress;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.ShardwatchActionEvent;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.profile.Profile;
import dev.shardwatch.tool.StaffTool;
import dev.shardwatch.tool.ToolService;
import dev.shardwatch.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lustre: staff earn it for handling Flares, passing verdicts, rewinding and so on. Lifetime Lustre sets the Clarity
 * level (spending never lowers it); spendable Lustre buys Refinements (tool tiers) and Keepsakes (cosmetics).
 */
public final class LustreService implements Listener {

    public record Keepsake(String id, String type, String name, String icon, int level, long cost, List<String> lore) {
    }

    /** Where Lustre comes from: actions are capped daily; shards only add spendable Lustre. */
    public enum Source { ACTION, SHARD, ADMIN }

    private final Shardwatch plugin;

    public LustreService(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("progression.enabled", true);
    }

    // ------------------------------------------------------------------ earning

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAction(ShardwatchActionEvent event) {
        Player p = event.getActor();
        if (!enabled() || p == null || !p.hasPermission("shardwatch.staff")) {
            return;
        }
        int points = plugin.getConfig().getInt("progression.rewards." + event.getAction().name().toLowerCase(Locale.ROOT), 0);
        if (points > 0) {
            grant(p, points, event.getAction().name().toLowerCase(Locale.ROOT).replace('_', ' '), Source.ACTION);
        }
    }

    /** Adds Lustre. Actions obey the daily cap; shards add spendable Lustre only; admin grants follow config. */
    public void grant(Player p, long amount, String why, Source source) {
        boolean capped = source == Source.ACTION;
        boolean countsForLevel = source == Source.ACTION
                || source == Source.ADMIN && plugin.getConfig().getBoolean("progression.admin-grants-count-for-level", true);
        Profile prof = plugin.profiles().get(p);
        long today = LocalDate.now().toEpochDay();
        if (prof.dailyDay() != today) {
            prof.daily(today, 0);
        }
        long give = amount;
        if (capped) {
            long cap = plugin.getConfig().getLong("progression.daily-cap", 400);
            give = Math.max(0, Math.min(amount, cap - prof.dailyEarned()));
            if (give == 0) {
                return;
            }
            prof.daily(today, prof.dailyEarned() + give);
        }
        int before = level(prof.lifetime());
        prof.lustre(prof.lustre() + give);
        if (countsForLevel) {
            prof.lifetime(prof.lifetime() + give);
        }
        plugin.profiles().save(prof);
        plugin.lang().sendActionBar(p, "lustre.gained", Text.p("amount", give), Text.p("why", why),
                Text.p("total", prof.lustre()));
        plugin.fx().playFor(p, "lustre-gain");
        int after = level(prof.lifetime());
        if (after > before) {
            levelUp(p, before, after);
        }
    }

    public boolean spend(Player p, long amount) {
        Profile prof = plugin.profiles().get(p);
        if (prof.lustre() < amount) {
            return false;
        }
        prof.lustre(prof.lustre() - amount);
        plugin.profiles().save(prof);
        return true;
    }

    // ------------------------------------------------------------------ levels

    public List<Long> thresholds() {
        List<Long> out = new ArrayList<>();
        for (Object o : plugin.getConfig().getList("progression.levels", List.of(0))) {
            out.add(((Number) o).longValue());
        }
        return out;
    }

    /** Clarity level (1-based) for a lifetime total. */
    public int level(long lifetime) {
        List<Long> t = thresholds();
        int lvl = 1;
        for (int i = 0; i < t.size(); i++) {
            if (lifetime >= t.get(i)) {
                lvl = i + 1;
            }
        }
        return lvl;
    }

    public int level(Player p) {
        return level(plugin.profiles().get(p).lifetime());
    }

    public int maxLevel() {
        return thresholds().size();
    }

    /** Lustre still needed for the next level, or -1 at max. */
    public long toNext(long lifetime) {
        List<Long> t = thresholds();
        int lvl = level(lifetime);
        return lvl >= t.size() ? -1 : t.get(lvl) - lifetime;
    }

    /** 10-segment progress bar toward the next level. */
    public String bar(long lifetime) {
        List<Long> t = thresholds();
        int lvl = level(lifetime);
        if (lvl >= t.size()) {
            return "<pink>" + "◆".repeat(10);
        }
        long from = t.get(lvl - 1), to = t.get(lvl);
        int filled = (int) Math.round(10.0 * (lifetime - from) / Math.max(1, to - from));
        return "<pink>" + "◆".repeat(filled) + "<muted>" + "◇".repeat(10 - filled);
    }

    public String clarity(int level) {
        return "Clarity " + ToolService.roman(Math.min(level, 5)) + (level > 5 ? "+" + ToolService.roman(level - 5) : "");
    }

    private void levelUp(Player p, int from, int to) {
        TagResolver r = TagResolver.resolver(Text.p("level", to), Text.p("clarity", clarity(to)), Text.p("player", p.getName()));
        plugin.lang().send(p, "lustre.level-up", r);
        plugin.lang().send(Bukkit.getOnlinePlayers().stream().filter(o -> o.hasPermission("shardwatch.hum") && o != p).toList(),
                "lustre.level-up-staff", r);
        plugin.animations().levelUp(p);
        plugin.fx().play("lustre-levelup", p.getLocation().add(0, 1, 0));
        for (int lvl = from + 1; lvl <= to; lvl++) {
            int shards = plugin.getConfig().getInt("progression.level-rewards." + lvl + ".shards", 0);
            if (shards > 0) {
                ItemStack s = plugin.tools().create(StaffTool.LUSTRE_SHARD, 1);
                s.setAmount(Math.min(64, shards));
                p.getInventory().addItem(s).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
            }
            for (String k : plugin.getConfig().getStringList("progression.level-rewards." + lvl + ".keepsakes")) {
                Keepsake ks = keepsake(k);
                if (ks != null && plugin.profiles().get(p).keepsakes().add(ks.id())) {
                    plugin.lang().send(p, "lustre.keepsake-unlocked", Text.pp("keepsake", ks.name()));
                }
            }
        }
        plugin.profiles().save(plugin.profiles().get(p));
    }

    // ------------------------------------------------------------------ refinements

    /** Next tier's cost and required level for a tool, or null at max tier. */
    public long[] nextTier(Player p, StaffTool tool) {
        int tier = plugin.profiles().get(p).tier(tool.id());
        if (tier >= 3) {
            return null;
        }
        String base = "progression.refinements." + tool.id() + ".tier-" + (tier + 1);
        return new long[]{tier + 1, plugin.getConfig().getLong(base + ".cost", 200L * tier),
                plugin.getConfig().getLong(base + ".level", tier * 3L)};
    }

    /** Upgrades a tool one tier if the player can afford it; swaps the items they carry. */
    public boolean refine(Player p, StaffTool tool) {
        long[] next = nextTier(p, tool);
        if (next == null) {
            plugin.lang().send(p, "lustre.refine-max");
            return false;
        }
        if (level(p) < next[2]) {
            plugin.lang().send(p, "lustre.refine-level", Text.p("level", next[2]));
            plugin.fx().playFor(p, "ui-deny");
            return false;
        }
        if (!spend(p, next[1])) {
            plugin.lang().send(p, "lustre.refine-cost", Text.p("cost", next[1]));
            plugin.fx().playFor(p, "ui-deny");
            return false;
        }
        Profile prof = plugin.profiles().get(p);
        prof.refinements().put(tool.id(), (int) next[0]);
        plugin.profiles().save(prof);
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (plugin.tools().toolOf(contents[i]) == tool) {
                p.getInventory().setItem(i, plugin.tools().create(tool, (int) next[0]));
            }
        }
        plugin.lang().send(p, "lustre.refined", Text.p("tool", tool.id()), Text.p("tier", ToolService.roman((int) next[0])));
        plugin.fx().play("refine-upgrade", p.getLocation().add(0, 1, 0));
        plugin.animations().refine(p);
        plugin.action(p, StaffAction.REFINE, p.getName(), tool.id() + " -> tier " + next[0]);
        return true;
    }

    /** A per-tier number from {@code progression.refinements.<tool>.<key>: [t1, t2, t3]}. */
    public int bonus(Player p, String tool, String key) {
        if (!enabled()) {
            return 0;
        }
        List<Integer> values = plugin.getConfig().getIntegerList("progression.refinements." + tool + "." + key);
        int tier = plugin.profiles().get(p).tier(tool);
        return values.isEmpty() ? 0 : values.get(Math.min(values.size(), tier) - 1);
    }

    // ------------------------------------------------------------------ keepsakes

    public List<Keepsake> keepsakes() {
        List<Keepsake> out = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("progression.keepsakes");
        if (sec == null) {
            return out;
        }
        for (String id : sec.getKeys(false)) {
            Keepsake k = keepsake(id);
            if (k != null) {
                out.add(k);
            }
        }
        return out;
    }

    public Keepsake keepsake(String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("progression.keepsakes." + id);
        if (s == null) {
            return null;
        }
        return new Keepsake(id, s.getString("type", "aura"), s.getString("name", id), s.getString("icon", "keepsake"),
                s.getInt("level", 1), s.getLong("cost", 0), s.getStringList("lore"));
    }

    /** Claims (buys) a keepsake, or toggles it on/off if already owned. */
    public void useKeepsake(Player p, Keepsake k) {
        Profile prof = plugin.profiles().get(p);
        if (!prof.keepsakes().contains(k.id())) {
            if (level(p) < k.level()) {
                plugin.lang().send(p, "lustre.keepsake-level", Text.p("level", k.level()));
                plugin.fx().playFor(p, "ui-deny");
                return;
            }
            if (!spend(p, k.cost())) {
                plugin.lang().send(p, "lustre.refine-cost", Text.p("cost", k.cost()));
                plugin.fx().playFor(p, "ui-deny");
                return;
            }
            prof.keepsakes().add(k.id());
            plugin.lang().send(p, "lustre.keepsake-claimed", Text.pp("keepsake", k.name()));
            plugin.fx().play("keepsake-claim", p.getLocation().add(0, 1, 0));
            plugin.action(p, StaffAction.KEEPSAKE_CLAIM, p.getName(), k.id());
            if (k.type().equals("shards")) {
                prof.keepsakes().remove(k.id()); // bundles can be bought again
                ItemStack s = plugin.tools().create(StaffTool.LUSTRE_SHARD, 1);
                s.setAmount(Math.max(1, Math.min(64, plugin.getConfig().getInt("progression.keepsakes." + k.id() + ".amount", 5))));
                p.getInventory().addItem(s);
            }
        } else if (k.type().equals("aura")) {
            prof.activeAura(k.id().equals(prof.activeAura()) ? null : k.id());
            plugin.lang().send(p, prof.activeAura() == null ? "lustre.aura-off" : "lustre.aura-on", Text.pp("keepsake", k.name()));
        } else if (k.type().equals("sigil")) {
            prof.activeSigil(k.id().equals(prof.activeSigil()) ? null : k.id());
            plugin.lang().send(p, prof.activeSigil() == null ? "lustre.sigil-off" : "lustre.sigil-on", Text.pp("keepsake", k.name()));
        }
        plugin.profiles().save(prof);
    }

    // ------------------------------------------------------------------ Lustre Shards

    @EventHandler(priority = EventPriority.HIGH)
    public void onRedeem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        ItemStack item = event.getItem();
        if (plugin.tools().toolOf(item) != StaffTool.LUSTRE_SHARD) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (!enabled() || !p.hasPermission("shardwatch.staff")) {
            plugin.lang().send(p, "lustre.shard-staff-only");
            return;
        }
        int value = plugin.getConfig().getInt("progression.shard-value", 25);
        int count = p.isSneaking() ? item.getAmount() : 1;
        item.setAmount(item.getAmount() - count);
        plugin.fx().play("lustre-pickup", p.getLocation().add(0, 1, 0));
        plugin.animations().shardBurst(p);
        grant(p, (long) value * count, "lustre shard", Source.SHARD);
    }
}
