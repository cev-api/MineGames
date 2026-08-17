package dev.minegame.mines;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MineAdminCommand implements CommandExecutor {
    private final MinesManager minesManager;
    private final CasinoFrameCommand casinoFrameCommand;
    private final HologramPlacementController hologramPlacementController;

    public MineAdminCommand(MinesManager minesManager, CasinoFrameCommand casinoFrameCommand, HologramPlacementController hologramPlacementController) {
        this.minesManager = minesManager;
        this.casinoFrameCommand = casinoFrameCommand;
        this.hologramPlacementController = hologramPlacementController;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean consoleReload = args.length > 0 && args[0].equalsIgnoreCase("reload") && !(sender instanceof Player);
        if (consoleReload) {
            minesManager.reloadConfig(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(minesManager.colorize(minesManager.text(
                    "messages.shared.only-players",
                    "Only players can use this command."
            )));
            return true;
        }
        if (!player.hasPermission("mine.admin")) {
            player.sendMessage(minesManager.colorize(minesManager.text(
                    "messages.shared.no-permission",
                    "&cNo permission."
            )));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(minesManager.colorize(minesManager.text(
                    "messages.minegame.command.admin-usage",
                    "&6Usage: &f/minegameadmin <command>\n&7create remove regen list set\n&7setframe sethidden setsafe setmine\n&7hologramsign holo debug casinoframe\n&7housebalance housewithdraw reload"
            )));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length >= 2) {
                    try {
                        int size = Integer.parseInt(args[1]);
                        minesManager.createStation(player, size);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(minesManager.colorize(minesManager.text(
                                "messages.minegame.command.create-bad-size",
                                "&cSize must be a number, e.g. /mineadmin create 4"
                        )));
                    }
                } else {
                    minesManager.createStation(player);
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    minesManager.listStationsForRemove(player);
                } else {
                    try {
                        int index = Integer.parseInt(args[1]);
                        minesManager.removeStationByIndex(player, index);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(minesManager.colorize(minesManager.text(
                                "messages.minegame.command.remove-bad-index",
                                "&cUsage: /mineadmin remove <number> — get the number from /mineadmin remove"
                        )));
                    }
                }
            }
            case "regen" -> minesManager.regenerateStation(player);
            case "move" -> {
                if (args.length != 3) player.sendMessage(minesManager.colorize("&6Usage: &f /minegameadmin move <x|y|z> <amount>"));
                else try { minesManager.moveAllStations(player, args[1], Integer.parseInt(args[2])); }
                catch (NumberFormatException ex) { player.sendMessage(minesManager.colorize("&cAmount must be a whole number.")); }
            }
            case "list" -> minesManager.listStations(player);
            case "setframe" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.current-board-frame",
                            "&eCurrent board.frame-block = %value%"
                    ).replace("%value%", String.valueOf(minesManager.getCurrentConfigValue("board.frame-block")))));
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.setframe-usage",
                            "&6Usage: &f /minegameadmin setframe <block> | /minegameadmin setframe reset"
                    )));
                } else {
                    handleBoardMaterialCommand(player, args, "setframe");
                }
            }
            case "sethidden" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.current-board-hidden",
                            "&eCurrent board.hidden-block = %value%"
                    ).replace("%value%", String.valueOf(minesManager.getCurrentConfigValue("board.hidden-block")))));
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.sethidden-usage",
                            "&6Usage: &f /minegameadmin sethidden <block> | /minegameadmin sethidden reset"
                    )));
                } else {
                    handleBoardMaterialCommand(player, args, "sethidden");
                }
            }
            case "setsafe" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.current-board-safe",
                            "&eCurrent board.safe-reveal-block = %value%"
                    ).replace("%value%", String.valueOf(minesManager.getCurrentConfigValue("board.safe-reveal-block")))));
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.setsafe-usage",
                            "&6Usage: &f /minegameadmin setsafe <block> | /minegameadmin setsafe reset"
                    )));
                } else {
                    handleBoardMaterialCommand(player, args, "setsafe");
                }
            }
            case "setmine" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.current-board-mine",
                            "&eCurrent board.mine-reveal-block = %value%"
                    ).replace("%value%", String.valueOf(minesManager.getCurrentConfigValue("board.mine-reveal-block")))));
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.setmine-usage",
                            "&6Usage: &f /minegameadmin setmine <block> | /minegameadmin setmine reset"
                    )));
                } else {
                    handleBoardMaterialCommand(player, args, "setmine");
                }
            }
            case "set" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("global")) {
                    if (args.length == 3) {
                        Object current = minesManager.getCurrentConfigValue(args[2]);
                        player.sendMessage(minesManager.colorize(minesManager.text(
                                "messages.minegame.command.current-config",
                                "&eCurrent %path% = %value%"
                        ).replace("%path%", args[2]).replace("%value%", String.valueOf(current))));
                        player.sendMessage(minesManager.colorize(minesManager.text(
                                "messages.minegame.command.set-global-usage",
                                "&6Usage: &f /minegameadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    if (args.length < 4) {
                        player.sendMessage(minesManager.colorize(minesManager.text(
                                "messages.minegame.command.set-global-usage",
                                "&6Usage: &f /minegameadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    minesManager.setConfigValue(player, args[2], args[3], true);
                } else if (args.length == 2) {
                    Object current = minesManager.getCurrentConfigValue(args[1]);
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.current-config",
                            "&eCurrent %path% = %value%"
                    ).replace("%path%", args[1]).replace("%value%", String.valueOf(current))));
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.set-usage",
                            "&6Usage: &f /minegameadmin set [global] <path> <value>"
                    )));
                } else if (args.length < 3) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.set-usage",
                            "&6Usage: &f /minegameadmin set [global] <path> <value>"
                    )));
                } else {
                    minesManager.setConfigValue(player, args[1], args[2]);
                }
            }
            case "hologramsign" -> handleHologramSign(player, args);
            case "holo", "hologram" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.holo-usage",
                            "&6Usage: &f /minegameadmin holo <on|off>"
                    )));
                } else if (args[1].equalsIgnoreCase("on")) {
                    minesManager.setHologramsEnabled(player, true);
                } else if (args[1].equalsIgnoreCase("off")) {
                    minesManager.setHologramsEnabled(player, false);
                } else {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.holo-usage",
                            "&6Usage: &f /minegameadmin holo <on|off>"
                    )));
                }
            }
            case "debug" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.debug-usage",
                            "&6Usage: &f /minegameadmin debug <on|off>"
                    )));
                } else if (args[1].equalsIgnoreCase("on")) {
                    minesManager.setDebugMode(player, true);
                } else if (args[1].equalsIgnoreCase("off")) {
                    minesManager.setDebugMode(player, false);
                } else {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.debug-usage",
                            "&6Usage: &f /minegameadmin debug <on|off>"
                    )));
                }
            }
            case "casinoframe" -> {
                String[] subArgs = new String[Math.max(0, args.length - 1)];
                if (subArgs.length > 0) {
                    System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                }
                casinoFrameCommand.execute(player, subArgs);
            }
            case "housebalance" -> minesManager.showHouseBalance(player);
            case "housewithdraw" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.housewithdraw-usage",
                            "&6Usage: &f /minegameadmin housewithdraw <amount|all>"
                    )));
                } else {
                    minesManager.withdrawHouseBalance(player, args[1]);
                }
            }
            case "reload" -> minesManager.reloadConfig(player);
            default -> player.sendMessage(minesManager.colorize(minesManager.text(
                    "messages.minegame.command.admin-usage",
                    "&6Usage: &f/minegameadmin <command>\n&7create remove regen list set\n&7setframe sethidden setsafe setmine\n&7hologramsign holo debug casinoframe\n&7housebalance housewithdraw reload"
            )));
        }
        return true;
    }

    private void handleBoardMaterialCommand(Player player, String[] args, String type) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            minesManager.resetBoardMaterialOverrides(player, false);
            return;
        }
        String materialName = args[1];
        if (materialName.equalsIgnoreCase("all")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("reset")) {
                minesManager.resetBoardMaterialOverrides(player, false);
                return;
            }
            if (args.length < 3) {
                player.sendMessage(minesManager.colorize(minesManager.text(
                        "messages.minegame.command.board-all-usage",
                        "&6Usage: &f /minegameadmin %type% <block>"
                ).replace("%type%", type)));
                return;
            }
            materialName = args[2];
        }
        applyBoardMaterial(player, type, materialName, false);
    }

    private void applyBoardMaterial(Player player, String type, String materialName, boolean applyAll) {
        switch (type) {
            case "setframe" -> minesManager.setFrameMaterial(player, materialName, applyAll);
            case "sethidden" -> minesManager.setHiddenMaterial(player, materialName, applyAll);
            case "setsafe" -> minesManager.setSafeRevealMaterial(player, materialName, applyAll);
            case "setmine" -> minesManager.setMineRevealMaterial(player, materialName, applyAll);
            default -> player.sendMessage(minesManager.colorize(minesManager.text(
                    "messages.minegame.command.unknown-material-command",
                    "&cUnknown material command."
            )));
        }
    }
    private void handleHologramSign(Player player, String[] args) {
        boolean remove = args.length >= 2 && args[1].equalsIgnoreCase("remove");
        int numberArg = remove ? 2 : 1;
        if (args.length <= numberArg) { player.sendMessage(minesManager.colorize("&6Usage: &f /minegameadmin hologramsign [remove] <number>")); return; }
        try { hologramPlacementController.arm(player, "minegame", Integer.parseInt(args[numberArg]), remove || (args.length >= 3 && args[2].equalsIgnoreCase("remove"))); }
        catch (NumberFormatException ex) { player.sendMessage(minesManager.colorize("&cStation number must be a number.")); }
    }
}