package dev.minegame.mines;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

public final class RouletteManager {
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String HOLO_TAG = "minegame_roulette_holo";
    private static final String HOLO_STATION_TAG_PREFIX = "minegame_roulette_holo_station_";
    private static final Random RNG = new Random();

    private final MinegamePlugin plugin;
    private final Economy economy;
    private final RouletteStationStorage stationStorage;
    private final BlockSnapshotStorage restoreStorage;
    private final Map<String, StationRuntime> runtimes = new HashMap<>();
    private final Map<String, List<UUID>> holograms = new HashMap<>();

    private BukkitTask ticker;

    private int boardSize;
    private double redPercent;
    private double blackPercent;
    private double greenPercent;
    private int bettingSeconds;
    private int spinSeconds;
    private int resultSeconds;
    private double minBet;
    private double maxBet;
    private double redMultiplier;
    private double blackMultiplier;
    private double greenMultiplier;
    private double houseEdgePercent;
    private double maxPayout;
    private int fireworksPerWinner;
    private boolean broadcastTopWinner;

    private Material frameBlock;
    private Material redBlock;
    private Material blackBlock;
    private Material greenBlock;
    private Material selectorBlock;

    public RouletteManager(MinegamePlugin plugin, Economy economy, RouletteStationStorage stationStorage, BlockSnapshotStorage restoreStorage) {
        this.plugin = plugin;
        this.economy = economy;
        this.stationStorage = stationStorage;
        this.restoreStorage = restoreStorage;
        loadConfig();

        for (RouletteStationData station : stationStorage.all()) {
            StationRuntime runtime = new StationRuntime(station);
            runtimes.put(station.key(), runtime);
            startRound(runtime);
        }
    }

    public void start() {
        removeOrphanHolograms();
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (StationRuntime runtime : runtimes.values()) {
            if (runtime.spinTask != null) {
                runtime.spinTask.cancel();
            }
            removeSelector(runtime);
        }
        clearAllHolograms();
    }

    public void reloadConfig(Player requester) {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        loadConfig();
        for (StationRuntime runtime : runtimes.values()) {
            startRound(runtime);
        }
        requester.sendMessage(color(msg("roulette.messages.reloaded", "&aRoulette config reloaded.")));
    }

    public void createStation(Player player) {
        Location l = player.getLocation();
        RouletteStationData station = new RouletteStationData(player.getWorld().getName(), l.getBlockX(), l.getBlockY() - 1, l.getBlockZ());
        captureStationBlocksIfNeeded(station);
        stationStorage.upsert(station);
        stationStorage.save();
        StationRuntime runtime = new StationRuntime(station);
        runtimes.put(station.key(), runtime);
        startRound(runtime);
        player.sendMessage(color(msg("roulette.messages.created", "&aRoulette station created.")));
    }

    public void removeStation(Player player) {
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
            runtime.spinTask = null;
        }
        runtime.phase = Phase.RESULT;
        removeSelector(runtime);
        if (restoreStorage.has(runtime.station.key())) {
            restoreStorage.restoreAndForget(runtime.station.key());
        } else {
            removeBoard(runtime);
        }
        deleteHologram(runtime.station.key());
        runtimes.remove(runtime.station.key());
        stationStorage.remove(runtime.station.key());
        stationStorage.save();
        player.sendMessage(color(msg("roulette.messages.removed", "&eRoulette station removed.")));
    }

    public void regenerateStation(Player player) {
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        startRound(runtime);
        player.sendMessage(color(msg("roulette.messages.regenerated", "&aRoulette station regenerated.")));
    }

    public void listStations(Player player) {
        player.sendMessage(color("&6[Roulette] &fStations: &e" + runtimes.size()));
        for (StationRuntime runtime : runtimes.values()) {
            RouletteStationData s = runtime.station;
            player.sendMessage(color("&7- &f" + s.worldName() + " &7(" + s.x() + ", " + s.y() + ", " + s.z() + ")"));
        }
    }

    public void setConfigValue(Player player, String pathInput, String valueInput) {
        String path = pathInput.toLowerCase();
        Object parsed = parseConfigValue(path, valueInput);
        if (parsed == null) {
            player.sendMessage(color(msg("roulette.messages.config-invalid", "&cInvalid roulette config value/path.")));
            return;
        }
        plugin.getConfig().set(path, parsed);
        plugin.saveConfig();
        loadConfig();
        for (StationRuntime runtime : runtimes.values()) {
            startRound(runtime);
        }
        player.sendMessage(color("&aSet &f" + path + " &ato &f" + parsed + "&a."));
    }

    public Object getCurrentConfigValue(String path) {
        return plugin.getConfig().get(path);
    }

    public void saveStation(RouletteStationData station, boolean regenerateBoard) {
        stationStorage.upsert(station);
        stationStorage.save();
        StationRuntime runtime = runtimes.get(station.key());
        if (runtime != null) {
            runtime.station = station;
            if (regenerateBoard) {
                startRound(runtime);
            }
        } else {
            StationRuntime created = new StationRuntime(station);
            runtimes.put(station.key(), created);
            if (regenerateBoard) {
                startRound(created);
            }
        }
    }

    public void saveAllStations(List<RouletteStationData> stations, boolean regenerateBoards) {
        for (RouletteStationData station : stations) {
            stationStorage.upsert(station);
        }
        stationStorage.save();
        for (RouletteStationData station : stations) {
            StationRuntime runtime = runtimes.get(station.key());
            if (runtime != null) {
                runtime.station = station;
                if (regenerateBoards) {
                    startRound(runtime);
                }
            } else {
                StationRuntime created = new StationRuntime(station);
                runtimes.put(station.key(), created);
                if (regenerateBoards) {
                    startRound(created);
                }
            }
        }
    }

    public Collection<RouletteStationData> stations() {
        return stationStorage.all();
    }

    private void setStationBoardMaterial(
            Player player,
            String materialName,
            boolean applyAll,
            String label,
            String target
    ) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isBlock()) {
            player.sendMessage(color("&cInvalid block material."));
            return;
        }
        if (applyAll) {
            List<RouletteStationData> updated = new ArrayList<>();
            for (RouletteStationData station : stationStorage.all()) {
                updated.add(switch (target) {
                    case "frame" -> station.withBoardMaterials(material.name(), station.boardRedBlock(), station.boardBlackBlock(), station.boardGreenBlock(), station.boardSelectorBlock());
                    case "red" -> station.withBoardMaterials(station.boardFrameBlock(), material.name(), station.boardBlackBlock(), station.boardGreenBlock(), station.boardSelectorBlock());
                    case "black" -> station.withBoardMaterials(station.boardFrameBlock(), station.boardRedBlock(), material.name(), station.boardGreenBlock(), station.boardSelectorBlock());
                    case "green" -> station.withBoardMaterials(station.boardFrameBlock(), station.boardRedBlock(), station.boardBlackBlock(), material.name(), station.boardSelectorBlock());
                    case "selector" -> station.withBoardMaterials(station.boardFrameBlock(), station.boardRedBlock(), station.boardBlackBlock(), station.boardGreenBlock(), material.name());
                    default -> station;
                });
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&a" + label + " set to &f" + material.name() + " &afor all roulette stations."));
            return;
        }

        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        RouletteStationData station = runtime.station;
        RouletteStationData updated = switch (target) {
            case "frame" -> station.withBoardMaterials(material.name(), station.boardRedBlock(), station.boardBlackBlock(), station.boardGreenBlock(), station.boardSelectorBlock());
            case "red" -> station.withBoardMaterials(station.boardFrameBlock(), material.name(), station.boardBlackBlock(), station.boardGreenBlock(), station.boardSelectorBlock());
            case "black" -> station.withBoardMaterials(station.boardFrameBlock(), station.boardRedBlock(), material.name(), station.boardGreenBlock(), station.boardSelectorBlock());
            case "green" -> station.withBoardMaterials(station.boardFrameBlock(), station.boardRedBlock(), station.boardBlackBlock(), material.name(), station.boardSelectorBlock());
            case "selector" -> station.withBoardMaterials(station.boardFrameBlock(), station.boardRedBlock(), station.boardBlackBlock(), station.boardGreenBlock(), material.name());
            default -> station;
        };
        saveStation(updated, true);
        player.sendMessage(color("&a" + label + " set to &f" + material.name() + "&a for this roulette station."));
    }

    public void setFrameMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Frame block", "frame");
    }

    public void setRedMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Red block", "red");
    }

    public void setBlackMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Black block", "black");
    }

    public void setGreenMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Green block", "green");
    }

    public void setSelectorMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Selector block", "selector");
    }

    public void resetBoardMaterialOverrides(Player player, boolean applyAll) {
        if (applyAll) {
            List<RouletteStationData> updated = new ArrayList<>();
            for (RouletteStationData station : stationStorage.all()) {
                updated.add(station.clearBoardMaterialOverrides());
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&aRoulette board material overrides reset for all stations."));
            return;
        }
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        saveStation(runtime.station.clearBoardMaterialOverrides(), true);
        player.sendMessage(color("&aRoulette board material overrides reset for this station."));
    }

    public void setFrameAnimation(Player player, String blockName, int pattern, boolean applyAll) {
        Material block = Material.matchMaterial(blockName);
        if (block == null || !block.isBlock()) {
            player.sendMessage(color("&cInvalid block material."));
            return;
        }
        if (pattern < 1 || pattern > 10) {
            player.sendMessage(color("&cPattern must be between 1 and 10."));
            return;
        }
        if (applyAll) {
            List<RouletteStationData> updated = new ArrayList<>();
            for (RouletteStationData station : stationStorage.all()) {
                updated.add(station.withFrameAnimation(true, block.name(), pattern, null));
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&aRoulette casino frame updated for all stations: &f" + block.name() + "&a, pattern &f" + pattern + "&a."));
            return;
        }
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        saveStation(runtime.station.withFrameAnimation(true, block.name(), pattern, null), true);
        player.sendMessage(color("&aRoulette casino frame updated: &f" + block.name() + "&a, pattern &f" + pattern + "&a."));
    }

    public void setFrameAnimationMode(Player player, String mode, boolean applyAll) {
        String normalized = normalizeRouletteFrameMode(mode);
        if (normalized == null) {
            player.sendMessage(color("&cMode must be always or betting_only."));
            return;
        }
        if (applyAll) {
            List<RouletteStationData> updated = new ArrayList<>();
            for (RouletteStationData station : stationStorage.all()) {
                updated.add(station.withFrameAnimation(true, null, null, normalized));
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&aRoulette casino frame mode set to &f" + normalized + " &afor all stations."));
            return;
        }
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        saveStation(runtime.station.withFrameAnimation(true, null, null, normalized), true);
        player.sendMessage(color("&aRoulette casino frame mode set to &f" + normalized + "&a."));
    }

    public void disableFrameAnimation(Player player, boolean applyAll) {
        if (applyAll) {
            List<RouletteStationData> updated = new ArrayList<>();
            for (RouletteStationData station : stationStorage.all()) {
                updated.add(station.withFrameAnimation(false, null, null, null));
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&eRoulette casino frame animation disabled for all stations."));
            return;
        }
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        saveStation(runtime.station.withFrameAnimation(false, null, null, null), true);
        player.sendMessage(color("&eRoulette casino frame animation disabled."));
    }

    public void resetFrameAnimationOverrides(Player player, boolean applyAll) {
        if (applyAll) {
            List<RouletteStationData> updated = new ArrayList<>();
            for (RouletteStationData station : stationStorage.all()) {
                updated.add(station.clearFrameAnimationOverrides());
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&aRoulette casino frame animation overrides reset for all stations."));
            return;
        }
        StationRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-on-station", "&cStand on a roulette board/frame to use this.")));
            return;
        }
        saveStation(runtime.station.clearFrameAnimationOverrides(), true);
        player.sendMessage(color("&aRoulette casino frame animation overrides reset for this station."));
    }

    public RouletteBoardGeometry geometryForStation(RouletteStationData station) {
        return new RouletteBoardGeometry(station, boardSize);
    }

    public boolean isBettingPhase(RouletteStationData station) {
        StationRuntime runtime = runtimes.get(station.key());
        return runtime != null && runtime.phase == Phase.BETTING;
    }

    public boolean isFrameAnimationEnabled(RouletteStationData station) {
        Boolean stationEnabled = station.frameAnimEnabled();
        return stationEnabled != null
                ? stationEnabled
                : plugin.getConfig().getBoolean("roulette-frame-animation.enabled", false);
    }

    public Material frameAnimationBlock(RouletteStationData station) {
        Material global = parseMaterial(
                plugin.getConfig().getString("roulette-frame-animation.block"),
                Material.REDSTONE_LAMP
        );
        return parseMaterial(station.frameAnimBlock(), global);
    }

    public int frameAnimationPattern(RouletteStationData station) {
        Integer stationPattern = station.frameAnimPattern();
        int global = plugin.getConfig().getInt("roulette-frame-animation.pattern", 1);
        return stationPattern != null ? stationPattern : global;
    }

    public String frameAnimationMode(RouletteStationData station) {
        String stationMode = normalizeRouletteFrameMode(station.frameAnimMode());
        if (stationMode != null) {
            return stationMode;
        }
        return normalizeRouletteFrameMode(plugin.getConfig().getString("roulette-frame-animation.mode", "always"));
    }

    public void placeBet(Player player, RouletteColor color, double amount) {
        if (color == null) {
            player.sendMessage(color(msg("roulette.messages.invalid-color", "&cUse red, black or green.")));
            return;
        }
        if (amount < minBet || (maxBet > 0 && amount > maxBet)) {
            player.sendMessage(color(msg("roulette.messages.invalid-bet", "&cInvalid bet amount.")));
            return;
        }
        StationRuntime runtime = stationForBet(player);
        if (runtime == null) {
            player.sendMessage(color(msg("roulette.messages.not-near", "&cYou are not near a roulette station.")));
            return;
        }
        if (runtime.phase != Phase.BETTING || runtime.secondsLeft <= 0) {
            player.sendMessage(color(msg("roulette.messages.bet-closed", "&cBetting is closed.")));
            return;
        }

        EconomyResponse withdraw = economy.withdrawPlayer(player, amount);
        if (!withdraw.transactionSuccess()) {
            player.sendMessage(color(msg("roulette.messages.no-money", "&cYou do not have enough money.")));
            return;
        }

        RouletteBet existing = runtime.bets.get(player.getUniqueId());
        if (existing != null && existing.color() != color) {
            economy.depositPlayer(player, existing.amount());
            runtime.bets.put(player.getUniqueId(), new RouletteBet(color, amount));
        } else if (existing != null) {
            runtime.bets.put(player.getUniqueId(), new RouletteBet(color, existing.amount() + amount));
        } else {
            runtime.bets.put(player.getUniqueId(), new RouletteBet(color, amount));
        }

        player.sendMessage(color(replace(msg("roulette.messages.bet-placed", "&aBet placed: &f%color% &6$%amount%"), Map.of(
                "%color%", color.displayName(),
                "%amount%", MONEY.format(amount)
        ))));
    }

    public boolean isProtectedBlock(Block block) {
        for (StationRuntime runtime : runtimes.values()) {
            RouletteBoardGeometry g;
            try {
                g = runtime.geometry(boardSize);
            } catch (IllegalStateException ignored) {
                continue;
            }
            if (g.isBoardBlock(block) || g.isFrameBlock(block)) {
                return true;
            }
            if (runtime.selectorIndex >= 0 && sameBlock(selectorBlockFor(runtime, runtime.selectorIndex), block)) {
                return true;
            }
        }
        return false;
    }

    private void tick() {
        for (StationRuntime runtime : runtimes.values()) {
            runtime.secondsLeft--;
            if (runtime.phase == Phase.BETTING && runtime.secondsLeft <= 0) {
                beginSpin(runtime);
            } else if (runtime.phase == Phase.SPINNING && runtime.secondsLeft <= 0) {
                finalizeSpin(runtime);
            } else if (runtime.phase == Phase.RESULT && runtime.secondsLeft <= 0) {
                startRound(runtime);
            }
            updateHologram(runtime);
        }
    }

    private void startRound(StationRuntime runtime) {
        runtime.phase = Phase.BETTING;
        runtime.secondsLeft = bettingSeconds;
        runtime.bets.clear();
        runtime.resultLines.clear();
        runtime.winningColor = null;
        clearSelectorLayer(runtime);
        generatePattern(runtime);
        renderBoard(runtime);
    }

    private void beginSpin(StationRuntime runtime) {
        runtime.phase = Phase.SPINNING;
        runtime.secondsLeft = spinSeconds;
        runtime.resultLines.clear();
        runtime.selectorIndex = RNG.nextInt(boardSize * boardSize);
        setSelector(runtime, runtime.selectorIndex);
        startSpinAnimation(runtime);
    }

    private void finalizeSpin(StationRuntime runtime) {
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
            runtime.spinTask = null;
        }
        if (runtime.selectorIndex < 0) {
            runtime.selectorIndex = RNG.nextInt(boardSize * boardSize);
            setSelector(runtime, runtime.selectorIndex);
        }

        RouletteColor win = runtime.pattern.getOrDefault(runtime.selectorIndex, RouletteColor.RED);
        runtime.winningColor = win;

        List<ResultEntry> results = settle(runtime, win);

        runtime.resultLines.clear();
        runtime.resultLines.add("&eLanded on " + win.colorCode() + win.displayName());
        for (ResultEntry entry : results.stream().limit(8).toList()) {
            String sign = entry.net >= 0 ? "&a+" : "&c";
            runtime.resultLines.add(sign + "$" + MONEY.format(Math.abs(entry.net)) + " &7| &f" + entry.player + " &8| " + entry.color.colorCode() + entry.color.displayName());
        }

        applyWinWipe(runtime, win);
        runtime.phase = Phase.RESULT;
        runtime.secondsLeft = resultSeconds;
    }

    private List<ResultEntry> settle(StationRuntime runtime, RouletteColor win) {
        List<ResultEntry> out = new ArrayList<>();
        ResultEntry topWinner = null;

        for (Map.Entry<UUID, RouletteBet> entry : runtime.bets.entrySet()) {
            UUID id = entry.getKey();
            RouletteBet bet = entry.getValue();
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (name == null) {
                name = "Player";
            }

            double payout = 0.0;
            if (bet.color() == win) {
                payout = clampPayout(bet.amount() * effectiveMultiplier(win));
                EconomyResponse response = economy.depositPlayer(Bukkit.getOfflinePlayer(id), payout);
                if (!response.transactionSuccess()) {
                    payout = 0.0;
                }
                launchWinnerFireworks(runtime, id);
            }
            double net = payout - bet.amount();
            ResultEntry result = new ResultEntry(name, bet.color(), payout, net);
            out.add(result);

            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                if (payout > 0) {
                    online.sendMessage(color(replace(msg("roulette.messages.win-private", "&aYou won &6$%payout% &aon %color%!"), Map.of(
                            "%payout%", MONEY.format(payout),
                            "%color%", win.displayName()
                    ))));
                } else {
                    online.sendMessage(color(replace(msg("roulette.messages.lose-private", "&cYou lost &6$%amount% &con %color%."), Map.of(
                            "%amount%", MONEY.format(bet.amount()),
                            "%color%", bet.color().displayName()
                    ))));
                }
            }

            if (payout > 0 && (topWinner == null || payout > topWinner.payout)) {
                topWinner = new ResultEntry(name, win, payout, net);
            }
        }

        if (broadcastTopWinner && topWinner != null) {
            Bukkit.broadcastMessage(color(replace(msg("roulette.messages.top-winner-broadcast", "&6[Roulette] &f%player% won &a$%payout% &fon %color%!"), Map.of(
                    "%player%", topWinner.player,
                    "%payout%", MONEY.format(topWinner.payout),
                    "%color%", win.displayName()
            ))));
        }

        out.sort(Comparator.comparingDouble((ResultEntry r) -> r.net).reversed());
        return out;
    }

    private void applyWinWipe(StationRuntime runtime, RouletteColor win) {
        RouletteBoardGeometry g = runtime.geometry(boardSize);
        int max = boardSize;
        for (int r = 0; r <= max; r++) {
            int radius = r;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int row = 0; row < boardSize; row++) {
                    for (int col = 0; col < boardSize; col++) {
                        int dx = Math.abs(col - boardSize / 2);
                        int dz = Math.abs(row - boardSize / 2);
                        if (dx + dz <= radius) {
                            g.cellBlock(col, row).setType(blockFor(runtime.station, win), false);
                        }
                    }
                }
            }, r * 2L);
        }
    }

    private void startSpinAnimation(StationRuntime runtime) {
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
        }
        int total = Math.max(20, spinSeconds * 6);
        scheduleSpinStep(runtime, 0, total);
    }

    private void scheduleSpinStep(StationRuntime runtime, int step, int total) {
        if (runtime.phase != Phase.SPINNING || step >= total) {
            return;
        }
        setSelector(runtime, RNG.nextInt(boardSize * boardSize));
        double progress = (double) step / Math.max(1, total - 1);
        int delay = 2 + (int) Math.floor(progress * 8.0);
        runtime.spinTask = Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleSpinStep(runtime, step + 1, total), delay);
    }

    private void setSelector(StationRuntime runtime, int index) {
        removeSelector(runtime);
        runtime.selectorIndex = index;
        selectorBlockFor(runtime, index).setType(selectorBlockFor(runtime.station), false);
    }

    private void removeSelector(StationRuntime runtime) {
        if (runtime.selectorIndex < 0) {
            return;
        }
        Block old = selectorBlockFor(runtime, runtime.selectorIndex);
        if (old.getType() != Material.AIR) {
            old.setType(Material.AIR, false);
        }
        runtime.selectorIndex = -1;
    }

    private void clearSelectorLayer(StationRuntime runtime) {
        RouletteBoardGeometry g = runtime.geometry(boardSize);
        for (Block block : g.boardBlocks()) {
            Block above = block.getWorld().getBlockAt(block.getLocation().add(0, 1, 0));
            if (above.getType() != Material.AIR) {
                above.setType(Material.AIR, false);
            }
        }
        runtime.selectorIndex = -1;
    }

    private void generatePattern(StationRuntime runtime) {
        runtime.pattern.clear();
        int total = boardSize * boardSize;
        Map<Integer, RouletteColor> pattern = runtime.pattern;

        int[] counts = calculateColorCounts(total);
        int reds = counts[0];
        int blacks = counts[1];
        int greens = counts[2];
        Set<Integer> greenIndexes = new HashSet<>();
        while (greenIndexes.size() < greens) {
            greenIndexes.add(RNG.nextInt(total));
        }

        List<Integer> nonGreenIndexes = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (!greenIndexes.contains(i)) {
                nonGreenIndexes.add(i);
            }
        }
        java.util.Collections.shuffle(nonGreenIndexes, RNG);
        int redCount = Math.max(0, Math.min(reds, nonGreenIndexes.size()));
        int blackCount = Math.max(0, Math.min(blacks, nonGreenIndexes.size() - redCount));
        for (int i = 0; i < nonGreenIndexes.size(); i++) {
            int idx = nonGreenIndexes.get(i);
            if (i < redCount) {
                pattern.put(idx, RouletteColor.RED);
            } else if (i < redCount + blackCount) {
                pattern.put(idx, RouletteColor.BLACK);
            } else {
                pattern.put(idx, (i % 2 == 0) ? RouletteColor.RED : RouletteColor.BLACK);
            }
        }
        for (int idx : greenIndexes) {
            pattern.put(idx, RouletteColor.GREEN);
        }
    }

    private int[] calculateColorCounts(int totalCells) {
        double r = Math.max(0.0, redPercent);
        double b = Math.max(0.0, blackPercent);
        double g = Math.max(0.0, greenPercent);
        double sum = r + b + g;
        if (sum <= 0.0) {
            return new int[] {totalCells / 2, totalCells - (totalCells / 2), 0};
        }

        double[] normalized = new double[] {r / sum, b / sum, g / sum};
        int[] counts = new int[3];
        double[] remainder = new double[3];
        int used = 0;
        for (int i = 0; i < 3; i++) {
            double exact = normalized[i] * totalCells;
            counts[i] = (int) Math.floor(exact);
            remainder[i] = exact - counts[i];
            used += counts[i];
        }

        int left = totalCells - used;
        while (left > 0) {
            int best = 0;
            for (int i = 1; i < 3; i++) {
                if (remainder[i] > remainder[best]) {
                    best = i;
                }
            }
            counts[best]++;
            remainder[best] = 0.0;
            left--;
        }
        return counts;
    }

    private void renderBoard(StationRuntime runtime) {
        RouletteBoardGeometry g = runtime.geometry(boardSize);
        Material stationFrameBlock = visualFrameBlock(runtime.station);
        for (Block frame : g.frameBlocks()) {
            frame.setType(stationFrameBlock, false);
        }
        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                int idx = row * boardSize + col;
                g.cellBlock(col, row).setType(blockFor(runtime.station, runtime.pattern.getOrDefault(idx, RouletteColor.RED)), false);
            }
        }
    }

    private void removeBoard(StationRuntime runtime) {
        RouletteBoardGeometry g = runtime.geometry(boardSize);
        for (Block block : g.boardBlocks()) {
            block.setType(Material.AIR, false);
            Block above = block.getWorld().getBlockAt(block.getLocation().add(0, 1, 0));
            if (isSelectorLayerMaterial(above.getType())) {
                above.setType(Material.AIR, false);
            }
        }
        for (Block frame : g.frameBlocks()) {
            frame.setType(Material.AIR, false);
        }
    }

    private Block selectorBlockFor(StationRuntime runtime, int index) {
        RouletteBoardGeometry g = runtime.geometry(boardSize);
        int row = index / boardSize;
        int col = index % boardSize;
        Block base = g.cellBlock(col, row);
        return base.getWorld().getBlockAt(base.getLocation().add(0, 1, 0));
    }

    private void updateHologram(StationRuntime runtime) {
        Location anchor = runtime.geometry(boardSize).centerAbove(plugin.getConfig().getDouble("roulette.hologram-height", 3.5));
        List<String> lines = linesFor(runtime);
        List<UUID> ids = holograms.get(runtime.station.key());
        pruneNearbyHologramDuplicates(anchor, ids);
        if (ids == null || ids.size() != lines.size()) {
            renderHologram(runtime.station.key(), anchor, lines);
            return;
        }
        List<Double> yOffsets = computeLineOffsets(lines);
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = Bukkit.getEntity(ids.get(i));
            if (!(entity instanceof TextDisplay display)) {
                renderHologram(runtime.station.key(), anchor, lines);
                return;
            }
            display.teleport(anchor.clone().add(0, -yOffsets.get(i), 0));
            display.text(component(lines.get(i)));
        }
    }

    private void renderHologram(String stationKey, Location anchor, List<String> lines) {
        deleteHologram(stationKey);
        purgeNearbyHolograms(anchor);
        List<Double> yOffsets = computeLineOffsets(lines);
        List<UUID> ids = new ArrayList<>();
        String stationTag = stationHoloTag(stationKey);
        for (int i = 0; i < lines.size(); i++) {
            Location loc = anchor.clone().add(0, -yOffsets.get(i), 0);
            int line = i;
            TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(HOLO_TAG);
                spawned.addScoreboardTag(stationTag);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setSeeThrough(true);
                spawned.setShadowed(false);
                spawned.setDefaultBackground(false);
                spawned.setLineWidth(Integer.MAX_VALUE);
                spawned.text(component(lines.get(line)));
            });
            ids.add(display.getUniqueId());
        }
        holograms.put(stationKey, ids);
    }

    private List<String> linesFor(StationRuntime runtime) {
        List<String> lines = new ArrayList<>();
        if (runtime.phase == Phase.BETTING) {
            lines.add("&6&lROULETTE");
            lines.add("&e" + runtime.secondsLeft + "s remaining to place bets");
            lines.add("&7Type &e/roulette <red|black|green> <amount>");
        } else if (runtime.phase == Phase.SPINNING) {
            lines.add("&6&lROULETTE");
            lines.add("&eSpinning... &f" + runtime.secondsLeft + "s");
        } else {
            lines.add("&6&lROULETTE");
            if (runtime.resultLines.isEmpty()) {
                lines.add("&eRound complete");
            } else {
                lines.addAll(runtime.resultLines);
            }
        }

        if (!runtime.bets.isEmpty() && runtime.phase != Phase.RESULT) {
            lines.add("&7Bets:");
            runtime.bets.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue().amount(), a.getValue().amount()))
                    .limit(6)
                    .forEach(entry -> {
                        String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        if (name == null) {
                            name = "Player";
                        }
                        RouletteBet bet = entry.getValue();
                        lines.add("&f" + name + " &7- " + bet.color().colorCode() + bet.color().displayName() + " &6$" + MONEY.format(bet.amount()));
                    });
        }
        return lines;
    }

    private List<Double> computeLineOffsets(List<String> lines) {
        double spacing = plugin.getConfig().getDouble("roulette.hologram-line-spacing", 0.28);
        List<Double> offsets = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            offsets.add(i * spacing);
        }
        return offsets;
    }

    private void deleteHologram(String stationKey) {
        String stationTag = stationHoloTag(stationKey);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(HOLO_TAG)
                        && entity.getScoreboardTags().contains(stationTag)) {
                    entity.remove();
                }
            }
        }
        List<UUID> ids = holograms.remove(stationKey);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private void clearAllHolograms() {
        for (String key : new ArrayList<>(holograms.keySet())) {
            deleteHologram(key);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(HOLO_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void removeOrphanHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(HOLO_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void purgeNearbyHolograms(Location anchor) {
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        Collection<Entity> nearby = anchor.getWorld().getNearbyEntities(anchor, 1.8, 3.0, 1.8);
        for (Entity entity : nearby) {
            if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(HOLO_TAG)) {
                entity.remove();
            }
        }
    }

    private void pruneNearbyHologramDuplicates(Location anchor, List<UUID> keep) {
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        Collection<Entity> nearby = anchor.getWorld().getNearbyEntities(anchor, 2.5, 4.0, 2.5);
        for (Entity entity : nearby) {
            if (!(entity instanceof TextDisplay) || !entity.getScoreboardTags().contains(HOLO_TAG)) {
                continue;
            }
            if (keep == null || !keep.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private String stationHoloTag(String stationKey) {
        String hash = UUID.nameUUIDFromBytes(stationKey.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return HOLO_STATION_TAG_PREFIX + hash;
    }

    private void captureStationBlocksIfNeeded(RouletteStationData station) {
        if (restoreStorage.has(station.key())) {
            return;
        }
        RouletteBoardGeometry geometry = new RouletteBoardGeometry(station, boardSize);
        List<Block> affected = new ArrayList<>();
        affected.addAll(geometry.frameBlocks());
        affected.addAll(geometry.boardBlocks());
        for (Block board : geometry.boardBlocks()) {
            affected.add(board.getWorld().getBlockAt(board.getX(), board.getY() + 1, board.getZ()));
        }
        restoreStorage.captureIfAbsent(station.key(), affected);
    }

    private StationRuntime stationForBet(Player player) {
        StationRuntime onBoard = runtimeForPlayer(player);
        if (onBoard != null) {
            return onBoard;
        }
        double maxDistance = plugin.getConfig().getDouble("roulette.max-bet-distance", 18.0);
        return runtimes.values().stream()
                .min(Comparator.comparingDouble(rt -> rt.geometry(boardSize).centerAbove(0).distanceSquared(player.getLocation())))
                .filter(rt -> rt.geometry(boardSize).centerAbove(0).distance(player.getLocation()) <= maxDistance)
                .orElse(null);
    }

    private StationRuntime runtimeForPlayer(Player player) {
        Block feet = player.getLocation().getBlock();
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        for (StationRuntime runtime : runtimes.values()) {
            RouletteBoardGeometry g;
            try {
                g = runtime.geometry(boardSize);
            } catch (IllegalStateException ignored) {
                continue;
            }
            if (g.isBoardBlock(feet) || g.isFrameBlock(feet) || g.isBoardBlock(below) || g.isFrameBlock(below)) {
                return runtime;
            }
        }
        return null;
    }

    private void launchWinnerFireworks(StationRuntime runtime, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        Location center = runtime.geometry(boardSize).centerAbove(1.2);
        for (int i = 0; i < fireworksPerWinner; i++) {
            int delay = i * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Firework firework = (Firework) center.getWorld().spawnEntity(center, EntityType.FIREWORK_ROCKET);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL)
                        .trail(true)
                        .flicker(true)
                        .withColor(Color.RED, Color.LIME, Color.BLACK)
                        .build());
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            }, delay);
        }
    }

    private Material blockFor(RouletteStationData station, RouletteColor color) {
        return switch (color) {
            case RED -> redBlockFor(station);
            case BLACK -> blackBlockFor(station);
            case GREEN -> greenBlockFor(station);
        };
    }

    private double effectiveMultiplier(RouletteColor color) {
        double raw = switch (color) {
            case RED -> redMultiplier;
            case BLACK -> blackMultiplier;
            case GREEN -> greenMultiplier;
        };
        double edgeFactor = Math.max(0.0, Math.min(1.0, 1.0 - (houseEdgePercent / 100.0)));
        return raw * edgeFactor;
    }

    private double clampPayout(double payout) {
        if (maxPayout > 0.0) {
            return Math.min(maxPayout, payout);
        }
        return payout;
    }

    private void loadConfig() {
        this.boardSize = Math.max(4, plugin.getConfig().getInt("roulette.board-size", 12));
        this.redPercent = Math.max(0.0, plugin.getConfig().getDouble("roulette.red-percent", 49.31));
        this.blackPercent = Math.max(0.0, plugin.getConfig().getDouble("roulette.black-percent", 49.31));
        this.greenPercent = Math.max(0.0, plugin.getConfig().getDouble("roulette.green-percent", 1.39));
        this.bettingSeconds = Math.max(3, plugin.getConfig().getInt("roulette.betting-seconds", 15));
        this.spinSeconds = Math.max(3, plugin.getConfig().getInt("roulette.spin-seconds", 10));
        this.resultSeconds = Math.max(2, plugin.getConfig().getInt("roulette.result-seconds", 5));
        this.minBet = Math.max(0.01, plugin.getConfig().getDouble("roulette.min-bet", 1.0));
        this.maxBet = plugin.getConfig().getDouble("roulette.max-bet", -1.0);
        this.redMultiplier = Math.max(1.0, plugin.getConfig().getDouble("roulette.red-multiplier", 2.0));
        this.blackMultiplier = Math.max(1.0, plugin.getConfig().getDouble("roulette.black-multiplier", 2.0));
        this.greenMultiplier = Math.max(1.0, plugin.getConfig().getDouble("roulette.green-multiplier", 12.0));
        this.houseEdgePercent = Math.max(0.0, plugin.getConfig().getDouble("roulette.house-edge-percent", 4.0));
        this.maxPayout = plugin.getConfig().getDouble("roulette.max-payout", -1.0);
        this.fireworksPerWinner = Math.max(1, plugin.getConfig().getInt("roulette.fireworks-per-winner", 1));
        this.broadcastTopWinner = plugin.getConfig().getBoolean("roulette.broadcast-top-winner", true);

        this.frameBlock = parseMaterial(plugin.getConfig().getString("roulette.blocks.frame"), Material.STONE_BRICKS);
        this.redBlock = parseMaterial(plugin.getConfig().getString("roulette.blocks.red"), Material.RED_CONCRETE);
        this.blackBlock = parseMaterial(plugin.getConfig().getString("roulette.blocks.black"), Material.BLACK_CONCRETE);
        this.greenBlock = parseMaterial(plugin.getConfig().getString("roulette.blocks.green"), Material.LIME_CONCRETE);
        this.selectorBlock = parseMaterial(plugin.getConfig().getString("roulette.blocks.selector"), Material.DRAGON_EGG);
    }

    private Object parseConfigValue(String path, String raw) {
        if (!path.startsWith("roulette.")) {
            return null;
        }
        if (path.startsWith("roulette.messages.")) {
            return raw;
        }
        try {
            return switch (path) {
                case "roulette.board-size", "roulette.betting-seconds", "roulette.spin-seconds", "roulette.result-seconds", "roulette.fireworks-per-winner" -> {
                    int v = Integer.parseInt(raw);
                    yield v > 0 ? v : null;
                }
                case "roulette.min-bet",
                        "roulette.max-bet",
                        "roulette.red-multiplier",
                        "roulette.black-multiplier",
                        "roulette.green-multiplier",
                        "roulette.house-edge-percent",
                        "roulette.max-payout",
                        "roulette.hologram-height",
                        "roulette.hologram-line-spacing",
                        "roulette.hologram-title-gap",
                        "roulette.hologram-section-gap",
                        "roulette.max-bet-distance",
                        "roulette.red-percent",
                        "roulette.black-percent",
                        "roulette.green-percent" -> {
                    double v = Double.parseDouble(raw);
                    if (path.endsWith("-percent")) {
                        yield v >= 0.0 ? v : null;
                    }
                    yield v;
                }
                case "roulette.broadcast-top-winner" -> parseBoolean(raw);
                case "roulette.blocks.frame", "roulette.blocks.red", "roulette.blocks.black", "roulette.blocks.green", "roulette.blocks.selector" -> {
                    Material m = Material.matchMaterial(raw);
                    yield m != null && m.isBlock() ? m.name() : null;
                }
                default -> null;
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(String raw) {
        String value = raw.toLowerCase();
        return switch (value) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> null;
        };
    }

    private String normalizeRouletteFrameMode(String raw) {
        if (raw == null) {
            return null;
        }
        String lower = raw.toLowerCase();
        if (lower.equals("always") || lower.equals("betting_only")) {
            return lower;
        }
        return null;
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(raw);
        return parsed != null && parsed.isBlock() ? parsed : fallback;
    }

    private Material visualFrameBlock(RouletteStationData station) {
        Boolean stationEnabled = station.frameAnimEnabled();
        boolean animEnabled = stationEnabled != null
                ? stationEnabled
                : plugin.getConfig().getBoolean("roulette-frame-animation.enabled", false);
        if (!animEnabled) {
            return frameBlockFor(station);
        }
        Material defaultAnimBlock = parseMaterial(
                plugin.getConfig().getString("roulette-frame-animation.block"),
                Material.REDSTONE_LAMP
        );
        return parseMaterial(station.frameAnimBlock(), defaultAnimBlock);
    }

    private Material frameBlockFor(RouletteStationData station) {
        return parseMaterial(station.boardFrameBlock(), frameBlock);
    }

    private Material redBlockFor(RouletteStationData station) {
        return parseMaterial(station.boardRedBlock(), redBlock);
    }

    private Material blackBlockFor(RouletteStationData station) {
        return parseMaterial(station.boardBlackBlock(), blackBlock);
    }

    private Material greenBlockFor(RouletteStationData station) {
        return parseMaterial(station.boardGreenBlock(), greenBlock);
    }

    private Material selectorBlockFor(RouletteStationData station) {
        return parseMaterial(station.boardSelectorBlock(), selectorBlock);
    }

    private String msg(String path, String fallback) {
        String v = plugin.getConfig().getString(path);
        return v == null ? fallback : v;
    }

    private String replace(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private Component component(String input) {
        return LEGACY.deserialize(input);
    }

    private boolean sameBlock(Block a, Block b) {
        return a.getWorld().equals(b.getWorld()) && a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private boolean isSelectorLayerMaterial(Material material) {
        return material == selectorBlock
                || material == redBlock
                || material == blackBlock
                || material == greenBlock
                || material == Material.RED_STAINED_GLASS
                || material == Material.BLACK_STAINED_GLASS
                || material == Material.LIME_STAINED_GLASS;
    }

    private enum Phase { BETTING, SPINNING, RESULT }

    private static final class StationRuntime {
        private RouletteStationData station;
        private final Map<Integer, RouletteColor> pattern = new HashMap<>();
        private final Map<UUID, RouletteBet> bets = new HashMap<>();
        private final List<String> resultLines = new ArrayList<>();
        private Phase phase = Phase.BETTING;
        private int secondsLeft;
        private int selectorIndex = -1;
        private RouletteColor winningColor;
        private BukkitTask spinTask;

        private StationRuntime(RouletteStationData station) {
            this.station = station;
        }

        private RouletteBoardGeometry geometry(int size) {
            return new RouletteBoardGeometry(station, size);
        }
    }

    private static final class ResultEntry {
        private final String player;
        private final RouletteColor color;
        private final double payout;
        private final double net;

        private ResultEntry(String player, RouletteColor color, double payout, double net) {
            this.player = player;
            this.color = color;
            this.payout = payout;
            this.net = net;
        }
    }
}
