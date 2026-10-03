package dev.shardwatch.echo;

/** One logged block change. */
public record Echo(long id, long time, String actorUuid, String actorName, Action action, String world, int x, int y,
                   int z, String oldData, String newData, boolean rewound) {

    public enum Action {
        BREAK, PLACE, EXPLODE, BURN, BUCKET_EMPTY, BUCKET_FILL, ENTITY;

        public static Action parse(String s) {
            try {
                return valueOf(s.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Block id without states, e.g. {@code oak_stairs}. */
    public static String shortId(String data) {
        int bracket = data.indexOf('[');
        String id = bracket >= 0 ? data.substring(0, bracket) : data;
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }
}
