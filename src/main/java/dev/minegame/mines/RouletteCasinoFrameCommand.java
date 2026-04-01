package dev.minegame.mines;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class RouletteCasinoFrameCommand {
    private final RouletteManager rouletteManager;

    public RouletteCasinoFrameCommand(RouletteManager rouletteManager) {
        this.rouletteManager = rouletteManager;
    }

    public boolean execute(Player player, String[] args) {
        if (!player.hasPermission("roulette.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        boolean applyAll = args.length > 0 && args[0].equalsIgnoreCase("all");
        int offset = applyAll ? 1 : 0;

        if (args.length == offset + 1 && args[offset].equalsIgnoreCase("off")) {
            rouletteManager.disableFrameAnimation(player, applyAll);
            return true;
        }

        if (args.length == offset + 1 && args[offset].equalsIgnoreCase("reset")) {
            rouletteManager.resetFrameAnimationOverrides(player, applyAll);
            return true;
        }

        if (args.length == offset + 2 && args[offset].equalsIgnoreCase("mode")) {
            rouletteManager.setFrameAnimationMode(player, args[offset + 1], applyAll);
            return true;
        }

        if (args.length < offset + 2) {
            sendUsage(player);
            return true;
        }

        Material block = Material.matchMaterial(args[offset]);
        if (block == null || !block.isBlock()) {
            player.sendMessage(ChatColor.RED + "Invalid block material.");
            return true;
        }
        int pattern;
        try {
            pattern = Integer.parseInt(args[offset + 1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Pattern must be a number (1-10).");
            return true;
        }
        if (pattern < 1 || pattern > 10) {
            player.sendMessage(ChatColor.RED + "Pattern must be between 1 and 10.");
            return true;
        }
        rouletteManager.setFrameAnimation(player, block.name(), pattern, applyAll);
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin casinoframe [all] <block> <pattern 1-10>");
        player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin casinoframe [all] mode <always|betting_only>");
        player.sendMessage(ChatColor.YELLOW + "Usage: /rouletteadmin casinoframe [all] <off|reset>");
    }
}
