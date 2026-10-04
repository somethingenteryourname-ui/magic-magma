package dev.customtrims.trim;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.effect.AuraShape;
import dev.customtrims.effect.EffectStyle;
import dev.customtrims.effect.ParticleStyle;
import dev.customtrims.effect.TrailShape;
import dev.customtrims.util.ColorUtil;
import dev.customtrims.util.Enums;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads/writes custom trims on armor items and holds presets and custom materials. */
public final class TrimManager {

    public static final String NAMESPACE = "customtrims";

    public static final List<String> VANILLA_PATTERNS = List.of(
            "bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry",
            "shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");

    /** Brand new trim patterns added by this plugin's data pack + resource pack. */
    public static final List<String> CUSTOM_PATTERNS = List.of(
            "nova", "circuit", "scales", "blaze", "rune", "fracture", "spiral", "aurora",
            "lightning", "vines", "constellation", "honeycomb", "chains", "crystal", "feathers", "web",
            "thorns", "tiger", "leopard", "camo", "heartbeat", "matrix", "sun", "moon",
            "skull", "hearts", "plaid", "ripple");

    public static final List<String> VANILLA_MATERIALS = List.of(
            "amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis",
            "netherite", "quartz", "redstone", "resin");

    private final CustomTrimsPlugin plugin;
    private final NamespacedKey key;
    private final Map<String, TrimSettings> presets = new LinkedHashMap<>();
    /** Custom trim materials from config: name -> colors (blended into the trim on the armor). */
    private final Map<String, List<Color>> customMaterials = new LinkedHashMap<>();
    private int minPieces = 1;
    private double maxSize = 3.0;
    private double maxDensity = 3.0;

    public TrimManager(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "trim");
        reload();
    }

    // ------------------------------------------------------------------ config

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        minPieces = Math.max(1, Math.min(4, c.getInt("min-armor-pieces", 1)));
        maxSize = Math.max(0.5, c.getDouble("max-size", 3.0));
        maxDensity = Math.max(0.5, c.getDouble("max-density", 3.0));
        TrimSettings.defaultStyle = Enums.parse(EffectStyle.class, c.getString("default-style"), EffectStyle.LIQUID);

        customMaterials.clear();
        ConfigurationSection mats = c.getConfigurationSection("custom-materials");
        if (mats != null) {
            for (String raw : mats.getKeys(false)) {
                String id = cleanId(raw);
                if (id.isEmpty() || VANILLA_MATERIALS.contains(id)) {
                    plugin.getLogger().warning("Skipping custom material '" + raw + "' (bad or vanilla name).");
                    continue;
                }
                List<Color> colors = parseColors(mats.getStringList(raw));
                if (colors.isEmpty()) {
                    plugin.getLogger().warning("Custom material '" + raw + "' has no valid colors.");
                    continue;
                }
                customMaterials.put(id, colors);
            }
        }

        presets.clear();
        ConfigurationSection sec = c.getConfigurationSection("presets");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection ps = sec.getConfigurationSection(id);
            if (ps != null) presets.put(id.toLowerCase(Locale.ROOT), fromConfig(id, ps));
        }
    }

    public static String cleanId(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    public static List<Color> parseColors(List<String> raw) {
        List<Color> out = new ArrayList<>();
        for (String r : raw) {
            Color c = ColorUtil.parse(r);
            if (c != null && out.size() < TrimSettings.MAX_COLORS) out.add(c);
        }
        return out;
    }

    private TrimSettings fromConfig(String id, ConfigurationSection c) {
        TrimSettings s = new TrimSettings();
        s.name = TrimSettings.cleanName(c.getString("name", id));
        s.pattern = c.getString("pattern", s.pattern).toLowerCase(Locale.ROOT);
        s.material = c.getString("material", s.material).toLowerCase(Locale.ROOT);
        s.setColors(parseColors(c.getStringList("colors")));
        s.trailParticle = Enums.parse(ParticleStyle.class, c.getString("trail"), s.trailParticle);
        s.trailShape = Enums.parse(TrailShape.class, c.getString("trail-shape"), s.trailShape);
        s.auraShape = Enums.parse(AuraShape.class, c.getString("aura"), s.auraShape);
        s.auraParticle = Enums.parse(ParticleStyle.class, c.getString("aura-particle"), s.auraParticle);
        s.size = clampSize(c.getDouble("size", 1.0));
        s.density = clampDensity(c.getDouble("density", 1.0));
        s.glint = c.getBoolean("glint", true);
        s.style = Enums.parse(EffectStyle.class, c.getString("style"), TrimSettings.defaultStyle);
        return s;
    }

    public Map<String, TrimSettings> getPresets() {
        return Collections.unmodifiableMap(presets);
    }

    public TrimSettings getPreset(String id) {
        TrimSettings s = presets.get(id.toLowerCase(Locale.ROOT));
        return s == null ? null : s.copy();
    }

    public Map<String, List<Color>> getCustomMaterials() {
        return Collections.unmodifiableMap(customMaterials);
    }

    public double clampSize(double v) {
        return Math.max(0.3, Math.min(maxSize, v));
    }

    public double clampDensity(double v) {
        return Math.max(0.25, Math.min(maxDensity, v));
    }

    public double getMaxSize() {
        return maxSize;
    }

    public double getMaxDensity() {
        return maxDensity;
    }

    // ------------------------------------------------------- patterns/materials

    public List<String> allPatterns() {
        List<String> out = new ArrayList<>(CUSTOM_PATTERNS);
        out.addAll(VANILLA_PATTERNS);
        return out;
    }

    public List<String> allMaterials() {
        List<String> out = new ArrayList<>(customMaterials.keySet());
        out.addAll(VANILLA_MATERIALS);
        return out;
    }

    public boolean isKnownPattern(String name) {
        return name.equals("none") || CUSTOM_PATTERNS.contains(name) || VANILLA_PATTERNS.contains(name);
    }

    public boolean isKnownMaterial(String name) {
        return customMaterials.containsKey(name) || VANILLA_MATERIALS.contains(name);
    }

    private static NamespacedKey patternKey(String name) {
        return CUSTOM_PATTERNS.contains(name) ? new NamespacedKey(NAMESPACE, name) : NamespacedKey.minecraft(name);
    }

    private NamespacedKey materialKey(String name) {
        return customMaterials.containsKey(name) ? new NamespacedKey(NAMESPACE, name) : NamespacedKey.minecraft(name);
    }

    /** Null if the pattern isn't loaded (a new one needs one server restart). */
    public TrimPattern resolvePattern(String name) {
        if (name == null || name.equals("none")) return null;
        try {
            Registry<TrimPattern> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
            return reg.get(patternKey(name));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public TrimMaterial resolveMaterial(String name) {
        if (name == null) return null;
        try {
            Registry<TrimMaterial> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
            return reg.get(materialKey(name));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True if both the pattern and material are loaded on the server right now. */
    public boolean isLoaded(TrimSettings s) {
        return s.pattern.equals("none") || (resolvePattern(s.pattern) != null && resolveMaterial(s.material) != null);
    }

    // ------------------------------------------------------------------ items

    public TrimSettings read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return raw == null ? null : TrimSettings.deserialize(raw);
    }

    /**
     * @param height 0 = helmet ... 1 = boots. Leather armor is dyed along your colors by height,
     *               so a multi-color trim fades from helmet to boots.
     */
    public void apply(ItemStack item, TrimSettings s, double height) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, s.serialize());

        if (meta instanceof ArmorMeta armorMeta) {
            TrimPattern pattern = resolvePattern(s.pattern);
            TrimMaterial material = resolveMaterial(s.material);
            armorMeta.setTrim(pattern != null && material != null ? new ArmorTrim(material, pattern) : null);
        }
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(s.sample(height));
        }
        meta.setEnchantmentGlintOverride(s.glint ? Boolean.TRUE : null);

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(TrimManager::isOurLore);
        lore.add("\u00A78\u2726 " + ColorUtil.gradient(s.name, s.colors) + " \u00A78Trim");
        lore.add("\u00A78\u25C7 \u00A77Look: \u00A7f" + Enums.prettyId(s.pattern) + " \u00A78(" + Enums.prettyId(s.material) + ")");
        lore.add("\u00A78\u25C7 \u00A77Trail: \u00A7f" + Enums.pretty(s.trailParticle) + " \u00A78(" + Enums.pretty(s.trailShape) + ")");
        lore.add("\u00A78\u25C7 \u00A77Aura: \u00A7f" + Enums.pretty(s.auraShape) + " \u00A78(" + Enums.pretty(s.auraParticle) + ")");
        lore.add("\u00A78\u25C7 \u00A77Style: \u00A7f" + Enums.pretty(s.style));
        lore.add("\u00A78\u25C7 \u00A77Colors: " + ColorUtil.swatches(s.colors));
        meta.setLore(lore);

        item.setItemMeta(meta);
    }

    public void clear(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(key);
        if (meta instanceof ArmorMeta armorMeta) armorMeta.setTrim(null);
        meta.setEnchantmentGlintOverride(null);
        if (meta.hasLore() && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(TrimManager::isOurLore);
            meta.setLore(lore.isEmpty() ? null : lore);
        }
        item.setItemMeta(meta);
    }

    private static boolean isOurLore(String line) {
        String plain = ColorUtil.strip(line);
        return (plain.startsWith("\u2726 ") && plain.endsWith(" Trim")) || plain.startsWith("\u25C7 ");
    }

    // ------------------------------------------------------------------ players

    /** The trim that should currently show effects, or null. */
    public TrimSettings getActive(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] order = {inv.getChestplate(), inv.getLeggings(), inv.getHelmet(), inv.getBoots()};
        TrimSettings first = null;
        int count = 0;
        for (ItemStack it : order) {
            TrimSettings s = read(it);
            if (s != null) {
                count++;
                if (first == null) first = s;
            }
        }
        return count >= minPieces ? first : null;
    }

    /** The trim on any worn piece (or the held item), ignoring min-armor-pieces. */
    public TrimSettings current(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] order = {inv.getChestplate(), inv.getLeggings(), inv.getHelmet(), inv.getBoots(), inv.getItemInMainHand()};
        for (ItemStack it : order) {
            TrimSettings s = read(it);
            if (s != null) return s;
        }
        return null;
    }

    public TrimSettings currentOrDefault(Player p) {
        TrimSettings s = current(p);
        if (s != null) return s;
        TrimSettings def = getPreset("default");
        return def != null ? def : new TrimSettings();
    }

    /** Applies settings to every worn armor piece (or the held armor piece if none are worn). */
    public int applyToWorn(Player p, TrimSettings s) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        ItemStack it = inv.getHelmet();
        if (notEmpty(it)) { apply(it, s, 0.0); inv.setHelmet(it); n++; }
        it = inv.getChestplate();
        if (notEmpty(it)) { apply(it, s, 1.0 / 3); inv.setChestplate(it); n++; }
        it = inv.getLeggings();
        if (notEmpty(it)) { apply(it, s, 2.0 / 3); inv.setLeggings(it); n++; }
        it = inv.getBoots();
        if (notEmpty(it)) { apply(it, s, 1.0); inv.setBoots(it); n++; }
        if (n == 0) {
            it = inv.getItemInMainHand();
            if (isArmorPiece(it)) { apply(it, s, heightOf(it.getType())); inv.setItemInMainHand(it); n++; }
        }
        return n;
    }

    public int clearWorn(Player p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        ItemStack it = inv.getHelmet();
        if (read(it) != null) { clear(it); inv.setHelmet(it); n++; }
        it = inv.getChestplate();
        if (read(it) != null) { clear(it); inv.setChestplate(it); n++; }
        it = inv.getLeggings();
        if (read(it) != null) { clear(it); inv.setLeggings(it); n++; }
        it = inv.getBoots();
        if (read(it) != null) { clear(it); inv.setBoots(it); n++; }
        it = inv.getItemInMainHand();
        if (read(it) != null) { clear(it); inv.setItemInMainHand(it); n++; }
        return n;
    }

    public static double heightOf(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return 0.0;
        if (n.endsWith("_CHESTPLATE") || m == Material.ELYTRA) return 1.0 / 3;
        if (n.endsWith("_LEGGINGS")) return 2.0 / 3;
        return 1.0;
    }

    private static boolean notEmpty(ItemStack it) {
        return it != null && !it.getType().isAir();
    }

    public static boolean isArmorPiece(ItemStack it) {
        if (it == null) return false;
        Material m = it.getType();
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || m == Material.ELYTRA;
    }
}
