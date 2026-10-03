package dev.shardwatch.echo;

/**
 * Filter for echo lookups. Null fields are not filtered. {@code radius < 0} means anywhere in the world (or every world
 * when {@code world} is null).
 */
public record EchoQuery(String actor, Echo.Action action, String world, int cx, int cy, int cz, int radius, long since,
                        String text, Boolean rewound) {

    public static EchoQuery any() {
        return new EchoQuery(null, null, null, 0, 0, 0, -1, 0, null, null);
    }
}
