package dev.crystalline.resonance.crystal;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The three kinds of Nether crystal. */
public enum CrystalType {
    AMETHYST("amethyst", Material.AMETHYST_SHARD),
    COPPER("copper", Material.COPPER_INGOT),
    QUARTZ("quartz", Material.QUARTZ);

    private final String id;
    private final Material defaultMaterial;

    CrystalType(String id, Material defaultMaterial) {
        this.id = id;
        this.defaultMaterial = defaultMaterial;
    }

    public String id() {
        return id;
    }

    public Material defaultMaterial() {
        return defaultMaterial;
    }

    public static CrystalType fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (CrystalType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(CrystalType::id).toList();
    }
}
