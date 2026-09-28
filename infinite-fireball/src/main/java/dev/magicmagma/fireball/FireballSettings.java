package dev.magicmagma.fireball;

import org.bukkit.configuration.file.FileConfiguration;

/** The fireball settings from config.yml. Setters write the new value back to the file. */
public final class FireballSettings {

    private final InfiniteFireball plugin;

    private double size;
    private double damageHearts;
    private boolean totemMode;
    private boolean breakBlocks;
    private boolean setFire;
    private double speed;
    private double cooldownSeconds;
    private boolean hurtSelf;
    private boolean canBeDeflected;
    private double maxFlightSeconds;
    private boolean trail;

    private double maxSize;
    private double maxDamage;
    private double maxSpeed;

    public FireballSettings(InfiniteFireball plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        maxSize = Math.max(0.1, config.getDouble("limits.max-size", 20.0));
        maxDamage = Math.max(0.0, config.getDouble("limits.max-damage", 1000.0));
        maxSpeed = Math.max(0.1, config.getDouble("limits.max-speed", 5.0));

        size = clamp(config.getDouble("fireball.size", 2.0), 0.1, maxSize);
        damageHearts = clamp(config.getDouble("fireball.damage", 5.0), 0.0, maxDamage);
        totemMode = config.getBoolean("fireball.totem-mode", false);
        breakBlocks = config.getBoolean("fireball.break-blocks", false);
        setFire = config.getBoolean("fireball.set-fire", false);
        speed = clamp(config.getDouble("fireball.speed", 1.5), 0.1, maxSpeed);
        cooldownSeconds = Math.max(0.0, config.getDouble("fireball.cooldown", 0.25));
        hurtSelf = config.getBoolean("fireball.hurt-self", false);
        canBeDeflected = config.getBoolean("fireball.can-be-deflected", false);
        maxFlightSeconds = Math.max(1.0, config.getDouble("fireball.max-flight-seconds", 10.0));
        trail = config.getBoolean("fireball.trail", true);
    }

    private void save(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public double size() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
        save("fireball.size", size);
    }

    public double damageHearts() {
        return damageHearts;
    }

    public void setDamageHearts(double damageHearts) {
        this.damageHearts = damageHearts;
        save("fireball.damage", damageHearts);
    }

    public boolean totemMode() {
        return totemMode;
    }

    public void setTotemMode(boolean totemMode) {
        this.totemMode = totemMode;
        save("fireball.totem-mode", totemMode);
    }

    public boolean breakBlocks() {
        return breakBlocks;
    }

    public void setBreakBlocks(boolean breakBlocks) {
        this.breakBlocks = breakBlocks;
        save("fireball.break-blocks", breakBlocks);
    }

    public boolean setFire() {
        return setFire;
    }

    public void setSetFire(boolean setFire) {
        this.setFire = setFire;
        save("fireball.set-fire", setFire);
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
        save("fireball.speed", speed);
    }

    public long cooldownMillis() {
        return Math.round(cooldownSeconds * 1000.0);
    }

    public double cooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(double cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
        save("fireball.cooldown", cooldownSeconds);
    }

    public boolean hurtSelf() {
        return hurtSelf;
    }

    public void setHurtSelf(boolean hurtSelf) {
        this.hurtSelf = hurtSelf;
        save("fireball.hurt-self", hurtSelf);
    }

    public boolean canBeDeflected() {
        return canBeDeflected;
    }

    public int maxFlightTicks() {
        return (int) Math.round(maxFlightSeconds * 20.0);
    }

    public boolean trail() {
        return trail;
    }

    public double maxSize() {
        return maxSize;
    }

    public double maxDamage() {
        return maxDamage;
    }

    public double maxSpeed() {
        return maxSpeed;
    }
}
