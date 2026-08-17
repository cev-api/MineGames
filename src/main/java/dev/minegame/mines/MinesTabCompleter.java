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
                return filterPrefix(args[0], Arrays.asList("create", "remove", "regen", "list", "set", "hologramsign", "setframe", "setred", "setblack", "setgreen", "setselector", "casinoframe", "housebalance", "housewithdraw", "reload"));
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("hologramsign"))) return filterPrefix(args[1], Arrays.asList("1", "2", "3", "4", "5"));
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
                        "global",
                        "board-size",
                        "red-percent",
                        "black-percent",
                        "green-percent",
                        "betting-seconds",
                        "spin-seconds",
                        "result-seconds",
                        "min-bet",
                        "max-bet",
                        "red-multiplier",
                        "black-multiplier",
                        "green-multiplier",
                        "house-edge-percent",
                        "max-payout",
                        "max-bet-distance",
                        "activation-distance-from-frame",
                        "hologram-height",
                        "hologram-line-spacing",
                        "hologram-view-range",
                        "hologram-title-gap",
                        "hologram-section-gap",
                        "fireworks-per-winner",
                        "broadcast-top-winner",
                        "blocks.frame",
                        "blocks.red",
                        "blocks.black",
                        "blocks.green",
                        "blocks.selector"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("global")) {
                return filterPrefix(args[2], Arrays.asList(
                        "board-size",
                        "red-percent",
                        "black-percent",
                        "green-percent",
                        "betting-seconds",
                        "spin-seconds",
                        "result-seconds",
                        "min-bet",
                        "max-bet",
                        "red-multiplier",
                        "black-multiplier",
                        "green-multiplier",
                        "house-edge-percent",
                        "max-payout",
                        "max-bet-distance",
                        "activation-distance-from-frame",
                        "hologram-height",
                        "hologram-line-spacing",
                        "hologram-view-range",
                        "hologram-title-gap",
                        "hologram-section-gap",
                        "fireworks-per-winner",
                        "broadcast-top-winner",
                        "blocks.frame",
                        "blocks.red",
                        "blocks.black",
                        "blocks.green",
                        "blocks.selector"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("broadcast-top-winner")) {
                    return filterPrefix(args[2], Arrays.asList("true", "false", "on", "off"));
                }
                if (args[1].startsWith("blocks.")) {
                    return materialSuggestions(args[2]);
                }
            }
            return List.of();
        }
        if (name.equals("slotsadmin")) {
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList(
                        "create", "remove", "regen", "list", "set", "hologramsign", "setouterframe", "setinnerframe", "setwinning", "casinoframe", "housebalance", "housewithdraw", "reload"
                ));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                return filterPrefix(args[1], Arrays.asList("3", "4", "5", "6", "7", "8"));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
                return filterPrefix(args[2], Arrays.asList("1", "2"));
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
                return filterPrefix(args[3], Arrays.asList("1", "2"));
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("hologramsign"))) return filterPrefix(args[1], Arrays.asList("1", "2", "3", "4", "5"));
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
            if (args.length == 2 && isSlotsSetMaterialCommand(args[0])) {
                return filterPrefix(args[1], withMaterialControlPrefixes(args[1]));
            }
            if (args.length == 3 && isSlotsSetMaterialCommand(args[0]) && args[1].equalsIgnoreCase("all")) {
                if (args[2].toLowerCase().startsWith("r")) {
                    return filterPrefix(args[2], Arrays.asList("reset"));
                }
                return materialSuggestions(args[2]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return filterPrefix(args[1], Arrays.asList(
                        "global",
                        "cost-per-spin",
                        "spin-seconds",
                        "stop-interval-ticks",
                        "result-seconds",
                        "activation-distance-from-frame",
                        "lever-placement",
                        "max-payout",
                        "fireworks-per-win",
                        "hologram-height",
                        "hologram-line-spacing",
                        "hologram-view-range",
                        "frame-animation.enabled",
                        "frame-animation.block",
                        "frame-animation.pattern",
                        "frame-animation.mode",
                        "frame-animation.interval-ticks",
                        "blocks.outer-frame",
                        "blocks.inner-frame",
                        "blocks.winning",
                        "payout-multipliers.1",
                        "payout-multipliers.2",
                        "payout-multipliers.3",
                        "payout-multipliers.4",
                        "payout-multipliers.5",
                        "payout-multipliers.6",
                        "payout-multipliers.7",
                        "payout-multipliers.8"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("global")) {
                return filterPrefix(args[2], Arrays.asList(
                        "cost-per-spin",
                        "spin-seconds",
                        "stop-interval-ticks",
                        "result-seconds",
                        "activation-distance-from-frame",
                        "lever-placement",
                        "max-payout",
                        "fireworks-per-win",
                        "hologram-height",
                        "hologram-line-spacing",
                        "hologram-view-range",
                        "frame-animation.enabled",
                        "frame-animation.block",
                        "frame-animation.pattern",
                        "frame-animation.mode",
                        "frame-animation.interval-ticks",
                        "blocks.outer-frame",
                        "blocks.inner-frame",
                        "blocks.winning",
                        "payout-multipliers.1",
                        "payout-multipliers.2",
                        "payout-multipliers.3",
                        "payout-multipliers.4",
                        "payout-multipliers.5",
                        "payout-multipliers.6",
                        "payout-multipliers.7",
                        "payout-multipliers.8"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("frame-animation.enabled")) {
                    return filterPrefix(args[2], Arrays.asList("true", "false", "on", "off"));
                }
                if (args[1].equalsIgnoreCase("lever-placement")) {
                    return filterPrefix(args[2], Arrays.asList("front_right_middle", "right_middle"));
                }
                if (args[1].equalsIgnoreCase("spin-light-mode")) {
                    return filterPrefix(args[2], Arrays.asList("flashing_fast", "flashing_slow", "cycle_quick", "cycle_slow"));
                }
                if (args[1].startsWith("blocks.") || args[1].equalsIgnoreCase("frame-animation.block")) {
                    return materialSuggestions(args[2]);
                }
                if (args[1].equalsIgnoreCase("frame-animation.mode")) {
                    return filterPrefix(args[2], Arrays.asList("idle_only", "always"));
                }
            }
            return List.of();
        }
        if (name.equals("minegamesjoin")) {
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList("true", "false", "set"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return filterPrefix(args[1], Arrays.asList("100", "500", "1000", "2500", "5000"));
            }
            return List.of();
        }
        if (name.equals("minegameadmin") || name.equals("mineadmin")) {
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList(
                        "create", "remove", "regen", "list", "move", "set", "setframe", "sethidden", "setsafe", "setmine", "holo", "hologramsign", "debug", "casinoframe", "housebalance", "housewithdraw", "reload"
                ));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("move")) return filterPrefix(args[1], Arrays.asList("x", "y", "z"));
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return filterPrefix(args[1], Arrays.asList("global",
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
                        "hologram.base-height","hologram.affix-to-wall", "hologram.background-color", "hologram.background-opacity", "hologram.foreground-color", "hologram.foreground-opacity",
                        "frame-animation.enabled",
                        "frame-animation.block",
                        "frame-animation.pattern",
                        "frame-animation.mode",
                        "frame-animation.interval-ticks"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("global")) {
                return filterPrefix(args[2], Arrays.asList(
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
                        "hologram.base-height","hologram.affix-to-wall", "hologram.background-color", "hologram.background-opacity", "hologram.foreground-color", "hologram.foreground-opacity",
                        "frame-animation.enabled",
                        "frame-animation.block",
                        "frame-animation.pattern",
                        "frame-animation.mode",
                        "frame-animation.interval-ticks"
                ));
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("hologramsign"))) return filterPrefix(args[1], Arrays.asList("1", "2", "3", "4", "5"));
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
                        "hologram.base-height","hologram.affix-to-wall", "hologram.background-color", "hologram.background-opacity", "hologram.foreground-color", "hologram.foreground-opacity",
                        "frame-animation.enabled",
                        "frame-animation.block",
                        "frame-animation.pattern",
                        "frame-animation.mode",
                        "frame-animation.interval-ticks"
                ));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("effects.fireworks-on-win")
                        || args[1].equalsIgnoreCase("board.frame-one-higher")
                        || args[1].equalsIgnoreCase("hologram.enabled")
                        || args[1].equalsIgnoreCase("frame-animation.enabled")
                        || args[1].equalsIgnoreCase("announcements.broadcast-start")
                        || args[1].equalsIgnoreCase("announcements.broadcast-cashout")
                        || args[1].equalsIgnoreCase("announcements.broadcast-win")
                        || args[1].equalsIgnoreCase("announcements.send-welcome-on-start")) {
                    return filterPrefix(args[2], Arrays.asList("true", "false", "on", "off"));
                }
                if ((args[1].startsWith("board.") && args[1].endsWith("-block"))
                        || args[1].equalsIgnoreCase("frame-animation.block")) {
                    return materialSuggestions(args[2]);
                }
                if (args[1].equalsIgnoreCase("frame-animation.mode")) {
                    return filterPrefix(args[2], Arrays.asList("idle_only", "always"));
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

    private boolean isSlotsSetMaterialCommand(String cmd) {
        String normalized = cmd.toLowerCase();
        return normalized.equals("setouterframe")
                || normalized.equals("setinnerframe")
                || normalized.equals("setwinning");
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
