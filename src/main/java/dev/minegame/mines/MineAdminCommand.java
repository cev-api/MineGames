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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("mine.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin <create|remove|regen|list|set|setframe|sethidden|setsafe|setmine|holo|debug|casinoframe|housebalance|housewithdraw|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> minesManager.createStation(player);
            case "remove" -> minesManager.removeStation(player);
            case "regen" -> minesManager.regenerateStation(player);
            case "list" -> minesManager.listStations(player);
            case "setframe" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Current board.frame-block = " + String.valueOf(minesManager.getCurrentConfigValue("board.frame-block")));
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin setframe <block> | /minegameadmin setframe all <block> | /minegameadmin setframe reset");
                } else {
                    handleBoardMaterialCommand(player, args, "setframe");
                }
            }
            case "sethidden" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Current board.hidden-block = " + String.valueOf(minesManager.getCurrentConfigValue("board.hidden-block")));
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin sethidden <block> | /minegameadmin sethidden all <block> | /minegameadmin sethidden reset");
                } else {
                    handleBoardMaterialCommand(player, args, "sethidden");
                }
            }
            case "setsafe" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Current board.safe-reveal-block = " + String.valueOf(minesManager.getCurrentConfigValue("board.safe-reveal-block")));
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin setsafe <block> | /minegameadmin setsafe all <block> | /minegameadmin setsafe reset");
                } else {
                    handleBoardMaterialCommand(player, args, "setsafe");
                }
            }
            case "setmine" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Current board.mine-reveal-block = " + String.valueOf(minesManager.getCurrentConfigValue("board.mine-reveal-block")));
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin setmine <block> | /minegameadmin setmine all <block> | /minegameadmin setmine reset");
                } else {
                    handleBoardMaterialCommand(player, args, "setmine");
                }
            }
            case "set" -> {
                if (args.length == 2) {
                    Object current = minesManager.getCurrentConfigValue(args[1]);
                    player.sendMessage(ChatColor.YELLOW + "Current " + args[1] + " = " + String.valueOf(current));
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin set <path> <value>");
                } else if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin set <path> <value>");
                } else {
                    minesManager.setConfigValue(player, args[1], args[2]);
                }
            }
            case "holo", "hologram" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin holo <on|off>");
                } else if (args[1].equalsIgnoreCase("on")) {
                    minesManager.setHologramsEnabled(player, true);
                } else if (args[1].equalsIgnoreCase("off")) {
                    minesManager.setHologramsEnabled(player, false);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin holo <on|off>");
                }
            }
            case "debug" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin debug <on|off>");
                } else if (args[1].equalsIgnoreCase("on")) {
                    minesManager.setDebugMode(player, true);
                } else if (args[1].equalsIgnoreCase("off")) {
                    minesManager.setDebugMode(player, false);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin debug <on|off>");
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
                    player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin housewithdraw <amount|all>");
                } else {
                    minesManager.withdrawHouseBalance(player, args[1]);
                }
            }
            case "reload" -> minesManager.reloadConfig(player);
            default -> player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin <create|remove|regen|list|set|setframe|sethidden|setsafe|setmine|holo|debug|casinoframe|housebalance|housewithdraw|reload>");
        }
        return true;
    }

    private void handleBoardMaterialCommand(Player player, String[] args, String type) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            minesManager.resetBoardMaterialOverrides(player, false);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("reset")) {
                minesManager.resetBoardMaterialOverrides(player, true);
                return;
            }
            if (args.length < 3) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin " + type + " all <block>");
                return;
            }
            applyBoardMaterial(player, type, args[2], true);
            return;
        }
        applyBoardMaterial(player, type, args[1], false);
    }

    private void applyBoardMaterial(Player player, String type, String materialName, boolean applyAll) {
        switch (type) {
            case "setframe" -> minesManager.setFrameMaterial(player, materialName, applyAll);
            case "sethidden" -> minesManager.setHiddenMaterial(player, materialName, applyAll);
            case "setsafe" -> minesManager.setSafeRevealMaterial(player, materialName, applyAll);
            case "setmine" -> minesManager.setMineRevealMaterial(player, materialName, applyAll);
            default -> player.sendMessage(ChatColor.RED + "Unknown material command.");
        }
    }
}
