package dev.magicnuke;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent data keys, plus the resource pack model ids. */
public final class Keys {

    public static final String NAMESPACE = "magicnuke";

    /** Item model of the missile (assets/magicnuke/items/nuke.json). */
    public static final NamespacedKey MODEL_NUKE = new NamespacedKey(NAMESPACE, "nuke");

    /** Size id stored on nuke items and placed nukes. */
    public final NamespacedKey size;
    /** Radius stored on nuke items and placed nukes. */
    public final NamespacedKey radius;
    /** Marks entities owned by this plugin: "nuke", "hitbox", "flight", "fx" or "debris". */
    public final NamespacedKey role;
    /** UUID of the partner entity (nuke body <-> hitbox). */
    public final NamespacedKey link;

    public Keys(Plugin plugin) {
        size = new NamespacedKey(plugin, "size");
        radius = new NamespacedKey(plugin, "radius");
        role = new NamespacedKey(plugin, "role");
        link = new NamespacedKey(plugin, "link");
    }

    public static NamespacedKey model(String name) {
        return new NamespacedKey(NAMESPACE, name);
    }
}
