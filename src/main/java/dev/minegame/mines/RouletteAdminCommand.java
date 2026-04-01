package dev.minegame.mines;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RouletteAdminCommand implements CommandExecutor {
    private final RouletteManager rouletteManager;
    private final RouletteCasinoFrameCommand rouletteCasinoFrameCommand;

    public RouletteAdminCommand(RouletteManager rouletteManager, RouletteCasinoFrameCommand rouletteCasinoFrameCommand) {
        this.rouletteManager = rouletteManager;
        this.rouletteCasinoFrameCommand = rouletteCasinoFrameCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("roulette.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin <create|remove|regen|list|set|setframe|setred|setblack|setgreen|setselector|casinoframe|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> rouletteManager.createStation(player);
            case "remove" -> rouletteManager.removeStation(player);
            case "regen" -> rouletteManager.regenerateStation(player);
            case "list" -> rouletteManager.listStations(player);
            case "set" -> {
                if (args.length == 2) {
                    Object current = rouletteManager.getCurrentConfigValue(args[1]);
                    player.sendMessage(ChatColor.YELLOW + "Current " + args[1] + " = " + String.valueOf(current));
                    player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin set <path> <value>");
                } else if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin set <path> <value>");
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
            case "setframe", "setred", "setblack", "setgreen", "setselector" -> {
                boolean applyAll = args.length >= 2 && args[1].equalsIgnoreCase("all");
                int materialArgIndex = applyAll ? 2 : 1;
                if (args.length <= materialArgIndex) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin " + args[0].toLowerCase() + " [all] <BLOCK|reset>");
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
                    default -> player.sendMessage(ChatColor.RED + "Unknown material command.");
                }
            }
            case "reload" -> rouletteManager.reloadConfig(player);
            default -> player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin <create|remove|regen|list|set|setframe|setred|setblack|setgreen|setselector|casinoframe|reload>");
        }
        return true;
    }
}
