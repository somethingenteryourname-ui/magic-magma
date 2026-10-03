package dev.shardwatch.verdict;

import java.util.Locale;

/** The five verdicts. */
public enum VerdictType {
    /** Warning. */
    CHIP(false, false, "#F7C6DF"),
    /** Mute. */
    HUSH(true, true, "#7FE8E0"),
    /** Kick. */
    EJECT(false, false, "#F59AC8"),
    /** Ban (timed or permanent). */
    ENCASE(true, true, "#D9468F"),
    /** Freeze. */
    PETRIFY(false, true, "#B7F2FF");

    private final boolean timed;
    private final boolean lasting;
    private final String color;

    VerdictType(boolean timed, boolean lasting, String color) {
        this.timed = timed;
        this.lasting = lasting;
        this.color = color;
    }

    /** Takes a duration argument. */
    public boolean timed() {
        return timed;
    }

    /** Stays in force until it expires or is revoked (as opposed to one-off Chip/Eject). */
    public boolean lasting() {
        return lasting;
    }

    public String color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String permission() {
        return "shardwatch.verdict." + id();
    }

    public static VerdictType parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
