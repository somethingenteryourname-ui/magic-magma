package dev.crystalline.resonance.spell;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Every castable spell. The id is used in config.yml, permissions and commands. */
public enum SpellType {
    FROST("frost"),
    INFERNO("inferno"),
    LIGHTNING("lightning");

    private final String id;

    SpellType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** {@code crystalline.spell.<id>} */
    public String permission() {
        return "crystalline.spell." + id;
    }

    /** {@code crystalline.craft.<id>} */
    public String craftPermission() {
        return "crystalline.craft." + id;
    }

    public static SpellType fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (SpellType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(SpellType::id).toList();
    }
}
