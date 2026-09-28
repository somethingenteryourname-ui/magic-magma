package dev.magicnuke.fx;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.nuke.NukeItems;
import dev.magicnuke.nuke.NukeManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A 3D mushroom cloud built from display entities using the pack's fireball and
 * smoke models: a white-hot fireball that rises and cools into a rolling,
 * churning cap on a smoky stem, with a dust surge racing out along the ground.
 * Particles are layered on top so it also looks good without the resource pack.
 */
final class MushroomCloud extends BukkitRunnable {

    private static final int RING = 10;
    private static final int DOME = 5;
    private static final int STEM = 7;
    private static final int BASE = 12;
    private static final int START = 8;
    private static final int FADE = 60;

    private final MagicNuke plugin;
    private final ExplosionTracker tracker;
    private final Location c;
    private final World world;
    private final double r;
    private final int total;
    private final Map<String, ItemStack> stacks = new HashMap<>();

    private Puff core;
    private final List<Puff> ring = new ArrayList<>();
    private final List<Puff> dome = new ArrayList<>();
    private final List<Puff> stem = new ArrayList<>();
    private final List<Puff> base = new ArrayList<>();
    private int t;

    private final class Puff {
        final ItemDisplay d;
        String model;
        final float spin;

        Puff(Location at, String model, float scale) {
            this.model = model;
            this.spin = (float) Fx.r(0, Math.PI * 2);
            this.d = world.spawn(at, ItemDisplay.class, e -> {
                e.setItemStack(stack(model));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTransformation(transform(scale, new Quaternionf()));
                e.setViewRange(16f);
                e.setTeleportDuration(3);
                e.setPersistent(false);
                e.setBrightness(brightness(model));
                NukeManager.tagFx(plugin, e, NukeManager.ROLE_FX);
            });
            tracker.track(d);
        }

        void update(Location at, String newModel, float scale, Quaternionf rot, int interp) {
            if (!d.isValid()) return;
            if (!newModel.equals(model)) {
                model = newModel;
                d.setItemStack(stack(newModel));
                d.setBrightness(brightness(newModel));
            }
            d.teleport(at);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformation(transform(scale, rot));
        }

        void remove() {
            tracker.untrack(d);
            d.remove();
        }
    }

    MushroomCloud(MagicNuke plugin, ExplosionTracker tracker, Location center, double radius) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.c = center.clone();
        this.world = center.getWorld();
        this.r = radius;
        this.total = plugin.settings().cloudTicks;
    }

    private ItemStack stack(String model) {
        return stacks.computeIfAbsent(model, NukeItems::fxStack);
    }

    private static Display.Brightness brightness(String model) {
        if (model.startsWith("fx_fireball")) return new Display.Brightness(15, 15);
        if (model.equals("fx_smoke_hot")) return new Display.Brightness(11, 15);
        return null;
    }

    private static Transformation transform(float scale, Quaternionf rot) {
        return new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    @Override
    public void run() {
        if (t == 0) {
            core = new Puff(at(c.getX(), c.getY() + r * 0.15, c.getZ()), "fx_fireball_white", 0.5f);
        }
        if (t == 1) {
            core.update(core.d.getLocation(), "fx_fireball_white", (float) (r * 1.15), new Quaternionf(), 6);
        }
        if (t == START) spawnPuffs();
        if (t >= START && t % 2 == 0) animate();
        if (t % 2 == 1) particles();
        if (t >= START + 20 && t < total * 0.7 && Fx.rnd().nextDouble() < 0.035) lightning();
        if (++t > total) {
            finish();
        }
    }

    private void spawnPuffs() {
        Location start = core.d.getLocation();
        for (int i = 0; i < RING; i++) ring.add(new Puff(start, "fx_fireball", 0.5f));
        for (int i = 0; i < DOME; i++) dome.add(new Puff(start, "fx_fireball", 0.5f));
        for (int i = 0; i < STEM; i++) stem.add(new Puff(at(c.getX(), c.getY() + 1, c.getZ()), "fx_smoke_hot", 0.5f));
        Location ground = at(c.getX(), c.getY() + r * 0.08, c.getZ());
        for (int i = 0; i < BASE; i++) base.add(new Puff(ground, "fx_smoke_dust", 0.5f));
    }

    private void animate() {
        int k = t - START;
        double u = Math.min(1, k / (total * 0.45));
        double e = 1 - Math.pow(1 - u, 3);
        double fade = t > total - FADE ? Math.max(0.02, (total - t) / (double) FADE) : 1.0;

        double capY = c.getY() + r * 0.3 + r * 1.9 * e;
        double ringR = r * (0.3 + 0.75 * e);
        float puff = (float) (r * (0.5 + 0.4 * e) * fade);
        double spinA = k * 0.006;
        float roll = k * 0.035f;

        String capModel = k < 45 ? "fx_fireball" : k < 140 ? "fx_smoke_hot" : t < total * 0.72 ? "fx_smoke" : "fx_smoke_light";
        String coreModel = k < 25 ? "fx_fireball" : k < 120 ? "fx_smoke_hot" : capModel;

        core.update(at(c.getX(), capY, c.getZ()), coreModel, (float) (r * (1.05 + 0.35 * e) * fade),
                new Quaternionf().rotateY(roll * 0.3f), 3);

        for (int i = 0; i < ring.size(); i++) {
            double a = spinA + Math.PI * 2 * i / RING;
            double cos = Math.cos(a), sin = Math.sin(a);
            Location at = at(c.getX() + cos * ringR, capY - r * 0.1 + Math.sin(k * 0.05 + i) * r * 0.04, c.getZ() + sin * ringR);
            // roll each puff around the ring's tangent so the cap churns like a vortex
            Quaternionf rot = new Quaternionf().rotateAxis(roll + ring.get(i).spin, (float) -sin, 0f, (float) cos);
            ring.get(i).update(at, capModel, puff, rot, 3);
        }
        for (int i = 0; i < dome.size(); i++) {
            double a = -spinA * 1.5 + Math.PI * 2 * i / DOME;
            double rr = ringR * (i == 0 ? 0 : 0.5);
            Location at = at(c.getX() + Math.cos(a) * rr, capY + r * (0.25 + 0.1 * e), c.getZ() + Math.sin(a) * rr);
            dome.get(i).update(at, capModel, (float) (puff * 1.05), new Quaternionf().rotateY(roll + dome.get(i).spin), 3);
        }
        double stemTop = capY - r * 0.35;
        for (int i = 0; i < stem.size(); i++) {
            double f = (i + 0.5) / STEM;
            double y = c.getY() + (stemTop - c.getY()) * f;
            double wob = Math.sin(k * 0.04 + i * 1.3) * r * 0.04;
            float sc = (float) (r * (0.55 - 0.25 * f) * (0.5 + 0.5 * e) * fade);
            String m = i < 2 ? "fx_smoke_dust" : k < 90 ? "fx_smoke_hot" : t < total * 0.72 ? "fx_smoke" : "fx_smoke_light";
            stem.get(i).update(at(c.getX() + wob, y, c.getZ() - wob), m, sc,
                    new Quaternionf().rotateY(-roll * 1.5f + stem.get(i).spin), 3);
        }
        double eb = 1 - Math.pow(1 - Math.min(1, k / 120.0), 2);
        for (int i = 0; i < base.size(); i++) {
            double a = Math.PI * 2 * i / BASE + base.get(i).spin * 0.1;
            double rr = r * (0.35 + 1.35 * eb);
            Location at = at(c.getX() + Math.cos(a) * rr, c.getY() + r * 0.06, c.getZ() + Math.sin(a) * rr);
            float sc = (float) (r * 0.5 * (1 - 0.35 * eb) * fade);
            base.get(i).update(at, "fx_smoke_dust", sc, new Quaternionf().rotateY(roll + base.get(i).spin), 3);
        }
    }

    private void particles() {
        int k = Math.max(0, t - START);
        double u = Math.min(1, k / (total * 0.45));
        double e = 1 - Math.pow(1 - u, 3);
        double capY = c.getY() + r * 0.3 + r * 1.9 * e;
        double ringR = r * (0.3 + 0.75 * e);
        Location cap = at(c.getX(), capY, c.getZ());
        int dens = (int) Math.min(40, 6 + r / 2);

        Fx.burst(world, Particle.LARGE_SMOKE, cap, dens, ringR * 0.7, r * 0.25, ringR * 0.7, 0.05);
        Fx.burst(world, Particle.CAMPFIRE_SIGNAL_SMOKE, cap, 3, ringR * 0.6, r * 0.2, ringR * 0.6, 0.02);
        Fx.dust(world, cap, dens / 2, ringR * 0.8, r * 0.25, ringR * 0.8, Color.fromRGB(70, 62, 58), 4f);
        if (k < 120) {
            Fx.burst(world, Particle.FLAME, cap, dens, ringR * 0.5, r * 0.2, ringR * 0.5, 0.08);
            Fx.burst(world, Particle.LAVA, cap, 3, ringR * 0.4, r * 0.15, ringR * 0.4, 0);
            Fx.dust(world, cap, dens / 2, ringR * 0.5, r * 0.2, ringR * 0.5,
                    Color.fromRGB(255, Fx.rnd().nextInt(90, 170), 20), 4f);
        }
        // the stem
        for (int i = 0; i < 4; i++) {
            double y = c.getY() + (capY - c.getY()) * Fx.r(0, 0.9);
            Location s = at(c.getX(), y, c.getZ());
            Fx.burst(world, Particle.CAMPFIRE_COSY_SMOKE, s, 2, r * 0.12, 1, r * 0.12, 0.02);
            Fx.dust(world, s, 2, r * 0.15, 1, r * 0.15, Color.fromRGB(95, 80, 66), 4f);
        }
        // condensation ring around the stem
        if (k > 15 && k < 150) {
            double cy = c.getY() + (capY - c.getY()) * 0.55;
            double cr = ringR * 1.25;
            int n = (int) Math.min(70, cr * 1.5);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                Fx.shoot(world, Particle.CLOUD, at(c.getX() + Math.cos(a) * cr, cy, c.getZ() + Math.sin(a) * cr),
                        new org.bukkit.util.Vector(Math.cos(a), 0, Math.sin(a)), 0.05);
            }
        }
    }

    private void lightning() {
        double a = Fx.r(0, Math.PI * 2);
        double d = Fx.r(r * 0.3, r * 1.3);
        Location at = at(c.getX() + Math.cos(a) * d, c.getY(), c.getZ() + Math.sin(a) * d);
        at.setY(world.getHighestBlockYAt(at) + 1);
        world.strikeLightningEffect(at);
    }

    private void finish() {
        if (core != null) core.remove();
        for (List<Puff> list : List.of(ring, dome, stem, base)) {
            for (Puff p : list) p.remove();
        }
        cancel();
        tracker.untrack(this);
    }
}
