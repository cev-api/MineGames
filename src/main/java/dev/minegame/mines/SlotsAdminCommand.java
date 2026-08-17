package dev.minegame.mines;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SlotsAdminCommand implements CommandExecutor {
    private final SlotsManager slotsManager;
    private final SlotsCasinoFrameCommand slotsCasinoFrameCommand;
    private final HologramPlacementController hologramPlacementController;

    public SlotsAdminCommand(SlotsManager slotsManager, SlotsCasinoFrameCommand slotsCasinoFrameCommand, HologramPlacementController hologramPlacementController) {
        this.slotsManager = slotsManager;
        this.slotsCasinoFrameCommand = slotsCasinoFrameCommand;
        this.hologramPlacementController = hologramPlacementController;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean consoleReload = args.length > 0 && args[0].equalsIgnoreCase("reload") && !(sender instanceof Player);
        if (!(sender instanceof Player player)) {
            if (consoleReload) {
                slotsManager.reloadConfig(sender);
                return true;
            }
            sender.sendMessage(slotsManager.colorize(slotsManager.text(
                    "messages.shared.only-players",
                    "Only players can use this command."
            )));
            return true;
        }
        if (!player.hasPermission("slots.admin")) {
            player.sendMessage(slotsManager.colorize(slotsManager.text(
                    "messages.shared.no-permission",
                    "&cNo permission."
            )));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(slotsManager.colorize(slotsManager.text(
                    "messages.slots.command.admin-usage",
                    "&6Usage: &f/slotsadmin <command>\n&7create remove regen list set\n&7hologramsign setouterframe setinnerframe\n&7setwinning casinoframe housebalance\n&7housewithdraw reload"
            )));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "hologramsign" -> handleHologramSign(player, args);
            case "create" -> {
                int reelCount = 3;
                int rowCount = 1;
                if (args.length >= 2) {
                    try {
                        reelCount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(slotsManager.colorize(slotsManager.text(
                                "messages.slots.command.reel-count-not-number",
                                "&cSlots width must be a number."
                        )));
                        return true;
                    }
                }
                if (args.length >= 3) {
                    try {
                        rowCount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(slotsManager.colorize(slotsManager.text(
                                "messages.slots.command.rows-not-number",
                                "&cRow count must be a number."
                        )));
                        return true;
                    }
                }
                slotsManager.createStation(player, reelCount, rowCount);
            }
            case "remove" -> {
                if (args.length < 2) {
                    slotsManager.listStationsForRemove(player);
                } else {
                    try {
                        int index = Integer.parseInt(args[1]);
                        slotsManager.removeStationByIndex(player, index);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(slotsManager.colorize(slotsManager.text(
                                "messages.slots.command.remove-bad-index",
                                "&cUsage: /slotsadmin remove <number> — get the number from /slotsadmin remove"
                        )));
                    }
                }
            }
            case "regen" -> slotsManager.regenerateStation(player);
            case "list" -> slotsManager.listStations(player);
            case "set" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("global")) {
                    if (args.length == 3) {
                        Object current = slotsManager.getCurrentConfigValue(args[2]);
                        player.sendMessage(slotsManager.colorize(slotsManager.text(
                                "messages.slots.command.current-config",
                                "&eCurrent %path% = %value%"
                        ).replace("%path%", args[2]).replace("%value%", String.valueOf(current))));
                        player.sendMessage(slotsManager.colorize(slotsManager.text(
                                "messages.slots.command.set-global-usage",
                                "&6Usage: &f /slotsadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    if (args.length < 4) {
                        player.sendMessage(slotsManager.colorize(slotsManager.text(
                                "messages.slots.command.set-global-usage",
                                "&6Usage: &f /slotsadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    slotsManager.setConfigValue(player, args[2], args[3], true);
                    return true;
                }
                if (args.length == 2) {
                    Object current = slotsManager.getCurrentConfigValue(args[1]);
                    player.sendMessage(slotsManager.colorize(slotsManager.text(
                            "messages.slots.command.current-config",
                            "&eCurrent %path% = %value%"
                    ).replace("%path%", args[1]).replace("%value%", String.valueOf(current))));
                    player.sendMessage(slotsManager.colorize(slotsManager.text(
                            "messages.slots.command.set-usage",
                            "&6Usage: &f /slotsadmin set [global] <path> <value>"
                    )));
                } else if (args.length < 3) {
                    player.sendMessage(slotsManager.colorize(slotsManager.text(
                            "messages.slots.command.set-usage",
                            "&6Usage: &f /slotsadmin set [global] <path> <value>"
                    )));
                } else {
                    slotsManager.setConfigValue(player, args[1], args[2]);
                }
            }
            case "setouterframe", "setinnerframe", "setwinning" -> {
                boolean applyAll = args.length >= 2 && args[1].equalsIgnoreCase("all");
                int materialArgIndex = applyAll ? 2 : 1;
                if (args.length <= materialArgIndex) {
                    player.sendMessage(slotsManager.colorize(slotsManager.text(
                            "messages.slots.command.board-usage",
                            "&6Usage: &f /slotsadmin %command% [all] <BLOCK|reset>"
                    ).replace("%command%", args[0].toLowerCase())));
                    return true;
                }
                String materialName = args[materialArgIndex];
                if (materialName.equalsIgnoreCase("reset")) {
                    slotsManager.resetBoardMaterialOverrides(player, applyAll);
                    return true;
                }
                switch (args[0].toLowerCase()) {
                    case "setouterframe" -> slotsManager.setOuterFrameMaterial(player, materialName, applyAll);
                    case "setinnerframe" -> slotsManager.setInnerFrameMaterial(player, materialName, applyAll);
                    case "setwinning" -> slotsManager.setWinningMaterial(player, materialName, applyAll);
                    default -> player.sendMessage(slotsManager.colorize(slotsManager.text(
                            "messages.slots.command.unknown-material-command",
                            "&cUnknown material command."
                    )));
                }
            }
            case "casinoframe" -> {
                String[] subArgs = new String[Math.max(0, args.length - 1)];
                if (subArgs.length > 0) {
                    System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                }
                slotsCasinoFrameCommand.execute(player, subArgs);
            }
            case "housebalance" -> slotsManager.showHouseBalance(player);
            case "housewithdraw" -> {
                if (args.length < 2) {
                    player.sendMessage(slotsManager.colorize(slotsManager.text(
                            "messages.slots.command.housewithdraw-usage",
                            "&6Usage: &f /slotsadmin housewithdraw <amount|all>"
                    )));
                } else {
                    slotsManager.withdrawHouseBalance(player, args[1]);
                }
            }
            case "reload" -> slotsManager.reloadConfig(player);
            default -> player.sendMessage(slotsManager.colorize(slotsManager.text(
                    "messages.slots.command.admin-usage",
                    "&6Usage: &f/slotsadmin <command>\n&7create remove regen list set\n&7hologramsign setouterframe setinnerframe\n&7setwinning casinoframe housebalance\n&7housewithdraw reload"
            )));
        }
        return true;
    }
    private void handleHologramSign(Player player, String[] args) {
        boolean remove = args.length >= 2 && args[1].equalsIgnoreCase("remove");
        int numberArg = remove ? 2 : 1;
        if (args.length <= numberArg) { player.sendMessage(slotsManager.colorize("&6Usage: &f /slotsadmin hologramsign [remove] <number>")); return; }
        try { hologramPlacementController.arm(player, "slots", Integer.parseInt(args[numberArg]), remove || (args.length >= 3 && args[2].equalsIgnoreCase("remove"))); }
        catch (NumberFormatException ex) { player.sendMessage(slotsManager.colorize("&cStation number must be a number.")); }
    }
}