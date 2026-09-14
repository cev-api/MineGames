package dev.minegame.mines;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Native Minecraft dialog based administrator UI for every casino subsystem. */
public final class CasinoGuiCommand implements CommandExecutor {
    private static final int STATIONS_PER_PAGE = 8;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder().uses(1).build();

    private enum Kind { BOOLEAN, TEXT, OPTION }

    private record Setting(
            String category,
            String path,
            String label,
            String description,
            Kind kind,
            List<String> options
    ) {
        private Setting {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    private record StationEntry(String key, String label, String details) {}

    private final MinegamePlugin plugin;
    private final MinesManager mines;
    private final RouletteManager roulette;
    private final SlotsManager slots;
    private final FightsManager fights;
    private final ChickenManager chicken;
    private final DiceManager dice;
    private final FrameAnimator frameAnimator;
    private final RouletteFrameAnimator rouletteFrameAnimator;
    private final SlotsFrameAnimator slotsFrameAnimator;
    private final JoinGiftManager joinGift;

    public CasinoGuiCommand(
            MinegamePlugin plugin,
            MinesManager mines,
            RouletteManager roulette,
            SlotsManager slots,
            FightsManager fights,
            ChickenManager chicken,
            DiceManager dice
    ) {
        this(plugin, mines, roulette, slots, fights, chicken, dice, null, null, null, null);
    }

    public CasinoGuiCommand(
            MinegamePlugin plugin,
            MinesManager mines,
            RouletteManager roulette,
            SlotsManager slots,
            FightsManager fights,
            ChickenManager chicken,
            DiceManager dice,
            FrameAnimator frameAnimator,
            RouletteFrameAnimator rouletteFrameAnimator,
            SlotsFrameAnimator slotsFrameAnimator,
            JoinGiftManager joinGift
    ) {
        this.plugin = plugin;
        this.mines = mines;
        this.roulette = roulette;
        this.slots = slots;
        this.fights = fights;
        this.chicken = chicken;
        this.dice = dice;
        this.frameAnimator = frameAnimator;
        this.rouletteFrameAnimator = rouletteFrameAnimator;
        this.slotsFrameAnimator = slotsFrameAnimator;
        this.joinGift = joinGift;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Operators only.");
            return true;
        }
        openHome(player);
        return true;
    }

    public void openHome(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        actions.add(button("MineGame", "MineGame board, betting and hologram settings", () -> openGame(player, "minegame")));
        actions.add(button("Roulette", "Roulette board, payouts and animation settings", () -> openGame(player, "roulette")));
        actions.add(button("Slots", "Slots machine, betting and payout settings", () -> openGame(player, "slots")));
        actions.add(button("Fights", "Fights arena, combat and betting settings", () -> openGame(player, "fights")));
        actions.add(button("Chicken", "Chicken board, lightning and payout settings", () -> openGame(player, "chicken")));
        actions.add(button("Dice & Craps", "Physical dice and craps settings", () -> openGame(player, "dice")));
        actions.add(button("General", "Shared hologram and first-join gift settings", () -> openGeneral(player)));
        show(player, dialog(
                "Casino settings",
                "Choose a game or shared server setting to configure. Every change is saved immediately.",
                List.of(),
                actions,
                button("Close", "Close casino settings", player::closeDialog)
        ));
    }

    public void openGame(Player player, String game) {
        if (game.equals("general")) {
            openGeneral(player);
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        for (String category : categories(game, false)) {
            actions.add(button(category, null,
                    () -> openSettings(player, game, category, null)));
        }
        if (!game.equals("dice") && !game.equals("general")) {
            actions.add(button("Stations / arenas", "Edit station-specific overrides for this game", () -> openStations(player, game, 0)));
        }
        show(player, dialog(
                gameTitle(game),
                "Choose a settings section. Global values are used by every station unless a station override is set.",
                List.of(),
                actions,
                button("Back", "Return to casino settings", () -> openHome(player))
        ));
    }

    private void openGeneral(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        for (String category : categories("general", false)) {
            actions.add(button(category, null,
                    () -> openSettings(player, "general", category, null)));
        }
        show(player, dialog(
                "General casino settings",
                "These values are shared by casino holograms and the first-join gift.",
                List.of(),
                actions,
                button("Back", "Return to casino settings", () -> openHome(player))
        ));
    }

    public void openStations(Player player, String game, int page) {
        if (game.equals("general") || game.equals("dice")) {
            openGame(player, game);
            return;
        }
        List<StationEntry> entries = stationEntries(game);
        int maxPage = Math.max(0, (entries.size() - 1) / STATIONS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * STATIONS_PER_PAGE;
        List<ActionButton> actions = new ArrayList<>();

        if (entries.isEmpty()) {
            actions.add(button("No stations found", "Create a station with the game's admin command", () -> {}));
        } else {
            for (int i = start; i < Math.min(entries.size(), start + STATIONS_PER_PAGE); i++) {
                StationEntry entry = entries.get(i);
                if (game.equals("chicken")) {
                    actions.add(button(entry.label(), entry.details(), () -> openGame(player, game)));
                } else {
                    actions.add(button(entry.label(), entry.details(), () -> openStation(player, game, entry.key())));
                }
            }
        }
        if (safePage > 0) {
            actions.add(button("Previous page", "Show the previous stations", () -> openStations(player, game, safePage - 1)));
        }
        if (safePage < maxPage) {
            actions.add(button("Next page", "Show more stations", () -> openStations(player, game, safePage + 1)));
        }

        show(player, dialog(
                gameTitle(game) + " stations",
                "Page " + (safePage + 1) + "/" + (maxPage + 1) + " · " + entries.size() + " station(s). Select a station to edit local overrides.",
                List.of(),
                actions,
                button("Back", "Return to game settings", () -> openGame(player, game))
        ));
    }

    private void openStation(Player player, String game, String stationKey) {
        List<ActionButton> actions = new ArrayList<>();
        for (String category : categories(game, true)) {
            actions.add(button(category, null,
                    () -> openSettings(player, game, category, stationKey)));
        }
        if (actions.isEmpty()) {
            actions.add(button("Global settings only", "This game has no station-specific settings", () -> openGame(player, game)));
        }
        show(player, dialog(
                gameTitle(game) + " station",
                stationDetails(game, stationKey) + "\nChoose a local override section.",
                List.of(),
                actions,
                button("Back", "Return to the station list", () -> openStations(player, game, 0))
        ));
    }

    private void openSettings(Player player, String game, String category, String stationKey) {
        List<Setting> selected = settings(game, stationKey != null).stream()
                .filter(setting -> setting.category().equals(category))
                .toList();
        List<DialogInput> inputs = selected.stream()
                .map(setting -> input(setting, value(game, stationKey, setting.path())))
                .toList();

        ActionButton save = responseButton("Save changes", "Validate and save every value in this section", response -> {
            for (Setting setting : selected) {
                String raw = responseValue(response, setting);
                if (raw != null) {
                    applySetting(player, game, stationKey, setting, raw);
                }
            }
            openSettings(player, game, category, stationKey);
        });
        Runnable back = stationKey == null
                ? () -> openGame(player, game)
                : () -> openStation(player, game, stationKey);
        show(player, dialog(
                gameTitle(game) + " · " + category,
                stationKey == null
                        ? "Edit global values. Numeric and material settings use the text boxes below."
                        : "Edit local overrides for this station. Values initially come from the global setting.",
                inputs,
                List.of(save),
                button("Back", "Return without changing this section", back)
        ));
    }

    private DialogInput input(Setting setting, Object current) {
        String key = inputKey(setting);
        Component label = Component.text(setting.label())
                .hoverEvent(HoverEvent.showText(Component.text(settingTooltip(setting, current))));
        if (setting.kind() == Kind.BOOLEAN) {
            boolean initial = current instanceof Boolean value
                    ? value
                    : Boolean.parseBoolean(String.valueOf(current));
            return DialogInput.bool(key, label, initial, "On", "Off");
        }
        if (setting.kind() == Kind.OPTION) {
            String currentText = String.valueOf(current);
            List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
            boolean selected = false;
            for (String option : setting.options()) {
                boolean initial = option.equalsIgnoreCase(currentText);
                selected |= initial;
                Component display = Component.text(option)
                        .hoverEvent(HoverEvent.showText(Component.text(settingTooltip(setting, current))));
                options.add(SingleOptionDialogInput.OptionEntry.create(option, display, initial));
            }
            if (!selected && !options.isEmpty()) {
                SingleOptionDialogInput.OptionEntry first = options.getFirst();
                options.set(0, SingleOptionDialogInput.OptionEntry.create(first.id(), first.display(), true));
            }
            return DialogInput.singleOption(key, label, options).build();
        }
        return DialogInput.text(key, label)
                .width(320)
                .labelVisible(true)
                .initial(displayValue(current))
                .maxLength(512)
                .build();
    }

    private String responseValue(io.papermc.paper.dialog.DialogResponseView response, Setting setting) {
        String key = inputKey(setting);
        if (setting.kind() == Kind.BOOLEAN) {
            Boolean value = response.getBoolean(key);
            return value == null ? null : Boolean.toString(value);
        }
        return response.getText(key);
    }

    private void applySetting(Player player, String game, String stationKey, Setting setting, String raw) {
        String value = setting.path().equals("minegame.game.title-prefix") ? raw : raw.strip();
        if (stationKey != null) {
            setStation(player, game, stationKey, setting.path(), value);
        } else {
            setGlobal(player, game, setting.path(), value);
        }
    }

    private void setGlobal(Player player, String game, String path, String value) {
        if (game.equals("general")) {
            if (path.equals("join-gift.enabled") && joinGift != null) {
                joinGift.setEnabled(Boolean.parseBoolean(value));
            } else if (path.equals("join-gift.amount") && joinGift != null) {
                try {
                    joinGift.setAmount(Double.parseDouble(value));
                } catch (NumberFormatException ex) {
                    player.sendMessage(Component.text("Invalid amount: " + value));
                }
            } else {
                setDirectConfigValue(player, path, value);
            }
            return;
        }

        if (game.equals("minegame")) {
            mines.setConfigValue(player, path, value, true);
            refreshAnimator(game, path);
        } else if (game.equals("roulette")) {
            roulette.setConfigValue(player, path, value, true);
            refreshAnimator(game, path);
        } else if (game.equals("slots")) {
            if (path.equals("slots.blocks.reel-options")) {
                setDirectConfigValue(player, path, value);
            } else {
                slots.setConfigValue(player, path, value, true);
            }
            refreshAnimator(game, path);
        } else if (game.equals("fights")) {
            fights.setConfigValue(player, path, value);
        } else if (game.equals("chicken")) {
            chicken.set(player, path, value);
        } else if (game.equals("dice")) {
            dice.setGuiConfigValue(player, path, value);
        }
    }

    private void setStation(Player player, String game, String stationKey, String path, String value) {
        boolean updated;
        if (game.equals("minegame")) {
            updated = mines.setStationConfigValue(stationKey, path, value);
        } else if (game.equals("roulette")) {
            updated = roulette.setStationConfigValue(stationKey, path, value);
        } else if (game.equals("slots")) {
            updated = slots.setStationConfigValue(stationKey, path, value);
        } else if (game.equals("fights")) {
            updated = fights.setStationConfigValue(player, stationKey, path, value);
        } else {
            updated = false;
        }
        if (!updated) {
            player.sendMessage(Component.text("Could not save " + path + " for this station."));
        }
    }

    private void setDirectConfigValue(Player player, String path, String raw) {
        if (path.equals("slots.blocks.reel-options")) {
            List<String> materials = new ArrayList<>();
            String normalized = raw.strip();
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            for (String token : normalized.split("[,\\n]+")) {
                Material material = Material.matchMaterial(token.strip());
                if (material == null || !material.isBlock()) {
                    player.sendMessage(Component.text("Invalid block material in reel options: " + token.strip()));
                    return;
                }
                materials.add(material.name());
            }
            if (materials.isEmpty()) {
                player.sendMessage(Component.text("Reel options must contain at least one block material."));
                return;
            }
            plugin.getConfig().set(path, materials);
        } else {
            Object parsed = parseDirectValue(plugin.getConfig().get(path), raw);
            if (parsed == null) {
                player.sendMessage(Component.text("Invalid value for " + path + ": " + raw));
                return;
            }
            plugin.getConfig().set(path, parsed);
        }
        plugin.saveConfig();
        if (path.startsWith("slots.")) {
            slots.reloadConfig(player);
        }
    }

    private Object parseDirectValue(Object current, String raw) {
        try {
            if (current instanceof Boolean) {
                if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on")) return true;
                if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off")) return false;
                return null;
            }
            if (current instanceof Integer) return Integer.parseInt(raw);
            if (current instanceof Number) return Double.parseDouble(raw);
            return raw;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void refreshAnimator(String game, String path) {
        if (!path.contains("frame-animation")) return;
        if (game.equals("minegame") && frameAnimator != null) {
            frameAnimator.reloadFromCurrentConfig();
        } else if (game.equals("roulette") && rouletteFrameAnimator != null) {
            rouletteFrameAnimator.reloadFromCurrentConfig();
        } else if (game.equals("slots") && slotsFrameAnimator != null) {
            slotsFrameAnimator.reloadFromCurrentConfig();
        }
    }

    private Dialog dialog(
            String title,
            String description,
            List<? extends DialogInput> inputs,
            List<ActionButton> actions,
            ActionButton exit
    ) {
        String displayTitle = title.toUpperCase(Locale.ROOT);
        DialogBase base = DialogBase.builder(Component.text(displayTitle))
                .externalTitle(Component.text(displayTitle))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(Component.text(description), 360)))
                .inputs(inputs)
                .build();
        var actionType = DialogType.multiAction(actions);
        if (exit != null) {
            actionType.exitAction(exit);
        }
        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(actionType
                        .columns(actions.size() > 1 ? 2 : 1)
                        .build()));
    }

    private ActionButton button(String label, String tooltip, Runnable action) {
        return responseButton(label, tooltip, response -> action.run());
    }

    private ActionButton responseButton(
            String label,
            String tooltip,
            java.util.function.Consumer<io.papermc.paper.dialog.DialogResponseView> action
    ) {
        var builder = ActionButton.builder(Component.text(label));
        if (tooltip != null && !tooltip.isBlank()) {
            builder.tooltip(Component.text(tooltip));
        }
        return builder
                .width(200)
                .action(DialogAction.customClick((response, audience) -> action.accept(response), CALLBACK_OPTIONS))
                .build();
    }

    private void show(Player player, Dialog dialog) {
        player.showDialog(dialog);
    }

    private List<StationEntry> stationEntries(String game) {
        List<StationEntry> entries = new ArrayList<>();
        if (game.equals("minegame")) {
            for (StationData station : mines.stations()) {
                entries.add(new StationEntry(station.key(), "MineGame #" + number(game, station.key()), location(station.worldName(), station.x(), station.y(), station.z())));
            }
        } else if (game.equals("roulette")) {
            for (RouletteStationData station : roulette.stations()) {
                entries.add(new StationEntry(station.key(), "Roulette #" + number(game, station.key()), location(station.worldName(), station.x(), station.y(), station.z())));
            }
        } else if (game.equals("slots")) {
            for (SlotStationData station : slots.stations()) {
                entries.add(new StationEntry(station.key(), "Slots #" + number(game, station.key()),
                        location(station.worldName(), station.x(), station.y(), station.z())
                                + " · " + station.reelCount() + " reel(s) × " + station.rowCount() + " row(s)"));
            }
        } else if (game.equals("fights")) {
            for (FightStationData station : fights.stations()) {
                entries.add(new StationEntry(station.key(), "Fights arena #" + number(game, station.key()),
                        location(station.worldName(), station.x(), station.y(), station.z())
                                + " · " + station.fighterCount() + " fighters"));
            }
        } else if (game.equals("chicken")) {
            for (ChickenStationData station : chicken.stations()) {
                entries.add(new StationEntry(station.key(), "Chicken board #" + number(game, station.key()),
                        location(station.worldName(), station.x(), station.y(), station.z())
                                + " · " + station.size() + " × " + station.size()));
            }
        }
        return entries;
    }

    private String stationDetails(String game, String key) {
        return stationEntries(game).stream()
                .filter(entry -> entry.key().equals(key))
                .map(entry -> entry.label() + " · " + entry.details())
                .findFirst()
                .orElse("Unknown station");
    }

    private List<String> categories(String game, boolean local) {
        return new ArrayList<>(new LinkedHashSet<>(settings(game, local).stream().map(Setting::category).toList()));
    }

    private String settingTooltip(Setting setting, Object current) {
        StringBuilder tooltip = new StringBuilder(setting.description())
                .append("\nPath: ").append(setting.path())
                .append("\nCurrent: ").append(displayValue(current));
        if (!setting.options().isEmpty()) {
            tooltip.append("\nOptions: ").append(String.join(", ", setting.options()));
        }
        return tooltip.toString();
    }

    private Object value(String game, String stationKey, String path) {
        return stationKey == null ? globalValue(game, path) : stationValue(game, stationKey, path);
    }

    private Object globalValue(String game, String path) {
        return switch (game) {
            case "general", "dice" -> plugin.getConfig().get(path);
            case "minegame" -> mines.getCurrentConfigValue(path);
            case "roulette" -> roulette.getCurrentConfigValue(path);
            case "slots" -> slots.getCurrentConfigValue(path);
            case "fights" -> fights.getCurrentConfigValue(path);
            case "chicken" -> chicken.current(path);
            default -> null;
        };
    }

    private Object stationValue(String game, String key, String path) {
        if (game.equals("minegame")) {
            for (StationData station : mines.stations()) {
                if (!station.key().equals(key)) continue;
                Object local = switch (path) {
                    case "minegame.board.grid-size" -> station.boardSize();
                    case "minegame.board.hidden-block" -> station.boardHiddenBlock();
                    case "minegame.board.safe-reveal-block" -> station.boardSafeRevealBlock();
                    case "minegame.board.mine-reveal-block" -> station.boardMineRevealBlock();
                    case "minegame.board.frame-block" -> station.boardFrameBlock();
                    case "minegame.frame-animation.enabled" -> station.frameAnimEnabled();
                    case "minegame.frame-animation.block" -> station.frameAnimBlock();
                    case "minegame.frame-animation.pattern" -> station.frameAnimPattern();
                    case "minegame.frame-animation.mode" -> station.frameAnimMode();
                    default -> null;
                };
                return local == null ? globalValue(game, path) : local;
            }
        } else if (game.equals("roulette")) {
            for (RouletteStationData station : roulette.stations()) {
                if (!station.key().equals(key)) continue;
                Object local = switch (path) {
                    case "roulette.board-size" -> station.boardSize();
                    case "roulette.blocks.frame" -> station.boardFrameBlock();
                    case "roulette.blocks.red" -> station.boardRedBlock();
                    case "roulette.blocks.black" -> station.boardBlackBlock();
                    case "roulette.blocks.green" -> station.boardGreenBlock();
                    case "roulette.blocks.selector" -> station.boardSelectorBlock();
                    case "roulette.frame-animation.enabled" -> station.frameAnimEnabled();
                    case "roulette.frame-animation.block" -> station.frameAnimBlock();
                    case "roulette.frame-animation.pattern" -> station.frameAnimPattern();
                    case "roulette.frame-animation.mode" -> station.frameAnimMode();
                    default -> null;
                };
                return local == null ? globalValue(game, path) : local;
            }
        } else if (game.equals("slots")) {
            for (SlotStationData station : slots.stations()) {
                if (!station.key().equals(key)) continue;
                Object local = switch (path) {
                    case "slots.reel-count" -> station.reelCount();
                    case "slots.row-count" -> station.rowCount();
                    case "slots.cost-per-spin" -> station.costPerSpin();
                    case "slots.blocks.outer-frame" -> station.outerFrameBlock();
                    case "slots.blocks.inner-frame" -> station.innerFrameBlock();
                    case "slots.blocks.winning" -> station.winningBlock();
                    case "slots.frame-animation.enabled" -> station.frameAnimEnabled();
                    case "slots.frame-animation.block" -> station.frameAnimBlock();
                    case "slots.frame-animation.pattern" -> station.frameAnimPattern();
                    case "slots.frame-animation.mode" -> station.frameAnimMode();
                    case "slots.bet-buttons.enabled" -> station.betButtonsEnabled();
                    case "slots.bet-buttons.material" -> station.betButtonMaterial();
                    case "slots.bet-buttons.adjust-percent" -> station.betAdjustPercent();
                    default -> null;
                };
                return local == null ? globalValue(game, path) : local;
            }
        } else if (game.equals("fights") && path.equals("fights.fighter-count")) {
            for (FightStationData station : fights.stations()) {
                if (station.key().equals(key)) return station.fighterCount();
            }
        }
        return globalValue(game, path);
    }

    private List<Setting> settings(String game, boolean local) {
        if (local) return localSettings(game);
        List<Setting> result = new ArrayList<>();
        switch (game) {
            case "general" -> {
                add(result, "Hologram style", "hologram.background-color", "Background color", "Hex color or empty", Kind.TEXT);
                add(result, "Hologram style", "hologram.background-opacity", "Background opacity", "0-255", Kind.TEXT);
                add(result, "Hologram style", "hologram.foreground-color", "Foreground color", "Hex color or empty", Kind.TEXT);
                add(result, "Hologram style", "hologram.foreground-opacity", "Foreground opacity", "0-255", Kind.TEXT);
                addBoolean(result, "Hologram style", "hologram.see-through-walls", "See through walls");
                addBoolean(result, "Join gift", "join-gift.enabled", "Enabled");
                add(result, "Join gift", "join-gift.amount", "Gift amount", "Economy amount", Kind.TEXT);
            }
            case "minegame" -> {
                add(result, "Board", "minegame.board.grid-size", "Grid size", "Square board size", Kind.TEXT);
                add(result, "Board", "minegame.board.wall-distance", "Wall distance", "Distance from station", Kind.TEXT);
                add(result, "Board", "minegame.board.reset-delay-seconds", "Reset delay", "Seconds before reset", Kind.TEXT);
                addBoolean(result, "Board", "minegame.board.frame-one-higher", "Frame one block higher");
                addMaterial(result, "Board", "minegame.board.station-block", "Station block");
                addMaterial(result, "Board", "minegame.board.hidden-block", "Hidden block");
                addMaterial(result, "Board", "minegame.board.safe-reveal-block", "Safe reveal block");
                addMaterial(result, "Board", "minegame.board.mine-reveal-block", "Mine reveal block");
                addMaterial(result, "Board", "minegame.board.frame-block", "Frame block");
                add(result, "Board", "minegame.casino-frame-activation-distance", "Casino frame activation distance", "Blocks", Kind.TEXT);
                addAnimation(result, "minegame", List.of("idle_only", "always"));
                add(result, "Game and betting", "minegame.game.duration-seconds", "Game duration", "Seconds before timeout", Kind.TEXT);
                add(result, "Game and betting", "minegame.game.house-edge-percent", "House edge", "Percentage", Kind.TEXT);
                add(result, "Game and betting", "minegame.game.max-multiplier", "Maximum multiplier", "Zero means unlimited", Kind.TEXT);
                add(result, "Game and betting", "minegame.game.max-payout", "Maximum payout", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Game and betting", "minegame.game.min-bet", "Minimum bet", "Economy amount", Kind.TEXT);
                add(result, "Game and betting", "minegame.game.max-bet", "Maximum bet", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Game and betting", "minegame.game.title-prefix", "Title prefix", "Chat prefix", Kind.TEXT);
                addBoolean(result, "Announcements", "minegame.announcements.broadcast-start", "Broadcast starts");
                addBoolean(result, "Announcements", "minegame.announcements.broadcast-cashout", "Broadcast cashouts");
                addBoolean(result, "Announcements", "minegame.announcements.broadcast-win", "Broadcast wins");
                addBoolean(result, "Announcements", "minegame.announcements.broadcast-loss", "Broadcast losses");
                addBoolean(result, "Announcements", "minegame.announcements.send-welcome-on-start", "Send welcome message");
                addBoolean(result, "Effects", "minegame.effects.fireworks-on-win", "Win fireworks");
                add(result, "Effects", "minegame.effects.firework-count", "Firework count", "Fireworks per win", Kind.TEXT);
                add(result, "Effects", "minegame.effects.win-ding-count", "Win ding count", "Victory sound repetitions", Kind.TEXT);
                addBoolean(result, "Hologram", "minegame.hologram.enabled", "Enabled");
                add(result, "Hologram", "minegame.hologram.line-spacing", "Line spacing", "Vertical spacing", Kind.TEXT);
                add(result, "Hologram", "minegame.hologram.view-range", "View range", "Blocks", Kind.TEXT);
                add(result, "Hologram", "minegame.hologram.behind-beacon-distance", "Behind beacon distance", "Blocks", Kind.TEXT);
                add(result, "Hologram", "minegame.hologram.base-height", "Base height", "Blocks", Kind.TEXT);
                addBoolean(result, "Hologram", "minegame.hologram.affix-to-wall", "Affix to wall");
            }
            case "roulette" -> {
                add(result, "Game and betting", "roulette.board-size", "Board size", "Board width", Kind.TEXT);
                add(result, "Game and betting", "roulette.betting-seconds", "Betting seconds", "Betting phase", Kind.TEXT);
                add(result, "Game and betting", "roulette.spin-seconds", "Spin seconds", "Spin phase", Kind.TEXT);
                add(result, "Game and betting", "roulette.result-seconds", "Result seconds", "Result phase", Kind.TEXT);
                add(result, "Game and betting", "roulette.min-bet", "Minimum bet", "Economy amount", Kind.TEXT);
                add(result, "Game and betting", "roulette.max-bet", "Maximum bet", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Game and betting", "roulette.max-bet-distance", "Maximum bet distance", "Blocks", Kind.TEXT);
                add(result, "Game and betting", "roulette.activation-distance-from-frame", "Activation distance", "Blocks", Kind.TEXT);
                add(result, "Payouts", "roulette.red-percent", "Red probability", "Percentage", Kind.TEXT);
                add(result, "Payouts", "roulette.black-percent", "Black probability", "Percentage", Kind.TEXT);
                add(result, "Payouts", "roulette.green-percent", "Green probability", "Percentage", Kind.TEXT);
                add(result, "Payouts", "roulette.red-multiplier", "Red multiplier", "Payout multiplier", Kind.TEXT);
                add(result, "Payouts", "roulette.black-multiplier", "Black multiplier", "Payout multiplier", Kind.TEXT);
                add(result, "Payouts", "roulette.green-multiplier", "Green multiplier", "Payout multiplier", Kind.TEXT);
                add(result, "Payouts", "roulette.house-edge-percent", "House edge", "Percentage", Kind.TEXT);
                add(result, "Payouts", "roulette.max-payout", "Maximum payout", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Payouts", "roulette.fireworks-per-winner", "Fireworks per winner", "Count", Kind.TEXT);
                addBoolean(result, "Display", "roulette.hologram.enabled", "Hologram enabled");
                add(result, "Display", "roulette.hologram-height", "Hologram height", "Blocks", Kind.TEXT);
                add(result, "Display", "roulette.hologram-line-spacing", "Hologram line spacing", "Blocks", Kind.TEXT);
                add(result, "Display", "roulette.hologram-title-gap", "Hologram title gap", "Blocks", Kind.TEXT);
                add(result, "Display", "roulette.hologram-section-gap", "Hologram section gap", "Blocks", Kind.TEXT);
                add(result, "Display", "roulette.hologram-view-range", "Hologram view range", "Blocks", Kind.TEXT);
                addBoolean(result, "Display", "roulette.broadcast-top-winner", "Broadcast top winner");
                addBoolean(result, "Display", "roulette.announcements.broadcast-win", "Broadcast wins");
                addBoolean(result, "Display", "roulette.announcements.broadcast-loss", "Broadcast losses");
                addMaterial(result, "Blocks", "roulette.blocks.frame", "Frame block");
                addMaterial(result, "Blocks", "roulette.blocks.red", "Red block");
                addMaterial(result, "Blocks", "roulette.blocks.black", "Black block");
                addMaterial(result, "Blocks", "roulette.blocks.green", "Green block");
                addMaterial(result, "Blocks", "roulette.blocks.selector", "Selector block");
                addAnimation(result, "roulette", List.of("always", "betting_only"));
            }
            case "fights" -> {
                add(result, "Arena and betting", "fights.betting-seconds", "Betting seconds", "Betting phase", Kind.TEXT);
                add(result, "Arena and betting", "fights.celebration-seconds", "Celebration seconds", "Post-fight phase", Kind.TEXT);
                add(result, "Arena and betting", "fights.max-fighters", "Maximum fighters", "2-16", Kind.TEXT);
                add(result, "Arena and betting", "fights.min-bet", "Minimum bet", "Economy amount", Kind.TEXT);
                add(result, "Arena and betting", "fights.max-bet", "Maximum bet", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Arena and betting", "fights.payout-multiplier", "Payout multiplier", "Multiplier", Kind.TEXT);
                add(result, "Arena and betting", "fights.max-payout", "Maximum payout", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Arena and betting", "fights.activation-distance", "Activation distance", "Blocks", Kind.TEXT);
                addOption(result, "Arena and betting", "fights.arena-style", "Arena style", List.of("fence", "walls"));
                addBoolean(result, "Combat", "fights.show-bettor-name-on-fighter", "Show bettor on fighter");
                addBoolean(result, "Combat", "fights.fireworks-for-winning-bettors", "Winner bettor fireworks");
                add(result, "Combat", "fights.fireworks-per-winner", "Fireworks per winner", "Count", Kind.TEXT);
                addBoolean(result, "Announcements", "fights.announcements.broadcast-win", "Broadcast wins");
                addBoolean(result, "Announcements", "fights.announcements.broadcast-loss", "Broadcast losses");
                addBoolean(result, "Hologram", "fights.hologram.enabled", "Enabled");
                addOption(result, "Hologram", "fights.hologram.bet-display-mode", "Bet display", List.of("below_fighter", "bottom"));
                add(result, "Hologram", "fights.hologram-height", "Hologram height", "Blocks", Kind.TEXT);
                add(result, "Hologram", "fights.hologram-line-spacing", "Hologram line spacing", "Blocks", Kind.TEXT);
                add(result, "Hologram", "fights.hologram-view-range", "Hologram view range", "Blocks", Kind.TEXT);
                addMaterial(result, "Blocks", "fights.blocks.fill-block", "Floor block");
                addMaterial(result, "Blocks", "fights.blocks.frame-block", "Wall block");
                addMaterial(result, "Blocks", "fights.blocks.fence-block", "Fence block");
                addMaterial(result, "Blocks", "fights.blocks.casino-frame-block", "Casino frame block");
                addAnimation(result, "fights.casino-frame-animation", List.of("betting_only", "always"));
            }
            case "chicken" -> {
                add(result, "Round and risk", "chicken.betting-seconds", "Betting seconds", "Betting phase", Kind.TEXT);
                add(result, "Round and risk", "chicken.result-seconds", "Result seconds", "Result phase", Kind.TEXT);
                add(result, "Round and risk", "chicken.min-lightning-seconds", "Minimum lightning seconds", "Seconds", Kind.TEXT);
                add(result, "Round and risk", "chicken.max-lightning-seconds", "Maximum lightning seconds", "Seconds", Kind.TEXT);
                add(result, "Round and risk", "chicken.lightning-randomness-curve", "Lightning randomness curve", "1.0 is uniform", Kind.TEXT);
                add(result, "Round and risk", "chicken.min-steps", "Minimum steps", "Route length", Kind.TEXT);
                add(result, "Round and risk", "chicken.max-steps", "Maximum steps", "Route length", Kind.TEXT);
                add(result, "Round and risk", "chicken.dead-tile-count", "Dead tiles", "Count", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.multiplier-increase-per-second", "Multiplier growth", "Per second", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.pickup-multiplier", "Pickup multiplier", "Bonus per pickup", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.red-bonus", "Red bonus", "Multiplier", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.blue-bonus", "Blue bonus", "Multiplier", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.gold-bonus", "Gold bonus", "Multiplier", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.green-bonus", "Green bonus", "Multiplier", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.min-bet", "Minimum bet", "Economy amount", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.max-bet", "Maximum bet", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Betting and payouts", "chicken.max-payout", "Maximum payout", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Board and display", "chicken.activation-distance", "Activation distance", "Blocks", Kind.TEXT);
                addBoolean(result, "Board and display", "chicken.hologram.enabled", "Hologram enabled");
                add(result, "Board and display", "chicken.hologram-height", "Hologram height", "Blocks", Kind.TEXT);
                add(result, "Board and display", "chicken.hologram-line-spacing", "Hologram line spacing", "Blocks", Kind.TEXT);
                add(result, "Board and display", "chicken.hologram-view-range", "Hologram view range", "Blocks", Kind.TEXT);
                addMaterial(result, "Blocks", "chicken.blocks.frame", "Frame block");
                addMaterial(result, "Blocks", "chicken.blocks.red", "Red block");
                addMaterial(result, "Blocks", "chicken.blocks.blue", "Blue block");
                addMaterial(result, "Blocks", "chicken.blocks.gold", "Gold block");
                addMaterial(result, "Blocks", "chicken.blocks.green", "Green block");
                addMaterial(result, "Blocks", "chicken.blocks.dead", "Dead block");
            }
            case "dice" -> {
                addBoolean(result, "Dice", "dice.command-enabled", "Command enabled");
                add(result, "Dice", "dice.default-amount", "Default dice amount", "1-64", Kind.TEXT);
                addOption(result, "Dice", "dice.color", "Dice color", List.of("red", "black", "white", "blue", "green", "yellow", "orange", "cyan", "magenta", "lime", "light_blue"));
                addBoolean(result, "Dice", "dice.player-alert", "Player alerts");
                addBoolean(result, "Dice", "dice.server-alert", "Server alerts");
                addBoolean(result, "Dice", "dice.glow", "Glow");
                addBoolean(result, "Dice", "dice.particles", "Particles");
                addBoolean(result, "Craps", "dice.craps.enabled", "Craps enabled");
                add(result, "Craps", "dice.craps.min-bet", "Craps minimum bet", "Economy amount", Kind.TEXT);
                add(result, "Craps", "dice.craps.max-bet", "Craps maximum bet", "Use -1 for unlimited", Kind.TEXT);
                addOption(result, "Craps", "dice.craps.color", "Craps color", List.of("inherit", "red", "black", "white", "blue", "green", "yellow", "orange", "cyan", "magenta", "lime", "light_blue"));
                addOption(result, "Craps", "dice.craps.player-alert", "Craps player alerts", List.of("inherit", "true", "false"));
                addOption(result, "Craps", "dice.craps.server-alert", "Craps server alerts", List.of("inherit", "true", "false"));
                addOption(result, "Craps", "dice.craps.glow", "Craps glow", List.of("inherit", "true", "false"));
                addOption(result, "Craps", "dice.craps.particles", "Craps particles", List.of("inherit", "true", "false"));
                add(result, "Rate limit", "dice.rate-limit.amount", "Successful rolls", "Use -1 for unlimited", Kind.TEXT);
                addOption(result, "Rate limit", "dice.rate-limit.period", "Rate limit period", List.of("24h", "day"));
            }
            case "slots" -> {
                add(result, "Machine", "slots.reel-count", "Reels / width", "3-8 globally", Kind.TEXT);
                add(result, "Machine", "slots.row-count", "Rows / height", "1-2 globally", Kind.TEXT);
                add(result, "Machine", "slots.spin-seconds", "Spin seconds", "Spin phase", Kind.TEXT);
                add(result, "Machine", "slots.stop-interval-ticks", "Stop interval", "Ticks between reels", Kind.TEXT);
                add(result, "Machine", "slots.result-seconds", "Result seconds", "Result phase", Kind.TEXT);
                add(result, "Machine", "slots.fireworks-per-win", "Win fireworks", "Count", Kind.TEXT);
                add(result, "Betting", "slots.cost-per-spin", "Default spin bet", "Economy amount", Kind.TEXT);
                add(result, "Betting", "slots.min-bet", "Minimum bet", "Economy amount", Kind.TEXT);
                add(result, "Betting", "slots.max-bet", "Maximum bet", "Use -1 for unlimited", Kind.TEXT);
                addBoolean(result, "Betting", "slots.bet-buttons.enabled", "Bet buttons enabled");
                addMaterial(result, "Betting", "slots.bet-buttons.material", "Bet button material");
                add(result, "Betting", "slots.bet-buttons.adjust-percent", "Bet adjustment", "Percentage", Kind.TEXT);
                add(result, "Betting", "slots.max-payout", "Maximum payout", "Use -1 for unlimited", Kind.TEXT);
                add(result, "Display", "slots.activation-distance-from-frame", "Activation distance", "Blocks", Kind.TEXT);
                addOption(result, "Display", "slots.lever-placement", "Lever placement", List.of("front_right_middle", "front_middle", "right_middle", "middle"));
                addOption(result, "Display", "slots.spin-light-mode", "Spin light mode", List.of("flashing_fast", "flashing_slow", "cycle_slow"));
                addBoolean(result, "Display", "slots.hologram.enabled", "Hologram enabled");
                add(result, "Display", "slots.hologram-height", "Hologram height", "Blocks", Kind.TEXT);
                add(result, "Display", "slots.hologram-line-spacing", "Hologram line spacing", "Blocks", Kind.TEXT);
                add(result, "Display", "slots.hologram-view-range", "Hologram view range", "Blocks", Kind.TEXT);
                addBoolean(result, "Display", "slots.announcements.broadcast-win", "Broadcast wins");
                addBoolean(result, "Display", "slots.announcements.broadcast-loss", "Broadcast losses");
                addMaterial(result, "Blocks", "slots.blocks.outer-frame", "Outer frame");
                addMaterial(result, "Blocks", "slots.blocks.inner-frame", "Inner frame");
                addMaterial(result, "Blocks", "slots.blocks.winning", "Winning block");
                addMaterial(result, "Blocks", "slots.shelf-mode.block", "Shelf block");
                addBoolean(result, "Blocks", "slots.shelf-mode.casino-frame", "Shelf casino frame");
                add(result, "Blocks", "slots.blocks.reel-options", "Reel option blocks", "Comma-separated block materials", Kind.TEXT);
                for (int i = 1; i <= 16; i++) {
                    add(result, "Payout multipliers", "slots.payout-multipliers." + i, i + " matching block(s)", "Multiplier", Kind.TEXT);
                }
                addAnimation(result, "slots", List.of("idle_only", "always"));
            }
            default -> {
                // Only known game identifiers are exposed by /casinogui.
            }
        }
        return result;
    }

    private List<Setting> localSettings(String game) {
        List<Setting> result = new ArrayList<>();
        switch (game) {
            case "minegame" -> {
                add(result, "Board override", "minegame.board.grid-size", "Grid size", "Station board size", Kind.TEXT);
                addMaterial(result, "Board override", "minegame.board.hidden-block", "Hidden block");
                addMaterial(result, "Board override", "minegame.board.safe-reveal-block", "Safe reveal block");
                addMaterial(result, "Board override", "minegame.board.mine-reveal-block", "Mine reveal block");
                addMaterial(result, "Board override", "minegame.board.frame-block", "Frame block");
                addBoolean(result, "Frame animation", "minegame.frame-animation.enabled", "Enabled");
                addMaterial(result, "Frame animation", "minegame.frame-animation.block", "Animation block");
                add(result, "Frame animation", "minegame.frame-animation.pattern", "Pattern", "1-10", Kind.TEXT);
                addOption(result, "Frame animation", "minegame.frame-animation.mode", "Mode", List.of("idle_only", "always"));
            }
            case "roulette" -> {
                add(result, "Board override", "roulette.board-size", "Board size", "Station board size", Kind.TEXT);
                addMaterial(result, "Board materials", "roulette.blocks.frame", "Frame block");
                addMaterial(result, "Board materials", "roulette.blocks.red", "Red block");
                addMaterial(result, "Board materials", "roulette.blocks.black", "Black block");
                addMaterial(result, "Board materials", "roulette.blocks.green", "Green block");
                addMaterial(result, "Board materials", "roulette.blocks.selector", "Selector block");
                addBoolean(result, "Frame animation", "roulette.frame-animation.enabled", "Enabled");
                addMaterial(result, "Frame animation", "roulette.frame-animation.block", "Animation block");
                add(result, "Frame animation", "roulette.frame-animation.pattern", "Pattern", "1-10", Kind.TEXT);
                addOption(result, "Frame animation", "roulette.frame-animation.mode", "Mode", List.of("always", "betting_only"));
            }
            case "slots" -> {
                add(result, "Machine size", "slots.reel-count", "Reels / width", "Station reel count", Kind.TEXT);
                add(result, "Machine size", "slots.row-count", "Rows / height", "Station row count", Kind.TEXT);
                add(result, "Betting", "slots.cost-per-spin", "Spin bet", "Station override", Kind.TEXT);
                addMaterial(result, "Blocks", "slots.blocks.outer-frame", "Outer frame");
                addMaterial(result, "Blocks", "slots.blocks.inner-frame", "Inner frame");
                addMaterial(result, "Blocks", "slots.blocks.winning", "Winning block");
                addBoolean(result, "Bet buttons", "slots.bet-buttons.enabled", "Enabled");
                addMaterial(result, "Bet buttons", "slots.bet-buttons.material", "Button material");
                add(result, "Bet buttons", "slots.bet-buttons.adjust-percent", "Adjustment percent", "0-100", Kind.TEXT);
                addBoolean(result, "Frame animation", "slots.frame-animation.enabled", "Enabled");
                addMaterial(result, "Frame animation", "slots.frame-animation.block", "Animation block");
                add(result, "Frame animation", "slots.frame-animation.pattern", "Pattern", "1-10", Kind.TEXT);
                addOption(result, "Frame animation", "slots.frame-animation.mode", "Mode", List.of("idle_only", "always"));
            }
            case "fights" -> add(result, "Arena", "fights.fighter-count", "Fighter count", "2-16", Kind.TEXT);
            default -> {
                // Chicken and dice settings are global only.
            }
        }
        return result;
    }

    private void addAnimation(List<Setting> result, String game, List<String> modes) {
        String prefix = game.contains(".") ? game : game + ".frame-animation";
        addBoolean(result, "Frame animation", prefix + ".enabled", "Enabled");
        addMaterial(result, "Frame animation", prefix + ".block", "Animation block");
        add(result, "Frame animation", prefix + ".pattern", "Pattern", "1-10", Kind.TEXT);
        addOption(result, "Frame animation", prefix + ".mode", "Mode", modes);
        if (!game.contains("casino-frame-animation")) {
            add(result, "Frame animation", prefix + ".interval-ticks", "Interval ticks", "Animation speed", Kind.TEXT);
        }
    }

    private void addMaterial(List<Setting> result, String category, String path, String label) {
        add(result, category, path, label, "Minecraft block used for " + lowerFirst(label) + ".", Kind.TEXT);
    }

    private void addBoolean(List<Setting> result, String category, String path, String label) {
        add(result, category, path, label, booleanDescription(path), Kind.BOOLEAN);
    }

    private void addOption(List<Setting> result, String category, String path, String label, List<String> options) {
        add(result, category, path, label, optionDescription(path, label), Kind.OPTION, options.toArray(String[]::new));
    }

    private String booleanDescription(String path) {
        if (path.endsWith("broadcast-start")) return "Announce when a MineGame round starts.";
        if (path.endsWith("broadcast-cashout")) return "Announce when a player cashes out of MineGame.";
        if (path.endsWith("broadcast-win")) return "Announce winning results to players.";
        if (path.endsWith("broadcast-loss")) return "Announce losing results to players.";
        if (path.endsWith("send-welcome-on-start")) return "Send the configured welcome message when a round starts.";
        if (path.endsWith("fireworks-on-win")) return "Launch fireworks when a player wins.";
        if (path.endsWith("fireworks-for-winning-bettors")) return "Launch fireworks for players who backed the winning fighter.";
        if (path.endsWith("show-bettor-name-on-fighter")) return "Show the bettor's name on the fighter hologram.";
        if (path.endsWith("see-through-walls")) return "Keep casino holograms visible through blocks.";
        if (path.endsWith("frame-one-higher")) return "Raise the board frame one block above the board.";
        if (path.endsWith("affix-to-wall")) return "Attach the hologram to the nearest wall.";
        if (path.endsWith("hologram.enabled")) return "Show this game's hologram.";
        if (path.endsWith("command-enabled")) return "Allow players to use the /dice command.";
        if (path.endsWith("player-alert")) return "Send the roll result to the player who rolled.";
        if (path.endsWith("server-alert")) return "Announce dice rolls to the server.";
        if (path.endsWith("dice.glow")) return "Give dice an enchantment-style glow.";
        if (path.endsWith("dice.particles")) return "Show particles when dice settle.";
        if (path.endsWith("craps.enabled")) return "Allow players to start craps games.";
        if (path.endsWith("bet-buttons.enabled")) return "Show the machine's bet adjustment buttons.";
        if (path.endsWith("shelf-mode.casino-frame")) return "Include the animated casino frame around the shelf.";
        if (path.endsWith("frame-animation.enabled")) return "Animate the casino frame around this game.";
        return "Controls whether this feature is active.";
    }

    private String optionDescription(String path, String label) {
        if (path.equals("dice.color")) return "Color used for newly issued dice.";
        if (path.equals("dice.craps.color")) return "Color used by craps dice; inherit follows the normal dice color.";
        if (path.startsWith("dice.craps.")) return "Craps-specific override; inherit follows the normal dice setting.";
        if (path.equals("dice.rate-limit.period")) return "Time window used for the successful-roll limit.";
        if (path.equals("fights.arena-style")) return "Structure used to build each Fights arena.";
        if (path.endsWith("hologram.bet-display-mode")) return "Where betting totals appear relative to the fighter.";
        if (path.equals("slots.lever-placement")) return "Side of the machine where the lever is placed.";
        if (path.equals("slots.spin-light-mode")) return "How the machine lights animate during a spin.";
        if (path.endsWith("frame-animation.mode")) return "When the casino frame animation runs.";
        return "Select the " + lowerFirst(label) + " used by this game.";
    }

    private String lowerFirst(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private void add(List<Setting> result, String category, String path, String label, String description, Kind kind, String... options) {
        result.add(new Setting(category, path, label, description, kind, List.of(options)));
    }

    private String inputKey(Setting setting) {
        return "setting_" + setting.path().replace('.', '_').replace('-', '_');
    }

    private String displayValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String gameTitle(String game) {
        return switch (game) {
            case "minegame" -> "MINEGAME";
            case "roulette" -> "ROULETTE";
            case "slots" -> "SLOTS";
            case "fights" -> "FIGHTS";
            case "chicken" -> "CHICKEN";
            case "dice" -> "DICE & CRAPS";
            default -> game.toUpperCase(Locale.ROOT);
        };
    }

    private String location(String world, int x, int y, int z) {
        return world + " · " + x + ", " + y + ", " + z;
    }

    private int number(String type, String key) {
        return plugin.stationNumberStorage().number(type, key);
    }
}
