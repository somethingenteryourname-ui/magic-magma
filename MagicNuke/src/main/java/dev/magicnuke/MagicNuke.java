package dev.magicnuke;

import dev.magicnuke.command.NukeCommand;
import dev.magicnuke.fx.ExplosionTracker;
import dev.magicnuke.nuke.NukeItems;
import dev.magicnuke.nuke.NukeListener;
import dev.magicnuke.nuke.NukeManager;
import dev.magicnuke.pack.ResourcePackHost;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MagicNuke extends JavaPlugin {

    private NukeConfig settings;
    private Keys keys;
    private NukeItems items;
    private NukeManager nukes;
    private ExplosionTracker explosions;
    private ResourcePackHost pack;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new NukeConfig(getConfig(), getLogger());
        keys = new Keys(this);
        items = new NukeItems(this);
        explosions = new ExplosionTracker(this);
        nukes = new NukeManager(this);
        pack = new ResourcePackHost(this);

        getServer().getPluginManager().registerEvents(new NukeListener(this), this);
        getServer().getPluginManager().registerEvents(pack, this);

        PluginCommand cmd = getCommand("nuke");
        if (cmd != null) {
            NukeCommand handler = new NukeCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        pack.start();
        nukes.start();
        getLogger().info("MagicNuke ready: " + settings.sizes.size() + " nuke sizes loaded.");
    }

    @Override
    public void onDisable() {
        if (nukes != null) nukes.shutdown();
        if (explosions != null) explosions.shutdown();
        if (pack != null) pack.stop();
    }

    public void reload() {
        reloadConfig();
        settings = new NukeConfig(getConfig(), getLogger());
        pack.stop();
        pack.start();
    }

    public NukeConfig settings() {
        return settings;
    }

    public Keys keys() {
        return keys;
    }

    public NukeItems items() {
        return items;
    }

    public NukeManager nukes() {
        return nukes;
    }

    public ExplosionTracker explosions() {
        return explosions;
    }

    public ResourcePackHost pack() {
        return pack;
    }
}
