package dev.shardwatch.fx;

import dev.shardwatch.Shardwatch;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Animations built from client-side item displays (Shardwatch crystal models, full-bright, interpolated transforms)
 * plus particles. Every display is non-persistent and tracked so it is removed on disable.
 */
public class Animations {

    protected final Shardwatch plugin;
    private final Set<ItemDisplay> live = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<ItemDisplay>> shells = new HashMap<>();

    public Animations(Shardwatch plugin) {
        this.plugin = plugin;
    }

    protected boolean on() {
        return plugin.getConfig().getBoolean("fx.enabled", true) && plugin.getConfig().getBoolean("fx.animations", true);
    }

    private boolean displays() {
        return on() && plugin.getConfig().getBoolean("fx.display-entities", true);
    }

    // ------------------------------------------------------------------ display helpers

    private ItemDisplay crystal(Location at, String model, float scale, Quaternionf rotation) {
        ItemStack item = plugin.fx().modelItem(Key.key("shardwatch", model));
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setBillboard(Display.Billboard.FIXED);
            e.setShadowRadius(0);
            e.setViewRange(0.6f);
            e.setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(scale), new Quaternionf()));
        });
        live.add(d);
        return d;
    }

    private static Quaternionf yaw(double radians) {
        return new Quaternionf(new AxisAngle4f((float) radians, 0, 1, 0));
    }

    /** Interpolates a display to a new transform over {@code ticks}, starting next tick. */
    private void tween(ItemDisplay d, int delay, Vector3f translation, Quaternionf rotation, float scale, int ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) {
                return;
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(translation, rotation, new Vector3f(scale), new Quaternionf()));
        }, Math.max(1, delay));
    }

    private void removeLater(ItemDisplay d, int ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            live.remove(d);
            if (d.isValid()) {
                d.remove();
            }
        }, ticks);
    }

    // ------------------------------------------------------------------ animations

    /** Eight crystal spikes close in around the player, then shatter as the Encase kick lands. */
    public void encase(Player p, int ticks) {
        if (!on()) {
            return;
        }
        if (displays()) {
            Location c = p.getLocation().add(0, 1.0, 0);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                Location at = c.clone().add(Math.cos(a) * 2.4, (i % 2) * 0.6 - 0.3, Math.sin(a) * 2.4);
                ItemDisplay d = crystal(at, "petrify_prism", 0.2f, yaw(-a));
                Vector3f inward = new Vector3f((float) (-Math.cos(a) * 1.7), 0, (float) (-Math.sin(a) * 1.7));
                tween(d, 2, inward, yaw(-a + Math.PI / 2), 1.3f, Math.max(4, ticks - 6));
                removeLater(d, ticks + 2);
            }
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > ticks) {
                    if (p.isOnline()) {
                        plugin.fx().play("encase-shatter", p.getLocation().add(0, 1, 0));
                    }
                    cancel();
                    return;
                }
                double progress = t / (double) Math.max(1, ticks);
                ring(p.getLocation().add(0, progress * 2.0, 0), 1.6 - progress * 1.1, 14, Color.fromRGB(0xD9468F), Color.fromRGB(0x7FE8E0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A crystal shell that grows around a petrified player and stays (riding them) until released. */
    public void petrify(Player p, boolean on) {
        List<ItemDisplay> old = shells.remove(p.getUniqueId());
        if (old != null) {
            for (ItemDisplay d : old) {
                tween(d, 1, new Vector3f(0, -0.4f, 0), d.getTransformation().getLeftRotation(), 0.01f, 6);
                removeLater(d, 8);
            }
        }
        if (!on()) {
            return;
        }
        Location l = p.getLocation();
        for (int i = 0; i < 4; i++) {
            ring(l.clone().add(0, 0.5 * i, 0), 0.8, 12, Color.fromRGB(on ? 0xB7F2FF : 0xF59AC8), Color.WHITE);
        }
        if (!on || !displays()) {
            return;
        }
        List<ItemDisplay> shell = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            ItemDisplay d = crystal(p.getLocation(), "particle_shard", 0.01f, yaw(-a));
            p.addPassenger(d);
            Vector3f pos = new Vector3f((float) Math.cos(a) * 0.55f, -1.2f + (i % 3) * 0.45f, (float) Math.sin(a) * 0.55f);
            Quaternionf rot = yaw(-a).rotateZ((float) Math.toRadians(70));
            tween(d, 2, pos, rot, 0.65f, 8);
            shell.add(d);
        }
        shells.put(p.getUniqueId(), shell);
    }

    /** A crystal flare rises and spins where a Flare was filed. */
    public void flareBeacon(Location at) {
        if (!on()) {
            return;
        }
        if (displays()) {
            ItemDisplay d = crystal(at.clone().add(0, 1.0, 0), "icon_flare", 0.2f, new Quaternionf());
            tween(d, 2, new Vector3f(0, 2.4f, 0), yaw(Math.PI), 0.9f, 24);
            tween(d, 27, new Vector3f(0, 2.8f, 0), yaw(Math.PI * 2), 0.01f, 8);
            removeLater(d, 36);
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (t++ > 20) {
                    cancel();
                    return;
                }
                at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0, 0.2 * t, 0), 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustTransition(Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0), 1.2f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The new rank's sigil spins above the head on promotion. */
    public void halo(Player p, String hex) {
        if (!on()) {
            return;
        }
        if (displays()) {
            var facet = plugin.facets().of(p);
            ItemDisplay d = crystal(p.getLocation(), "sigil_" + (facet == null ? "shardling" : facet.sigil()), 0.01f, new Quaternionf());
            p.addPassenger(d);
            for (int k = 0; k < 4; k++) {
                tween(d, 2 + k * 10, new Vector3f(0, 0.55f, 0), yaw(Math.PI / 2 * (k + 1)), 0.6f, 10);
            }
            tween(d, 44, new Vector3f(0, 0.9f, 0), yaw(Math.PI * 3), 0.01f, 8);
            removeLater(d, 54);
        }
        Color c = Fx.hex(hex);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 40) {
                    cancel();
                    return;
                }
                ring(p.getLocation().add(0, 2.3, 0), 0.55, 10, c, Color.WHITE);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** A ring of crystal motes sweeps outward when a Rewind starts. */
    public void rewindSweep(Location center, int radius) {
        if (!on()) {
            return;
        }
        int max = Math.max(3, Math.min(radius, 30));
        if (displays()) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                ItemDisplay d = crystal(center.clone().add(0, 0.6, 0), "particle_mote", 0.35f, yaw(-a));
                Vector3f out = new Vector3f((float) Math.cos(a) * max, 0.2f, (float) Math.sin(a) * max);
                tween(d, 2, out, yaw(-a + Math.PI), 0.05f, Math.min(30, max + 6));
                removeLater(d, Math.min(30, max + 6) + 4);
            }
        }
        new BukkitRunnable() {
            int r = 1;

            @Override
            public void run() {
                if (r > max) {
                    cancel();
                    return;
                }
                ring(center.clone().add(0, 0.2, 0), r, r * 6, Color.fromRGB(0x7FE8E0), Color.fromRGB(0xF59AC8));
                r++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A Lustre Shard rises, spins and bursts on a Clarity level-up. */
    public void levelUp(Player p) {
        if (!on()) {
            return;
        }
        if (displays()) {
            ItemDisplay d = crystal(p.getLocation().add(0, 1.2, 0), "lustre_shard", 0.1f, new Quaternionf());
            tween(d, 2, new Vector3f(0, 1.4f, 0), yaw(Math.PI), 1.0f, 18);
            tween(d, 21, new Vector3f(0, 1.6f, 0), yaw(Math.PI * 2), 1.3f, 8);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (d.isValid()) {
                    plugin.fx().play("lustre-burst", d.getLocation().add(0, 1.6, 0));
                }
            }, 30);
            removeLater(d, 31);
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 30) {
                    cancel();
                    return;
                }
                ring(p.getLocation().add(0, t * 0.08, 0), 0.9 - t * 0.02, 12, Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The refined tool floats in front of the player and turns once. */
    public void refine(Player p) {
        if (!on()) {
            return;
        }
        ring(p.getLocation().add(0, 1.0, 0), 0.7, 16, Color.fromRGB(0x7FE8E0), Color.WHITE);
        ring(p.getLocation().add(0, 1.4, 0), 0.5, 12, Color.fromRGB(0xF59AC8), Color.WHITE);
        if (!displays()) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!plugin.tools().isTool(held)) {
            return;
        }
        Location front = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(1.3));
        ItemDisplay d = front.getWorld().spawn(front, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(held.clone());
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.1f), new Quaternionf()));
        });
        live.add(d);
        for (int k = 0; k < 4; k++) {
            tween(d, 2 + k * 6, new Vector3f(0, 0.1f * k, 0), yaw(Math.PI / 2 * (k + 1)), 0.8f, 6);
        }
        tween(d, 28, new Vector3f(0, 0.5f, 0), yaw(Math.PI * 2.5), 0.01f, 6);
        removeLater(d, 36);
    }

    /** Crystal chips fly out of a redeemed Lustre Shard. */
    public void shardBurst(Player p) {
        if (!on()) {
            return;
        }
        Location c = p.getLocation().add(0, 1.2, 0);
        p.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, c, 24, 0.4, 0.4, 0.4, 0,
                new Particle.DustTransition(Color.fromRGB(0xF59AC8), Color.fromRGB(0x7FE8E0), 1.1f));
        if (!displays()) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            ItemDisplay d = crystal(c, "particle_shard", 0.25f, yaw(-a));
            tween(d, 1, new Vector3f((float) Math.cos(a) * 1.2f, 0.6f, (float) Math.sin(a) * 1.2f), yaw(-a + Math.PI), 0.02f, 10);
            removeLater(d, 13);
        }
    }

    /** Removes every display this class spawned (plugin disable). */
    public void shutdown() {
        for (ItemDisplay d : live) {
            if (d.isValid()) {
                d.remove();
            }
        }
        live.clear();
        shells.clear();
    }

    protected static void ring(Location c, double radius, int points, Color from, Color to) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            c.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, c.getX() + Math.cos(a) * radius, c.getY(),
                    c.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, new Particle.DustTransition(from, to, 1.0f), true);
        }
    }
}
