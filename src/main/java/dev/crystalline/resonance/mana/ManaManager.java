package dev.crystalline.resonance.mana;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.util.Messages;
import dev.crystalline.resonance.util.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's mana, regenerates it over time, shows it in the action bar and stores it in
 * the player's persistent data so it survives relogs and restarts.
 */
public final class ManaManager implements Listener {

    public enum DisplayMode { ALWAYS, HOLDING_TOME, SMART }

    private static final class ManaState {
        double mana;
        long regenBlockedUntil;
        long feedbackUntil;
    }

    private final CrystallineResonance plugin;
    private final Map<UUID, ManaState> states = new HashMap<>();
    private final Map<String, List<SoundEffect>> feedbackSounds = new HashMap<>();
    private BukkitTask task;

    private double maxMana;
    private double startingMana;
    private double regenPerSecond;
    private long regenDelayMillis;
    private int intervalTicks;
    private DisplayMode displayMode;
    private String format;
    private int barLength;
    private String barSymbol;
    private TextColor filledColor;
    private TextColor lowColor;
    private TextColor emptyColor;
    private double lowThreshold;
    private long feedbackMillis;

    public ManaManager(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        maxMana = Math.max(1.0, config.getDouble("mana.max", 100.0));
        startingMana = clamp(config.getDouble("mana.starting", maxMana), 0.0, maxMana);
        regenPerSecond = Math.max(0.0, config.getDouble("mana.regen-per-second", 4.0));
        regenDelayMillis = Math.max(0L, (long) (config.getDouble("mana.regen-delay", 1.5) * 1000));
        intervalTicks = Math.max(1, config.getInt("mana.update-interval-ticks", 10));

        String modeName = config.getString("mana.display.mode", "SMART");
        try {
            displayMode = DisplayMode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown mana.display.mode '" + modeName + "'; using SMART.");
            displayMode = DisplayMode.SMART;
        }
        format = config.getString("mana.display.format", "<aqua>Mana</aqua> <bar> <mana>/<max>");
        barLength = Math.max(1, config.getInt("mana.display.bar.length", 20));
        barSymbol = config.getString("mana.display.bar.symbol", "|");
        filledColor = color(config.getString("mana.display.bar.filled-color"), NamedTextColor.AQUA);
        lowColor = color(config.getString("mana.display.bar.low-color"), NamedTextColor.RED);
        emptyColor = color(config.getString("mana.display.bar.empty-color"), NamedTextColor.DARK_GRAY);
        lowThreshold = clamp(config.getDouble("mana.display.bar.low-threshold", 0.25), 0.0, 1.0);
        feedbackMillis = Math.max(0L, (long) (config.getDouble("mana.display.feedback-duration", 1.5) * 1000));

        feedbackSounds.clear();
        for (String key : List.of("insufficient-mana", "cooldown", "no-permission", "crystal-found")) {
            String path = "feedback-sounds." + key;
            feedbackSounds.put(key, SoundEffect.parseAll(config.getStringList(path), path, plugin.getLogger()));
        }

        for (ManaState state : states.values()) {
            state.mana = Math.min(state.mana, maxMana);
        }
    }

    /** Loads online players (for plugin reloads) and starts the regeneration task. */
    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player);
        }
        schedule();
    }

    /** Restarts the task so a changed update interval takes effect. */
    public void restart() {
        if (task != null) {
            task.cancel();
        }
        schedule();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            save(player);
        }
        states.clear();
    }

    private void schedule() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double gain = regenPerSecond * intervalTicks / 20.0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ManaState state = state(player);
            boolean regenerated = false;
            if (state.mana < maxMana && gain > 0 && now >= state.regenBlockedUntil) {
                state.mana = Math.min(maxMana, state.mana + gain);
                regenerated = true;
            }
            if (now < state.feedbackUntil) {
                continue;
            }
            boolean show = switch (displayMode) {
                case ALWAYS -> true;
                case HOLDING_TOME -> isHoldingTome(player);
                case SMART -> regenerated || state.mana < maxMana || isHoldingTome(player);
            };
            if (show) {
                player.sendActionBar(render(state.mana));
            }
        }
    }

    private boolean isHoldingTome(Player player) {
        return plugin.items().tomeSpell(player.getInventory().getItemInMainHand()) != null;
    }

    /** Builds the action-bar mana display. */
    public Component render(double mana) {
        double fraction = mana / maxMana;
        int filled = (int) Math.round(barLength * fraction);
        TextColor fillColor = fraction < lowThreshold ? lowColor : filledColor;
        Component bar = Component.text()
                .append(Component.text(barSymbol.repeat(filled), fillColor))
                .append(Component.text(barSymbol.repeat(barLength - filled), emptyColor))
                .build();
        return Messages.parse(format,
                Placeholder.component("bar", bar),
                Placeholder.unparsed("mana", formatMana(mana)),
                Placeholder.unparsed("max", formatMana(maxMana)),
                Placeholder.unparsed("percent", String.valueOf((int) Math.floor(fraction * 100))));
    }

    public static String formatMana(double mana) {
        return String.valueOf((int) Math.floor(mana));
    }

    public double getMana(Player player) {
        return state(player).mana;
    }

    public double getMaxMana() {
        return maxMana;
    }

    public void setMana(Player player, double mana) {
        state(player).mana = clamp(mana, 0.0, maxMana);
        showNow(player);
    }

    /** Spends mana if the player has enough; returns whether it was spent. */
    public boolean tryConsume(Player player, double amount) {
        ManaState state = state(player);
        if (state.mana + 1.0E-9 < amount) {
            return false;
        }
        state.mana = Math.max(0.0, state.mana - amount);
        if (amount > 0) {
            state.regenBlockedUntil = System.currentTimeMillis() + regenDelayMillis;
        }
        return true;
    }

    /** Shows the mana bar right away, unless feedback is currently displayed. */
    public void showNow(Player player) {
        ManaState state = state(player);
        if (System.currentTimeMillis() >= state.feedbackUntil) {
            player.sendActionBar(render(state.mana));
        }
    }

    /** Shows a message in the action bar and holds it there for the configured feedback duration. */
    public void notify(Player player, Component message) {
        player.sendActionBar(message);
        state(player).feedbackUntil = System.currentTimeMillis() + feedbackMillis;
    }

    /** Plays one of the {@code feedback-sounds} to the player. */
    public void playFeedback(Player player, String key) {
        SoundEffect.playTo(plugin, feedbackSounds.getOrDefault(key, List.of()), player);
    }

    private ManaState state(Player player) {
        ManaState state = states.get(player.getUniqueId());
        return state != null ? state : load(player);
    }

    private ManaState load(Player player) {
        Double stored = player.getPersistentDataContainer().get(plugin.keys().mana, PersistentDataType.DOUBLE);
        ManaState state = new ManaState();
        state.mana = stored == null ? startingMana : clamp(stored, 0.0, maxMana);
        states.put(player.getUniqueId(), state);
        return state;
    }

    private void save(Player player) {
        ManaState state = states.get(player.getUniqueId());
        if (state != null) {
            player.getPersistentDataContainer().set(plugin.keys().mana, PersistentDataType.DOUBLE, state.mana);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        save(player);
        states.remove(player.getUniqueId());
    }

    private static TextColor color(String hex, TextColor fallback) {
        TextColor parsed = hex == null ? null : TextColor.fromHexString(hex.trim());
        return parsed != null ? parsed : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
