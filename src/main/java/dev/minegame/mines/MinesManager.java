package dev.minegame.mines;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class MinesManager {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    private final MinegamePlugin plugin;
    private final Economy economy;
    private final StationStorage stationStorage;
    private final BlockSnapshotStorage restoreStorage;
    private final HouseBalanceStorage houseBalanceStorage;
    private final Map<UUID, ActiveGame> activeByPlayer = new HashMap<>();
    private final Map<String, ActiveGame> activeByStation = new HashMap<>();
    private final Map<String, BukkitTask> resetTasksByStation = new HashMap<>();
    private final Set<UUID> debugPlayers = new HashSet<>();

    private int gridSize;
    private int wallDistance;
    private int frameVerticalOffset;
    private int durationSeconds;
    private double houseEdgePercent;
    private double maxMultiplier;
    private double maxPayout;
    private int resetDelayTicks;
    private Material hiddenBlock;
    private Material safeRevealBlock;
    private Material mineRevealBlock;
    private Material frameBlock;
    private Material stationBlock;
    private boolean fireworksOnWin;
    private int fireworkCount;
    private int winDingCount;
    private String titlePrefix;
    private boolean broadcastStart;
    private boolean broadcastCashout;
    private boolean broadcastWin;
    private boolean sendWelcomeOnStart;

    public MinesManager(
            MinegamePlugin plugin,
            Economy economy,
            StationStorage stationStorage,
            BlockSnapshotStorage restoreStorage,
            HouseBalanceStorage houseBalanceStorage
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.stationStorage = stationStorage;
        this.restoreStorage = restoreStorage;
        this.houseBalanceStorage = houseBalanceStorage;
        loadConfigValues();

        for (StationData station : stationStorage.all()) {
            regenerateBoard(station);
        }
    }

    public void reloadConfig(Player requester) {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        reloadFromCurrentConfigOnly();
        msg(requester, "messages.reloaded");
    }

    public void reloadFromCurrentConfigOnly() {
        loadConfigValues();
        for (StationData station : stationStorage.all()) {
            regenerateBoard(station);
        }
    }

    private void loadConfigValues() {
        this.gridSize = plugin.getConfig().getInt("board.grid-size", 5);
        this.wallDistance = plugin.getConfig().getInt("board.wall-distance", 4);
        this.frameVerticalOffset = plugin.getConfig().getBoolean("board.frame-one-higher", true) ? 1 : 0;
        this.durationSeconds = plugin.getConfig().getInt("game.duration-seconds", 300);
        this.houseEdgePercent = plugin.getConfig().getDouble("game.house-edge-percent", 4.0D);
        this.maxMultiplier = plugin.getConfig().getDouble("game.max-multiplier", 25.0D);
        this.maxPayout = plugin.getConfig().getDouble("game.max-payout", -1.0D);
        this.resetDelayTicks = plugin.getConfig().getInt("board.reset-delay-seconds", 3) * 20;
        this.hiddenBlock = parseMaterial(plugin.getConfig().getString("board.hidden-block"), Material.STONE);
        this.safeRevealBlock = parseMaterial(plugin.getConfig().getString("board.safe-reveal-block"), Material.DIAMOND_BLOCK);
        this.mineRevealBlock = parseMaterial(plugin.getConfig().getString("board.mine-reveal-block"), Material.TNT);
        this.frameBlock = parseMaterial(plugin.getConfig().getString("board.frame-block"), Material.QUARTZ_BLOCK);
        this.stationBlock = parseMaterial(plugin.getConfig().getString("board.station-block"), Material.BEACON);
        this.fireworksOnWin = plugin.getConfig().getBoolean("effects.fireworks-on-win", true);
        this.fireworkCount = Math.max(1, plugin.getConfig().getInt("effects.firework-count", 3));
        this.winDingCount = Math.max(1, plugin.getConfig().getInt("effects.win-ding-count", 5));
        this.titlePrefix = plugin.getConfig().getString("game.title-prefix", "&6[MineGame] &f");
        this.broadcastStart = plugin.getConfig().getBoolean("announcements.broadcast-start", false);
        this.broadcastCashout = plugin.getConfig().getBoolean("announcements.broadcast-cashout", false);
        this.broadcastWin = plugin.getConfig().getBoolean("announcements.broadcast-win", false);
        this.sendWelcomeOnStart = plugin.getConfig().getBoolean("announcements.send-welcome-on-start", true);
    }

    public void shutdown() {
        for (ActiveGame game : new ArrayList<>(activeByPlayer.values())) {
            cancelTicker(game);
        }
        for (BukkitTask task : new ArrayList<>(resetTasksByStation.values())) {
            task.cancel();
        }
        resetTasksByStation.clear();
        activeByPlayer.clear();
        activeByStation.clear();
    }

    public void createStation(Player player) {
        Block beacon = player.getLocation().subtract(0, 1, 0).getBlock();
        if (beacon.getType() != stationBlock) {
            msg(player, "messages.not-on-beacon");
            return;
        }

        BlockFace facing = BoardGeometry.cardinalFromYaw(player.getYaw());
        StationData station = new StationData(
                beacon.getWorld().getName(),
                beacon.getX(),
                beacon.getY(),
                beacon.getZ(),
                facing
        );
        captureStationBlocksIfNeeded(station);

        stationStorage.upsert(station);
        stationStorage.save();
        regenerateBoard(station);
        msg(player, "messages.created");
    }

    public void removeStation(Player player) {
        StationData station = stationFromPlayerBeacon(player);
        if (station == null) {
            msg(player, "messages.not-on-beacon");
            return;
        }
        BukkitTask pendingReset = resetTasksByStation.remove(station.key());
        if (pendingReset != null) {
            pendingReset.cancel();
        }
        ActiveGame activeGame = activeByStation.remove(station.key());
        if (activeGame != null) {
            cancelTicker(activeGame);
            activeByPlayer.remove(activeGame.playerId());
        }
        if (restoreStorage.has(station.key())) {
            restoreStorage.restoreAndForget(station.key());
        } else {
            clearBoard(station);
        }
        stationStorage.remove(station.key());
        stationStorage.save();
        msg(player, "messages.removed");
    }

    public void regenerateStation(Player player) {
        StationData station = stationFromPlayerBeacon(player);
        if (station == null) {
            msg(player, "messages.not-on-beacon");
            return;
        }
        regenerateBoard(station);
        msg(player, "messages.regenerated");
    }

    public void listStations(Player player) {
        player.sendMessage(color("&6[MineGame] &fStations: &e" + stationStorage.all().size()));
        for (StationData station : stationStorage.all()) {
            player.sendMessage(color("&7- &f" + station.worldName() + " &7(" + station.x() + ", " + station.y() + ", " + station.z() + ") &8facing " + station.facing().name()));
        }
    }

    public void startGame(Player player, int mines, double wager) {
        if (mines < 1 || mines > (gridSize * gridSize - 1)) {
            player.sendMessage(color(replaceVars("messages.invalid-mines", Map.of(
                    "%max%", String.valueOf(gridSize * gridSize - 1)
            ))));
            return;
        }
        if (wager <= 0D) {
            msg(player, "messages.invalid-wager");
            return;
        }
        if (activeByPlayer.containsKey(player.getUniqueId())) {
            msg(player, "messages.already-playing");
            return;
        }

        StationData station = stationFromPlayerBeacon(player);
        if (station == null) {
            msg(player, "messages.not-on-beacon");
            return;
        }
        if (activeByStation.containsKey(station.key())) {
            msg(player, "messages.station-busy");
            return;
        }
        if (resetTasksByStation.containsKey(station.key())) {
            msg(player, "messages.station-busy");
            return;
        }

        if (economy.getBalance(player) < wager) {
            msg(player, "messages.no-money");
            return;
        }
        EconomyResponse withdraw = economy.withdrawPlayer(player, wager);
        if (!withdraw.transactionSuccess()) {
            msg(player, "messages.no-money");
            return;
        }

        regenerateBoard(station);
        Set<Integer> mineIndices = generateMines(mines, gridSize * gridSize);
        ActiveGame game = new ActiveGame(player.getUniqueId(), station, mines, wager, mineIndices, durationSeconds);
        activeByPlayer.put(player.getUniqueId(), game);
        activeByStation.put(station.key(), game);

        BukkitTask ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickGame(game), 20L, 20L);
        game.setTickerTask(ticker);

        renderActiveBoard(game);

        player.sendMessage(color(replaceVars("messages.started", Map.of(
                "%mines%", String.valueOf(mines),
                "%wager%", MONEY_FORMAT.format(wager)
        ))));
        if (sendWelcomeOnStart) {
            Material stationSafeBlock = safeRevealBlockFor(station);
            Material stationMineBlock = mineRevealBlockFor(station);
            player.sendMessage(color(prefixed(replaceVars("messages.welcome", Map.of(
                    "%safe_block%", prettyMaterial(stationSafeBlock),
                    "%mine_block%", prettyMaterial(stationMineBlock)
            )))));
        }
        if (broadcastStart) {
            Bukkit.broadcastMessage(color(prefixed(replaceVars("messages.started-broadcast", Map.of(
                    "%player%", player.getName(),
                    "%wager%", MONEY_FORMAT.format(wager),
                    "%mines%", String.valueOf(mines)
            )))));
        }
        sendProgressActionbar(player, game);
        sendProgressChat(player, game);
    }

    public boolean onBlockInteract(Player player, Block block) {
        ActiveGame game = activeByPlayer.get(player.getUniqueId());
        if (game != null) {
            BoardGeometry geometry = geometry(game.station());
            Integer index = geometry.toIndex(block);
            if (index != null) {
                revealBlock(game, player, block, index);
                return true;
            }
            if (geometry.isFrameBlock(block)) {
                cashout(player);
                return true;
            }
        }
        return isStationProtectedBlock(block);
    }

    public boolean onBlockBreak(Player player, Block block) {
        ActiveGame game = activeByPlayer.get(player.getUniqueId());
        if (game != null) {
            BoardGeometry geometry = geometry(game.station());
            Integer index = geometry.toIndex(block);
            if (index != null) {
                revealBlock(game, player, block, index);
                return true;
            }
            if (geometry.isFrameBlock(block)) {
                cashout(player);
                return true;
            }
        }
        return isStationProtectedBlock(block);
    }

    public void cashout(Player player) {
        ActiveGame game = activeByPlayer.get(player.getUniqueId());
        if (game == null) {
            msg(player, "messages.not-playing");
            return;
        }
        if (game.revealedSafeCount() < 1) {
            msg(player, "messages.cashout-none");
            return;
        }
        double multiplier = currentMultiplierFor(game);
        double payout = clampPayout(game.wager() * multiplier);
        EconomyResponse deposit = economy.depositPlayer(player, payout);
        if (!deposit.transactionSuccess()) {
            player.sendMessage(color("&cEconomy error: payout failed. Contact staff."));
            return;
        }
        houseBalanceStorage.recordMinesResult(game.wager(), payout);
        revealMines(game);
        Location beacon = game.station().beaconLocation();
        if (beacon != null && beacon.getWorld() != null) {
            beacon.getWorld().playSound(beacon.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        }
        double profit = Math.max(0.0, payout - game.wager());
        Map<String, String> vars = Map.of(
                "%player%", player.getName(),
                "%payout%", MONEY_FORMAT.format(payout),
                "%xmult%", MONEY_FORMAT.format(multiplier),
                "%profit%", MONEY_FORMAT.format(profit)
        );
        player.sendMessage(color(resolveMessage("messages.cashout", "messages.cashout-broadcast", vars)));
        if (broadcastCashout) {
            Bukkit.broadcastMessage(color(prefixed(resolveMessage("messages.cashout-broadcast", "messages.cashout", vars))));
        }
        cancelAndCleanup(game);
        scheduleStationReset(game.station());
    }

    public void setFrameMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Frame block", "frame");
    }

    public void setHiddenMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Inner hidden block", "hidden");
    }

    public void setSafeRevealMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Prize block", "safe");
    }

    public void setMineRevealMaterial(Player player, String materialName, boolean applyAll) {
        setStationBoardMaterial(player, materialName, applyAll, "Mine block", "mine");
    }

    public void resetBoardMaterialOverrides(Player player, boolean applyAll) {
        if (applyAll) {
            List<StationData> updated = new ArrayList<>();
            for (StationData station : stationStorage.all()) {
                updated.add(station.clearBoardMaterialOverrides());
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&aBoard material overrides reset for all stations."));
            return;
        }
        StationData station = stationFromPlayerBeacon(player);
        if (station == null) {
            msg(player, "messages.not-on-beacon");
            return;
        }
        saveStation(station.clearBoardMaterialOverrides(), true);
        player.sendMessage(color("&aBoard material overrides reset for this station."));
    }

    public void setHologramsEnabled(Player player, boolean enabled) {
        plugin.getConfig().set("hologram.enabled", enabled);
        plugin.saveConfig();
        msg(player, enabled ? "messages.hologram-enabled" : "messages.hologram-disabled");
    }

    public void setDebugMode(Player player, boolean enabled) {
        if (enabled) {
            debugPlayers.add(player.getUniqueId());
            msg(player, "messages.debug-on");
        } else {
            debugPlayers.remove(player.getUniqueId());
            msg(player, "messages.debug-off");
        }
    }

    public void setConfigValue(Player player, String pathInput, String valueInput) {
        String path = pathInput.toLowerCase();
        Object parsed = parseConfigValue(path, valueInput);
        if (parsed == null) {
            if (isAllowedConfigPath(path)) {
                player.sendMessage(color(replaceVars("messages.config-invalid-value", Map.of("%path%", path))));
            } else {
                msg(player, "messages.config-invalid-path");
            }
            return;
        }

        plugin.getConfig().set(path, parsed);
        plugin.saveConfig();
        loadConfigValues();

        if (path.startsWith("board.")) {
            for (StationData station : stationStorage.all()) {
                regenerateBoard(station);
            }
        }

        player.sendMessage(color(replaceVars("messages.config-set", Map.of(
                "%path%", path,
                "%value%", String.valueOf(parsed)
        ))));
    }

    public Collection<StationData> stations() {
        return stationStorage.all();
    }

    public Object getCurrentConfigValue(String path) {
        return plugin.getConfig().get(path);
    }

    public StationData stationFromPlayerBeacon(Player player) {
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getType() != stationBlock) {
            return null;
        }
        String key = below.getWorld().getName() + ":" + below.getX() + ":" + below.getY() + ":" + below.getZ();
        return stationStorage.get(key);
    }

    public void saveStation(StationData station, boolean regenerateBoard) {
        stationStorage.upsert(station);
        stationStorage.save();
        if (regenerateBoard) {
            regenerateBoard(station);
        }
    }

    public void saveAllStations(List<StationData> stations, boolean regenerateBoards) {
        for (StationData station : stations) {
            stationStorage.upsert(station);
        }
        stationStorage.save();
        if (regenerateBoards) {
            for (StationData station : stations) {
                regenerateBoard(station);
            }
        }
    }

    public ActiveGame activeGameForStation(StationData station) {
        return activeByStation.get(station.key());
    }

    public BoardGeometry geometryForStation(StationData station) {
        return geometry(station);
    }

    public int safeTarget(ActiveGame game) {
        return game.safeTargetCount(gridSize);
    }

    public double currentMultiplierFor(ActiveGame game) {
        return multiplierForRevealed(game, game.revealedSafeCount());
    }

    public double nextMultiplierFor(ActiveGame game) {
        int nextRevealed = Math.min(game.revealedSafeCount() + 1, safeTarget(game));
        return multiplierForRevealed(game, nextRevealed);
    }

    public double potentialPayoutFor(ActiveGame game) {
        return clampPayout(game.wager() * currentMultiplierFor(game));
    }

    public double potentialNextPayoutFor(ActiveGame game) {
        return clampPayout(game.wager() * nextMultiplierFor(game));
    }

    private void revealBlock(ActiveGame game, Player player, Block block, int index) {
        StationData station = currentStationState(game.station());
        Material stationMineRevealBlock = mineRevealBlockFor(station);
        Material stationSafeRevealBlock = safeRevealBlockFor(station);
        if (game.isMine(index)) {
            setBlockRevealed(block, stationMineRevealBlock);
            playMineEffect(block.getLocation());
            loseGame(game, player, "messages.hit-mine");
            return;
        }

        if (!game.revealSafe(index)) {
            return;
        }
        setBlockRevealed(block, stationSafeRevealBlock);
        renderActiveBoard(game);
        sendProgressActionbar(player, game);
        sendProgressChat(player, game);

        if (game.revealedSafeCount() >= game.safeTargetCount(gridSize)) {
            winGame(game, player);
        }
    }

    private void setStationBoardMaterial(
            Player player,
            String materialName,
            boolean applyAll,
            String label,
            String kind
    ) {
        Material parsed = Material.matchMaterial(materialName);
        if (parsed == null || !parsed.isBlock()) {
            player.sendMessage(color("&cInvalid block material: &f" + materialName));
            return;
        }

        if (applyAll) {
            List<StationData> updated = new ArrayList<>();
            for (StationData station : stationStorage.all()) {
                updated.add(updateBoardMaterial(station, kind, parsed.name()));
            }
            saveAllStations(updated, true);
            player.sendMessage(color("&a" + label + " set to &f" + parsed.name() + "&a for all stations."));
            return;
        }

        StationData station = stationFromPlayerBeacon(player);
        if (station == null) {
            msg(player, "messages.not-on-beacon");
            return;
        }
        StationData updated = updateBoardMaterial(station, kind, parsed.name());
        saveStation(updated, true);
        player.sendMessage(color("&a" + label + " set to &f" + parsed.name() + "&a for this station."));
    }

    private StationData updateBoardMaterial(StationData station, String kind, String value) {
        String hidden = station.boardHiddenBlock();
        String safe = station.boardSafeRevealBlock();
        String mine = station.boardMineRevealBlock();
        String frame = station.boardFrameBlock();
        switch (kind) {
            case "hidden" -> hidden = value;
            case "safe" -> safe = value;
            case "mine" -> mine = value;
            case "frame" -> frame = value;
            default -> {
            }
        }
        return station.withBoardMaterials(hidden, safe, mine, frame);
    }

    public boolean isStationBoardBlock(Block block) {
        for (StationData station : stationStorage.all()) {
            try {
                if (geometry(station).isBoardBlock(block)) {
                    return true;
                }
            } catch (IllegalStateException ignored) {
                // World not loaded; ignore.
            }
        }
        return false;
    }

    public boolean isStationBeaconBlock(Block block) {
        for (StationData station : stationStorage.all()) {
            if (!station.worldName().equals(block.getWorld().getName())) {
                continue;
            }
            if (station.x() == block.getX() && station.y() == block.getY() && station.z() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    public boolean isStationProtectedBlock(Block block) {
        return isStationBoardBlock(block) || isStationBeaconBlock(block);
    }

    public void onPlayerQuit(UUID playerId) {
        ActiveGame game = activeByPlayer.get(playerId);
        if (game == null) {
            return;
        }
        cancelAndCleanup(game);
        scheduleStationReset(game.station());
    }

    private void tickGame(ActiveGame game) {
        Player player = Bukkit.getPlayer(game.playerId());
        if (player == null || !player.isOnline()) {
            onPlayerQuit(game.playerId());
            return;
        }

        game.decrementSecond();
        renderActiveBoard(game);
        sendProgressActionbar(player, game);
        if (game.secondsLeft() <= 0) {
            loseGame(game, player, "messages.timeout");
        }
    }

    private void sendProgressActionbar(Player player, ActiveGame game) {
        int safeTarget = game.safeTargetCount(gridSize);
        double multiplier = currentMultiplierFor(game);
        player.sendActionBar(color("&7Time: &f" + game.secondsLeft() + "s &7| &a"
                + game.revealedSafeCount() + "/" + safeTarget + " safe &7| &e"
                + MONEY_FORMAT.format(multiplier) + "x"));
    }

    private void sendProgressChat(Player player, ActiveGame game) {
        int safeTarget = game.safeTargetCount(gridSize);
        double multiplier = currentMultiplierFor(game);
        double nextMultiplier = nextMultiplierFor(game);
        double potential = potentialPayoutFor(game);
        double nextPotential = potentialNextPayoutFor(game);
        Map<String, String> vars = Map.of(
                "%revealed%", String.valueOf(game.revealedSafeCount()),
                "%target%", String.valueOf(safeTarget),
                "%xmult%", MONEY_FORMAT.format(multiplier),
                "%xnext%", MONEY_FORMAT.format(nextMultiplier),
                "%potential%", MONEY_FORMAT.format(potential),
                "%next_potential%", MONEY_FORMAT.format(nextPotential)
        );
        player.sendMessage(color(replaceVars("messages.progress", vars)));
    }

    private void loseGame(ActiveGame game, Player player, String messageKey) {
        houseBalanceStorage.recordMinesResult(game.wager(), 0.0);
        player.sendMessage(color(replaceVars(messageKey, Map.of(
                "%wager%", MONEY_FORMAT.format(game.wager())
        ))));
        revealMines(game);
        cancelAndCleanup(game);
        scheduleStationReset(game.station());
    }

    private void playMineEffect(Location location) {
        Location center = location.clone().add(0.5, 0.5, 0.5);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 2, 0.15, 0.15, 0.15, 0.01);
        center.getWorld().spawnParticle(Particle.SMOKE, center, 25, 0.35, 0.35, 0.35, 0.02);
    }

    private void setBlockRevealed(Block block, Material material) {
        block.setType(material, false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != material) {
                block.setType(material, false);
            }
        });
    }

    private void winGame(ActiveGame game, Player player) {
        double payout = clampPayout(game.wager() * currentMultiplierFor(game));
        EconomyResponse deposit = economy.depositPlayer(player, payout);
        double settledPayout = payout;
        if (!deposit.transactionSuccess()) {
            settledPayout = 0.0;
            player.sendMessage(color("&cEconomy error: payout failed. Contact staff."));
        }
        houseBalanceStorage.recordMinesResult(game.wager(), settledPayout);
        player.sendMessage(color(replaceVars("messages.won", Map.of(
                "%payout%", MONEY_FORMAT.format(payout),
                "%xmult%", MONEY_FORMAT.format(currentMultiplierFor(game))
        ))));
        if (broadcastWin) {
            double profit = Math.max(0.0, payout - game.wager());
            Bukkit.broadcastMessage(color(prefixed(resolveMessage("messages.won-broadcast", "messages.won", Map.of(
                    "%player%", player.getName(),
                    "%payout%", MONEY_FORMAT.format(payout),
                    "%xmult%", MONEY_FORMAT.format(currentMultiplierFor(game)),
                    "%profit%", MONEY_FORMAT.format(profit),
                    "%wager%", MONEY_FORMAT.format(game.wager()),
                    "%mines%", String.valueOf(game.mines())
            )))));
        }
        if (fireworksOnWin) {
            launchWinCelebration(game.station());
        }
        cancelAndCleanup(game);
        scheduleStationReset(game.station());
    }

    private void revealMines(ActiveGame game) {
        StationData station = currentStationState(game.station());
        BoardGeometry geometry = geometry(station);
        Material stationMineRevealBlock = mineRevealBlockFor(station);
        for (int i = 0; i < gridSize * gridSize; i++) {
            if (game.isMine(i)) {
                int row = i / gridSize;
                int col = i % gridSize;
                geometry.gridBlock(col, row).setType(stationMineRevealBlock, false);
            }
        }
    }

    private void renderActiveBoard(ActiveGame game) {
        StationData station = currentStationState(game.station());
        BoardGeometry geometry = geometry(station);
        Material stationHiddenBlock = hiddenBlockFor(station);
        Material stationSafeRevealBlock = safeRevealBlockFor(station);
        Material stationMineRevealBlock = mineRevealBlockFor(station);
        boolean showMinesForDebug = debugPlayers.contains(game.playerId());
        Set<Integer> revealedSafe = game.revealedSafeIndices();
        for (int i = 0; i < gridSize * gridSize; i++) {
            Material target;
            if (revealedSafe.contains(i)) {
                target = stationSafeRevealBlock;
            } else if (showMinesForDebug && game.isMine(i)) {
                target = stationMineRevealBlock;
            } else {
                target = stationHiddenBlock;
            }
            int row = i / gridSize;
            int col = i % gridSize;
            Block block = geometry.gridBlock(col, row);
            if (block.getType() != target) {
                block.setType(target, false);
            }
        }
    }

    private void cancelAndCleanup(ActiveGame game) {
        cancelTicker(game);
        activeByPlayer.remove(game.playerId());
        activeByStation.remove(game.station().key());
    }

    private void scheduleStationReset(StationData station) {
        String key = station.key();
        BukkitTask existing = resetTasksByStation.remove(key);
        if (existing != null) {
            existing.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                regenerateBoard(station);
            } finally {
                resetTasksByStation.remove(key);
            }
        }, resetDelayTicks);
        resetTasksByStation.put(key, task);
    }

    private void cancelTicker(ActiveGame game) {
        if (game.tickerTask() != null) {
            game.tickerTask().cancel();
        }
    }

    private void regenerateBoard(StationData station) {
        StationData current = currentStationState(station);
        BoardGeometry geometry = geometry(current);
        Material stationFrameBlock = visualFrameBlockFor(current);
        Material stationHiddenBlock = hiddenBlockFor(current);
        for (Block frame : geometry.frameBlocks()) {
            frame.setType(stationFrameBlock, false);
        }
        for (Block cell : geometry.gridBlocks()) {
            cell.setType(stationHiddenBlock, false);
        }
    }

    private void clearBoard(StationData station) {
        BoardGeometry geometry = geometry(station);
        for (Block frame : geometry.frameBlocks()) {
            frame.setType(Material.AIR, false);
        }
        for (Block cell : geometry.gridBlocks()) {
            cell.setType(Material.AIR, false);
        }
    }

    private void captureStationBlocksIfNeeded(StationData station) {
        if (restoreStorage.has(station.key())) {
            return;
        }
        BoardGeometry geometry = geometry(station);
        List<Block> affected = new ArrayList<>();
        affected.addAll(geometry.frameBlocks());
        affected.addAll(geometry.gridBlocks());
        restoreStorage.captureIfAbsent(station.key(), affected);
    }

    private void launchWinCelebration(StationData station) {
        BoardGeometry geometry = geometry(station);
        List<Location> launches = geometry.frontCelebrationLocations();
        for (int i = 0; i < fireworkCount; i++) {
            int delay = i * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Location launch : launches) {
                    Firework firework = (Firework) launch.getWorld().spawnEntity(launch, EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .flicker(true)
                            .trail(true)
                            .with(FireworkEffect.Type.BURST)
                            .withColor(org.bukkit.Color.AQUA, org.bukkit.Color.LIME)
                            .build());
                    meta.setPower(1);
                    firework.setFireworkMeta(meta);
                    firework.setVelocity(new Vector(0, 0.65, 0));
                }
            }, delay);
        }
        Location beacon = station.beaconLocation();
        if (beacon != null && beacon.getWorld() != null) {
            for (int i = 0; i < winDingCount; i++) {
                int dingDelay = i * 6;
                float pitch = 1.0f + (i * 0.08f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> beacon.getWorld()
                        .playSound(beacon.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch), dingDelay);
            }
        }
    }

    private Set<Integer> generateMines(int mines, int totalCells) {
        Set<Integer> result = new HashSet<>();
        while (result.size() < mines) {
            result.add((int) (Math.random() * totalCells));
        }
        return result;
    }

    private double multiplierForRevealed(ActiveGame game, int revealed) {
        if (revealed <= 0) {
            return 1.0;
        }
        int safeTarget = game.safeTargetCount(gridSize);
        if (safeTarget <= 0) {
            return 1.0;
        }
        double edgeFactor = Math.max(0.0, Math.min(1.0, 1.0 - (houseEdgePercent / 100.0)));

        if (maxMultiplier > 0.0D) {
            int clampedRevealed = Math.min(revealed, safeTarget);
            double progress = (double) clampedRevealed / safeTarget;
            double configuredMax = Math.max(1.0D, maxMultiplier);
            double effectiveMax = 1.0D + (configuredMax - 1.0D) * edgeFactor;
            return 1.0D + (effectiveMax - 1.0D) * progress;
        }

        int total = gridSize * gridSize;
        int safe = total - game.mines();
        int clampedRevealed = Math.min(revealed, safeTarget);
        double surviveProbability = combination(safe, clampedRevealed) / combination(total, clampedRevealed);
        if (surviveProbability <= 0.0D) {
            return 1.0D;
        }
        return (1.0D / surviveProbability) * edgeFactor;
    }

    private double clampPayout(double payout) {
        if (maxPayout > 0.0D) {
            return Math.min(payout, maxPayout);
        }
        return payout;
    }

    private double combination(int n, int k) {
        if (k < 0 || k > n) {
            return 0.0;
        }
        if (k == 0 || k == n) {
            return 1.0;
        }
        int r = Math.min(k, n - k);
        double result = 1.0;
        for (int i = 1; i <= r; i++) {
            result = result * (n - r + i) / i;
        }
        return result;
    }

    private BoardGeometry geometry(StationData station) {
        return new BoardGeometry(station, wallDistance, gridSize, frameVerticalOffset);
    }

    private boolean isAllowedConfigPath(String path) {
        return switch (path) {
            case "board.grid-size",
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
                 "hologram.base-height" -> true;
            default -> false;
        };
    }

    private Object parseConfigValue(String path, String raw) {
        if (!isAllowedConfigPath(path)) {
            return null;
        }
        try {
            return switch (path) {
                case "board.grid-size" -> {
                    int value = Integer.parseInt(raw);
                    yield value >= 2 ? value : null;
                }
                case "board.wall-distance" -> {
                    int value = Integer.parseInt(raw);
                    yield value >= 1 ? value : null;
                }
                case "board.reset-delay-seconds" -> {
                    int value = Integer.parseInt(raw);
                    yield value >= 1 ? value : null;
                }
                case "game.duration-seconds" -> {
                    int value = Integer.parseInt(raw);
                    yield value >= 1 ? value : null;
                }
                case "effects.firework-count", "effects.win-ding-count" -> {
                    int value = Integer.parseInt(raw);
                    yield value >= 1 ? value : null;
                }
                case "game.house-edge-percent" -> {
                    double value = Double.parseDouble(raw);
                    yield value >= 0.0 ? value : null;
                }
                case "game.max-multiplier" -> {
                    double value = Double.parseDouble(raw);
                    yield value >= 0.0 ? value : null;
                }
                case "game.max-payout" -> {
                    double value = Double.parseDouble(raw);
                    yield (value == -1.0 || value > 0.0) ? value : null;
                }
                case "hologram.line-spacing" -> {
                    double value = Double.parseDouble(raw);
                    yield value > 0.0 ? value : null;
                }
                case "hologram.view-range" -> {
                    double value = Double.parseDouble(raw);
                    yield value > 0.0 ? value : null;
                }
                case "casino-frame-activation-distance" -> {
                    double value = Double.parseDouble(raw);
                    yield value >= 0.0 ? value : null;
                }
                case "hologram.behind-beacon-distance", "hologram.base-height" -> Double.parseDouble(raw);
                case "effects.fireworks-on-win",
                        "hologram.enabled",
                        "board.frame-one-higher",
                        "announcements.broadcast-start",
                        "announcements.broadcast-cashout",
                        "announcements.broadcast-win",
                        "announcements.send-welcome-on-start" -> parseBoolean(raw);
                case "game.title-prefix" -> raw;
                case "board.hidden-block", "board.safe-reveal-block", "board.mine-reveal-block", "board.frame-block", "board.station-block" -> {
                    Material material = Material.matchMaterial(raw);
                    yield (material != null && material.isBlock()) ? material.name() : null;
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

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(raw);
        return parsed == null ? fallback : parsed;
    }

    private Material hiddenBlockFor(StationData station) {
        return parseMaterial(station.boardHiddenBlock(), hiddenBlock);
    }

    private Material safeRevealBlockFor(StationData station) {
        return parseMaterial(station.boardSafeRevealBlock(), safeRevealBlock);
    }

    private Material mineRevealBlockFor(StationData station) {
        return parseMaterial(station.boardMineRevealBlock(), mineRevealBlock);
    }

    private Material frameBlockFor(StationData station) {
        return parseMaterial(station.boardFrameBlock(), frameBlock);
    }

    private Material visualFrameBlockFor(StationData station) {
        boolean animEnabledDefault = plugin.getConfig().getBoolean("frame-animation.enabled", false);
        boolean animEnabled = station.frameAnimEnabled() != null ? station.frameAnimEnabled() : animEnabledDefault;
        if (!animEnabled) {
            return frameBlockFor(station);
        }
        Material defaultAnimBlock = parseMaterial(plugin.getConfig().getString("frame-animation.block"), Material.REDSTONE_LAMP);
        return parseMaterial(station.frameAnimBlock(), defaultAnimBlock);
    }

    private StationData currentStationState(StationData station) {
        StationData current = stationStorage.get(station.key());
        return current == null ? station : current;
    }

    private void msg(Player player, String path) {
        String value = plugin.getConfig().getString(path);
        if (value == null && plugin.getConfig().getDefaults() != null) {
            value = plugin.getConfig().getDefaults().getString(path);
        }
        if (value == null) {
            value = "&cMissing message: " + path;
        }
        player.sendMessage(color(value));
    }

    private String replaceVars(String path, Map<String, String> vars) {
        String value = messageTemplate(path);
        if (value == null) {
            value = "&cMissing message: " + path;
        }
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
    }

    private String resolveMessage(String primaryPath, String fallbackPath, Map<String, String> vars) {
        String value = messageTemplate(primaryPath);
        if (value == null && fallbackPath != null) {
            value = messageTemplate(fallbackPath);
        }
        if (value == null) {
            value = "&cMissing message: " + primaryPath;
        }
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
    }

    private String messageTemplate(String path) {
        String value = plugin.getConfig().getString(path);
        if (value == null && plugin.getConfig().getDefaults() != null) {
            value = plugin.getConfig().getDefaults().getString(path);
        }
        return value;
    }

    private String prefixed(String message) {
        return titlePrefix + message;
    }

    public void showHouseBalance(Player player) {
        double balance = houseBalanceStorage.minesBalance();
        double wagered = houseBalanceStorage.minesTotalWagered();
        double paid = houseBalanceStorage.minesTotalPayout();
        double edge = wagered <= 0.0 ? 0.0 : ((wagered - paid) / wagered) * 100.0;
        player.sendMessage(color(prefixed("&eHouse Balance: &a$" + MONEY_FORMAT.format(balance)
                + " &7| &eEdge: &a" + MONEY_FORMAT.format(edge) + "%")));
        player.sendMessage(color("&7Wagered: &f$" + MONEY_FORMAT.format(wagered)
                + " &7| Paid: &f$" + MONEY_FORMAT.format(paid)));
    }

    public void withdrawHouseBalance(Player player, String rawAmount) {
        if (!player.hasPermission("mine.admin")) {
            player.sendMessage(color("&cNo permission."));
            return;
        }
        double current = houseBalanceStorage.minesBalance();
        if (current <= 0.0) {
            player.sendMessage(color(prefixed("&cNo house balance available to withdraw.")));
            return;
        }
        double wanted;
        if (rawAmount.equalsIgnoreCase("all")) {
            wanted = current;
        } else {
            try {
                wanted = Double.parseDouble(rawAmount);
            } catch (NumberFormatException ex) {
                player.sendMessage(color("&cAmount must be a number or 'all'."));
                return;
            }
            if (wanted <= 0.0) {
                player.sendMessage(color("&cAmount must be greater than 0."));
                return;
            }
        }

        double withdrawn = houseBalanceStorage.withdrawMines(wanted);
        if (withdrawn <= 0.0) {
            player.sendMessage(color(prefixed("&cNothing to withdraw.")));
            return;
        }
        EconomyResponse deposit = economy.depositPlayer(player, withdrawn);
        if (!deposit.transactionSuccess()) {
            houseBalanceStorage.refundMinesWithdrawal(withdrawn);
            player.sendMessage(color("&cEconomy error: withdraw failed."));
            return;
        }
        player.sendMessage(color(prefixed("&aWithdrew &6$" + MONEY_FORMAT.format(withdrawn) + " &afrom MineGame house balance.")));
        player.sendMessage(color("&7Remaining MineGame house balance: &a$" + MONEY_FORMAT.format(houseBalanceStorage.minesBalance())));
    }

    private String prettyMaterial(Material material) {
        if (material == null) {
            return "unknown";
        }
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String formatted;
            if (part.equals("tnt")) {
                formatted = "TNT";
            } else {
                formatted = Character.toUpperCase(part.charAt(0)) + part.substring(1);
            }
            if (i > 0) {
                out.append(' ');
            }
            out.append(formatted);
        }
        return out.toString();
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
