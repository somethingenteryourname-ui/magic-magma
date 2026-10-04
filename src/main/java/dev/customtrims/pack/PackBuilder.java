package dev.customtrims.pack;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.trim.TrimManager;
import dev.customtrims.util.ColorUtil;
import dev.customtrims.util.Enums;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the two packs that make new trims show up on armor:
 * - a data pack (in the main world's datapacks folder) that registers the new patterns + materials
 * - a resource pack (sent to players) with the textures and color palettes
 */
public final class PackBuilder {

    private static final int DATA_PACK_FORMAT = 94;     // Minecraft 1.21.11
    private static final int RESOURCE_PACK_FORMAT = 75; // Minecraft 1.21.11

    private static final List<String> VANILLA_PALETTES = List.of(
            "amethyst", "copper", "copper_darker", "diamond", "diamond_darker", "emerald", "gold", "gold_darker",
            "iron", "iron_darker", "lapis", "netherite", "netherite_darker", "quartz", "redstone", "resin");

    private final CustomTrimsPlugin plugin;
    private byte[] resourcePack;
    private byte[] sha1;
    private String sha1Hex = "";
    private boolean restartNeeded;

    public PackBuilder(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    public byte[] getResourcePack() {
        return resourcePack;
    }

    public byte[] getSha1() {
        return sha1;
    }

    public String getSha1Hex() {
        return sha1Hex;
    }

    /** True if the data pack changed and the server must restart once for it to load. */
    public boolean isRestartNeeded() {
        return restartNeeded;
    }

    public void build() {
        TrimManager tm = plugin.getTrimManager();
        try {
            if (writeDataPack(tm)) restartNeeded = true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write the data pack: " + e.getMessage());
        }
        try {
            resourcePack = buildResourcePack(tm);
            sha1 = MessageDigest.getInstance("SHA-1").digest(resourcePack);
            StringBuilder sb = new StringBuilder();
            for (byte b : sha1) sb.append(String.format("%02x", b));
            sha1Hex = sb.toString();
            File out = new File(plugin.getDataFolder(), "CustomTrims-ResourcePack.zip");
            Files.write(out.toPath(), resourcePack);
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Could not build the resource pack: " + e.getMessage());
        }
        if (restartNeeded) {
            plugin.getLogger().warning("==============================================================");
            plugin.getLogger().warning(" CustomTrims installed/updated its data pack.");
            plugin.getLogger().warning(" RESTART the server once so the new trims and materials load!");
            plugin.getLogger().warning("==============================================================");
        }
    }

    // ================================================================ data pack

    /** @return true if anything was written (changed). */
    private boolean writeDataPack(TrimManager tm) throws IOException {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) throw new IOException("no world loaded");
        File root = new File(new File(worlds.get(0).getWorldFolder(), "datapacks"), "CustomTrims");

        Map<String, String> files = new TreeMap<>();
        files.put("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"CustomTrims: new armor trims\",\n"
                + "    \"min_format\": " + DATA_PACK_FORMAT + ",\n    \"max_format\": " + DATA_PACK_FORMAT + "\n  }\n}\n");

        for (String p : TrimManager.CUSTOM_PATTERNS) {
            files.put("data/" + TrimManager.NAMESPACE + "/trim_pattern/" + p + ".json",
                    "{\n  \"asset_id\": \"" + TrimManager.NAMESPACE + ":" + p + "\",\n  \"decal\": false,\n"
                            + "  \"description\": { \"text\": \"" + Enums.prettyId(p) + " Armor Trim\" }\n}\n");
        }
        for (Map.Entry<String, List<Color>> e : tm.getCustomMaterials().entrySet()) {
            String id = e.getKey();
            Color mid = ColorUtil.sample(e.getValue(), 0.5);
            files.put("data/" + TrimManager.NAMESPACE + "/trim_material/" + id + ".json",
                    "{\n  \"asset_name\": \"" + assetName(id) + "\",\n"
                            + "  \"description\": { \"text\": \"" + Enums.prettyId(id) + " Material\", \"color\": \""
                            + ColorUtil.hex(mid) + "\" }\n}\n");
        }

        // Note: old material files are never deleted on purpose. Removing a material that
        // players' armor still uses could break those items.
        boolean changed = false;
        for (Map.Entry<String, String> e : files.entrySet()) {
            File f = new File(root, e.getKey());
            byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
            if (f.exists() && Arrays.equals(Files.readAllBytes(f.toPath()), bytes)) continue;
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("can't create " + parent);
            Files.write(f.toPath(), bytes);
            changed = true;
        }
        return changed;
    }

    static String assetName(String materialId) {
        return "ct_" + materialId;
    }

    // ============================================================ resource pack

    private byte[] buildResourcePack(TrimManager tm) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        String ns = TrimManager.NAMESPACE;

        files.put("pack.mcmeta", ("{\n  \"pack\": {\n    \"description\": \"CustomTrims armor trims\",\n"
                + "    \"min_format\": " + RESOURCE_PACK_FORMAT + ",\n    \"max_format\": " + RESOURCE_PACK_FORMAT + "\n  }\n}\n")
                .getBytes(StandardCharsets.UTF_8));

        // pattern textures (grayscale, recolored by the game for each material)
        for (String p : TrimManager.CUSTOM_PATTERNS) {
            files.put("assets/" + ns + "/textures/trims/entity/humanoid/" + p + ".png", resource("pack/patterns/" + p + "_humanoid.png"));
            files.put("assets/" + ns + "/textures/trims/entity/humanoid_leggings/" + p + ".png", resource("pack/patterns/" + p + "_humanoid_leggings.png"));
        }

        // color palettes for custom materials
        for (Map.Entry<String, List<Color>> e : tm.getCustomMaterials().entrySet()) {
            files.put("assets/" + ns + "/textures/trims/color_palettes/" + e.getKey() + ".png", palettePng(e.getValue()));
        }

        files.put("assets/minecraft/atlases/armor_trims.json", atlasJson(tm).getBytes(StandardCharsets.UTF_8));

        // liquid aura/trail models and animated textures
        String index = new String(resource("pack/static/index.txt"), StandardCharsets.UTF_8);
        for (String line : index.split("\\R")) {
            String path = line.trim();
            if (!path.isEmpty()) files.put(path, resource("pack/static/" + path));
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            FileTime fixed = FileTime.fromMillis(315532800000L); // fixed time so the pack hash stays the same
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setLastModifiedTime(fixed);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String atlasJson(TrimManager tm) {
        String ns = TrimManager.NAMESPACE;
        List<String> customPerms = new ArrayList<>();
        for (String id : tm.getCustomMaterials().keySet()) {
            customPerms.add("\"" + assetName(id) + "\": \"" + ns + ":trims/color_palettes/" + id + "\"");
        }
        List<String> vanillaPerms = new ArrayList<>();
        for (String v : VANILLA_PALETTES) {
            vanillaPerms.add("\"" + v + "\": \"minecraft:trims/color_palettes/" + v + "\"");
        }

        List<String> customTextures = new ArrayList<>();
        for (String p : TrimManager.CUSTOM_PATTERNS) {
            customTextures.add("\"" + ns + ":trims/entity/humanoid/" + p + "\"");
            customTextures.add("\"" + ns + ":trims/entity/humanoid_leggings/" + p + "\"");
        }
        List<String> vanillaTextures = new ArrayList<>();
        for (String p : TrimManager.VANILLA_PATTERNS) {
            vanillaTextures.add("\"minecraft:trims/entity/humanoid/" + p + "\"");
            vanillaTextures.add("\"minecraft:trims/entity/humanoid_leggings/" + p + "\"");
        }

        List<String> allPerms = new ArrayList<>(vanillaPerms);
        allPerms.addAll(customPerms);

        StringBuilder sb = new StringBuilder("{\n  \"sources\": [\n");
        // new patterns x every material
        sb.append(source(customTextures, allPerms));
        // vanilla patterns x the new materials
        if (!customPerms.isEmpty()) sb.append(",\n").append(source(vanillaTextures, customPerms));
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String source(List<String> textures, List<String> perms) {
        return "    {\n      \"type\": \"minecraft:paletted_permutations\",\n"
                + "      \"palette_key\": \"minecraft:trims/color_palettes/trim_palette\",\n"
                + "      \"permutations\": {\n        " + String.join(",\n        ", perms) + "\n      },\n"
                + "      \"textures\": [\n        " + String.join(",\n        ", textures) + "\n      ]\n    }";
    }

    /**
     * An 8-pixel palette. The new trim textures shade from slot 0 (top of the body) to slot 4
     * (feet), so several colors become a smooth blend down the armor. Slots 5-7 are shadows.
     */
    private static byte[] palettePng(List<Color> colors) throws IOException {
        Color[] slots = new Color[8];
        if (colors.size() == 1) {
            Color c = colors.get(0);
            slots[0] = ColorUtil.lerp(c, Color.WHITE, 0.35);
            double[] f = {1.0, 0.86, 0.74, 0.62, 0.5, 0.4, 0.3};
            for (int i = 1; i < 8; i++) slots[i] = ColorUtil.darken(c, f[i - 1]);
        } else {
            for (int i = 0; i < 5; i++) slots[i] = ColorUtil.sample(colors, i / 4.0);
            for (int i = 5; i < 8; i++) slots[i] = ColorUtil.darken(slots[i - 3], 0.55);
        }
        BufferedImage img = new BufferedImage(8, 1, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 8; i++) img.setRGB(i, 0, slots[i].asRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) throw new IOException("missing " + path + " in plugin jar");
            return in.readAllBytes();
        }
    }
}
