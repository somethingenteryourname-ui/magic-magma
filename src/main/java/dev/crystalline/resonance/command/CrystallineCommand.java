package dev.crystalline.resonance.command;

import dev.crystalline.resonance.CrystallineResonance;
import dev.crystalline.resonance.crystal.CrystalType;
import dev.crystalline.resonance.mana.ManaManager;
import dev.crystalline.resonance.spell.SpellSettings;
import dev.crystalline.resonance.spell.SpellType;
import dev.crystalline.resonance.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import java.util.Map;
import java.util.function.Supplier;

/** {@code /crystalline} (aliases {@code /cr}, {@code /crystal}). */
public final class CrystallineCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "crystalline.admin";
    private static final String MANA = "crystalline.command.mana";
    private static final int MAX_GIVE = 2304;

    private final CrystallineResonance plugin;

    public CrystallineCommand(CrystallineResonance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mana" -> mana(sender, args);
            case "spells" -> spells(sender);
            case "give" -> {
                if (requireAdmin(sender)) {
                    give(sender, args);
                }
            }
            case "setmana" -> {
                if (requireAdmin(sender)) {
                    setMana(sender, args);
                }
            }
            case "resetcooldowns" -> {
                if (requireAdmin(sender)) {
                    resetCooldowns(sender, args);
                }
            }
            case "reload" -> {
                if (requireAdmin(sender)) {
                    plugin.reload();
                    plugin.messages().send(sender, "reloaded");
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Messages messages = plugin.messages();
        messages.sendLines(sender, "help");
        if (sender.hasPermission(ADMIN)) {
            messages.sendLines(sender, "help-admin");
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private void mana(CommandSender sender, String[] args) {
        ManaManager mana = plugin.mana();
        if (args.length >= 2) {
            if (!requireAdmin(sender)) {
                return;
            }
            Player target = findPlayer(sender, args[1]);
            if (target != null) {
                plugin.messages().send(sender, "mana-other",
                        Placeholder.unparsed("player", target.getName()),
                        Placeholder.unparsed("mana", ManaManager.formatMana(mana.getMana(target))),
                        Placeholder.unparsed("max", ManaManager.formatMana(mana.getMaxMana())));
            }
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission(MANA)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.messages().send(player, "mana-self",
                Placeholder.unparsed("mana", ManaManager.formatMana(mana.getMana(player))),
                Placeholder.unparsed("max", ManaManager.formatMana(mana.getMaxMana())));
        mana.showNow(player);
    }

    private void spells(CommandSender sender) {
        Messages messages = plugin.messages();
        sender.sendMessage(messages.get("spell-list-header"));
        for (SpellType type : SpellType.values()) {
            SpellSettings settings = plugin.spells().settings(type);
            if (!settings.enabled()) {
                continue;
            }
            Component status = messages.get(sender.hasPermission(type.permission()) ? "spell-list-attuned" : "spell-list-locked");
            sender.sendMessage(messages.get("spell-list-entry",
                    Placeholder.component("spell", settings.displayName()),
                    Placeholder.unparsed("cost", SpellSettings.formatNumber(settings.manaCost())),
                    Placeholder.unparsed("cooldown", SpellSettings.formatNumber(settings.cooldownMillis() / 1000.0)),
                    Placeholder.component("status", status)));
        }
    }

    // /cr give <player> <crystal|tome> <type> [amount]
    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendAdminUsage(sender);
            return;
        }
        Player target = findPlayer(sender, args[1]);
        if (target == null) {
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            Integer parsed = parseInt(args[4]);
            if (parsed == null || parsed < 1) {
                plugin.messages().send(sender, "invalid-number", Placeholder.unparsed("value", args[4]));
                return;
            }
            amount = Math.min(parsed, MAX_GIVE);
        }

        Supplier<ItemStack> factory;
        Component itemName;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "crystal" -> {
                CrystalType type = CrystalType.fromId(args[3]);
                if (type == null) {
                    unknownType(sender, "crystal", args[3], CrystalType.ids());
                    return;
                }
                factory = () -> plugin.items().createCrystal(type, 1);
                itemName = Messages.parse(plugin.crystalSettings().definition(type).name());
            }
            case "tome" -> {
                SpellType spell = SpellType.fromId(args[3]);
                if (spell == null) {
                    unknownType(sender, "spell", args[3], SpellType.ids());
                    return;
                }
                factory = () -> plugin.items().createTome(spell);
                itemName = plugin.spells().settings(spell).displayName();
            }
            default -> {
                unknownType(sender, "item", args[2], List.of("crystal", "tome"));
                return;
            }
        }

        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = factory.get();
            int size = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(size);
            remaining -= size;
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                target.getWorld().dropItem(target.getLocation(), leftover);
            }
        }
        plugin.messages().send(sender, "given",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.component("item", itemName),
                Placeholder.unparsed("player", target.getName()));
    }

    // /cr setmana <player> <amount|max>
    private void setMana(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendAdminUsage(sender);
            return;
        }
        Player target = findPlayer(sender, args[1]);
        if (target == null) {
            return;
        }
        ManaManager mana = plugin.mana();
        double value;
        if (args[2].equalsIgnoreCase("max")) {
            value = mana.getMaxMana();
        } else {
            try {
                value = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                plugin.messages().send(sender, "invalid-number", Placeholder.unparsed("value", args[2]));
                return;
            }
            if (!Double.isFinite(value)) {
                plugin.messages().send(sender, "invalid-number", Placeholder.unparsed("value", args[2]));
                return;
            }
        }
        mana.setMana(target, value);
        plugin.messages().send(sender, "mana-set",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("mana", ManaManager.formatMana(mana.getMana(target))));
    }

    // /cr resetcooldowns <player>
    private void resetCooldowns(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendAdminUsage(sender);
            return;
        }
        Player target = findPlayer(sender, args[1]);
        if (target != null) {
            plugin.spells().resetCooldowns(target);
            plugin.messages().send(sender, "cooldowns-reset", Placeholder.unparsed("player", target.getName()));
        }
    }

    private void sendAdminUsage(CommandSender sender) {
        plugin.messages().sendLines(sender, "help-admin");
    }

    private Player findPlayer(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("player", name));
        }
        return player;
    }

    private void unknownType(CommandSender sender, String kind, String value, List<String> options) {
        plugin.messages().send(sender, "unknown-type",
                Placeholder.unparsed("kind", kind),
                Placeholder.unparsed("value", value),
                Placeholder.unparsed("options", String.join(", ", options)));
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("help");
            options.add("mana");
            options.add("spells");
            if (admin) {
                options.addAll(List.of("give", "setmana", "resetcooldowns", "reload"));
            }
        } else if (admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && List.of("mana", "give", "setmana", "resetcooldowns").contains(sub)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    options.add(player.getName());
                }
            } else if (sub.equals("give")) {
                if (args.length == 3) {
                    options.addAll(List.of("crystal", "tome"));
                } else if (args.length == 4) {
                    options.addAll(args[2].equalsIgnoreCase("tome") ? SpellType.ids() : CrystalType.ids());
                } else if (args.length == 5) {
                    options.addAll(List.of("1", "16", "64"));
                }
            } else if (sub.equals("setmana") && args.length == 3) {
                options.addAll(List.of("0", "50", "max"));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
