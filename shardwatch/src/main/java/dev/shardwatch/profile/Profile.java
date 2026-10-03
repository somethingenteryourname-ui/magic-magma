package dev.shardwatch.profile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player staff state: Facet, toggles, and (from Stage 4) Lustre progress. Mutated on the main thread only. */
public final class Profile {

    private final UUID uuid;
    private String name;
    private String facet;
    private long lustre;
    private long lifetime;
    private final Map<String, Integer> refinements = new LinkedHashMap<>();
    private final Set<String> keepsakes = new LinkedHashSet<>();
    private String activeAura;
    private String activeSigil;
    private boolean alertsOn = true;
    private boolean fxOn = true;
    private boolean veiled;
    private long dailyDay;
    private long dailyEarned;

    public Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String facet() {
        return facet;
    }

    public void facet(String facet) {
        this.facet = facet;
    }

    public long lustre() {
        return lustre;
    }

    public void lustre(long lustre) {
        this.lustre = lustre;
    }

    /** Total Lustre ever earned; levels are based on this so spending never lowers your level. */
    public long lifetime() {
        return lifetime;
    }

    public void lifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    public Map<String, Integer> refinements() {
        return refinements;
    }

    public int tier(String tool) {
        return refinements.getOrDefault(tool, 1);
    }

    public Set<String> keepsakes() {
        return keepsakes;
    }

    public String activeAura() {
        return activeAura;
    }

    public void activeAura(String a) {
        this.activeAura = a;
    }

    public String activeSigil() {
        return activeSigil;
    }

    public void activeSigil(String s) {
        this.activeSigil = s;
    }

    public boolean alertsOn() {
        return alertsOn;
    }

    public void alertsOn(boolean b) {
        this.alertsOn = b;
    }

    public boolean fxOn() {
        return fxOn;
    }

    public void fxOn(boolean b) {
        this.fxOn = b;
    }

    public boolean veiled() {
        return veiled;
    }

    public void veiled(boolean b) {
        this.veiled = b;
    }

    public long dailyDay() {
        return dailyDay;
    }

    public long dailyEarned() {
        return dailyEarned;
    }

    public void daily(long day, long earned) {
        this.dailyDay = day;
        this.dailyEarned = earned;
    }

    String refinementsCsv() {
        StringBuilder sb = new StringBuilder();
        refinements.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ",").append(k).append(':').append(v));
        return sb.toString();
    }

    void refinementsCsv(String csv) {
        refinements.clear();
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try {
                    refinements.put(kv[0], Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    String keepsakesCsv() {
        return String.join(",", keepsakes);
    }

    void keepsakesCsv(String csv) {
        keepsakes.clear();
        if (csv != null && !csv.isBlank()) {
            keepsakes.addAll(java.util.Arrays.asList(csv.split(",")));
        }
    }
}
