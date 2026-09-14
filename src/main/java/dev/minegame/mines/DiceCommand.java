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
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.dice.command.usage",
                    "&eUsage: /dice"
            )));
            return true;
        }
        if (!diceManager.diceCommandEnabled() && !player.hasPermission("dice.admin")) {
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.dice.gameplay.command-disabled",
                    "&cThe /dice command is disabled."
            )));
            return true;
        }
        int amount = diceManager.defaultDiceAmount();
        if (diceManager.giveDice(player, amount)) {
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.dice.gameplay.received",
                    "&aYou received &f%amount% &adice."
            ).replace("%amount%", Integer.toString(amount))));
        }
        return true;
    }
}
