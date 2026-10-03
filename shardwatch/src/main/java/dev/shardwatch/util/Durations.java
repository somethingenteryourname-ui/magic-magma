package dev.shardwatch.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats durations like {@code 30m}, {@code 2h}, {@code 1w2d}. A duration of 0 means permanent.
 */
public final class Durations {

    public static final long PERMANENT = 0L;
    private static final Pattern PART = Pattern.compile("(\\d+)(mo|s|m|h|d|w|y)");

    private Durations() {
    }

    /** True if the token looks like a duration or a "permanent" keyword. */
    public static boolean looksLikeDuration(String token) {
        return parse(token) != null;
    }

    /**
     * @return milliseconds, {@link #PERMANENT} for perm/permanent/forever, or null if the text is not a duration
     */
    public static Long parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String t = text.toLowerCase(Locale.ROOT);
        if (t.equals("perm") || t.equals("permanent") || t.equals("forever") || t.equals("p")) {
            return PERMANENT;
        }
        Matcher m = PART.matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                return null;
            }
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * unit(m.group(2));
        }
        return end == t.length() && total > 0 ? total : null;
    }

    private static long unit(String u) {
        return switch (u) {
            case "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            case "mo" -> 2_592_000_000L;
            case "y" -> 31_536_000_000L;
            default -> 0L;
        };
    }

    /** Formats a span like {@code 2d 3h 4m}. Shows at most {@code parts} units. */
    public static String format(long millis, int parts) {
        if (millis <= 0) {
            return "0s";
        }
        long[] sizes = {31_536_000_000L, 604_800_000L, 86_400_000L, 3_600_000L, 60_000L, 1_000L};
        String[] names = {"y", "w", "d", "h", "m", "s"};
        StringBuilder sb = new StringBuilder();
        int used = 0;
        long left = millis;
        for (int i = 0; i < sizes.length && used < parts; i++) {
            long n = left / sizes[i];
            if (n > 0) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(n).append(names[i]);
                left -= n * sizes[i];
                used++;
            }
        }
        return sb.isEmpty() ? "1s" : sb.toString();
    }

    public static String format(long millis) {
        return format(millis, 2);
    }

    /** "3m ago" style. */
    public static String ago(long timestamp) {
        long d = System.currentTimeMillis() - timestamp;
        return d < 1000 ? "now" : format(d, 1) + " ago";
    }
}
