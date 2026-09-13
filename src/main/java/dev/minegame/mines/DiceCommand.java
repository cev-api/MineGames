package dev.minegame.mines;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DiceCommand implements CommandExecutor {
    private final DiceManager diceManager;

    public DiceCommand(DiceManager diceManager) {
        this.diceManager = diceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("Usage: /dice");
            return true;
        }
        diceManager.giveDice(player, 2);
        player.sendMessage("§aYou received two dice.");
        return true;
    }
}
