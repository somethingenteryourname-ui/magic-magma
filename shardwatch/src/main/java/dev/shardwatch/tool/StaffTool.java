package dev.shardwatch.tool;

import java.util.Locale;

/** The staff tools. Each one is an amethyst shard with its own item_model, so no vanilla item is replaced. */
public enum StaffTool {
    ECHO_LENS("echo_lens", true),
    TIMEGLASS("timeglass", true),
    VERDICT_GAVEL("verdict_gavel", true),
    PETRIFY_PRISM("petrify_prism", true),
    VEIL_LANTERN("veil_lantern", true),
    FLARE_COMPASS("flare_compass", true),
    GLINT_MONOCLE("glint_monocle", true),
    LUSTRE_SHARD("lustre_shard", false),
    SIGIL("sigil", false);

    private final String id;
    private final boolean tiered;

    StaffTool(String id, boolean tiered) {
        this.id = id;
        this.tiered = tiered;
    }

    public String id() {
        return id;
    }

    /** Has Refinement tiers I–III. */
    public boolean tiered() {
        return tiered;
    }

    /** Part of the staff kit (as opposed to rewards and badges). */
    public boolean kit() {
        return this != LUSTRE_SHARD && this != SIGIL;
    }

    public String permission() {
        return "shardwatch.tool." + id;
    }

    public static StaffTool parse(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        for (StaffTool t : values()) {
            if (t.id.equals(low) || t.name().equalsIgnoreCase(low)) {
                return t;
            }
        }
        return null;
    }
}
