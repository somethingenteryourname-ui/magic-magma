package dev.magicnuke.command;

import dev.magicnuke.MagicNuke;
import dev.magicnuke.Msg;
import dev.magicnuke.nuke.NukeSize;
import dev.magicnuke.nuke.PlacedNuke;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /nuke give <size|radius> [amount] [player]
 * /nuke launch <size|radius> [x y z [world]]
 * /nuke strike <size|radius> [player | x y z [world]]
 * /nuke sizes | list | clear [radius] | pack [player] | reload
 */
public final class NukeCommand implements TabExecutor {

    private static final String ADMIN = "magicnuke.admin";
    private final MagicNuke plugin;

    public NukeCommand(MagicNuke plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, label, args);
            case "launch" -> launch(sender, label, args);
            case "strike" -> strike(sender, label, args);
            case "sizes" -> sizes(sender);
            case "list" -> list(sender);
            case "clear" -> clear(sender, args);
            case "pack" -> pack(sender, args);
            case "reload" -> {
                if (!perm(sender, ADMIN)) return true;
                plugin.reload();
                Msg.send(sender, "<green>Config reloaded. <gray>" + plugin.settings().sizes.size() + " sizes loaded.");
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        String l = "/" + label;
        Msg.send(s, "<gold>Commands:");
        if (s.hasPermission(ADMIN)) {
            s.sendMessage(Msg.mm("<yellow>" + l + " give <size|radius> [amount] [player] <gray>- get nukes"));
            s.sendMessage(Msg.mm("<yellow>" + l + " launch <size|radius> [x y z] [world] <gray>- set up and launch one right away"));
            s.sendMessage(Msg.mm("<yellow>" + l + " strike <size|radius> [player | x y z] <gray>- drop one from the sky"));
            s.sendMessage(Msg.mm("<yellow>" + l + " list <gray>- placed nukes"));
            s.sendMessage(Msg.mm("<yellow>" + l + " clear [radius] <gray>- remove placed nukes"));
            s.sendMessage(Msg.mm("<yellow>" + l + " reload <gray>- reload config.yml"));
        }
        s.sendMessage(Msg.mm("<yellow>" + l + " sizes <gray>- list nuke sizes"));
        s.sendMessage(Msg.mm("<yellow>" + l + " pack <gray>- re-download the resource pack"));
    }

    // ------------------------------------------------------------------ give

    private void give(CommandSender s, String label, String[] args) {
        if (!perm(s, ADMIN)) return;
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /" + label + " give <size|radius> [amount] [player]");
            return;
        }
        NukeSize size = size(s, args[1]);
        if (size == null) return;
        int amount = 1;
        Player target = s instanceof Player p ? p : null;
        for (int i = 2; i < args.length; i++) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[i])));
            } catch (NumberFormatException e) {
                target = Bukkit.getPlayerExact(args[i]);
                if (target == null) {
                    Msg.send(s, "<red>Player not found: <white>" + Msg.escape(args[i]));
                    return;
                }
            }
        }
        if (target == null) {
            Msg.send(s, "<red>Say which player to give it to.");
            return;
        }
        ItemStack item = plugin.items().create(size, amount);
        Player to = target;
        to.getInventory().addItem(item).values().forEach(left -> to.getWorld().dropItemNaturally(to.getLocation(), left));
        Msg.send(s, "<green>Gave <white>" + amount + "x</white> " + size.name() + " <green>to <white>" + to.getName() + "<green>.");
        if (to != s) Msg.send(to, "<green>You got <white>" + amount + "x</white> " + size.name() + "<green>. Handle with care.");
        if (!plugin.pack().hasPack(to) && plugin.settings().packEnabled) {
            Msg.send(to, "<gray>Tip: the 3D nuke needs the resource pack. Type <yellow>/nuke pack</yellow> if it looks like a TNT block or a purple cube.");
        }
    }

    // ------------------------------------------------------------------ launch / strike

    private void launch(CommandSender s, String label, String[] args) {
        if (!perm(s, ADMIN)) return;
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /" + label + " launch <size|radius> [x y z] [world]");
            return;
        }
        NukeSize size = size(s, args[1]);
        if (size == null) return;
        Location at = location(s, args, 2);
        if (at == null) return;
        if (plugin.settings().isDisabled(at.getWorld())) {
            Msg.send(s, "<red>Nukes are disabled in that world.");
            return;
        }
        Block b = at.getBlock();
        if (!b.isPassable()) b = b.getRelative(0, 1, 0);
        plugin.nukes().launchAt(b.getLocation().add(0.5, 0, 0.5), size);
        Msg.send(s, "<gold>Launching a " + size.name() + " <gold>at <white>" + fmt(b.getLocation()) + "<gold>.");
    }

    private void strike(CommandSender s, String label, String[] args) {
        if (!perm(s, ADMIN)) return;
        if (args.length < 2) {
            Msg.send(s, "<red>Usage: /" + label + " strike <size|radius> [player | x y z [world]]");
            return;
        }
        NukeSize size = size(s, args[1]);
        if (size == null) return;
        Location at;
        if (args.length == 3) {
            Player p = Bukkit.getPlayerExact(args[2]);
            if (p == null) {
                Msg.send(s, "<red>Player not found: <white>" + Msg.escape(args[2]));
                return;
            }
            at = p.getLocation();
        } else {
            at = location(s, args, 2);
        }
        if (at == null) return;
        if (plugin.settings().isDisabled(at.getWorld())) {
            Msg.send(s, "<red>Nukes are disabled in that world.");
            return;
        }
        Location ground = at.getWorld().getHighestBlockAt(at).getLocation().add(0.5, 1, 0.5);
        if (ground.getY() > at.getY() + 1 && !(s instanceof Player)) ground = at.getBlock().getLocation().add(0.5, 0, 0.5);
        plugin.nukes().strike(ground, size);
        Msg.send(s, "<red>☢ Strike inbound on <white>" + fmt(ground) + "<red>.");
    }

    /**
     * Location from "x y z [world]" arguments (supports ~ and ~offset), or, when none
     * are given, the block the player is looking at.
     */
    private Location location(CommandSender s, String[] args, int from) {
        Location origin = origin(s);
        if (args.length > from) {
            if (args.length < from + 3) {
                Msg.send(s, "<red>Give all three coordinates: x y z");
                return null;
            }
            World world = origin != null ? origin.getWorld() : Bukkit.getWorlds().get(0);
            if (args.length > from + 3) {
                world = Bukkit.getWorld(args[from + 3]);
                if (world == null) {
                    Msg.send(s, "<red>World not found: <white>" + Msg.escape(args[from + 3]));
                    return null;
                }
            }
            if (origin == null || origin.getWorld() != world) origin = world.getSpawnLocation();
            try {
                double x = coord(args[from], origin.getX());
                double z = coord(args[from + 2], origin.getZ());
                double y;
                if (args[from + 1].equals("~") && !(s instanceof Player)) {
                    y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
                } else {
                    y = coord(args[from + 1], origin.getY());
                }
                return new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                Msg.send(s, "<red>Bad coordinates.");
                return null;
            }
        }
        if (s instanceof Player p) {
            Block b = p.getTargetBlockExact(160);
            if (b == null) {
                Msg.send(s, "<red>Look at a block (within 160 blocks) or give coordinates.");
                return null;
            }
            return b.getLocation().add(0, 1, 0);
        }
        Msg.send(s, "<red>Give coordinates: x y z [world]");
        return null;
    }

    private static Location origin(CommandSender s) {
        if (s instanceof Entity e) return e.getLocation();
        if (s instanceof BlockCommandSender b) return b.getBlock().getLocation();
        return null;
    }

    private static double coord(String arg, double base) {
        if (arg.startsWith("~")) {
            return base + (arg.length() == 1 ? 0 : Double.parseDouble(arg.substring(1)));
        }
        return Double.parseDouble(arg);
    }

    // ------------------------------------------------------------------ info

    private void sizes(CommandSender s) {
        Msg.send(s, "<gold>Nuke sizes:");
        for (NukeSize size : plugin.settings().sizes.values()) {
            s.sendMessage(Msg.mm(" <dark_gray>•</dark_gray> <yellow>" + size.id() + "</yellow> <dark_gray>-</dark_gray> " + size.name()
                    + " <gray>radius <white>" + (int) size.radius() + "</white>, flies <white>" + (int) size.flightHeight() + "</white> up"));
        }
        s.sendMessage(Msg.mm(" <dark_gray>•</dark_gray> <gray>or any number from <white>" + (int) plugin.settings().customMinRadius
                + "</white> to <white>" + (int) plugin.settings().customMaxRadius + "</white> for a custom radius, e.g. <yellow>/nuke give 33"));
    }

    private void list(CommandSender s) {
        if (!perm(s, ADMIN)) return;
        List<PlacedNuke> all = new ArrayList<>(plugin.nukes().placed());
        Msg.send(s, "<gold>" + all.size() + " placed nuke(s) in loaded chunks, <red>" + plugin.nukes().flightCount()
                + "</red> in flight, <yellow>" + plugin.explosions().activeCount() + "</yellow> explosion effects running.");
        int shown = 0;
        for (PlacedNuke p : all) {
            if (shown++ >= 15) {
                s.sendMessage(Msg.mm(" <gray>..."));
                break;
            }
            s.sendMessage(Msg.mm(" <dark_gray>•</dark_gray> " + p.size().name() + " <gray>at <white>" + fmt(p.base())));
        }
    }

    private void clear(CommandSender s, String[] args) {
        if (!perm(s, ADMIN)) return;
        double radius = -1;
        if (args.length > 1) {
            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                Msg.send(s, "<red>Radius must be a number.");
                return;
            }
        }
        Location origin = origin(s);
        int n = 0;
        for (PlacedNuke p : plugin.nukes().placed()) {
            if (radius > 0) {
                if (origin == null || p.body().getWorld() != origin.getWorld() || p.base().distance(origin) > radius) continue;
            }
            plugin.nukes().remove(p);
            n++;
        }
        Msg.send(s, "<green>Removed <white>" + n + "</white> placed nuke(s)" + (radius > 0 ? " within " + (int) radius + " blocks." : " in loaded chunks."));
    }

    private void pack(CommandSender s, String[] args) {
        Player target;
        if (args.length > 1) {
            if (!perm(s, ADMIN)) return;
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Msg.send(s, "<red>Player not found: <white>" + Msg.escape(args[1]));
                return;
            }
        } else if (s instanceof Player p) {
            if (!perm(s, "magicnuke.pack")) return;
            target = p;
        } else {
            Msg.send(s, "<gray>Pack file: <white>plugins/MagicNuke/MagicNuke-ResourcePack.zip <gray>(sha1 " + plugin.pack().sha1() + ")");
            Msg.send(s, "<gray>Built-in host running: <white>" + plugin.pack().isHosting());
            return;
        }
        String url = plugin.pack().urlFor(target);
        if (url == null) {
            Msg.send(s, "<red>The resource pack isn't available. <gray>Check pack settings in config.yml and the console.");
            return;
        }
        plugin.pack().send(target);
        Msg.send(s, "<green>Sending the resource pack to <white>" + target.getName() + "<green>.");
        if (s.hasPermission(ADMIN)) s.sendMessage(Msg.mm(" <dark_gray>URL: <gray>" + Msg.escape(url)));
    }

    // ------------------------------------------------------------------ helpers

    private NukeSize size(CommandSender s, String arg) {
        NukeSize size = plugin.settings().resolve(arg);
        if (size == null) {
            Msg.send(s, "<red>Unknown size <white>" + Msg.escape(arg) + "</white>. Use one of <yellow>"
                    + String.join(", ", plugin.settings().sizes.keySet()) + "</yellow> or a radius from "
                    + (int) plugin.settings().customMinRadius + " to " + (int) plugin.settings().customMaxRadius + ".");
        }
        return size;
    }

    private static boolean perm(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        Msg.send(s, "<red>You don't have permission to do that.");
        return false;
    }

    private static String fmt(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission(ADMIN);
        if (args.length == 1) {
            out.add("sizes");
            out.add("pack");
            out.add("help");
            if (admin) out.addAll(List.of("give", "launch", "strike", "list", "clear", "reload"));
        } else if (admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && (sub.equals("give") || sub.equals("launch") || sub.equals("strike"))) {
                out.addAll(plugin.settings().sizes.keySet());
                out.add("25");
            } else if (sub.equals("give") && args.length == 3) {
                out.addAll(List.of("1", "16", "64"));
            } else if ((sub.equals("give") && args.length == 4) || (sub.equals("strike") && args.length == 3)
                    || (sub.equals("pack") && args.length == 2)) {
                out.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                if (sub.equals("strike")) out.add("~");
            } else if ((sub.equals("launch") || sub.equals("strike")) && args.length >= 3 && args.length <= 5) {
                out.add("~");
            } else if ((sub.equals("launch") || sub.equals("strike")) && args.length == 6) {
                Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
            } else if (sub.equals("clear") && args.length == 2) {
                out.addAll(List.of("16", "64", "256"));
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
