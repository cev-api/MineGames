package dev.minegame.mines;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MineAdminCommand implements CommandExecutor {
    private final MinesManager minesManager;
    private final CasinoFrameCommand casinoFrameCommand;

    public MineAdminCommand(MinesManager minesManager, CasinoFrameCommand casinoFrameCommand) {
        this.minesManager = minesManager;
        this.casinoFrameCommand = casinoFrameCommand;
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
                    "&eUsage: /minegameadmin <create|remove|regen|list|set|setframe|sethidden|setsafe|setmine|holo|debug|casinoframe|housebalance|housewithdraw|reload>"
            )));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> minesManager.createStation(player);
            case "remove" -> minesManager.removeStation(player);
            case "regen" -> minesManager.regenerateStation(player);
            case "list" -> minesManager.listStations(player);
            case "setframe" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.current-board-frame",
                            "&eCurrent board.frame-block = %value%"
                    ).replace("%value%", String.valueOf(minesManager.getCurrentConfigValue("board.frame-block")))));
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.setframe-usage",
                            "&eUsage: /minegameadmin setframe <block> | /minegameadmin setframe reset"
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
                            "&eUsage: /minegameadmin sethidden <block> | /minegameadmin sethidden reset"
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
                            "&eUsage: /minegameadmin setsafe <block> | /minegameadmin setsafe reset"
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
                            "&eUsage: /minegameadmin setmine <block> | /minegameadmin setmine reset"
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
                                "&eUsage: /minegameadmin set global <path> <value>"
                        )));
                        return true;
                    }
                    if (args.length < 4) {
                        player.sendMessage(minesManager.colorize(minesManager.text(
                                "messages.minegame.command.set-global-usage",
                                "&eUsage: /minegameadmin set global <path> <value>"
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
                            "&eUsage: /minegameadmin set [global] <path> <value>"
                    )));
                } else if (args.length < 3) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.set-usage",
                            "&eUsage: /minegameadmin set [global] <path> <value>"
                    )));
                } else {
                    minesManager.setConfigValue(player, args[1], args[2]);
                }
            }
            case "holo", "hologram" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.holo-usage",
                            "&eUsage: /minegameadmin holo <on|off>"
                    )));
                } else if (args[1].equalsIgnoreCase("on")) {
                    minesManager.setHologramsEnabled(player, true);
                } else if (args[1].equalsIgnoreCase("off")) {
                    minesManager.setHologramsEnabled(player, false);
                } else {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.holo-usage",
                            "&eUsage: /minegameadmin holo <on|off>"
                    )));
                }
            }
            case "debug" -> {
                if (args.length < 2) {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.debug-usage",
                            "&eUsage: /minegameadmin debug <on|off>"
                    )));
                } else if (args[1].equalsIgnoreCase("on")) {
                    minesManager.setDebugMode(player, true);
                } else if (args[1].equalsIgnoreCase("off")) {
                    minesManager.setDebugMode(player, false);
                } else {
                    player.sendMessage(minesManager.colorize(minesManager.text(
                            "messages.minegame.command.debug-usage",
                            "&eUsage: /minegameadmin debug <on|off>"
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
                            "&eUsage: /minegameadmin housewithdraw <amount|all>"
                    )));
                } else {
                    minesManager.withdrawHouseBalance(player, args[1]);
                }
            }
            case "reload" -> minesManager.reloadConfig(player);
            default -> player.sendMessage(minesManager.colorize(minesManager.text(
                    "messages.minegame.command.admin-usage",
                    "&eUsage: /minegameadmin <create|remove|regen|list|set|setframe|sethidden|setsafe|setmine|holo|debug|casinoframe|housebalance|housewithdraw|reload>"
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
                        "&eUsage: /minegameadmin %type% <block>"
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
}
