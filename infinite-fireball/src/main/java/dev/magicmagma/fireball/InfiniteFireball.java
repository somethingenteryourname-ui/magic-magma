package dev.magicmagma.fireball;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfiniteFireball extends JavaPlugin {

    private FireballSettings settings;
    private FireballItem items;
    private FireballListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        settings = new FireballSettings(this);
        settings.load();
        items = new FireballItem(this);
        listener = new FireballListener(this, settings, items);

        getServer().getPluginManager().registerEvents(listener, this);

        PluginCommand command = getCommand("fireball");
        if (command != null) {
            FireballCommand handler = new FireballCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        listener.start();
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.stop();
        }
    }

    public void reload() {
        reloadConfig();
        settings.load();
    }

    public FireballSettings settings() {
        return settings;
    }

    public FireballItem items() {
        return items;
    }
}
