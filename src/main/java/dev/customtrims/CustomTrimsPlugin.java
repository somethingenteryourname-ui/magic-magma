package dev.customtrims;

import dev.customtrims.command.TrimCommand;
import dev.customtrims.effect.EffectTask;
import dev.customtrims.effect.LiquidTask;
import dev.customtrims.pack.PackBuilder;
import dev.customtrims.pack.PackServer;
import dev.customtrims.trim.TrimManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CustomTrimsPlugin extends JavaPlugin {

    private TrimManager trimManager;
    private PackBuilder packBuilder;
    private PackServer packServer;
    private EffectTask effectTask;
    private LiquidTask liquidTask;
    private final Set<UUID> hidden = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LiquidTask.removeAllTagged();   // leftovers from a crash or /reload
        trimManager = new TrimManager(this);

        packBuilder = new PackBuilder(this);
        packBuilder.build();
        packServer = new PackServer(this);
        packServer.start();
        getServer().getPluginManager().registerEvents(packServer, this);

        TrimCommand command = new TrimCommand(this);
        PluginCommand pc = getCommand("ctrim");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        startEffects();
        getLogger().info("CustomTrims enabled: " + trimManager.getPresets().size() + " presets, "
                + TrimManager.CUSTOM_PATTERNS.size() + " new patterns, "
                + trimManager.getCustomMaterials().size() + " custom materials.");
    }

    @Override
    public void onDisable() {
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
        if (liquidTask != null) {
            liquidTask.shutdown();
            liquidTask = null;
        }
        if (packServer != null) packServer.stop();
    }

    private void startEffects() {
        if (effectTask != null) effectTask.cancel();
        int interval = Math.max(1, Math.min(20, getConfig().getInt("tick-interval", 2)));
        effectTask = new EffectTask(this, interval);
        effectTask.runTaskTimer(this, 20L, interval);

        if (liquidTask != null) liquidTask.shutdown();
        liquidTask = new LiquidTask(this);
        liquidTask.runTaskTimer(this, 20L, 1L);
    }

    public void reloadAll() {
        reloadConfig();
        trimManager.reload();
        String oldHash = packBuilder.getSha1Hex();
        packBuilder.build();
        packServer.start();
        if (!packBuilder.getSha1Hex().equals(oldHash)) packServer.sendToAll();
        startEffects();
    }

    public void invalidate(Player p) {
        if (effectTask != null) effectTask.invalidate(p.getUniqueId());
        if (liquidTask != null) liquidTask.invalidate(p.getUniqueId());
    }

    public boolean isHidden(UUID id) {
        return hidden.contains(id);
    }

    /** @return true if effects are now hidden */
    public boolean toggleHidden(UUID id) {
        if (hidden.remove(id)) return false;
        hidden.add(id);
        return true;
    }

    public TrimManager getTrimManager() {
        return trimManager;
    }

    public PackBuilder getPackBuilder() {
        return packBuilder;
    }

    public PackServer getPackServer() {
        return packServer;
    }
}
