package dev.crystalline.resonance.crystal;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.util.Effects;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Crystal items, drop tables and chest loot, read from the {@code crystals} section. */
public final class CrystalSettings {

    public record CrystalDefinition(
            CrystalType type,
            Material material,
            String name,
            List<String> lore,
            Color color,
            int lootWeight,
            Map<Material, Double> drops
    ) {
    }

    private final CrystallineResonance plugin;
    private final Map<CrystalType, CrystalDefinition> definitions = new EnumMap<>(CrystalType.class);
    private Set<Material> droppingBlocks = EnumSet.noneOf(Material.class);

    private boolean requireNether;
    private boolean silkTouchDrops;
    private boolean ignorePlacedBlocks;
    private double fortuneBonus;
    private double globalMultiplier;

    private boolean lootEnabled;
    private double lootChance;
    private int lootMin;
    private int lootMax;
    private Set<String> lootTables = Set.of();

    public CrystalSettings(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        requireNether = config.getBoolean("crystals.require-nether", true);
        silkTouchDrops = config.getBoolean("crystals.silk-touch-drops", false);
        ignorePlacedBlocks = config.getBoolean("crystals.ignore-placed-blocks", true);
        fortuneBonus = Math.max(0.0, config.getDouble("crystals.fortune-bonus", 0.25));
        globalMultiplier = Math.max(0.0, config.getDouble("crystals.global-multiplier", 1.0));

        lootEnabled = config.getBoolean("crystals.loot.enabled", true);
        lootChance = clamp01(config.getDouble("crystals.loot.chance", 0.35));
        lootMin = Math.max(0, config.getInt("crystals.loot.min", 1));
        lootMax = Math.max(lootMin, config.getInt("crystals.loot.max", 3));
        Set<String> tables = new HashSet<>();
        for (String table : config.getStringList("crystals.loot.tables")) {
            tables.add(stripNamespace(table.toLowerCase(Locale.ROOT)));
        }
        lootTables = Collections.unmodifiableSet(tables);

        definitions.clear();
        Set<Material> blocks = EnumSet.noneOf(Material.class);
        for (CrystalType type : CrystalType.values()) {
            String path = "crystals.types." + type.id();
            ConfigurationSection section = config.getConfigurationSection(path);
            if (section == null) {
                logger.warning("Missing config section " + path + "; using defaults.");
                section = config.createSection(path);
            }

            Material material = Material.matchMaterial(section.getString("material", type.defaultMaterial().name()));
            if (material == null || !material.isItem() || material.isAir()) {
                logger.warning("Invalid material at " + path + ".material; using " + type.defaultMaterial() + ".");
                material = type.defaultMaterial();
            }

            Map<Material, Double> drops = new EnumMap<>(Material.class);
            ConfigurationSection dropSection = section.getConfigurationSection("drops");
            if (dropSection != null) {
                for (String blockName : dropSection.getKeys(false)) {
                    Material block = Material.matchMaterial(blockName);
                    if (block == null || !block.isBlock()) {
                        logger.warning("Unknown block '" + blockName + "' at " + path + ".drops");
                        continue;
                    }
                    double chance = clamp01(dropSection.getDouble(blockName));
                    if (chance > 0) {
                        drops.put(block, chance);
                        blocks.add(block);
                    }
                }
            }

            definitions.put(type, new CrystalDefinition(
                    type,
                    material,
                    section.getString("name", type.id()),
                    section.getStringList("lore"),
                    Effects.parseColor(section.getString("color"), Color.WHITE),
                    Math.max(0, section.getInt("loot-weight", 1)),
                    Collections.unmodifiableMap(drops)
            ));
        }
        droppingBlocks = blocks;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String stripNamespace(String key) {
        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
    }

    public CrystalDefinition definition(CrystalType type) {
        return definitions.get(type);
    }

    /** Whether any crystal can drop from this block type. */
    public boolean canDropFrom(Material block) {
        return droppingBlocks.contains(block);
    }

    public boolean requireNether() {
        return requireNether;
    }

    public boolean silkTouchDrops() {
        return silkTouchDrops;
    }

    public boolean ignorePlacedBlocks() {
        return ignorePlacedBlocks;
    }

    public double fortuneBonus() {
        return fortuneBonus;
    }

    public double globalMultiplier() {
        return globalMultiplier;
    }

    public boolean lootEnabled() {
        return lootEnabled;
    }

    public double lootChance() {
        return lootChance;
    }

    public int lootMin() {
        return lootMin;
    }

    public int lootMax() {
        return lootMax;
    }

    /** Whether crystals may be added to the given loot table (key without namespace). */
    public boolean lootTableAllowed(String tableKey) {
        String key = stripNamespace(tableKey.toLowerCase(Locale.ROOT));
        return lootTables.isEmpty() ? key.startsWith("chests/") : lootTables.contains(key);
    }
}
