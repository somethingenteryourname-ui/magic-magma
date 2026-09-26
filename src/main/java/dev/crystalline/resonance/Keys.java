package dev.crystalline.resonance;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent data keys used by the plugin. */
public final class Keys {

    /** Crystal type id stored on crystal items. */
    public final NamespacedKey crystal;
    /** Spell id stored on tome items. */
    public final NamespacedKey tome;
    /** Current mana stored on the player. */
    public final NamespacedKey mana;
    /** Player-placed block positions stored on chunks. */
    public final NamespacedKey placedBlocks;

    Keys(Plugin plugin) {
        this.crystal = new NamespacedKey(plugin, "crystal");
        this.tome = new NamespacedKey(plugin, "tome");
        this.mana = new NamespacedKey(plugin, "mana");
        this.placedBlocks = new NamespacedKey(plugin, "placed_blocks");
    }
}
