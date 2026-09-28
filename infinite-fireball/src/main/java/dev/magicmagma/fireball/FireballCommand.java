package dev.magicmagma.fireball;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** {@code /fireball} (aliases {@code /fb}, {@code /infinitefireball}). */
public final class FireballCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "infinitefireball.admin";
    private static final List<String> SUBCOMMANDS = List.of(
            "give", "size", "damage", "totem", "blocks", "fire", "speed", "cooldown", "hurtself", "settings", "reload", "help");
    private static final List<String> ON_OFF = List.of("on", "off");

    private final InfiniteFireball plugin;

    public FireballCommand(InfiniteFireball plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            send(sender, "<red>You don't have permission to use this command.");
            return true;
        }
        FireballSettings settings = plugin.settings();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "size" -> setNumber(sender, args, "Size", 0.1, settings.maxSize(), settings::setSize, "");
            case "damage" -> setNumber(sender, args, "Damage", 0.0, settings.maxDamage(), settings::setDamageHearts, " hearts");
            case "speed" -> setNumber(sender, args, "Speed", 0.1, settings.maxSpeed(), settings::setSpeed, "");
            case "cooldown" -> setNumber(sender, args, "Cooldown", 0.0, 60.0, settings::setCooldownSeconds, " seconds");
            case "totem" -> {
                Boolean value = parseToggle(sender, args, settings.totemMode());
                if (value != null) {
                    settings.setTotemMode(value);
                    send(sender, value
                            ? "Totem mode <green>ON</green><gray>: every hit pops a totem, or kills if there is none. Armor takes no damage."
                            : "Totem mode <red>OFF</red><gray>: fireballs deal " + format(settings.damageHearts()) + " hearts.");
                }
            }
            case "blocks" -> {
                Boolean value = parseToggle(sender, args, settings.breakBlocks());
                if (value != null) {
                    settings.setBreakBlocks(value);
                    send(sender, "Breaking blocks: " + onOff(value));
                }
            }
            case "fire" -> {
                Boolean value = parseToggle(sender, args, settings.setFire());
                if (value != null) {
                    settings.setSetFire(value);
                    send(sender, "Setting fire: " + onOff(value));
                }
            }
            case "hurtself" -> {
                Boolean value = parseToggle(sender, args, settings.hurtSelf());
                if (value != null) {
                    settings.setHurtSelf(value);
                    send(sender, "Hurting yourself with your own fireballs: " + onOff(value));
                }
            }
            case "settings" -> showSettings(sender);
            case "reload" -> {
                plugin.reload();
                send(sender, "<green>Config reloaded.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                send(sender, "<red>Player <white>" + MiniMessage.miniMessage().escapeTags(args[1]) + "</white> isn't online.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            send(sender, "<red>Usage: /fireball give <player>");
            return;
        }

        ItemStack item = plugin.items().create();
        target.getInventory().addItem(item).values()
                .forEach(leftover -> target.getWorld().dropItem(target.getLocation(), leftover));
        if (target == sender) {
            send(sender, "<green>You got the Infinite Fire Charge. Right-click to throw!");
        } else {
            send(sender, "<green>Gave the Infinite Fire Charge to " + target.getName() + ".");
            send(target, "<green>You got the Infinite Fire Charge. Right-click to throw!");
        }
    }

    private void setNumber(CommandSender sender, String[] args, String name, double min, double max,
                           DoubleConsumer setter, String unit) {
        if (args.length < 2) {
            send(sender, "<red>Usage: /fireball " + args[0].toLowerCase(Locale.ROOT) + " <" + format(min) + "-" + format(max) + ">");
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "<red>That isn't a number.");
            return;
        }
        if (Double.isNaN(value) || value < min || value > max) {
            send(sender, "<red>" + name + " must be between " + format(min) + " and " + format(max) + ".");
            return;
        }
        setter.accept(value);
        send(sender, name + " set to <yellow>" + format(value) + unit + "</yellow>.");
        if (name.equals("Damage") && plugin.settings().totemMode()) {
            send(sender, "<gray>Note: totem mode is on, so damage isn't used until you turn it off with /fireball totem off.");
        }
    }

    /** Returns the new value, flipping the current one when no argument is given, or null on bad input. */
    private Boolean parseToggle(CommandSender sender, String[] args, boolean current) {
        if (args.length < 2) {
            return !current;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "enable" -> true;
            case "off", "false", "no", "disable" -> false;
            default -> {
                send(sender, "<red>Use on or off.");
                yield null;
            }
        };
    }

    private void showSettings(CommandSender sender) {
        FireballSettings s = plugin.settings();
        send(sender, "<gold>Current fireball settings:");
        line(sender, "Size", format(s.size()));
        line(sender, "Damage", format(s.damageHearts()) + " hearts" + (s.totemMode() ? " <gray>(not used in totem mode)" : ""));
        line(sender, "Totem mode", onOff(s.totemMode()));
        line(sender, "Break blocks", onOff(s.breakBlocks()));
        line(sender, "Set fire", onOff(s.setFire()));
        line(sender, "Speed", format(s.speed()));
        line(sender, "Cooldown", format(s.cooldownSeconds()) + " seconds");
        line(sender, "Hurt yourself", onOff(s.hurtSelf()));
    }

    private void help(CommandSender sender) {
        FireballSettings s = plugin.settings();
        send(sender, "<gold>InfiniteFireball commands:");
        helpLine(sender, "give [player]", "Get the Infinite Fire Charge");
        helpLine(sender, "size <0.1-" + format(s.maxSize()) + ">", "How big the explosion is (ghast = 1, TNT = 4)");
        helpLine(sender, "damage <hearts>", "Damage to everything in the blast");
        helpLine(sender, "totem <on|off>", "Pop totems / kill, with no armor damage");
        helpLine(sender, "blocks <on|off>", "Whether the blast breaks blocks");
        helpLine(sender, "fire <on|off>", "Whether the blast sets fire");
        helpLine(sender, "speed <0.1-" + format(s.maxSpeed()) + ">", "How fast fireballs fly");
        helpLine(sender, "cooldown <seconds>", "Time between throws");
        helpLine(sender, "hurtself <on|off>", "Whether your own fireballs can hit you");
        helpLine(sender, "settings", "Show the current settings");
        helpLine(sender, "reload", "Reload config.yml");
    }

    private void line(CommandSender sender, String name, String value) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(" <gray>" + name + ": <yellow>" + value));
    }

    private void helpLine(CommandSender sender, String usage, String description) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                " <yellow>/fireball " + MiniMessage.miniMessage().escapeTags(usage) + "</yellow> <dark_gray>-</dark_gray> <gray>" + description));
    }

    private void send(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + "<gray>" + message));
    }

    private static String onOff(boolean value) {
        return value ? "<green>ON</green>" : "<red>OFF</red>";
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    yield filter(names, args[1]);
                }
                case "totem", "blocks", "fire", "hurtself" -> filter(ON_OFF, args[1]);
                case "size" -> filter(List.of("1", "2", "4", "6", "10"), args[1]);
                case "damage" -> filter(List.of("2", "5", "10", "20"), args[1]);
                case "speed" -> filter(List.of("1", "1.5", "2", "3"), args[1]);
                case "cooldown" -> filter(List.of("0", "0.25", "0.5", "1"), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
