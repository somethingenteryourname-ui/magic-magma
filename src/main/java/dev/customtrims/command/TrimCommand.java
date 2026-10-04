package dev.customtrims.command;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.effect.AuraShape;
import dev.customtrims.effect.EffectStyle;
import dev.customtrims.effect.ParticleStyle;
import dev.customtrims.effect.TrailShape;
import dev.customtrims.trim.TrimManager;
import dev.customtrims.trim.TrimSettings;
import dev.customtrims.util.ColorUtil;
import dev.customtrims.util.Enums;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class TrimCommand implements TabExecutor {

    private static final String PREFIX = "\u00A7b\u00A7lTrims \u00A78\u00BB \u00A77";
    private static final List<String> NONE = List.of();
    private static final List<String> SUBS = List.of(
            "help", "presets", "preset", "give", "color", "trail", "aura", "pattern", "material",
            "style", "size", "density", "glint", "name", "info", "remove", "toggle", "options", "pack",
            "newmaterial", "reload");
    private static final List<String> ARMOR_TYPES = List.of(
            "netherite", "diamond", "iron", "golden", "chainmail", "leather", "copper");
    private static final String[] PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    private final CustomTrimsPlugin plugin;

    public TrimCommand(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    private TrimManager tm() {
        return plugin.getTrimManager();
    }

    private static void msg(CommandSender s, String text) {
        s.sendMessage(PREFIX + text);
    }

    private static boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) return true;
        msg(s, "\u00A7cYou don't have permission to do that.");
        return false;
    }

    private static String fancy(TrimSettings s) {
        return ColorUtil.gradient(s.name, s.colors);
    }

    // ================================================================= execute

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "presets", "list" -> listPresets(sender, label);
            case "options" -> options(sender);
            case "preset" -> preset(sender, args, label);
            case "give" -> give(sender, args, label);
            case "remove" -> remove(sender, args);
            case "toggle" -> toggle(sender);
            case "info" -> info(sender);
            case "pack" -> pack(sender);
            case "newmaterial" -> newMaterial(sender, args, label);
            case "reload" -> {
                if (perm(sender, "customtrims.admin")) {
                    plugin.reloadAll();
                    msg(sender, "Reloaded! \u00A7f" + tm().getPresets().size() + "\u00A77 presets, \u00A7f"
                            + tm().getCustomMaterials().size() + "\u00A77 custom materials.");
                    if (plugin.getPackBuilder().isRestartNeeded()) {
                        msg(sender, "\u00A7eNew trims/materials were added: restart the server once to load them.");
                    }
                }
            }
            case "color", "colour", "colors", "trail", "aura", "pattern", "material", "style", "size", "density", "glint", "name" ->
                    edit(sender, sub.equals("colors") ? "color" : sub, args, label);
            default -> msg(sender, "Unknown subcommand. Try \u00A7f/" + label + " help");
        }
        return true;
    }

    private void help(CommandSender s, String l) {
        s.sendMessage("\u00A78\u00A7m                                                  ");
        s.sendMessage(ColorUtil.gradient("  Custom Trims", List.of(Color.fromRGB(0xFF2DD9), Color.fromRGB(0x8B2DFF), Color.fromRGB(0x3DF5FF)))
                + " \u00A78- \u00A77make your armor glow");
        s.sendMessage("\u00A7f/" + l + " presets \u00A78- \u00A77list ready-made trims");
        s.sendMessage("\u00A7f/" + l + " preset <name> \u00A78- \u00A77put a preset on your worn armor");
        s.sendMessage("\u00A7f/" + l + " color <c1> [c2] [c3]... \u00A78- \u00A77up to 8 colors, blended together");
        s.sendMessage("\u00A7f/" + l + " pattern <pattern> [material] \u00A78- \u00A77trim design on the armor");
        s.sendMessage("\u00A7f/" + l + " material <material> \u00A78- \u00A77trim color on the armor");
        s.sendMessage("\u00A7f/" + l + " trail <particle> [shape] \u00A78- \u00A77trail behind you");
        s.sendMessage("\u00A7f/" + l + " aura <shape> [particle] \u00A78- \u00A77aura around you");
        s.sendMessage("\u00A7f/" + l + " style <liquid|particles|both> \u00A78- \u00A77flowing liquid or particles");
        s.sendMessage("\u00A7f/" + l + " size <n> \u00A78| \u00A7fdensity <n> \u00A78| \u00A7fglint on/off \u00A78| \u00A7fname <text>");
        s.sendMessage("\u00A7f/" + l + " info \u00A78| \u00A7fremove \u00A78| \u00A7ftoggle \u00A78| \u00A7foptions \u00A78| \u00A7fpack");
        if (s.hasPermission("customtrims.give")) {
            s.sendMessage("\u00A7f/" + l + " give <player> <preset> [armor type] \u00A78- \u00A77full trimmed set");
        }
        if (s.hasPermission("customtrims.admin")) {
            s.sendMessage("\u00A7f/" + l + " newmaterial <name> <c1> [c2]... \u00A78- \u00A77new trim material");
            s.sendMessage("\u00A7f/" + l + " reload");
        }
        s.sendMessage("\u00A78\u00A7m                                                  ");
    }

    private void listPresets(CommandSender s, String label) {
        Map<String, TrimSettings> presets = tm().getPresets();
        if (presets.isEmpty()) {
            msg(s, "No presets in the config.");
            return;
        }
        msg(s, "Presets \u00A78(\u00A7f/" + label + " preset <name>\u00A78):");
        for (Entry<String, TrimSettings> e : presets.entrySet()) {
            TrimSettings p = e.getValue();
            s.sendMessage(" \u00A78\u2022 \u00A7f" + e.getKey() + " \u00A78- " + fancy(p)
                    + " \u00A78(\u00A77" + Enums.prettyId(p.pattern) + ", " + Enums.pretty(p.auraShape) + " aura\u00A78)");
        }
    }

    private void options(CommandSender s) {
        msg(s, "\u00A7dNEW patterns: \u00A7f" + String.join(", ", TrimManager.CUSTOM_PATTERNS));
        msg(s, "\u00A7bVanilla patterns: \u00A7f" + String.join(", ", TrimManager.VANILLA_PATTERNS) + ", none");
        StringBuilder mats = new StringBuilder();
        for (Entry<String, List<Color>> e : tm().getCustomMaterials().entrySet()) {
            mats.append(ColorUtil.gradient(e.getKey(), e.getValue())).append(' ');
        }
        msg(s, "\u00A7dNEW materials: " + (mats.length() == 0 ? "\u00A77(none)" : mats.toString()));
        msg(s, "\u00A7bVanilla materials: \u00A7f" + String.join(", ", TrimManager.VANILLA_MATERIALS));
        msg(s, "\u00A7bTrail/aura particles: \u00A7f" + String.join(", ", Enums.names(ParticleStyle.values())));
        msg(s, "\u00A7bTrail shapes: \u00A7f" + String.join(", ", Enums.names(TrailShape.values())));
        msg(s, "\u00A7bAura shapes: \u00A7f" + String.join(", ", Enums.names(AuraShape.values())));
        msg(s, "\u00A7bStyles: \u00A7f" + String.join(", ", Enums.names(EffectStyle.values())));
        StringBuilder colors = new StringBuilder();
        for (Entry<String, Color> e : ColorUtil.NAMED.entrySet()) {
            colors.append(ColorUtil.chat(e.getValue())).append(e.getKey()).append(' ');
        }
        msg(s, "\u00A7bColors: " + colors + "\u00A77or any hex like \u00A7f#FF00AA");
    }

    // ================================================================== presets

    private void preset(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            msg(sender, "Usage: \u00A7f/" + label + " preset <name> [player]");
            return;
        }
        TrimSettings preset = tm().getPreset(args[1]);
        if (preset == null) {
            msg(sender, "\u00A7cUnknown preset. See \u00A7f/" + label + " presets");
            return;
        }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;

        int changed = tm().applyToWorn(target, preset);
        if (changed == 0) {
            msg(sender, "\u00A7c" + (target == sender ? "You need" : target.getName() + " needs") + " to wear armor (or hold a piece).");
            return;
        }
        plugin.invalidate(target);
        msg(sender, "Applied " + fancy(preset) + "\u00A77 to \u00A7f" + changed + "\u00A77 piece" + (changed == 1 ? "" : "s")
                + (target == sender ? "" : " on \u00A7f" + target.getName()) + "\u00A77.");
        warnIfNotLoaded(sender, preset);
    }

    private void give(CommandSender sender, String[] args, String label) {
        if (!perm(sender, "customtrims.give")) return;
        if (args.length < 3) {
            msg(sender, "Usage: \u00A7f/" + label + " give <player> <preset> [" + String.join("|", ARMOR_TYPES) + "]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "\u00A7cPlayer not found.");
            return;
        }
        TrimSettings preset = tm().getPreset(args[2]);
        if (preset == null) {
            msg(sender, "\u00A7cUnknown preset. See \u00A7f/" + label + " presets");
            return;
        }
        String type = args.length >= 4 ? args[3].toUpperCase(Locale.ROOT) : "NETHERITE";
        if (type.equals("GOLD")) type = "GOLDEN";

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < PIECES.length; i++) {
            Material m = Material.matchMaterial(type + "_" + PIECES[i]);
            if (m == null) {
                msg(sender, "\u00A7cUnknown armor type. Use: " + String.join(", ", ARMOR_TYPES));
                return;
            }
            ItemStack item = new ItemStack(m);
            tm().apply(item, preset, i / 3.0);
            items.add(item);
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        msg(sender, "Gave \u00A7f" + target.getName() + "\u00A77 a " + fancy(preset) + "\u00A77 " + type.toLowerCase(Locale.ROOT) + " set.");
        if (target != sender) msg(target, "You received a " + fancy(preset) + "\u00A77 armor set!");
        warnIfNotLoaded(sender, preset);
    }

    private void remove(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;
        int n = tm().clearWorn(target);
        plugin.invalidate(target);
        msg(sender, n == 0 ? "No custom trims found on worn armor."
                : "Removed custom trims from \u00A7f" + n + "\u00A77 piece" + (n == 1 ? "" : "s") + ".");
    }

    private void toggle(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Only players can do that.");
            return;
        }
        boolean hidden = plugin.toggleHidden(p.getUniqueId());
        msg(sender, hidden ? "Your trim effects are now \u00A7chidden\u00A77." : "Your trim effects are now \u00A7ashowing\u00A77.");
    }

    private void pack(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Only players can do that.");
            return;
        }
        if (plugin.getPackServer().send(p)) {
            msg(sender, "Sending the trim resource pack. Accept it to see the new trims!");
        } else {
            msg(sender, "\u00A7cThe resource pack isn't set up. An admin should check resource-pack in config.yml.");
        }
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Only players can do that.");
            return;
        }
        TrimSettings s = tm().current(p);
        if (s == null) {
            msg(sender, "You aren't wearing a custom trim. Try \u00A7f/ctrim presets");
            return;
        }
        msg(sender, fancy(s) + "\u00A77 trim:");
        sender.sendMessage(" \u00A77Look: \u00A7f" + Enums.prettyId(s.pattern) + "\u00A77 pattern, \u00A7f" + Enums.prettyId(s.material)
                + "\u00A77 material, glint \u00A7f" + (s.glint ? "on" : "off"));
        StringBuilder hexes = new StringBuilder();
        for (Color c : s.colors) hexes.append(ColorUtil.chat(c)).append("\u25A0 ").append(ColorUtil.hex(c)).append(' ');
        sender.sendMessage(" \u00A77Colors: " + hexes);
        sender.sendMessage(" \u00A77Trail: \u00A7f" + Enums.pretty(s.trailParticle) + "\u00A77 (" + Enums.pretty(s.trailShape) + ")");
        sender.sendMessage(" \u00A77Aura: \u00A7f" + Enums.pretty(s.auraShape) + "\u00A77 (" + Enums.pretty(s.auraParticle) + ")");
        sender.sendMessage(" \u00A77Style: \u00A7f" + Enums.pretty(s.style));
        sender.sendMessage(" \u00A77Size: \u00A7f" + s.size + "\u00A77  Density: \u00A7f" + s.density);
        if (plugin.isHidden(p.getUniqueId())) sender.sendMessage(" \u00A7cEffects are hidden (/ctrim toggle).");
        warnIfNotLoaded(sender, s);
    }

    private void newMaterial(CommandSender sender, String[] args, String label) {
        if (!perm(sender, "customtrims.admin")) return;
        if (args.length < 3) {
            msg(sender, "Usage: \u00A7f/" + label + " newmaterial <name> <color1> [color2] ... (up to 8)");
            return;
        }
        String id = TrimManager.cleanId(args[1]);
        if (id.isEmpty() || TrimManager.VANILLA_MATERIALS.contains(id)) {
            msg(sender, "\u00A7cPick a different name (letters, numbers and _ only).");
            return;
        }
        List<String> hexes = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        for (int i = 2; i < args.length && colors.size() < TrimSettings.MAX_COLORS; i++) {
            Color c = ColorUtil.parse(args[i]);
            if (c == null) {
                msg(sender, "\u00A7cUnknown color '" + args[i] + "'.");
                return;
            }
            colors.add(c);
            hexes.add(ColorUtil.hex(c));
        }
        plugin.getConfig().set("custom-materials." + id, hexes);
        plugin.saveConfig();
        plugin.reloadAll();
        msg(sender, "Saved material " + ColorUtil.gradient(id, colors) + "\u00A77!");
        msg(sender, "\u00A7eRestart the server once, then use \u00A7f/" + label + " material " + id);
    }

    private void warnIfNotLoaded(CommandSender sender, TrimSettings s) {
        if (!tm().isLoaded(s)) {
            msg(sender, "\u00A7eThe \u00A7f" + s.pattern + "\u00A7e/\u00A7f" + s.material
                    + "\u00A7e trim isn't loaded yet: restart the server once. Effects still work now.");
        }
    }

    /** Self if no name given; another player if a name is given and sender is admin. */
    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            if (!perm(sender, "customtrims.admin")) return null;
            Player t = Bukkit.getPlayerExact(args[index]);
            if (t == null) msg(sender, "\u00A7cPlayer not found.");
            return t;
        }
        if (!(sender instanceof Player p)) {
            msg(sender, "\u00A7cConsole must name a player.");
            return null;
        }
        return perm(sender, "customtrims.use") ? p : null;
    }

    // ===================================================================== edit

    private void edit(CommandSender sender, String sub, String[] args, String label) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Only players can edit their armor.");
            return;
        }
        if (!perm(sender, "customtrims.use")) return;
        if (args.length < 2) {
            msg(sender, "Usage: \u00A7f" + usage(sub, label));
            return;
        }
        TrimSettings s = tm().currentOrDefault(p);
        String error = applyEdit(s, sub, args);
        if (error != null) {
            msg(sender, "\u00A7c" + error);
            return;
        }
        int changed = tm().applyToWorn(p, s);
        if (changed == 0) {
            msg(sender, "\u00A7cYou need to wear armor (or hold a piece) first.");
            return;
        }
        plugin.invalidate(p);
        msg(sender, "Updated \u00A7f" + changed + "\u00A77 piece" + (changed == 1 ? "" : "s") + ": " + describe(sub, s));
        if (sub.equals("pattern") || sub.equals("material")) warnIfNotLoaded(sender, s);
    }

    private String usage(String sub, String l) {
        return switch (sub) {
            case "color", "colour" -> "/" + l + " color <color1> [color2] [color3] ... (up to 8)";
            case "trail" -> "/" + l + " trail <particle> [shape]";
            case "aura" -> "/" + l + " aura <shape> [particle]";
            case "pattern" -> "/" + l + " pattern <pattern> [material]";
            case "material" -> "/" + l + " material <material>";
            case "size" -> "/" + l + " size <0.3-" + tm().getMaxSize() + ">";
            case "density" -> "/" + l + " density <0.25-" + tm().getMaxDensity() + ">";
            case "glint" -> "/" + l + " glint <on|off>";
            case "style" -> "/" + l + " style <liquid|particles|both>";
            case "name" -> "/" + l + " name <text>";
            default -> "/" + l + " help";
        };
    }

    /** Changes the settings. Returns an error message, or null on success. */
    private String applyEdit(TrimSettings s, String sub, String[] a) {
        switch (sub) {
            case "color", "colour" -> {
                List<Color> list = new ArrayList<>();
                for (int i = 1; i < a.length; i++) {
                    if (list.size() >= TrimSettings.MAX_COLORS) break;
                    Color c = ColorUtil.parse(a[i]);
                    if (c == null) return "Unknown color '" + a[i] + "'. Use a name (see /ctrim options) or hex like #FF00AA.";
                    list.add(c);
                }
                s.setColors(list);
            }
            case "trail" -> {
                ParticleStyle st = Enums.parse(ParticleStyle.class, a[1], null);
                if (st == null) return "Unknown particle. See /ctrim options";
                s.trailParticle = st;
                if (a.length >= 3) {
                    TrailShape shape = Enums.parse(TrailShape.class, a[2], null);
                    if (shape == null) return "Unknown trail shape. See /ctrim options";
                    s.trailShape = shape;
                }
            }
            case "aura" -> {
                AuraShape shape = Enums.parse(AuraShape.class, a[1], null);
                if (shape == null) return "Unknown aura shape. See /ctrim options";
                s.auraShape = shape;
                if (a.length >= 3) {
                    ParticleStyle st = Enums.parse(ParticleStyle.class, a[2], null);
                    if (st == null) return "Unknown particle. See /ctrim options";
                    s.auraParticle = st;
                } else if (s.auraParticle == ParticleStyle.NONE && shape != AuraShape.NONE) {
                    s.auraParticle = ParticleStyle.BLEND;
                }
            }
            case "pattern" -> {
                String pat = a[1].toLowerCase(Locale.ROOT);
                if (!tm().isKnownPattern(pat)) return "Unknown pattern. See /ctrim options";
                s.pattern = pat;
                if (a.length >= 3) {
                    String mat = a[2].toLowerCase(Locale.ROOT);
                    if (!tm().isKnownMaterial(mat)) return "Unknown material. See /ctrim options";
                    s.material = mat;
                }
            }
            case "material" -> {
                String mat = a[1].toLowerCase(Locale.ROOT);
                if (!tm().isKnownMaterial(mat)) return "Unknown material. See /ctrim options";
                s.material = mat;
            }
            case "size" -> {
                try {
                    s.size = tm().clampSize(Double.parseDouble(a[1]));
                } catch (NumberFormatException e) {
                    return "Size must be a number, like 1.5";
                }
            }
            case "density" -> {
                try {
                    s.density = tm().clampDensity(Double.parseDouble(a[1]));
                } catch (NumberFormatException e) {
                    return "Density must be a number, like 1.5";
                }
            }
            case "style" -> {
                EffectStyle st = Enums.parse(EffectStyle.class, a[1], null);
                if (st == null) return "Use liquid, particles or both.";
                s.style = st;
            }
            case "glint" -> {
                String v = a[1].toLowerCase(Locale.ROOT);
                if (v.equals("on") || v.equals("true") || v.equals("yes")) s.glint = true;
                else if (v.equals("off") || v.equals("false") || v.equals("no")) s.glint = false;
                else return "Use on or off.";
            }
            case "name" -> s.name = TrimSettings.cleanName(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            default -> {
                return "Unknown option.";
            }
        }
        return null;
    }

    private String describe(String sub, TrimSettings s) {
        return switch (sub) {
            case "color", "colour" -> ColorUtil.swatches(s.colors) + " \u00A77(" + s.colors.size() + " color"
                    + (s.colors.size() == 1 ? "" : "s, blended") + ")";
            case "trail" -> "\u00A7f" + Enums.pretty(s.trailParticle) + "\u00A77 trail (" + Enums.pretty(s.trailShape) + ")";
            case "aura" -> "\u00A7f" + Enums.pretty(s.auraShape) + "\u00A77 aura (" + Enums.pretty(s.auraParticle) + ")";
            case "pattern", "material" -> "\u00A7f" + Enums.prettyId(s.pattern) + "\u00A77 pattern, \u00A7f" + Enums.prettyId(s.material) + "\u00A77 material";
            case "size" -> "size \u00A7f" + s.size;
            case "density" -> "density \u00A7f" + s.density;
            case "glint" -> "glint \u00A7f" + (s.glint ? "on" : "off");
            case "style" -> "\u00A7f" + Enums.pretty(s.style) + "\u00A77 style" + (s.style == EffectStyle.PARTICLES ? ""
                    : " \u00A78(needs the resource pack: /ctrim pack)");
            case "name" -> fancy(s);
            default -> "";
        };
    }

    // ============================================================ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        int n = a.length;
        List<String> options = switch (sub) {
            case "color", "colour", "colors" -> n <= 9 ? colorSuggestions() : NONE;
            case "trail" -> n == 2 ? Enums.names(ParticleStyle.values()) : n == 3 ? Enums.names(TrailShape.values()) : NONE;
            case "aura" -> n == 2 ? Enums.names(AuraShape.values()) : n == 3 ? Enums.names(ParticleStyle.values()) : NONE;
            case "pattern" -> n == 2 ? patternSuggestions() : n == 3 ? tm().allMaterials() : NONE;
            case "material" -> n == 2 ? tm().allMaterials() : NONE;
            case "size", "density" -> n == 2 ? List.of("0.5", "1", "1.5", "2", "3") : NONE;
            case "glint" -> n == 2 ? List.of("on", "off") : NONE;
            case "style" -> n == 2 ? Enums.names(EffectStyle.values()) : NONE;
            case "preset" -> n == 2 ? new ArrayList<>(tm().getPresets().keySet())
                    : (n == 3 && sender.hasPermission("customtrims.admin")) ? players() : NONE;
            case "give" -> n == 2 ? players() : n == 3 ? new ArrayList<>(tm().getPresets().keySet()) : n == 4 ? ARMOR_TYPES : NONE;
            case "remove" -> (n == 2 && sender.hasPermission("customtrims.admin")) ? players() : NONE;
            case "newmaterial" -> n >= 3 && n <= 10 ? colorSuggestions() : NONE;
            default -> NONE;
        };
        return filter(options, a[n - 1]);
    }

    private static List<String> colorSuggestions() {
        List<String> out = new ArrayList<>(ColorUtil.NAMED.keySet());
        out.add("#");
        return out;
    }

    private List<String> patternSuggestions() {
        List<String> out = tm().allPatterns();
        out.add("none");
        return out;
    }

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        return out;
    }
}
