package dev.minegame.mines;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class DiceAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SETTINGS = List.of(
            "color", "player-alert", "server-alert", "amount", "players", "glow", "particles", "craps", "rate-limit"
    );
    private final DiceManager diceManager;

    public DiceAdminCommand(DiceManager diceManager) {
        this.diceManager = diceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean consoleAction = !(sender instanceof Player)
                && args.length > 0
                && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("settings"));
        if (consoleAction) {
            if (args[0].equalsIgnoreCase("reload")) {
                diceManager.reloadConfig(sender);
            } else {
                diceManager.showSettings(sender);
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.shared.only-players",
                    "Only players can use this command."
            )));
            return true;
        }
        if (!player.hasPermission("dice.admin")) {
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.shared.no-permission",
                    "&cNo permission."
            )));
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "settings", "show" -> diceManager.showSettings(player);
            case "reload" -> diceManager.reloadConfig(player);
            case "housebalance" -> diceManager.showHouseBalance(player);
            case "housewithdraw" -> {
                if (args.length < 2) {
                    player.sendMessage(diceManager.colorize(diceManager.text(
                            "messages.craps.admin.housewithdraw-usage",
                            "&6Usage: &f/diceadmin housewithdraw <amount|all>"
                    )));
                } else {
                    diceManager.withdrawHouseBalance(player, args[1]);
                }
            }
            case "rate-limit" -> {
                if (args.length < 2) {
                    player.sendMessage(diceManager.colorize(diceManager.text(
                            "messages.dice.admin.rate-limit-usage",
                            "&eUsage: /diceadmin rate-limit <amount|off> [24h|day]"
                    )));
                } else {
                    diceManager.configureRateLimit(player, args[1], args.length >= 3 ? args[2] : null);
                }
            }
            case "color", "player-alert", "alert-player", "server-alert", "alert-server", "amount",
                    "default-amount", "players", "command", "glow", "particles" -> handleSetting(player, args);
            case "craps" -> {
                if (args.length < 2) {
                    sendUsage(player);
                } else {
                    diceManager.adminSet(player, "craps", args[1], false);
                }
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleSetting(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.dice.admin.setting-usage",
                    "&eUsage: /diceadmin <setting> <value> [craps]"
            )));
            return;
        }
        boolean crapsScope = false;
        if (args.length >= 3) {
            if (args[2].equalsIgnoreCase("craps")) {
                crapsScope = true;
            } else if (!args[2].equalsIgnoreCase("all")) {
                player.sendMessage(diceManager.colorize(diceManager.text(
                        "messages.dice.admin.setting-usage",
                        "&eUsage: /diceadmin <setting> <value> [craps]"
                )));
                return;
            }
        }
        diceManager.adminSet(player, args[0], args[1], crapsScope);
    }

    private void sendUsage(Player player) {
        player.sendMessage(diceManager.colorize(diceManager.text(
                "messages.dice.command.admin-usage",
                "&6Usage: &f/diceadmin <color|player-alert|server-alert|amount|players|glow|particles|craps|settings|housebalance|housewithdraw|reload> ..."
        )));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SETTINGS);
            options.addAll(List.of("settings", "housebalance", "housewithdraw", "reload"));
            return partial(options, args[0]);
        }
        if (args.length == 2) {
            String setting = args[0].toLowerCase(Locale.ROOT);
            if (setting.equals("color")) {
                return partial(new ArrayList<>(diceManagerColors()), args[1]);
            }
            if (setting.equals("housewithdraw")) {
                return partial(List.of("all"), args[1]);
            }
            if (setting.equals("craps")) {
                return partial(List.of("on", "off"), args[1]);
            }
            if (setting.equals("rate-limit")) {
                return partial(List.of("off", "1", "2", "5", "10"), args[1]);
            }
            if (SETTINGS.contains(setting)) {
                return partial(List.of("on", "off"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rate-limit")) {
            return partial(List.of("24h", "day"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("color") || SETTINGS.contains(args[0].toLowerCase(Locale.ROOT)))) {
            return partial(List.of("craps", "all"), args[2]);
        }
        return List.of();
    }

    private List<String> diceManagerColors() {
        return new ArrayList<>(diceManager.colors());
    }

    private List<String> partial(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
