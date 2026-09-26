package dev.crystalline.resonance.spell;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.api.SpellCastEvent;
import dev.crystalline.resonance.mana.ManaManager;
import dev.crystalline.resonance.spell.impl.FrostBolt;
import dev.crystalline.resonance.spell.impl.InfernoWave;
import dev.crystalline.resonance.spell.impl.LightningStrike;
import dev.crystalline.resonance.util.Messages;
import dev.crystalline.resonance.util.SoundEffect;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns the spells, their settings and cooldowns, and runs every cast through its checks. */
public final class SpellManager {

    public static final String BYPASS_COOLDOWN = "crystalline.bypass.cooldown";
    public static final String BYPASS_MANA = "crystalline.bypass.mana";

    /** Guards against one right-click firing two interact events. */
    private static final long CAST_LOCK_MILLIS = 150L;

    private final CrystallineResonance plugin;
    private final Map<SpellType, Spell> spells = new EnumMap<>(SpellType.class);
    private final Map<SpellType, SpellSettings> settings = new EnumMap<>(SpellType.class);
    private final Map<UUID, Map<SpellType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> castLocks = new HashMap<>();
    private Set<String> disabledWorlds = Set.of();
    private SpellTargets targets = new SpellTargets(true, false);

    public SpellManager(CrystallineResonance plugin) {
        this.plugin = plugin;
        register(new FrostBolt(plugin));
        register(new InfernoWave(plugin));
        register(new LightningStrike(plugin));
    }

    private void register(Spell spell) {
        spells.put(spell.type(), spell);
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        for (SpellType type : SpellType.values()) {
            settings.put(type, SpellSettings.load(config, type, plugin.getLogger()));
        }
        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("disabled-worlds")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }
        disabledWorlds = Set.copyOf(worlds);
        targets = new SpellTargets(
                config.getBoolean("combat.affect-players", true),
                config.getBoolean("combat.hit-own-pets", false));
    }

    public SpellSettings settings(SpellType type) {
        return settings.get(type);
    }

    public SpellTargets targets() {
        return targets;
    }

    /** Remaining cooldown in milliseconds, or 0 when the spell is ready. */
    public long remainingCooldown(Player player, SpellType type) {
        Map<SpellType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0L;
        }
        Long readyAt = playerCooldowns.get(type);
        return readyAt == null ? 0L : Math.max(0L, readyAt - System.currentTimeMillis());
    }

    public void resetCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    /** Attempts to cast a spell, giving the player feedback if anything prevents it. */
    public void tryCast(Player player, SpellType type) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lockedUntil = castLocks.get(id);
        if (lockedUntil != null && now < lockedUntil) {
            return;
        }
        castLocks.put(id, now + CAST_LOCK_MILLIS);

        SpellSettings spellSettings = settings.get(type);
        ManaManager mana = plugin.mana();
        Messages messages = plugin.messages();
        TagResolver spellName = Placeholder.component("spell", spellSettings.displayName());

        if (!player.hasPermission(type.permission())) {
            mana.notify(player, messages.get("spell-no-permission", spellName));
            mana.playFeedback(player, "no-permission");
            return;
        }
        if (!spellSettings.enabled()) {
            mana.notify(player, messages.get("spell-disabled", spellName));
            mana.playFeedback(player, "no-permission");
            return;
        }
        if (disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            mana.notify(player, messages.get("world-disabled", spellName));
            mana.playFeedback(player, "no-permission");
            return;
        }

        if (!player.hasPermission(BYPASS_COOLDOWN)) {
            long remaining = remainingCooldown(player, type);
            if (remaining > 0) {
                mana.notify(player, messages.get("on-cooldown", spellName,
                        Placeholder.unparsed("time", String.format(Locale.ROOT, "%.1f", remaining / 1000.0))));
                mana.playFeedback(player, "cooldown");
                return;
            }
        }

        SpellCastEvent event = new SpellCastEvent(player, type, spellSettings.manaCost());
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        double cost = event.getManaCost();
        if (!player.hasPermission(BYPASS_MANA) && !mana.tryConsume(player, cost)) {
            mana.notify(player, messages.get("insufficient-mana", spellName,
                    Placeholder.unparsed("cost", SpellSettings.formatNumber(cost)),
                    Placeholder.unparsed("mana", ManaManager.formatMana(mana.getMana(player))),
                    Placeholder.unparsed("max", ManaManager.formatMana(mana.getMaxMana()))));
            mana.playFeedback(player, "insufficient-mana");
            return;
        }

        if (spellSettings.cooldownMillis() > 0) {
            cooldowns.computeIfAbsent(id, key -> new EnumMap<>(SpellType.class))
                    .put(type, now + spellSettings.cooldownMillis());
        }
        SoundEffect.playAt(plugin, spellSettings.sounds("cast"), player.getLocation());
        spells.get(type).cast(player, spellSettings);
        mana.showNow(player);
    }
}
