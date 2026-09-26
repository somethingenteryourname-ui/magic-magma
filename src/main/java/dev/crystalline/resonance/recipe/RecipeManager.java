package dev.crystalline.resonance.recipe;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.crystal.CrystalType;
import dev.crystalline.resonance.spell.SpellType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Registers the configurable spell tome recipes. */
public final class RecipeManager implements Listener {

    private static final String CRYSTAL_PREFIX = "crystal:";

    /** A registered tome recipe and the number of each crystal it needs. */
    public record TomeRecipe(SpellType spell, Map<CrystalType, Integer> crystals) {
    }

    private final CrystallineResonance plugin;
    private final Map<NamespacedKey, TomeRecipe> registered = new HashMap<>();

    public RecipeManager(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        for (SpellType spell : SpellType.values()) {
            register(spell);
        }
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered.keySet()) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }

    /** The tome recipe with this key, or {@code null} if it isn't one of ours. */
    public TomeRecipe recipe(NamespacedKey key) {
        return registered.get(key);
    }

    public void discoverForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            discover(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discover(event.getPlayer());
    }

    private void discover(Player player) {
        for (Map.Entry<NamespacedKey, TomeRecipe> entry : registered.entrySet()) {
            if (player.hasPermission(entry.getValue().spell().craftPermission())) {
                player.discoverRecipe(entry.getKey());
            }
        }
    }

    private void register(SpellType spell) {
        Logger logger = plugin.getLogger();
        String path = "spells." + spell.id() + ".recipe";
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty() || shape.size() > 3) {
            logger.warning(path + ".shape must have 1 to 3 rows; recipe skipped.");
            return;
        }
        int width = shape.get(0).length();
        for (String row : shape) {
            if (row.isEmpty() || row.length() > 3 || row.length() != width) {
                logger.warning(path + ".shape rows must all be 1 to 3 characters and the same length; recipe skipped.");
                return;
            }
        }

        NamespacedKey key = new NamespacedKey(plugin, "tome_" + spell.id());
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.items().createTome(spell));
        recipe.shape(shape.toArray(String[]::new));
        recipe.setCategory(CraftingBookCategory.MISC);

        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        Map<CrystalType, Integer> crystals = new EnumMap<>(CrystalType.class);
        String symbols = String.join("", shape);
        for (char symbol : symbols.replace(" ", "").chars().distinct().mapToObj(c -> (char) c).toList()) {
            String spec = ingredients != null ? ingredients.getString(String.valueOf(symbol)) : null;
            if (spec == null) {
                logger.warning(path + ".ingredients has no entry for '" + symbol + "'; recipe skipped.");
                return;
            }
            Material material;
            if (spec.toLowerCase(Locale.ROOT).startsWith(CRYSTAL_PREFIX)) {
                CrystalType crystal = CrystalType.fromId(spec.substring(CRYSTAL_PREFIX.length()));
                if (crystal == null) {
                    logger.warning("Unknown crystal '" + spec + "' in " + path + "; recipe skipped.");
                    return;
                }
                material = plugin.crystalSettings().definition(crystal).material();
                int uses = (int) symbols.chars().filter(c -> c == symbol).count();
                crystals.merge(crystal, uses, Integer::sum);
            } else {
                material = Material.matchMaterial(spec);
                if (material == null || !material.isItem() || material.isAir()) {
                    logger.warning("Unknown material '" + spec + "' in " + path + "; recipe skipped.");
                    return;
                }
            }
            // Crystals match on their base material here; ItemProtectionListener checks they are real crystals.
            recipe.setIngredient(symbol, new RecipeChoice.MaterialChoice(material));
        }

        if (!Bukkit.addRecipe(recipe)) {
            logger.warning("Could not register recipe " + key + ".");
            return;
        }
        registered.put(key, new TomeRecipe(spell, Collections.unmodifiableMap(crystals)));
    }
}
