package dev.minegame.mines;

import org.bukkit.command.*; import org.bukkit.entity.Player;

public final class ChickenCommand implements CommandExecutor {
    private final ChickenManager manager; public ChickenCommand(ChickenManager manager){this.manager=manager;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Only players can use this command.");return true;}if(args.length==1&&args[0].equalsIgnoreCase("cashout")){manager.cashout(p);return true;}if(args.length<2){p.sendMessage(manager.color("&6Usage: &f/chicken <red|blue|gold|green> <amount> &7| &f/chicken cashout"));return true;}try{manager.placeBet(p,args[0],Double.parseDouble(args[1]));}catch(NumberFormatException ex){p.sendMessage(manager.color("&cAmount must be a number."));}return true;}
}