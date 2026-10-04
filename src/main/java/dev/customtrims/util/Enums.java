package dev.customtrims.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String input, E fallback) {
        if (input == null) return fallback;
        String key = input.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, key);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static List<String> names(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> e : values) out.add(e.name().toLowerCase(Locale.ROOT));
        return out;
    }

    /** "galaxy_dust" -> "Galaxy Dust" */
    public static String prettyId(String id) {
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** DOUBLE_HELIX -> "Double Helix" */
    public static String pretty(Enum<?> e) {
        String[] parts = e.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
