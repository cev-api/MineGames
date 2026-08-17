package dev.minegame.mines;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RouletteAdminCommand implements CommandExecutor {
    private final RouletteManager rouletteManager;
    private final RouletteCasinoFrameCommand rouletteCasinoFrameCommand;
    private final HologramPlacementController hologramPlacementController;

    public RouletteAdminCommand(RouletteManager rouletteManager, RouletteCasinoFrameCommand rouletteCasinoFrameCommand, HologramPlacementController hologramPlacementController) {
        this.rouletteManager = rouletteManager;
        this.rouletteCasinoFrameCommand = rouletteCasinoFrameCommand;
        this.hologramPlacementController = hologramPlacementController;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean consoleReload = args.length > 0 && args[0].equalsIgnoreCase("reload") && !(sender instanceof Player);
        if (consoleReload) {
            rouletteManager.reloadConfig(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(rouletteManager.colorize(rouletteManager.text(
                    "messages.shared.only-players",
                    "Only players can use this command."
            )));
            return true;
        }
        if (!player.hasPermission("roulette.admin")) {
            player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                    "messages.shared.no-permission",
                    "&cNo permission."
            )));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                    "messages.roulette.command.admin-usage",
                    "&6Usage: &f/rouletteadmin <command>\n&7create remove regen list set\n&7setframe setred setblack setgreen setselector\n&7casinoframe housebalance housewithdraw reload"
            )));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "hologramsign" -> handleHologramSign(player, args);
            case "create" -> {
                if (args.length >= 2) {
                    try {
                        int size = Integer.parseInt(args[1]);
                        rouletteManager.createStation(player, size);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                                "messages.roulette.command.create-bad-size",
                                "&cSize must be a number, e.g. /rouletteadmin create 4"
                        )));
                    }
                } else {
                    rouletteManager.createStation(player);
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    rouletteManager.listStationsForRemove(player);
                } else {
                    try {
                        int index = Integer.parseInt(args[1]);
                        rouletteManager.removeStationByIndex(player, index);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                                "messages.roulette.command.remove-bad-index",
                                "&cUsage: /rouletteadmin remove <number> — get the number from /rouletteadmin remove"
                        )));
                    }
                }
            }
            case "regen" -> rouletteManager.regenerateStation(player);
            case "list" -> rouletteManager.listStations(player);
            case "set" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("global")) {
                    if (args.length == 3) {
                        Object current = rouletteManager.getCurrentConfigValue(args[2]);
                        player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                                "messages.roulette.command.current-config",
                                "&eCurrent %path% = %value%"
                        ).replace("%path%", args[2]).replace("%value%", String.valueOf(current))));
                        player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                                "messages.roulette.command.set-global-usage",
                                "&6Usage: &f /rouletteadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    if (args.length < 4) {
                        player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                                "messages.roulette.command.set-global-usage",
                                "&6Usage: &f /rouletteadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    rouletteManager.setConfigValue(player, args[2], args[3], true);
                    return true;
                }
                if (args.length == 2) {
                    Object current = rouletteManager.getCurrentConfigValue(args[1]);
                    player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                            "messages.roulette.command.current-config",
                            "&eCurrent %path% = %value%"
                    ).replace("%path%", args[1]).replace("%value%", String.valueOf(current))));
                    player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                            "messages.roulette.command.set-usage",
                            "&6Usage: &f /rouletteadmin set [global] <path> <value>"
                    )));
                } else if (args.length < 3) {
                    player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                            "messages.roulette.command.set-usage",
                            "&6Usage: &f /rouletteadmin set [global] <path> <value>"
                    )));
                } else {
                    rouletteManager.setConfigValue(player, args[1], args[2]);
                }
            }
            case "casinoframe" -> {
                String[] subArgs = new String[Math.max(0, args.length - 1)];
                if (subArgs.length > 0) {
                    System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                }
                rouletteCasinoFrameCommand.execute(player, subArgs);
            }
            case "housebalance" -> rouletteManager.showHouseBalance(player);
            case "housewithdraw" -> {
                if (args.length < 2) {
                    player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                            "messages.roulette.command.housewithdraw-usage",
                            "&6Usage: &f /rouletteadmin housewithdraw <amount|all>"
                    )));
                } else {
                    rouletteManager.withdrawHouseBalance(player, args[1]);
                }
            }
            case "setframe", "setred", "setblack", "setgreen", "setselector" -> {
                boolean applyAll = args.length >= 2 && args[1].equalsIgnoreCase("all");
                int materialArgIndex = applyAll ? 2 : 1;
                if (args.length <= materialArgIndex) {
                    player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                            "messages.roulette.command.board-usage",
                            "&6Usage: &f /rouletteadmin %command% [all] <BLOCK|reset>"
                    ).replace("%command%", args[0].toLowerCase())));
                    return true;
                }
                String materialName = args[materialArgIndex];
                if (materialName.equalsIgnoreCase("reset")) {
                    rouletteManager.resetBoardMaterialOverrides(player, applyAll);
                    return true;
                }
                switch (args[0].toLowerCase()) {
                    case "setframe" -> rouletteManager.setFrameMaterial(player, materialName, applyAll);
                    case "setred" -> rouletteManager.setRedMaterial(player, materialName, applyAll);
                    case "setblack" -> rouletteManager.setBlackMaterial(player, materialName, applyAll);
                    case "setgreen" -> rouletteManager.setGreenMaterial(player, materialName, applyAll);
                    case "setselector" -> rouletteManager.setSelectorMaterial(player, materialName, applyAll);
                    default -> player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                            "messages.roulette.command.unknown-material-command",
                            "&cUnknown material command."
                    )));
                }
            }
            case "reload" -> rouletteManager.reloadConfig(player);
            default -> player.sendMessage(rouletteManager.colorize(rouletteManager.text(
                    "messages.roulette.command.admin-usage",
                    "&6Usage: &f/rouletteadmin <command>\n&7create remove regen list set\n&7setframe setred setblack setgreen setselector\n&7casinoframe housebalance housewithdraw reload"
            )));
        }
        return true;
    }

    private void handleHologramSign(Player player, String[] args) {
        boolean remove = args.length >= 2 && args[1].equalsIgnoreCase("remove");
        int numberArg = remove ? 2 : 1;
        if (args.length <= numberArg) { player.sendMessage(rouletteManager.colorize("&6Usage: &f /rouletteadmin hologramsign [remove] <number>")); return; }
        try { hologramPlacementController.arm(player, "roulette", Integer.parseInt(args[numberArg]), remove || (args.length >= 3 && args[2].equalsIgnoreCase("remove"))); }
        catch (NumberFormatException ex) { player.sendMessage(rouletteManager.colorize("&cStation number must be a number.")); }
    }}
