package dev.shardwatch.command;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /shardwatch hub. */
public final class SwCommand extends BaseCommand {

    public SwCommand(Shardwatch plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!has(sender, "shardwatch.admin.reload")) {
                    return true;
                }
                long start = System.nanoTime();
                plugin.reload();
                plugin.lang().send(sender, "admin.reloaded", Text.p("ms", (System.nanoTime() - start) / 1_000_000));
                plugin.action(sender, StaffAction.RELOAD, "", "config, lang, facets");
            }
            case "version" -> plugin.lang().send(sender, "admin.version",
                    Text.p("version", plugin.getPluginMeta().getVersion()));
            default -> plugin.lang().getLines("help").forEach(sender::sendMessage);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            return filter(List.of("help", "reload", "version"), args[0]);
        }
        return List.of();
    }
}
