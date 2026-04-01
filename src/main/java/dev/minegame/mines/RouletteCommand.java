package dev.minegame.mines;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RouletteCommand implements CommandExecutor {
    private final RouletteManager rouletteManager;

    public RouletteCommand(RouletteManager rouletteManager) {
        this.rouletteManager = rouletteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /roulette <red|black|green> <amount>");
            return true;
        }
        RouletteColor color = RouletteColor.fromInput(args[0]);
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage("Amount must be a number.");
            return true;
        }
        rouletteManager.placeBet(player, color, amount);
        return true;
    }
}
