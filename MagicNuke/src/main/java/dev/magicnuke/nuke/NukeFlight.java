package dev.magicnuke.nuke;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.Msg;
import dev.magicnuke.fx.Fx;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;

/**
 * A lit nuke: countdown, liftoff straight up, flip over at the top, then a
 * whistling fall back down onto the launch pad and detonation on impact.
 */
public final class NukeFlight extends BukkitRunnable {

    private enum Phase {FUSE, ASCENT, TURN, FALL}

    private static final int TURN_TICKS = 32;
    private static final double GRAVITY = 0.085;
    private static final double MAX_FALL_SPEED = 4.2;

    private final MagicNuke plugin;
    private final ItemDisplay body;
    private final NukeSize size;
    private final Location ground;
    private final World world;
    private final float scale;
    private final double halfH;
    private final int fuseTicks;
    private final int ascentTicks;

    private Phase phase;
    private int t;
    private int totalTicks;
    private double y;
    private double vy;
    private final double startY;
    private double apexY;
    private float roll;
    private float pitch;
    private boolean finished;

    NukeFlight(MagicNuke plugin, ItemDisplay body, NukeSize size, Location ground, int fuseTicks) {
        this.plugin = plugin;
        this.body = body;
        this.size = size;
        this.ground = ground.clone();
        this.world = ground.getWorld();
        this.scale = (float) size.modelScale();
        this.halfH = size.halfHeight();
        this.fuseTicks = Math.max(0, fuseTicks);
        this.ascentTicks = (int) Math.max(45, Math.min(150, size.flightHeight() * 0.85));
        this.startY = ground.getY() + halfH;
        this.y = startY;
        this.apexY = startY + size.flightHeight();
        this.phase = Phase.FUSE;
    }

    /** A nuke that is already high in the air, falling onto a target. */
    static NukeFlight strike(MagicNuke plugin, ItemDisplay body, NukeSize size, Location target) {
        NukeFlight f = new NukeFlight(plugin, body, size, target, 0);
        f.phase = Phase.FALL;
        f.y = body.getLocation().getY();
        f.apexY = f.y;
        f.pitch = (float) Math.PI;
        f.announceIncoming();
        return f;
    }

    public void start() {
        body.setTeleportDuration(2);
        runTaskTimer(plugin, 0L, 1L);
    }

    public boolean owns(Entity e) {
        return e.getUniqueId().equals(body.getUniqueId());
    }

    public void abort() {
        if (finished) return;
        finished = true;
        try {
            cancel();
        } catch (IllegalStateException ignored) {
        }
        body.remove();
        plugin.nukes().flightEnded(this);
    }

    @Override
    public void run() {
        if (!body.isValid()) {
            // chunk unloaded or entity killed: fizzle out quietly
            abort();
            return;
        }
        totalTicks++;
        switch (phase) {
            case FUSE -> fuse();
            case ASCENT -> ascent();
            case TURN -> turn();
            case FALL -> fall();
        }
        t++;
    }

    // ------------------------------------------------------------------ phases

    private void fuse() {
        double p = fuseTicks == 0 ? 1 : (double) t / fuseTicks;
        if (t == 0) {
            Fx.sound(ground, Sound.ENTITY_TNT_PRIMED, 2f, 0.5f);
            Fx.sound(ground, Sound.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
            for (Player pl : Fx.playersNear(ground, plugin.settings().warningRadius)) {
                Msg.send(pl, "<red>A " + size.name() + " <red>has been armed <gray>(" + (int) pl.getLocation().distance(ground) + " blocks away)");
            }
        }
        if (t >= fuseTicks) {
            phase = Phase.ASCENT;
            t = -1;
            liftoff();
            return;
        }
        // shake harder as the countdown runs out
        float j = (float) ((0.01 + 0.05 * p) * scale);
        setTransform(new Vector3f((float) Fx.r(-j, j), (float) Fx.r(-j * 0.5, j * 0.5), (float) Fx.r(-j, j)));

        Location nozzle = ground.clone().add(0, 0.25 * scale, 0);
        Fx.burst(world, Particle.SMOKE, nozzle, 3 + (int) (6 * p), 0.3 * scale, 0.05, 0.3 * scale, 0.02);
        if (p > 0.4) Fx.burst(world, Particle.SMALL_FLAME, nozzle, 2 + (int) (6 * p), 0.25 * scale, 0.05, 0.25 * scale, 0.02);
        if (t % 3 == 0) {
            Location spark = ground.clone().add(Fx.r(-0.4, 0.4) * scale, Fx.r(0.2, 2.3) * scale, Fx.r(-0.4, 0.4) * scale);
            Fx.burst(world, Particle.ELECTRIC_SPARK, spark, 4, 0.1, 0.02);
        }
        // klaxon alarm
        if (t % 8 == 0) Fx.sound(ground, Sound.BLOCK_NOTE_BLOCK_BIT, 3f, (t / 8) % 2 == 0 ? 0.7f : 1.0f);
        if (t % 20 == 0) {
            int secs = (int) Math.ceil((fuseTicks - t) / 20.0);
            Title title = Title.title(
                    Msg.mm("<dark_red>☢ <red><bold>NUCLEAR LAUNCH DETECTED</bold></red> <dark_red>☢"),
                    Msg.mm("<yellow>Liftoff in <gold><bold>" + secs + "</bold></gold>..."),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(250)));
            for (Player pl : Fx.playersNear(ground, plugin.settings().warningRadius)) pl.showTitle(title);
            Fx.sound(ground, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 3f, 0.5f);
        }
    }

    private void liftoff() {
        Fx.sound(ground, Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.5f);
        Fx.sound(ground, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 6f, 0.5f);
        Fx.sound(ground, Sound.ENTITY_BLAZE_SHOOT, 6f, 0.5f);
        Fx.sound(ground, Sound.ENTITY_BREEZE_WIND_BURST, 6f, 0.6f);
        Location pad = ground.clone().add(0, 0.3, 0);
        Fx.burst(world, Particle.EXPLOSION, pad, 4, 0.8 * scale, 0.2, 0.8 * scale, 0);
        Fx.flash(world, pad, Color.fromRGB(255, 200, 120));
        groundBlast(1.0);
        for (Player pl : Fx.playersNear(ground, 24 * scale)) pl.sendHurtAnimation(Fx.rnd().nextFloat() * 360f);
    }

    /** Smoke and flame rolling out across the ground under the launch pad. */
    private void groundBlast(double strength) {
        Location pad = ground.clone().add(0, 0.2, 0);
        int n = (int) (36 * strength) + 6;
        for (int i = 0; i < n; i++) {
            double a = Fx.r(0, Math.PI * 2);
            Vector dir = new Vector(Math.cos(a), Fx.r(0.0, 0.12), Math.sin(a));
            Fx.shoot(world, Particle.CAMPFIRE_COSY_SMOKE, pad, dir, Fx.r(0.15, 0.4) * scale * strength);
            Fx.shoot(world, Particle.CLOUD, pad, dir, Fx.r(0.3, 0.8) * scale * strength);
            if (i % 3 == 0) Fx.shoot(world, Particle.FLAME, pad, dir, Fx.r(0.2, 0.5) * scale * strength);
        }
    }

    private void ascent() {
        double u = Math.min(1.0, (double) t / ascentTicks);
        double prevY = y;
        // quick-ish start, gentle coast to the apex
        double e = 1 - Math.pow(1 - u, 2.2);
        y = startY + (apexY - startY) * e;
        double speed = y - prevY;
        roll += 0.10f;
        move(null);
        exhaust(speed, 1.0);
        if (t < 25) groundBlast(1.0 - t / 25.0);
        if (t % 5 == 0) Fx.sound(nozzle(), Sound.ENTITY_BLAZE_SHOOT, 5f, 0.5f + (float) (0.3 * u));
        if (t % 9 == 0) Fx.sound(nozzle(), Sound.BLOCK_FIRE_AMBIENT, 5f, 0.5f);
        if (u >= 1.0) {
            phase = Phase.TURN;
            t = -1;
        }
    }

    private void turn() {
        double u = (double) (t + 1) / TURN_TICKS;
        double s = u * u * (3 - 2 * u);
        pitch = (float) (Math.PI * s);
        y += 0.05 * (1 - u);
        roll += 0.06f * (float) (1 - u);
        move(null);
        if (t < 8) exhaust(0.1, 1.0 - t / 8.0);
        if (t == 0) {
            Fx.sound(nozzle(), Sound.BLOCK_FIRE_EXTINGUISH, 6f, 0.5f);
            Fx.burst(world, Particle.LARGE_SMOKE, nozzle(), 30, 0.4 * scale, 0.05);
        }
        if (t == 14) announceIncoming();
        if (t + 1 >= TURN_TICKS) {
            phase = Phase.FALL;
            t = -1;
            vy = 0;
        }
    }

    private void announceIncoming() {
        Title title = Title.title(
                Msg.mm("<dark_red>☢ <red><bold>INCOMING</bold></red> <dark_red>☢"),
                Msg.mm("<yellow>Take cover!"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(500)));
        for (Player pl : Fx.playersNear(ground, plugin.settings().warningRadius)) {
            pl.showTitle(title);
            Fx.distantSound(pl, ground.clone().add(0, size.flightHeight(), 0), Sound.ITEM_GOAT_HORN_SOUND_0, 1.5f, 0.8f);
        }
    }

    private void fall() {
        vy = Math.max(vy - GRAVITY, -MAX_FALL_SPEED);
        double noseY = y - halfH;
        Location nose = new Location(world, ground.getX(), noseY, ground.getZ());
        double dist = -vy + 0.15;
        RayTraceResult hit = world.rayTraceBlocks(nose, new Vector(0, -1, 0), dist, FluidCollisionMode.ALWAYS, true);
        if (hit != null) {
            impact(hit.getHitPosition().toLocation(world));
            return;
        }
        if (noseY + vy < world.getMinHeight() || totalTicks > 20 * 90) {
            impact(new Location(world, ground.getX(), Math.max(world.getMinHeight(), ground.getY()), ground.getZ()));
            return;
        }
        y += vy;
        move(null);

        double range = Math.max(1, apexY - ground.getY());
        double remaining = Math.max(0, Math.min(1, (y - ground.getY()) / range));

        // re-entry glow at the nose and a streak behind
        Location tip = new Location(world, ground.getX(), y - halfH, ground.getZ());
        Location tail = new Location(world, ground.getX(), y + halfH, ground.getZ());
        Fx.burst(world, Particle.FLAME, tip, 4, 0.2 * scale, 0.1, 0.2 * scale, 0.03);
        if (t % 2 == 0) Fx.burst(world, Particle.LAVA, tip, 1, 0.2 * scale, 0);
        Fx.burst(world, Particle.FIREWORK, tail, 3, 0.2 * scale, 0.3, 0.2 * scale, 0.02);
        Fx.burst(world, Particle.CLOUD, tail, 2, 0.2 * scale, 0.3, 0.2 * scale, 0.01);
        if (t % 3 == 0) Fx.burst(world, Particle.CAMPFIRE_SIGNAL_SMOKE, tail, 1, 0.1, 0.004);

        // target marker closing in on ground zero
        if (t % 2 == 0) targetMarker(remaining);

        // falling whistle, dropping in pitch
        if (t % 2 == 0) {
            float pitchF = Fx.clampPitch(0.5 + 1.5 * remaining);
            Location at = new Location(world, ground.getX(), y, ground.getZ());
            Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_FLUTE, 4f, pitchF);
            for (Player pl : Fx.playersNear(ground, plugin.settings().warningRadius)) {
                Fx.distantSound(pl, at, Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.6f, pitchF);
            }
        }
        if (t == 0) Fx.sound(new Location(world, ground.getX(), y, ground.getZ()), Sound.ITEM_ELYTRA_FLYING, 6f, 0.6f);
    }

    private void targetMarker(double remaining) {
        double gy = ground.getY() + 0.15;
        double rr = Math.max(1.5, size.radius() * Math.max(0.08, remaining));
        int n = (int) Math.min(90, Math.max(12, rr * Math.PI * 2 / 1.4));
        Color red = Color.fromRGB(255, 30, 30);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Location at = new Location(world, ground.getX() + Math.cos(a) * rr, gy, ground.getZ() + Math.sin(a) * rr);
            Fx.dust(world, at, 1, 0, red, 2.2f);
        }
        for (int i = 0; i < 6; i++) {
            Location at = new Location(world, ground.getX(), gy + i * 0.6, ground.getZ());
            Fx.dust(world, at, 1, 0.05, Color.fromRGB(255, 70, 40), 1.6f);
        }
    }

    private void impact(Location at) {
        if (finished) return;
        finished = true;
        cancel();
        body.remove();
        plugin.nukes().flightEnded(this);
        plugin.explosions().detonate(at, size);
    }

    // ------------------------------------------------------------------ helpers

    private Location nozzle() {
        // the nozzle is at the opposite end from the nose
        double dirY = Math.cos(pitch);
        return new Location(world, ground.getX(), y - dirY * halfH, ground.getZ());
    }

    private void exhaust(double speed, double strength) {
        Location noz = nozzle();
        double s = scale;
        double dirY = -Math.cos(pitch);
        double dirZ = -Math.sin(pitch);
        int flames = (int) Math.max(1, 7 * strength);
        for (int i = 0; i < flames; i++) {
            Vector v = new Vector(Fx.r(-0.18, 0.18), dirY, dirZ + Fx.r(-0.18, 0.18));
            Fx.shoot(world, Particle.FLAME, noz, v, (0.35 + Math.abs(speed) * 0.5) * s);
        }
        Fx.burst(world, Particle.LARGE_SMOKE, noz, (int) (3 * strength) + 1, 0.3 * s, 0.2, 0.3 * s, 0.02);
        Fx.dust(world, noz, (int) (4 * strength) + 1, 0.25 * s, 0.1, 0.25 * s, Color.fromRGB(255, Fx.rnd().nextInt(110, 190), 30), (float) (2.4 * s));
        if (strength > 0.5) Fx.burst(world, Particle.CAMPFIRE_SIGNAL_SMOKE, noz, 2, 0.15 * s, 0.1, 0.15 * s, 0.01);
        if (t % 2 == 0) Fx.burst(world, Particle.FIREWORK, noz, 2, 0.2 * s, 0.1);
        if (t % 4 == 0) Fx.burst(world, Particle.LAVA, noz, 1, 0.2 * s, 0);
    }

    private void move(Vector3f jitter) {
        Location to = new Location(world, ground.getX(), y, ground.getZ(), body.getLocation().getYaw(), 0);
        body.teleport(to);
        setTransform(jitter == null ? new Vector3f() : jitter);
    }

    private void setTransform(Vector3f translation) {
        Quaternionf rot = new Quaternionf().rotateX(pitch).rotateY(roll);
        body.setInterpolationDelay(0);
        body.setInterpolationDuration(2);
        body.setTransformation(new Transformation(translation, rot, new Vector3f(scale, scale, scale), new Quaternionf()));
    }
}
