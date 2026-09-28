package dev.magicnuke.nuke;

/**
 * One nuke size: either a preset from config.yml or a custom radius.
 *
 * @param id           preset id, or {@link #CUSTOM_ID}
 * @param name         display name in MiniMessage format
 * @param radius       crater radius in blocks
 * @param modelScale   scale of the 3D missile (1.0 = 2.5 blocks tall)
 * @param flightHeight blocks flown up before turning around
 * @param fuseTicks    countdown before liftoff
 */
public record NukeSize(String id, String name, double radius, double modelScale, double flightHeight, int fuseTicks) {

    public static final String CUSTOM_ID = "custom";

    /** Height of the missile model in blocks at scale 1. */
    public static final double MODEL_HEIGHT = 2.5;
    /** Width of the missile incl. fins in blocks at scale 1. */
    public static final double MODEL_WIDTH = 1.0;

    public static NukeSize custom(double radius) {
        double scale = clamp(0.7 + radius / 42.0, 0.7, 3.0);
        double height = clamp(40 + radius * 1.5, 40, 200);
        int fuse = (int) clamp(60 + radius, 60, 120);
        String name = "<gradient:#ffb347:#ff1f4b>Custom Nuke</gradient> <gray>(" + (int) Math.round(radius) + ")";
        return new NukeSize(CUSTOM_ID, name, radius, scale, height, fuse);
    }

    public double height() {
        return MODEL_HEIGHT * modelScale;
    }

    public double halfHeight() {
        return height() / 2.0;
    }

    public boolean isCustom() {
        return CUSTOM_ID.equals(id);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
