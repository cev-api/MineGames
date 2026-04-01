package dev.minegame.mines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class MinesTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("minegame")) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>();
                options.add("cashout");
                options.addAll(Arrays.asList("1", "3", "5", "7", "10", "12", "15", "19"));
                return filterPrefix(args[0], options);
            }
            if (args.length == 2
                    && !args[0].equalsIgnoreCase("cashout")
                    ) {
                return filterPrefix(args[1], Arrays.asList("10", "100", "1000"));
            }
            return List.of();
        }
        if (name.equals("roulette")) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(Arrays.asList("red", "black", "green"));
                return filterPrefix(args[0], options);
            }
            if (args.length == 2) {
                return filterPrefix(args[1], Arrays.asList("1", "10", "100", "1000"));
            }
            return List.of();
        }
        if (name.equals("rouletteadmin")) {
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList("create", "remove", "regen", "list", "set", "setframe", "setred", "setblack", "setgreen", "setselector", "casinoframe", "housebalance", "housewithdraw", "reload"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("housewithdraw")) {
                return filterPrefix(args[1], Arrays.asList("all", "10", "100", "1000"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("casinoframe")) {
                return filterPrefix(args[1], Arrays.asList(
                        "all", "off", "reset", "mode", "REDSTONE_LAMP", "COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("casinoframe") && args[1].equalsIgnoreCase("mode")) {
                return filterPrefix(args[2], Arrays.asList("always", "betting_only"));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("casinoframe") && args[1].equalsIgnoreCase("all")) {
                return filterPrefix(args[2], Arrays.asList(
                        "off", "reset", "mode", "REDSTONE_LAMP", "COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB"
                ));
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("casinoframe") && args[1].equalsIgnoreCase("all") && args[2].equalsIgnoreCase("mode")) {
                return filterPrefix(args[3], Arrays.asList("always", "betting_only"));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("casinoframe") && !args[1].equalsIgnoreCase("mode") && !args[1].equalsIgnoreCase("all")) {
                return filterPrefix(args[2], Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("casinoframe")
                    && args[1].equalsIgnoreCase("all")
                    && !args[2].equalsIgnoreCase("mode")
                    && !args[2].equalsIgnoreCase("off")
                    && !args[2].equalsIgnoreCase("reset")) {
                return filterPrefix(args[3], Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            }
            if (args.length == 2 && isRouletteSetMaterialCommand(args[0])) {
                return filterPrefix(args[1], withMaterialControlPrefixes(args[1]));
            }
            if (args.length == 3 && isRouletteSetMaterialCommand(args[0]) && args[1].equalsIgnoreCase("all")) {
                if (args[2].toLowerCase().startsWith("r")) {
                    return filterPrefix(args[2], Arrays.asList("reset"));
                }
                return materialSuggestions(args[2]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return filterPrefix(args[1], Arrays.asList(
                        "roulette.board-size",
                        "roulette.red-percent",
                        "roulette.black-percent",
                        "roulette.green-percent",
                        "roulette.betting-seconds",
                        "roulette.spin-seconds",
                        "roulette.result-seconds",
                        "roulette.min-bet",
                        "roulette.max-bet",
                        "roulette.red-multiplier",
                        "roulette.black-multiplier",
                        "roulette.green-multiplier",
                        "roulette.house-edge-percent",
                        "roulette.max-payout",
                        "roulette.max-bet-distance",
                        "roulette.activation-distance-from-frame",
                        "roulette.hologram-height",
                        "roulette.hologram-line-spacing",
                        "roulette.hologram-view-range",
                        "roulette.hologram-title-gap",
                        "roulette.hologram-section-gap",
                        "roulette.fireworks-per-winner",
                        "roulette.broadcast-top-winner",
                        "roulette.blocks.frame",
                        "roulette.blocks.red",
                        "roulette.blocks.black",
                        "roulette.blocks.green",
                        "roulette.blocks.selector"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("roulette.broadcast-top-winner")) {
                    return filterPrefix(args[2], Arrays.asList("true", "false", "on", "off"));
                }
                if (args[1].startsWith("roulette.blocks.")) {
                    return materialSuggestions(args[2]);
                }
            }
            return List.of();
        }
        if (name.equals("minegameadmin") || name.equals("mineadmin")) {
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList(
                        "create", "remove", "regen", "list", "set", "setframe", "sethidden", "setsafe", "setmine", "holo", "debug", "casinoframe", "housebalance", "housewithdraw", "reload"
                ));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("housewithdraw")) {
                return filterPrefix(args[1], Arrays.asList("all", "10", "100", "1000"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("casinoframe")) {
                return filterPrefix(args[1], Arrays.asList(
                        "all", "off", "reset", "mode", "REDSTONE_LAMP", "COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("casinoframe") && args[1].equalsIgnoreCase("mode")) {
                return filterPrefix(args[2], Arrays.asList("idle_only", "always"));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("casinoframe") && args[1].equalsIgnoreCase("all")) {
                return filterPrefix(args[2], Arrays.asList(
                        "off", "reset", "mode", "REDSTONE_LAMP", "COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB"
                ));
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("casinoframe")
                    && args[1].equalsIgnoreCase("all") && args[2].equalsIgnoreCase("mode")) {
                return filterPrefix(args[3], Arrays.asList("idle_only", "always"));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("casinoframe")
                    && !args[1].equalsIgnoreCase("mode") && !args[1].equalsIgnoreCase("all")) {
                return filterPrefix(args[2], Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("casinoframe")
                    && args[1].equalsIgnoreCase("all")
                    && !args[2].equalsIgnoreCase("mode")
                    && !args[2].equalsIgnoreCase("off")
                    && !args[2].equalsIgnoreCase("reset")) {
                return filterPrefix(args[3], Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("holo") || args[0].equalsIgnoreCase("hologram")
                    || args[0].equalsIgnoreCase("debug"))) {
                return filterPrefix(args[1], Arrays.asList("on", "off"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return filterPrefix(args[1], Arrays.asList(
                        "board.grid-size",
                        "board.wall-distance",
                        "board.frame-one-higher",
                        "board.station-block",
                        "board.hidden-block",
                        "board.safe-reveal-block",
                        "board.mine-reveal-block",
                        "board.frame-block",
                        "board.reset-delay-seconds",
                        "game.duration-seconds",
                        "game.house-edge-percent",
                        "game.max-multiplier",
                        "game.max-payout",
                        "game.title-prefix",
                        "effects.fireworks-on-win",
                        "effects.firework-count",
                        "effects.win-ding-count",
                        "announcements.broadcast-start",
                        "announcements.broadcast-cashout",
                        "announcements.broadcast-win",
                        "announcements.send-welcome-on-start",
                        "casino-frame-activation-distance",
                        "hologram.enabled",
                        "hologram.line-spacing",
                        "hologram.view-range",
                        "hologram.behind-beacon-distance",
                        "hologram.base-height"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("effects.fireworks-on-win")
                        || args[1].equalsIgnoreCase("board.frame-one-higher")
                        || args[1].equalsIgnoreCase("hologram.enabled")
                        || args[1].equalsIgnoreCase("announcements.broadcast-start")
                        || args[1].equalsIgnoreCase("announcements.broadcast-cashout")
                        || args[1].equalsIgnoreCase("announcements.broadcast-win")
                        || args[1].equalsIgnoreCase("announcements.send-welcome-on-start")) {
                    return filterPrefix(args[2], Arrays.asList("true", "false", "on", "off"));
                }
                if (args[1].startsWith("board.") && args[1].endsWith("-block")) {
                    return materialSuggestions(args[2]);
                }
            }
            if (args.length == 2 && isSetCommand(args[0])) {
                return filterPrefix(args[1], withMaterialControlPrefixes(args[1]));
            }
            if (args.length == 3 && isSetCommand(args[0]) && args[1].equalsIgnoreCase("all")) {
                if (args[2].toLowerCase().startsWith("r")) {
                    return filterPrefix(args[2], Arrays.asList("reset"));
                }
                return materialSuggestions(args[2]);
            }
            return List.of();
        }
        return List.of();
    }

    private boolean isSetCommand(String cmd) {
        String normalized = cmd.toLowerCase();
        return normalized.equals("setframe")
                || normalized.equals("sethidden")
                || normalized.equals("setsafe")
                || normalized.equals("setmine");
    }

    private boolean isRouletteSetMaterialCommand(String cmd) {
        String normalized = cmd.toLowerCase();
        return normalized.equals("setframe")
                || normalized.equals("setred")
                || normalized.equals("setblack")
                || normalized.equals("setgreen")
                || normalized.equals("setselector");
    }

    private List<String> materialSuggestions(String prefix) {
        String p = prefix.toUpperCase();
        List<String> out = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isBlock()) {
                continue;
            }
            String name = material.name();
            if (!name.startsWith(p)) {
                continue;
            }
            out.add(name);
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private List<String> withMaterialControlPrefixes(String prefix) {
        List<String> out = new ArrayList<>();
        out.addAll(filterPrefix(prefix, Arrays.asList("all", "reset")));
        out.addAll(materialSuggestions(prefix));
        return out;
    }

    private List<String> filterPrefix(String input, List<String> values) {
        String lowered = input.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lowered)) {
                out.add(value);
            }
        }
        return out;
    }

}
