package dev.magicnuke;

import dev.magicnuke.nuke.NukeSize;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Everything read from config.yml. Rebuilt on /nuke reload. */
public final class NukeConfig {

    public final Map<String, NukeSize> sizes;
    public final double customMinRadius;
    public final double customMaxRadius;

    public final boolean breakBlocks;
    public final double craterDepth;
    public final double blastHeight;
    public final double maxMillisPerTick;
    public final boolean scorch;
    public final boolean fire;
    public final boolean debris;
    public final boolean damageEntities;
    public final double maxDamage;
    public final double damageRadius;
    public final boolean chainReaction;
    public final boolean flash;
    public final boolean screenShake;
    public final boolean nausea;
    public final boolean mushroomCloud;
    public final int cloudTicks;
    public final boolean fallout;
    public final int falloutTicks;
    public final boolean radiation;
    private final Set<String> disabledWorlds;

    public final boolean allowRedstone;
    public final boolean pickupWithPunch;
    public final boolean igniteFromExplosions;
    public final boolean dispensersLaunch;
    public final double warningRadius;

    public final boolean packEnabled;
    public final boolean packSendOnJoin;
    public final boolean packRequired;
    public final String packPrompt;
    public final int packPort;
    public final String packBindAddress;
    public final String packPublicAddress;
    public final String packExternalUrl;

    public NukeConfig(FileConfiguration c, Logger log) {
        Map<String, NukeSize> map = new LinkedHashMap<>();
        ConfigurationSection sec = c.getConfigurationSection("sizes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                String id = key.toLowerCase(Locale.ROOT);
                if (id.equals(NukeSize.CUSTOM_ID)) {
                    log.warning("Size name 'custom' is reserved, skipping it.");
                    continue;
                }
                double radius = s.getDouble("radius", 16);
                if (radius <= 0) {
                    log.warning("Size '" + key + "' has an invalid radius, skipping it.");
                    continue;
                }
                map.put(id, new NukeSize(id,
                        s.getString("name", key),
                        radius,
                        Math.max(0.2, s.getDouble("model-scale", 1.0)),
                        Math.max(10, s.getDouble("flight-height", 60)),
                        (int) Math.max(0, Math.round(s.getDouble("fuse-seconds", 3) * 20))));
            }
        }
        if (map.isEmpty()) {
            log.warning("No nuke sizes configured, using a default 'small' size.");
            map.put("small", new NukeSize("small", "Tactical Nuke", 16, 1.0, 65, 60));
        }
        sizes = Collections.unmodifiableMap(map);
        customMinRadius = Math.max(1, c.getDouble("custom.min-radius", 3));
        customMaxRadius = Math.max(customMinRadius, Math.min(200, c.getDouble("custom.max-radius", 100)));

        breakBlocks = c.getBoolean("explosion.break-blocks", true);
        craterDepth = clamp(c.getDouble("explosion.crater-depth", 0.5), 0.05, 1.5);
        blastHeight = clamp(c.getDouble("explosion.blast-height", 0.9), 0.05, 2.0);
        maxMillisPerTick = clamp(c.getDouble("explosion.max-millis-per-tick", 25), 1, 45);
        scorch = c.getBoolean("explosion.scorch", true);
        fire = c.getBoolean("explosion.fire", true);
        debris = c.getBoolean("explosion.debris", true);
        damageEntities = c.getBoolean("explosion.damage-entities", true);
        maxDamage = Math.max(0, c.getDouble("explosion.max-damage", 150));
        damageRadius = clamp(c.getDouble("explosion.damage-radius", 1.6), 0.1, 10);
        chainReaction = c.getBoolean("explosion.chain-reaction", true);
        flash = c.getBoolean("explosion.flash", true);
        screenShake = c.getBoolean("explosion.screen-shake", true);
        nausea = c.getBoolean("explosion.nausea", true);
        mushroomCloud = c.getBoolean("explosion.mushroom-cloud", true);
        cloudTicks = (int) clamp(c.getDouble("explosion.cloud-seconds", 22) * 20, 100, 20 * 120);
        fallout = c.getBoolean("explosion.fallout", true);
        falloutTicks = (int) clamp(c.getDouble("explosion.fallout-seconds", 40) * 20, 0, 20 * 600);
        radiation = c.getBoolean("explosion.radiation", true);
        Set<String> worlds = new HashSet<>();
        for (String w : c.getStringList("explosion.disabled-worlds")) worlds.add(w.toLowerCase(Locale.ROOT));
        disabledWorlds = worlds;

        allowRedstone = c.getBoolean("nuke.allow-redstone", true);
        pickupWithPunch = c.getBoolean("nuke.pickup-with-punch", true);
        igniteFromExplosions = c.getBoolean("nuke.ignite-from-explosions", true);
        dispensersLaunch = c.getBoolean("nuke.dispensers-launch", true);
        warningRadius = Math.max(16, c.getDouble("nuke.warning-radius", 200));

        packEnabled = c.getBoolean("pack.enabled", true);
        packSendOnJoin = c.getBoolean("pack.send-on-join", true);
        packRequired = c.getBoolean("pack.required", false);
        packPrompt = c.getString("pack.prompt", "");
        packPort = c.getInt("pack.port", 8163);
        packBindAddress = c.getString("pack.bind-address", "0.0.0.0");
        packPublicAddress = c.getString("pack.public-address", "").trim();
        packExternalUrl = c.getString("pack.external-url", "").trim();
    }

    public boolean isDisabled(World world) {
        return world == null || disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    /** Resolve a size name or a number (custom radius). Returns null if invalid. */
    public NukeSize resolve(String input) {
        if (input == null) return null;
        NukeSize preset = sizes.get(input.toLowerCase(Locale.ROOT));
        if (preset != null) return preset;
        try {
            double r = Double.parseDouble(input);
            if (Double.isNaN(r) || r < customMinRadius || r > customMaxRadius) return null;
            return NukeSize.custom(r);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
