package dev.minegame.mines;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CrapsCommand implements CommandExecutor {
    private final DiceManager diceManager;

    public CrapsCommand(DiceManager diceManager) {
        this.diceManager = diceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.shared.only-players",
                    "Only players can use this command."
            )));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            diceManager.cancelCraps(player);
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.craps.command.usage",
                    "&eUsage: /craps <bet> | /craps cancel"
            )));
            return true;
        }
        double wager;
        try {
            wager = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage(diceManager.colorize(diceManager.text(
                    "messages.craps.command.amount-not-number",
                    "&cBet must be a number."
            )));
            return true;
        }
        diceManager.startCraps(player, wager);
        return true;
    }
}
