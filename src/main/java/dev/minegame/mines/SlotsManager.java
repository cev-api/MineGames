package dev.minegame.mines;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

public final class SlotsManager {
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String HOLO_TAG = "minegame_slots_holo";
    private static final String HOLO_STATION_TAG_PREFIX = "minegame_slots_holo_station_";
    private static final Random RNG = new Random();

    private final MinegamePlugin plugin;
    private final Economy economy;
    private final SlotStationStorage stationStorage;
    private final BlockSnapshotStorage restoreStorage;
    private final HouseBalanceStorage houseBalanceStorage;
    private final Map<String, SlotRuntime> runtimes = new HashMap<>();
    private final Map<String, List<UUID>> holograms = new HashMap<>();

    private BukkitTask ticker;

    private double costPerSpin;
    private int spinSeconds;
    private int stopIntervalTicks;
    private int resultSeconds;
    private String leverPlacement;
    private String spinLightMode;
    private double maxPayout;
    private Material outerFrameBlock;
    private Material innerFrameBlock;
    private Material winningBlock;
    private int fireworksPerWin;
    private List<Material> reelOptions = List.of(Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.DIAMOND_BLOCK);
    private final Map<Integer, Double> payoutMultipliers = new HashMap<>();

    public SlotsManager(
            MinegamePlugin plugin,
            Economy economy,
            SlotStationStorage stationStorage,
            BlockSnapshotStorage restoreStorage,
            HouseBalanceStorage houseBalanceStorage
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.stationStorage = stationStorage;
        this.restoreStorage = restoreStorage;
        this.houseBalanceStorage = houseBalanceStorage;
        loadConfig();
        for (SlotStationData station : stationStorage.all()) {
            SlotRuntime runtime = new SlotRuntime(this, station);
            runtimes.put(station.key(), runtime);
            renderIdle(runtime);
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
        for (SlotRuntime runtime : runtimes.values()) {
            if (runtime.spinTask != null) {
                runtime.spinTask.cancel();
            }
        }
        clearAllHolograms();
    }

    public void reloadConfig(CommandSender requester) {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        loadConfig();
        for (SlotRuntime runtime : runtimes.values()) {
            if (runtime.phase == Phase.IDLE) {
                renderIdle(runtime);
            } else {
                renderCurrent(runtime);
            }
        }
        requester.sendMessage(color(text("messages.slots.admin.reloaded", "&aSlots config reloaded.")));
    }

    public void createStation(Player player, int requestedReelCount) {
        createStation(player, requestedReelCount, 1);
    }

    public void createStation(Player player, int requestedReelCount, int requestedRowCount) {
        int reelCount = Math.max(3, Math.min(8, requestedReelCount));
        int rowCount = Math.max(1, Math.min(2, requestedRowCount));
        BlockFace facing = yawToCardinal(player.getLocation().getYaw());
        Block frontBlock = player.getLocation().getBlock().getRelative(facing);
        int totalWidth = reelCount + 2;
        Block leftBottom = frontBlock.getRelative(rotateLeft(facing), totalWidth / 2);
        SlotStationData station = new SlotStationData(
                player.getWorld().getName(),
                leftBottom.getX(),
                leftBottom.getY(),
                leftBottom.getZ(),
                facing,
                reelCount,
                rowCount,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        captureStationBlocksIfNeeded(station);
        stationStorage.upsert(station);
        stationStorage.save();
        SlotRuntime runtime = new SlotRuntime(this, station);
        runtimes.put(station.key(), runtime);
        renderIdle(runtime);
        player.sendMessage(color(replace(text(
                "messages.slots.admin.created",
                "&aSlots station created with &f%count% &areels and &f%rows% &arow(s)."
        ), Map.of(
                "%count%", String.valueOf(reelCount),
                "%rows%", String.valueOf(rowCount)
        ))));
    }

    public void removeStation(Player player) {
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
        }
        if (restoreStorage.has(runtime.station.key())) {
            restoreStorage.restoreAndForget(runtime.station.key());
        } else {
            for (Block block : runtime.geometry().allBlocks()) {
                block.setType(Material.AIR, false);
            }
        }
        deleteHologram(runtime.station.key());
        runtimes.remove(runtime.station.key());
        stationStorage.remove(runtime.station.key());
        stationStorage.save();
        player.sendMessage(color(text("messages.slots.admin.removed", "&eSlots station removed.")));
    }

    public void regenerateStation(Player player) {
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
            runtime.spinTask = null;
        }
        runtime.phase = Phase.IDLE;
        runtime.playerId = null;
        runtime.wager = 0.0;
        runtime.resultLines.clear();
        renderIdle(runtime);
        player.sendMessage(color(text("messages.slots.admin.regenerated", "&aSlots station regenerated.")));
    }

    public void listStations(Player player) {
        player.sendMessage(color(replace(text("messages.slots.admin.station-list-header", "&6[Slots] &fStations: &e%count%"), Map.of(
                "%count%", String.valueOf(runtimes.size())
        ))));
        int num = 1;
        for (SlotRuntime runtime : runtimes.values()) {
            SlotStationData s = runtime.station;
            player.sendMessage(color(replace(text(
                    "messages.slots.admin.station-list-entry",
                    "&7%num%. &f%world% &7(%x%, %y%, %z%) &8width %width% &7rows %rows%"
            ), Map.of(
                    "%num%", String.valueOf(num),
                    "%world%", s.worldName(),
                    "%x%", String.valueOf(s.x()),
                    "%y%", String.valueOf(s.y()),
                    "%z%", String.valueOf(s.z()),
                    "%width%", String.valueOf(s.reelCount()),
                    "%rows%", String.valueOf(s.rowCount())
            ))));
            num++;
        }
    }

    public void listStationsForRemove(Player player) {
        if (runtimes.isEmpty()) {
            player.sendMessage(color(text("messages.slots.admin.no-stations", "&eNo slots stations exist.")));
            return;
        }
        player.sendMessage(color(text("messages.slots.admin.remove-list-header",
                "&6[Slots] &eStations — use &f/slotsadmin remove <number> &eto delete:")));
        int num = 1;
        for (SlotRuntime runtime : runtimes.values()) {
            SlotStationData s = runtime.station;
            player.sendMessage(color(replace(text(
                    "messages.slots.admin.station-list-entry",
                    "&7%num%. &f%world% &7(%x%, %y%, %z%) &8width %width% &7rows %rows%"
            ), Map.of(
                    "%num%", String.valueOf(num),
                    "%world%", s.worldName(),
                    "%x%", String.valueOf(s.x()),
                    "%y%", String.valueOf(s.y()),
                    "%z%", String.valueOf(s.z()),
                    "%width%", String.valueOf(s.reelCount()),
                    "%rows%", String.valueOf(s.rowCount())
            ))));
            num++;
        }
    }

    public void removeStationByIndex(Player player, int index) {
        if (index < 1 || index > runtimes.size()) {
            player.sendMessage(color(replace(text("messages.slots.admin.remove-bad-index-out-of-range",
                    "&cStation #%index% does not exist (1-%max%)."), Map.of(
                    "%index%", String.valueOf(index),
                    "%max%", String.valueOf(runtimes.size())
            ))));
            return;
        }
        SlotRuntime runtime = null;
        int i = 1;
        for (SlotRuntime rt : runtimes.values()) {
            if (i == index) {
                runtime = rt;
                break;
            }
            i++;
        }
        if (runtime == null) {
            return;
        }
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
        }
        if (restoreStorage.has(runtime.station.key())) {
            restoreStorage.restoreAndForget(runtime.station.key());
        } else {
            for (Block block : runtime.geometry().allBlocks()) {
                block.setType(Material.AIR, false);
            }
        }
        deleteHologram(runtime.station.key());
        runtimes.remove(runtime.station.key());
        stationStorage.remove(runtime.station.key());
        stationStorage.save();
        SlotStationData s = runtime.station;
        player.sendMessage(color(replace(text("messages.slots.admin.removed",
                "&eSlots station #%num% at %world% (%x%, %y%, %z%) removed."), Map.of(
                "%num%", String.valueOf(index),
                "%world%", s.worldName(),
                "%x%", String.valueOf(s.x()),
                "%y%", String.valueOf(s.y()),
                "%z%", String.valueOf(s.z())
        ))));
    }

    public void pullLever(Player player, Block leverBlock) {
        SlotRuntime runtime = runtimeForLever(leverBlock);
        if (runtime == null) {
            return;
        }
        if (runtime.phase == Phase.SPINNING) {
            player.sendMessage(color(text("messages.slots.gameplay.station-busy", "&cThat slots machine is already spinning.")));
            return;
        }
        double wager = costPerSpinFor(runtime.station);
        EconomyResponse withdraw = economy.withdrawPlayer(player, wager);
        if (!withdraw.transactionSuccess()) {
            player.sendMessage(color(text("messages.slots.gameplay.no-money", "&cYou do not have enough money.")));
            return;
        }

        runtime.phase = Phase.SPINNING;
        runtime.playerId = player.getUniqueId();
        runtime.wager = wager;
        runtime.resultLines.clear();
        runtime.finalSymbols = randomSymbols(runtime.station);
        runtime.lockedReels = 0;
        runtime.spinSecondsLeft = spinSeconds;
        startSpinAnimation(runtime);
        player.sendMessage(color(replace(text(
                "messages.slots.gameplay.spin-charged",
                "&aSpin started for &6$%amount%&a. Good luck!"
        ), Map.of("%amount%", MONEY.format(wager)))));
    }

    public boolean isProtectedBlock(Block block) {
        for (SlotRuntime runtime : runtimes.values()) {
            try {
                if (runtime.geometry().isMachineBlock(block)) {
                    return true;
                }
            } catch (IllegalStateException ignored) {
            }
        }
        return false;
    }

    public boolean isLeverBlock(Block block) {
        for (SlotRuntime runtime : runtimes.values()) {
            try {
                if (runtime.geometry().isLeverBlock(block)) {
                    return true;
                }
            } catch (IllegalStateException ignored) {
            }
        }
        return false;
    }

    public Collection<SlotStationData> stations() {
        return stationStorage.all();
    }

    public SlotsGeometry geometryForStation(SlotStationData station) {
        return new SlotsGeometry(station, leverPlacement);
    }

    public boolean isStationActive(SlotStationData station) {
        SlotRuntime runtime = runtimes.get(station.key());
        return runtime != null && runtime.phase != Phase.IDLE;
    }

    public boolean isStationSpinning(SlotStationData station) {
        SlotRuntime runtime = runtimes.get(station.key());
        return runtime != null && runtime.phase == Phase.SPINNING;
    }

    public int lockedReels(SlotStationData station) {
        SlotRuntime runtime = runtimes.get(station.key());
        return runtime == null ? 0 : runtime.lockedReels;
    }

    public boolean isFrameAnimationEnabled(SlotStationData station) {
        Boolean stationEnabled = station.frameAnimEnabled();
        return stationEnabled != null
                ? stationEnabled
                : plugin.getConfig().getBoolean("slots.frame-animation.enabled", false);
    }

    public Material frameAnimationBlock(SlotStationData station) {
        Material global = parseMaterial(plugin.getConfig().getString("slots.frame-animation.block"), Material.REDSTONE_LAMP);
        return parseMaterial(station.frameAnimBlock(), global);
    }

    public int frameAnimationPattern(SlotStationData station) {
        Integer stationPattern = station.frameAnimPattern();
        int global = plugin.getConfig().getInt("slots.frame-animation.pattern", 1);
        return stationPattern != null ? stationPattern : global;
    }

    public String frameAnimationMode(SlotStationData station) {
        String stationMode = normalizeFrameMode(station.frameAnimMode());
        if (stationMode != null) {
            return stationMode;
        }
        return normalizeFrameMode(plugin.getConfig().getString("slots.frame-animation.mode", "idle_only"));
    }

    public void setOuterFrameMaterial(Player player, String materialName, boolean applyAll) {
        setStationMaterial(player, materialName, applyAll, "Outer frame", "outer");
    }

    public void setInnerFrameMaterial(Player player, String materialName, boolean applyAll) {
        setStationMaterial(player, materialName, applyAll, "Inner frame", "inner");
    }

    public void setWinningMaterial(Player player, String materialName, boolean applyAll) {
        setStationMaterial(player, materialName, applyAll, "Winning block", "winning");
    }

    public void resetBoardMaterialOverrides(Player player, boolean applyAll) {
        if (applyAll) {
            List<SlotStationData> updated = new ArrayList<>();
            for (SlotStationData station : stationStorage.all()) {
                updated.add(station.clearBoardMaterialOverrides());
            }
            saveAllStations(updated, true);
            player.sendMessage(color(text("messages.slots.admin.board-material-reset-all", "&aSlots material overrides reset for all stations.")));
            return;
        }
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        saveStation(runtime.station.clearBoardMaterialOverrides(), true);
        player.sendMessage(color(text("messages.slots.admin.board-material-reset-station", "&aSlots material overrides reset for this station.")));
    }

    public void setFrameAnimation(Player player, String blockName, int pattern, boolean applyAll) {
        Material block = Material.matchMaterial(blockName);
        if (block == null || !block.isBlock()) {
            player.sendMessage(color(text("messages.shared.invalid-block-material", "&cInvalid block material.")));
            return;
        }
        if (pattern < 1 || pattern > 10) {
            player.sendMessage(color(text("messages.shared.pattern-out-of-range", "&cPattern must be between 1 and 10.")));
            return;
        }
        if (applyAll) {
            List<SlotStationData> updated = new ArrayList<>();
            for (SlotStationData station : stationStorage.all()) {
                updated.add(station.withFrameAnimation(true, block.name(), pattern, null));
            }
            saveAllStations(updated, true);
            player.sendMessage(color(replace(text(
                    "messages.slots.admin.casino-frame.updated-all",
                    "&aSlots casino frame updated for all stations: &f%block%&a, pattern &f%pattern%&a."
            ), Map.of(
                    "%block%", block.name(),
                    "%pattern%", String.valueOf(pattern)
            ))));
            return;
        }
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        saveStation(runtime.station.withFrameAnimation(true, block.name(), pattern, null), true);
        player.sendMessage(color(replace(text(
                "messages.slots.admin.casino-frame.updated-station",
                "&aSlots casino frame updated: &f%block%&a, pattern &f%pattern%&a."
        ), Map.of(
                "%block%", block.name(),
                "%pattern%", String.valueOf(pattern)
        ))));
    }

    public void setFrameAnimationMode(Player player, String mode, boolean applyAll) {
        String normalized = normalizeFrameMode(mode);
        if (normalized == null) {
            player.sendMessage(color(text("messages.slots.admin.casino-frame.invalid-mode", "&cMode must be idle_only or always.")));
            return;
        }
        if (applyAll) {
            List<SlotStationData> updated = new ArrayList<>();
            for (SlotStationData station : stationStorage.all()) {
                updated.add(station.withFrameAnimation(true, null, null, normalized));
            }
            saveAllStations(updated, true);
            player.sendMessage(color(replace(text(
                    "messages.slots.admin.casino-frame.mode-set-all",
                    "&aSlots casino frame mode set to &f%mode% &afor all stations."
            ), Map.of("%mode%", normalized))));
            return;
        }
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        saveStation(runtime.station.withFrameAnimation(true, null, null, normalized), true);
        player.sendMessage(color(replace(text(
                "messages.slots.admin.casino-frame.mode-set-station",
                "&aSlots casino frame mode set to &f%mode%&a."
        ), Map.of("%mode%", normalized))));
    }

    public void disableFrameAnimation(Player player, boolean applyAll) {
        if (applyAll) {
            List<SlotStationData> updated = new ArrayList<>();
            for (SlotStationData station : stationStorage.all()) {
                updated.add(station.withFrameAnimation(false, null, null, null));
            }
            saveAllStations(updated, true);
            player.sendMessage(color(text("messages.slots.admin.casino-frame.disabled-all", "&eSlots casino frame animation disabled for all stations.")));
            return;
        }
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        saveStation(runtime.station.withFrameAnimation(false, null, null, null), true);
        player.sendMessage(color(text("messages.slots.admin.casino-frame.disabled-station", "&eSlots casino frame animation disabled.")));
    }

    public void resetFrameAnimationOverrides(Player player, boolean applyAll) {
        if (applyAll) {
            List<SlotStationData> updated = new ArrayList<>();
            for (SlotStationData station : stationStorage.all()) {
                updated.add(station.clearFrameAnimationOverrides());
            }
            saveAllStations(updated, true);
            player.sendMessage(color(text("messages.slots.admin.casino-frame.reset-all", "&aSlots casino frame animation overrides reset for all stations.")));
            return;
        }
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        saveStation(runtime.station.clearFrameAnimationOverrides(), true);
        player.sendMessage(color(text("messages.slots.admin.casino-frame.reset-station", "&aSlots casino frame animation overrides reset for this station.")));
    }

    public void showHouseBalance(Player player) {
        double balance = houseBalanceStorage.slotsBalance();
        double wagered = houseBalanceStorage.slotsTotalWagered();
        double paid = houseBalanceStorage.slotsTotalPayout();
        double edge = wagered <= 0.0 ? 0.0 : ((wagered - paid) / wagered) * 100.0;
        player.sendMessage(color(replace(text(
                "messages.slots.admin.house-balance-header",
                "&6[Slots] &eHouse Balance: &a$%balance% &7| &eEdge: &a%edge%%"
        ), Map.of(
                "%balance%", MONEY.format(balance),
                "%edge%", MONEY.format(edge)
        ))));
        player.sendMessage(color(replace(text(
                "messages.slots.admin.house-balance-details",
                "&7Wagered: &f$%wagered% &7| Paid: &f$%paid%"
        ), Map.of(
                "%wagered%", MONEY.format(wagered),
                "%paid%", MONEY.format(paid)
        ))));
    }

    public void withdrawHouseBalance(Player player, String rawAmount) {
        double current = houseBalanceStorage.slotsBalance();
        if (current <= 0.0) {
            player.sendMessage(color(text("messages.slots.admin.house-no-balance", "&6[Slots] &cNo house balance available to withdraw.")));
            return;
        }
        double wanted;
        if (rawAmount.equalsIgnoreCase("all")) {
            wanted = current;
        } else {
            try {
                wanted = Double.parseDouble(rawAmount);
            } catch (NumberFormatException ex) {
                player.sendMessage(color(text("messages.shared.amount-must-be-number-or-all", "&cAmount must be a number or 'all'.")));
                return;
            }
            if (wanted <= 0.0) {
                player.sendMessage(color(text("messages.shared.amount-must-be-greater-than-zero", "&cAmount must be greater than 0.")));
                return;
            }
        }
        double withdrawn = houseBalanceStorage.withdrawSlots(wanted);
        if (withdrawn <= 0.0) {
            player.sendMessage(color(text("messages.slots.admin.house-nothing-to-withdraw", "&6[Slots] &cNothing to withdraw.")));
            return;
        }
        EconomyResponse deposit = economy.depositPlayer(player, withdrawn);
        if (!deposit.transactionSuccess()) {
            houseBalanceStorage.refundSlotsWithdrawal(withdrawn);
            player.sendMessage(color(text("messages.shared.economy-withdraw-failed", "&cEconomy error: withdraw failed.")));
            return;
        }
        player.sendMessage(color(replace(text(
                "messages.slots.admin.house-withdraw-success",
                "&6[Slots] &aWithdrew &6$%amount% &afrom slots house balance."
        ), Map.of("%amount%", MONEY.format(withdrawn)))));
        player.sendMessage(color(replace(text(
                "messages.slots.admin.house-remaining-balance",
                "&7Remaining slots house balance: &a$%balance%"
        ), Map.of("%balance%", MONEY.format(houseBalanceStorage.slotsBalance())))));
    }

    public void setConfigValue(Player player, String pathInput, String valueInput) {
        setConfigValue(player, pathInput, valueInput, false);
    }

    public void setConfigValue(Player player, String pathInput, String valueInput, boolean forceGlobal) {
        String path = normalizeConfigPath(pathInput);
        Object parsed = parseConfigValue(path, valueInput);
        if (parsed == null) {
            player.sendMessage(color(text("messages.slots.admin.config-invalid", "&cInvalid slots config value/path.")));
            return;
        }
        if (!forceGlobal && isStationConfigPath(path)) {
            SlotRuntime runtime = runtimeForPlayer(player);
            if (runtime != null) {
                SlotStationData updated = applyStationConfigValue(runtime.station, path, parsed);
                if (updated != null) {
                    saveStation(updated, true);
                    player.sendMessage(color(replace(text("messages.slots.admin.config-set", "&aSet &f%path% &ato &f%value%&a."), Map.of(
                            "%path%", path,
                            "%value%", String.valueOf(parsed)
                    ))));
                    return;
                }
            }
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        plugin.getConfig().set(path, parsed);
        plugin.saveConfig();
        loadConfig();
        for (SlotRuntime runtime : runtimes.values()) {
            if (runtime.phase == Phase.IDLE) {
                renderIdle(runtime);
            } else {
                renderCurrent(runtime);
            }
        }
        player.sendMessage(color(replace(text("messages.slots.admin.config-set", "&aSet &f%path% &ato &f%value%&a."), Map.of(
                "%path%", path,
                "%value%", String.valueOf(parsed)
        ))));
    }

    private boolean isStationConfigPath(String path) {
        return switch (path) {
            case "slots.cost-per-spin",
                    "slots.frame-animation.enabled",
                    "slots.frame-animation.block",
                    "slots.frame-animation.pattern",
                    "slots.frame-animation.mode",
                    "slots.blocks.outer-frame",
                    "slots.blocks.inner-frame",
                    "slots.blocks.winning" -> true;
            default -> false;
        };
    }

    private SlotStationData applyStationConfigValue(SlotStationData station, String path, Object parsed) {
        return switch (path) {
            case "slots.cost-per-spin" -> station.withCostPerSpin((Double) parsed);
            case "slots.frame-animation.enabled" -> station.withFrameAnimation((Boolean) parsed, null, null, null);
            case "slots.frame-animation.block" -> station.withFrameAnimation(null, String.valueOf(parsed), null, null);
            case "slots.frame-animation.pattern" -> station.withFrameAnimation(null, null, (Integer) parsed, null);
            case "slots.frame-animation.mode" -> station.withFrameAnimation(null, null, null, String.valueOf(parsed));
            case "slots.blocks.outer-frame" -> station.withBoardMaterials(String.valueOf(parsed), station.innerFrameBlock(), station.winningBlock());
            case "slots.blocks.inner-frame" -> station.withBoardMaterials(station.outerFrameBlock(), String.valueOf(parsed), station.winningBlock());
            case "slots.blocks.winning" -> station.withBoardMaterials(station.outerFrameBlock(), station.innerFrameBlock(), String.valueOf(parsed));
            default -> null;
        };
    }

    public Object getCurrentConfigValue(String path) {
        return plugin.getConfig().get(normalizeConfigPath(path));
    }

    public String text(String path, String fallback) {
        String v = plugin.getConfig().getString(path);
        if (v == null && plugin.getConfig().getDefaults() != null) {
            v = plugin.getConfig().getDefaults().getString(path);
        }
        return v == null ? fallback : v;
    }

    public String colorize(String input) {
        return color(input);
    }

    public List<String> lines(String path, List<String> fallback) {
        List<String> lines = plugin.getConfig().getStringList(path);
        return lines.isEmpty() ? List.copyOf(fallback) : List.copyOf(lines);
    }

    private void tick() {
        for (SlotRuntime runtime : runtimes.values()) {
            if (runtime.phase == Phase.SPINNING) {
                runtime.spinSecondsLeft = Math.max(0, runtime.spinSecondsLeft - 1);
            } else if (runtime.phase == Phase.RESULT) {
                runtime.resultSecondsLeft--;
                if (runtime.resultSecondsLeft <= 0) {
                    runtime.phase = Phase.IDLE;
                    runtime.playerId = null;
                    runtime.wager = 0.0;
                    runtime.resultLines.clear();
                    renderIdle(runtime);
                }
            }
            updateHologram(runtime);
        }
    }

    private void startSpinAnimation(SlotRuntime runtime) {
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
        }
        int warmupTicks = spinSeconds * 20;
        runtime.elapsedSpinTicks = 0;
        runtime.spinTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            runtime.elapsedSpinTicks += 2;
            if (runtime.elapsedSpinTicks > warmupTicks) {
                runtime.lockedReels = Math.min(runtime.station.reelCount(),
                        1 + ((runtime.elapsedSpinTicks - warmupTicks - 1) / Math.max(1, stopIntervalTicks)));
            }
            for (int row = 0; row < runtime.station.rowCount(); row++) {
                for (int col = 0; col < runtime.station.reelCount(); col++) {
                    if (col < runtime.lockedReels) {
                        runtime.currentSymbols.get(row).set(col, runtime.finalSymbols.get(row).get(col));
                    } else {
                        runtime.currentSymbols.get(row).set(col, randomReelBlock());
                    }
                }
            }
            renderCurrent(runtime);
            updateSpinLights(runtime);
            if (runtime.lockedReels >= runtime.station.reelCount()) {
                finalizeSpin(runtime);
            }
        }, 0L, 2L);
    }

    private void finalizeSpin(SlotRuntime runtime) {
        if (runtime.spinTask != null) {
            runtime.spinTask.cancel();
            runtime.spinTask = null;
        }
        runtime.currentSymbols = copyGrid(runtime.finalSymbols);
        renderCurrent(runtime);

        WinResult winResult = determineWin(runtime.finalSymbols, runtime.station);
        runtime.winningPositions = winResult.winningPositions();
        int wins = winResult.winCount();
        double multiplier = multiplierFor(runtime.station, wins, winResult.patternName());
        double payout = clampPayout(runtime.wager * multiplier);
        double profit = Math.max(0.0, payout - runtime.wager);
        if (payout > 0.0 && runtime.playerId != null) {
            EconomyResponse response = economy.depositPlayer(Bukkit.getOfflinePlayer(runtime.playerId), payout);
            if (!response.transactionSuccess()) {
                payout = 0.0;
                profit = 0.0;
            }
        }
        if (runtime.wager > 0.0 || payout > 0.0) {
            houseBalanceStorage.recordSlotsResult(runtime.wager, payout);
        }

        Player player = runtime.playerId == null ? null : Bukkit.getPlayer(runtime.playerId);
        if (player != null) {
            if (payout > 0.0) {
                player.sendMessage(color(replace(text(
                        "messages.slots.gameplay.win",
                        "&a%pattern% &7| &f%wins% &awinning blocks and won &6$%payout% &7(%xmult%x) &7| &a+$%profit%"
                ), Map.of(
                        "%pattern%", winResult.patternName(),
                        "%wins%", String.valueOf(wins),
                        "%payout%", MONEY.format(payout),
                        "%xmult%", MONEY.format(multiplier),
                        "%profit%", MONEY.format(profit)
                ))));
                launchWinnerFireworks(runtime, player.getUniqueId());
            } else {
                player.sendMessage(color(replace(text(
                        "messages.slots.gameplay.lose",
                        "&cNo payout this spin. Wager lost: &6$%amount%"
                ), Map.of("%amount%", MONEY.format(runtime.wager)))));
            }
        }

        runtime.resultLines.clear();
        runtime.resultLines.add(replace(text(
                "messages.slots.gameplay.result-header",
                "&e%player%'s result"
        ), Map.of("%player%", player != null ? player.getName() : "Player")));
        runtime.resultLines.add(replace(text(
                "messages.slots.gameplay.result-wins",
                "&7Winning blocks: &b%wins%"
        ), Map.of("%wins%", String.valueOf(wins))));
        runtime.resultLines.add(replace(text(
                "messages.slots.gameplay.result-payout",
                "&7Payout: &6$%payout% &7(%xmult%x)"
        ), Map.of(
                "%payout%", MONEY.format(payout),
                "%xmult%", MONEY.format(multiplier)
        )));
        runtime.resultLines.add(replace(text(
                "messages.slots.gameplay.result-pattern",
                "&7Pattern: &f%pattern%"
        ), Map.of("%pattern%", winResult.patternName())));
        runtime.phase = Phase.RESULT;
        runtime.resultSecondsLeft = resultSeconds;
        runtime.spinSecondsLeft = 0;
    }

    private void renderIdle(SlotRuntime runtime) {
        runtime.currentSymbols = randomSymbols(runtime.station);
        renderCurrent(runtime);
    }

    private void renderCurrent(SlotRuntime runtime) {
        SlotsGeometry geometry = runtime.geometry();
        for (Block block : geometry.outerFrameBlocks()) {
            block.setType(outerFrameBlockFor(runtime.station), false);
        }
        for (SlotsGeometry.FrameCell cell : geometry.innerFrameCells()) {
            cell.block().setType(visualInnerFrameBlock(runtime.station), false);
        }
        for (int row = 0; row < runtime.station.rowCount(); row++) {
            for (int col = 0; col < runtime.station.reelCount(); col++) {
                geometry.reelBlock(col, row).setType(runtime.currentSymbols.get(row).get(col), false);
            }
        }
        configureLever(geometry.leverBlock(), runtime.station);
    }

    private void updateSpinLights(SlotRuntime runtime) {
        SlotsGeometry geometry = runtime.geometry();
        List<SlotsGeometry.FrameCell> cells = geometry.innerFrameCells();
        if (cells.isEmpty()) {
            return;
        }
        String mode = spinLightModeFor(runtime.station);
        int step = switch (mode) {
            case "flashing_slow", "cycle_slow" -> 6;
            default -> 2;
        };
        boolean flash = ((runtime.elapsedSpinTicks / step) % 2) == 0;
        int cycleIndex = cells.isEmpty() ? 0 : ((runtime.elapsedSpinTicks / step) % cells.size());
        for (int i = 0; i < cells.size(); i++) {
            SlotsGeometry.FrameCell cell = cells.get(i);
            int col = Math.max(0, cell.col() - 1);
            boolean solid = col < runtime.lockedReels;
            boolean on = switch (mode) {
                case "cycle_quick", "cycle_slow" -> i == cycleIndex;
                default -> flash;
            };
            applyLightState(cell.block(), visualInnerFrameBlock(runtime.station), solid || on);
        }
    }

    private void applyLightState(Block block, Material type, boolean on) {
        if (block.getType() != type) {
            block.setType(type, false);
        }
        BlockData data = block.getBlockData();
        boolean changed = false;
        if (data instanceof Lightable lightable) {
            lightable.setLit(on);
            changed = true;
        }
        if (data instanceof Powerable powerable) {
            powerable.setPowered(on);
            changed = true;
        }
        if (changed) {
            block.setBlockData(data, false);
        }
    }

    private void configureLever(Block leverBlock, SlotStationData station) {
        SlotsGeometry geometry = new SlotsGeometry(station, leverPlacement);
        leverBlock.setType(Material.LEVER, false);
        if (leverBlock.getBlockData() instanceof Switch leverData) {
            leverData.setAttachedFace(FaceAttachable.AttachedFace.WALL);
            leverData.setFacing(geometry.leverFacing().getOppositeFace());
            leverBlock.setBlockData(leverData, false);
        }
    }

    private void updateHologram(SlotRuntime runtime) {
        Location anchor = runtime.geometry().centerAbove(plugin.getConfig().getDouble("slots.hologram-height", 5.8));
        double viewRange = plugin.getConfig().getDouble("slots.hologram-view-range", 20.0D);
        if (!hasNearbyHologramViewer(anchor, viewRange)) {
            deleteHologram(runtime.station.key());
            return;
        }
        List<String> lines = buildHologramLines(runtime);
        List<UUID> ids = holograms.get(runtime.station.key());
        pruneNearbyHologramDuplicates(anchor, ids);
        if (ids == null || ids.size() != lines.size()) {
            renderHologram(runtime.station.key(), anchor, lines);
            return;
        }
        boolean changed = false;
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = Bukkit.getEntity(ids.get(i));
            if (!(entity instanceof TextDisplay display)) {
                changed = true;
                break;
            }
            Component next = component(lines.get(i));
            if (!display.text().equals(next)) {
                display.text(next);
            }
        }
        if (changed) {
            renderHologram(runtime.station.key(), anchor, lines);
        }
    }

    private List<String> buildHologramLines(SlotRuntime runtime) {
        Map<String, String> vars = new HashMap<>();
        vars.put("%price%", MONEY.format(costPerSpinFor(runtime.station)));
        vars.put("%winning_block%", displayName(winningBlockFor(runtime.station)));
        vars.put("%seconds%", String.valueOf(runtime.spinSecondsLeft));
        String playerName = runtime.playerId == null ? "Player" : Bukkit.getOfflinePlayer(runtime.playerId).getName();
        vars.put("%player%", playerName == null ? "Player" : playerName);

        if (runtime.phase == Phase.SPINNING) {
            return replaceLines(configuredLines(
                    "messages.slots.hologram.spinning-lines",
                    "&6&lSLOTS",
                    "&eSpinning for %player%...",
                    "&7Winning block: &b%winning_block%"
            ), vars);
        }
        if (runtime.phase == Phase.RESULT) {
            List<String> lines = new ArrayList<>(replaceLines(configuredLines(
                    "messages.slots.hologram.result-header-lines",
                    "&6&lSLOTS"
            ), vars));
            if (runtime.resultLines.isEmpty()) {
                lines.addAll(configuredLines("messages.slots.hologram.result-empty-lines", "&eRound complete"));
            } else {
                lines.addAll(runtime.resultLines);
            }
            return lines;
        }
        return replaceLines(configuredLines(
                "messages.slots.hologram.idle-lines",
                "&6&lSLOTS",
                "&7Pull the lever to spin for &6$%price%",
                "&7Hit &b%winning_block% &7symbols to win"
        ), vars);
    }

    private void renderHologram(String stationKey, Location anchor, List<String> lines) {
        deleteHologram(stationKey);
        purgeNearbyHolograms(anchor);
        float viewRange = (float) plugin.getConfig().getDouble("slots.hologram-view-range", 20.0D);
        double spacing = plugin.getConfig().getDouble("slots.hologram-line-spacing", 0.6D);
        List<UUID> ids = new ArrayList<>();
        String stationTag = stationHoloTag(stationKey);
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = anchor.clone().subtract(0, i * spacing, 0);
            TextDisplay display = (TextDisplay) anchor.getWorld().spawnEntity(lineLoc, EntityType.TEXT_DISPLAY);
            display.text(component(lines.get(i)));
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setPersistent(false);
            display.setDefaultBackground(false);
            display.setViewRange(viewRange);
            display.setLineWidth(Integer.MAX_VALUE);
            display.addScoreboardTag(HOLO_TAG);
            display.addScoreboardTag(stationTag);
            ids.add(display.getUniqueId());
        }
        holograms.put(stationKey, ids);
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

    private boolean hasNearbyHologramViewer(Location anchor, double range) {
        if (anchor == null || anchor.getWorld() == null) {
            return false;
        }
        double maxSq = range * range;
        for (Player player : anchor.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(anchor) <= maxSq) {
                return true;
            }
        }
        return false;
    }

    private void captureStationBlocksIfNeeded(SlotStationData station) {
        if (restoreStorage.has(station.key())) {
            return;
        }
        restoreStorage.captureIfAbsent(station.key(), new SlotsGeometry(station, leverPlacement).allBlocks());
    }

    private SlotRuntime runtimeForLever(Block leverBlock) {
        for (SlotRuntime runtime : runtimes.values()) {
            try {
                if (runtime.geometry().isLeverBlock(leverBlock)) {
                    return runtime;
                }
            } catch (IllegalStateException ignored) {
            }
        }
        return null;
    }

    private SlotRuntime runtimeForPlayer(Player player) {
        Location playerLoc = player.getLocation();
        return runtimes.values().stream()
                .filter(runtime -> {
                    try {
                        Location center = runtime.geometry().centerAbove(2.5);
                        return center.getWorld() != null
                                && playerLoc.getWorld() != null
                                && center.getWorld().equals(playerLoc.getWorld())
                                && center.distance(playerLoc) <= 4.5;
                    } catch (IllegalStateException ex) {
                        return false;
                    }
                })
                .min(Comparator.comparingDouble(runtime -> runtime.geometry().centerAbove(2.5).distanceSquared(playerLoc)))
                .orElse(null);
    }

    private void setStationMaterial(Player player, String materialName, boolean applyAll, String label, String target) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isBlock()) {
            player.sendMessage(color(text("messages.shared.invalid-block-material", "&cInvalid block material.")));
            return;
        }
        if (applyAll) {
            List<SlotStationData> updated = new ArrayList<>();
            for (SlotStationData station : stationStorage.all()) {
                updated.add(switch (target) {
                    case "outer" -> station.withBoardMaterials(material.name(), station.innerFrameBlock(), station.winningBlock());
                    case "inner" -> station.withBoardMaterials(station.outerFrameBlock(), material.name(), station.winningBlock());
                    case "winning" -> station.withBoardMaterials(station.outerFrameBlock(), station.innerFrameBlock(), material.name());
                    default -> station;
                });
            }
            saveAllStations(updated, true);
            player.sendMessage(color(replace(text(
                    "messages.slots.admin.board-material-set-all",
                    "&a%label% set to &f%material% &afor all slots stations."
            ), Map.of(
                    "%label%", label,
                    "%material%", material.name()
            ))));
            return;
        }
        SlotRuntime runtime = runtimeForPlayer(player);
        if (runtime == null) {
            player.sendMessage(color(text("messages.slots.gameplay.not-near", "&cStand near a slots station to use this.")));
            return;
        }
        SlotStationData station = runtime.station;
        SlotStationData updated = switch (target) {
            case "outer" -> station.withBoardMaterials(material.name(), station.innerFrameBlock(), station.winningBlock());
            case "inner" -> station.withBoardMaterials(station.outerFrameBlock(), material.name(), station.winningBlock());
            case "winning" -> station.withBoardMaterials(station.outerFrameBlock(), station.innerFrameBlock(), material.name());
            default -> station;
        };
        saveStation(updated, true);
        player.sendMessage(color(replace(text(
                "messages.slots.admin.board-material-set-station",
                "&a%label% set to &f%material%&a for this slots station."
        ), Map.of(
                "%label%", label,
                "%material%", material.name()
        ))));
    }

    private void saveStation(SlotStationData station, boolean rerender) {
        SlotStationData previous = stationStorage.get(station.key());
        boolean resized = previous != null
                && (previous.reelCount() != station.reelCount() || previous.rowCount() != station.rowCount());
        if (resized) {
            rebaselineStation(station, previous);
        }
        stationStorage.upsert(station);
        stationStorage.save();
        SlotRuntime runtime = runtimes.get(station.key());
        if (runtime != null) {
            runtime.station = station;
            if (rerender) {
                renderCurrent(runtime);
            }
        } else {
            SlotRuntime created = new SlotRuntime(this, station);
            runtimes.put(station.key(), created);
            if (rerender) {
                renderIdle(created);
            }
        }
    }

    private void saveAllStations(List<SlotStationData> stations, boolean rerender) {
        for (SlotStationData station : stations) {
            SlotStationData previous = stationStorage.get(station.key());
            if (previous != null
                    && (previous.reelCount() != station.reelCount() || previous.rowCount() != station.rowCount())) {
                rebaselineStation(station, previous);
            }
            stationStorage.upsert(station);
        }
        stationStorage.save();
        for (SlotStationData station : stations) {
            SlotRuntime runtime = runtimes.get(station.key());
            if (runtime != null) {
                runtime.station = station;
                if (rerender) {
                    renderCurrent(runtime);
                }
            } else {
                SlotRuntime created = new SlotRuntime(this, station);
                runtimes.put(station.key(), created);
                if (rerender) {
                    renderIdle(created);
                }
            }
        }
    }

    private List<List<Material>> randomSymbols(SlotStationData station) {
        List<List<Material>> grid = new ArrayList<>(station.rowCount());
        for (int row = 0; row < station.rowCount(); row++) {
            List<Material> line = new ArrayList<>(station.reelCount());
            for (int col = 0; col < station.reelCount(); col++) {
                line.add(randomReelBlock());
            }
            grid.add(line);
        }
        return grid;
    }

    private List<List<Material>> copyGrid(List<List<Material>> source) {
        List<List<Material>> copy = new ArrayList<>(source.size());
        for (List<Material> row : source) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }

    private Material randomReelBlock() {
        if (reelOptions.isEmpty()) {
            return winningBlock;
        }
        return reelOptions.get(RNG.nextInt(reelOptions.size()));
    }

    private void rebaselineStation(SlotStationData station, SlotStationData previous) {
        // Clear old machine area (using PREVIOUS geometry) since the restore snapshot
        // only covers the area that was captured, not the full old machine.
        if (previous != null) {
            SlotsGeometry oldGeometry = new SlotsGeometry(previous, leverPlacement);
            SlotsGeometry newGeometry = new SlotsGeometry(station, leverPlacement);
            Set<String> newKeys = new HashSet<>();
            for (Block b : newGeometry.allBlocks()) newKeys.add(slotKey(b));
            for (Block block : oldGeometry.allBlocks()) {
                if (!newKeys.contains(slotKey(block))) block.setType(Material.AIR, false);
            }
        }
        if (restoreStorage.has(station.key())) {
            restoreStorage.restoreAndForget(station.key());
        }
        captureStationBlocksIfNeeded(station);
    }

    private String slotKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void launchWinnerFireworks(SlotRuntime runtime, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        List<SlotPos> positions = new ArrayList<>(runtime.winningPositions);
        if (positions.isEmpty()) {
            positions.add(new SlotPos(0, 0));
        }
        positions.sort(Comparator.comparingInt(SlotPos::col).thenComparingInt(SlotPos::row));
        int burstDelay = 0;
        for (SlotPos pos : positions) {
            for (int burst = 0; burst < Math.max(1, fireworksPerWin); burst++) {
                int delay = burstDelay;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    SlotsGeometry geometry = runtime.geometry();
                    Block slotBlock = geometry.reelBlock(pos.col(), pos.row());
                    Location launchPoint = slotBlock.getRelative(BlockFace.DOWN).getLocation().add(0.5, 0.1, 0.5);
                    Firework firework = (Firework) launchPoint.getWorld().spawnEntity(launchPoint, EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL)
                            .trail(true)
                            .flicker(true)
                            .withColor(Color.AQUA, Color.YELLOW, Color.WHITE)
                            .build());
                    meta.setPower(1);
                    firework.setFireworkMeta(meta);
                }, delay);
                burstDelay += 4;
            }
        }
    }

    private void loadConfig() {
        this.costPerSpin = Math.max(0.01, plugin.getConfig().getDouble("slots.cost-per-spin", 100.0));
        this.spinSeconds = Math.max(1, plugin.getConfig().getInt("slots.spin-seconds", 5));
        this.stopIntervalTicks = Math.max(1, plugin.getConfig().getInt("slots.stop-interval-ticks", 12));
        this.resultSeconds = Math.max(1, plugin.getConfig().getInt("slots.result-seconds", 5));
        this.leverPlacement = normalizeLeverPlacement(plugin.getConfig().getString("slots.lever-placement", "front_right_middle"));
        this.spinLightMode = normalizeSpinLightMode(plugin.getConfig().getString("slots.spin-light-mode", "flashing_fast"));
        this.maxPayout = plugin.getConfig().getDouble("slots.max-payout", -1.0);
        this.fireworksPerWin = Math.max(1, plugin.getConfig().getInt("slots.fireworks-per-win", 1));
        this.outerFrameBlock = parseMaterial(plugin.getConfig().getString("slots.blocks.outer-frame"), Material.STONE);
        this.innerFrameBlock = parseMaterial(plugin.getConfig().getString("slots.blocks.inner-frame"), Material.REDSTONE_LAMP);
        this.winningBlock = parseMaterial(plugin.getConfig().getString("slots.blocks.winning"), Material.DIAMOND_BLOCK);

        List<String> configuredReels = plugin.getConfig().getStringList("slots.blocks.reel-options");
        List<Material> parsedReels = new ArrayList<>();
        for (String raw : configuredReels) {
            Material material = parseMaterial(raw, null);
            if (material != null) {
                parsedReels.add(material);
            }
        }
        if (!parsedReels.contains(winningBlock)) {
            parsedReels.add(winningBlock);
        }
        if (!parsedReels.isEmpty()) {
            this.reelOptions = List.copyOf(parsedReels);
        }

        payoutMultipliers.clear();
        for (int i = 1; i <= 16; i++) {
            payoutMultipliers.put(i, Math.max(0.0, plugin.getConfig().getDouble("slots.payout-multipliers." + i, defaultMultiplier(i))));
        }
    }

    private double costPerSpinFor(SlotStationData station) {
        return station.costPerSpin() != null ? Math.max(0.01, station.costPerSpin()) : costPerSpin;
    }

    private double defaultMultiplier(int wins) {
        return switch (wins) {
            case 1 -> 0.0;
            case 2 -> 0.5;
            case 3 -> 2.0;
            case 4 -> 5.0;
            case 5 -> 12.0;
            case 6 -> 30.0;
            case 7 -> 80.0;
            case 8 -> 200.0;
            case 9 -> 350.0;
            case 10 -> 600.0;
            case 11 -> 1000.0;
            case 12 -> 1600.0;
            case 13 -> 2500.0;
            case 14 -> 4000.0;
            case 15 -> 6500.0;
            case 16 -> 10000.0;
            default -> 0.0;
        };
    }

    private Object parseConfigValue(String path, String raw) {
        if (!path.startsWith("slots.")) {
            return null;
        }
        try {
            if (path.startsWith("slots.payout-multipliers.")) {
                int wins = Integer.parseInt(path.substring("slots.payout-multipliers.".length()));
                if (wins < 1 || wins > 16) {
                    return null;
                }
                double value = Double.parseDouble(raw);
                return value >= 0.0 ? value : null;
            }
            return switch (path) {
                case "slots.cost-per-spin",
                        "slots.activation-distance-from-frame",
                        "slots.max-payout",
                        "slots.hologram-height",
                        "slots.hologram-line-spacing",
                        "slots.hologram-view-range" -> Double.parseDouble(raw);
                case "slots.spin-seconds",
                        "slots.stop-interval-ticks",
                        "slots.result-seconds",
                        "slots.fireworks-per-win",
                        "slots.frame-animation.interval-ticks" -> {
                    int v = Integer.parseInt(raw);
                    yield v > 0 ? v : null;
                }
                case "slots.lever-placement" -> normalizeLeverPlacement(raw);
                case "slots.spin-light-mode" -> normalizeSpinLightMode(raw);
                case "slots.blocks.outer-frame", "slots.blocks.inner-frame", "slots.blocks.winning", "slots.frame-animation.block" -> {
                    Material m = Material.matchMaterial(raw);
                    yield m != null && m.isBlock() ? m.name() : null;
                }
                case "slots.frame-animation.enabled" -> parseBoolean(raw);
                case "slots.frame-animation.pattern" -> {
                    int v = Integer.parseInt(raw);
                    yield (v >= 1 && v <= 10) ? v : null;
                }
                case "slots.frame-animation.mode" -> normalizeFrameMode(raw);
                default -> null;
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeConfigPath(String rawPath) {
        String path = rawPath.toLowerCase();
        return path.startsWith("slots.") ? path : "slots." + path;
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
        return parsed != null && parsed.isBlock() ? parsed : fallback;
    }

    private String normalizeFrameMode(String raw) {
        if (raw == null) {
            return null;
        }
        String lower = raw.toLowerCase();
        if (lower.equals("idle_only") || lower.equals("always")) {
            return lower;
        }
        return null;
    }

    private String normalizeLeverPlacement(String raw) {
        if (raw == null) {
            return "front_right_middle";
        }
        String lower = raw.toLowerCase();
        if (lower.equals("front_right_middle") || lower.equals("front_middle") || lower.equals("right_middle") || lower.equals("middle")) {
            return lower;
        }
        return null;
    }

    private String spinLightModeFor(SlotStationData station) {
        return spinLightMode == null ? "flashing_fast" : spinLightMode;
    }

    private String normalizeSpinLightMode(String raw) {
        if (raw == null) {
            return "flashing_fast";
        }
        String lower = raw.toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (lower) {
            case "flashing_fast", "flashing_slow", "cycle_quick", "cycle_slow" -> lower;
            default -> null;
        };
    }

    private double clampPayout(double payout) {
        if (maxPayout > 0.0) {
            return Math.min(maxPayout, payout);
        }
        return payout;
    }

    private Material outerFrameBlockFor(SlotStationData station) {
        return parseMaterial(station.outerFrameBlock(), outerFrameBlock);
    }

    private Material visualInnerFrameBlock(SlotStationData station) {
        Boolean stationEnabled = station.frameAnimEnabled();
        boolean animEnabled = stationEnabled != null
                ? stationEnabled
                : plugin.getConfig().getBoolean("slots.frame-animation.enabled", false);
        if (!animEnabled) {
            return innerFrameBlockFor(station);
        }
        Material defaultAnimBlock = parseMaterial(plugin.getConfig().getString("slots.frame-animation.block"), Material.REDSTONE_LAMP);
        return parseMaterial(station.frameAnimBlock(), defaultAnimBlock);
    }

    private Material innerFrameBlockFor(SlotStationData station) {
        return parseMaterial(station.innerFrameBlock(), innerFrameBlock);
    }

    private Material winningBlockFor(SlotStationData station) {
        return parseMaterial(station.winningBlock(), winningBlock);
    }

    private String displayName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

    private WinResult determineWin(List<List<Material>> grid, SlotStationData station) {
        Material winning = winningBlockFor(station);
        int totalMatches = 0;
        List<SlotPos> winningPositions = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < grid.size(); rowIndex++) {
            List<Material> row = grid.get(rowIndex);
            for (int col = 0; col < row.size(); col++) {
                Material material = row.get(col);
                if (material == winning) {
                    totalMatches++;
                    winningPositions.add(new SlotPos(col, rowIndex));
                }
            }
        }
        if (totalMatches <= 0) {
            return new WinResult(0, "No Win", List.of());
        }

        if (station.rowCount() == 1) {
            return new WinResult(totalMatches, "Single Line", List.copyOf(winningPositions));
        }

        List<Payline> paylines = List.of(
                new Payline("Top Line", rowValues(grid, 0)),
                new Payline("Bottom Line", rowValues(grid, 1)),
                new Payline("Diagonal", diagonalValues(grid, true)),
                new Payline("Reverse Diagonal", diagonalValues(grid, false))
        );
        for (Payline line : paylines) {
            if (line.isWinning(winning)) {
                return new WinResult(line.values().size(), line.name(), List.copyOf(winningPositions));
            }
        }
        if (totalMatches == station.reelCount() * station.rowCount()) {
            return new WinResult(totalMatches, "Full Screen", List.copyOf(winningPositions));
        }
        return new WinResult(totalMatches, "Mixed Line", List.copyOf(winningPositions));
    }

    private double multiplierFor(SlotStationData station, int wins, String patternName) {
        double base = payoutMultipliers.getOrDefault(wins, defaultMultiplier(wins));
        double sizeScale = 1.0 + ((station.reelCount() - 3) * 0.16) + ((station.rowCount() - 1) * 0.35);
        double patternScale = switch (patternName) {
            case "Full Screen" -> 2.0;
            case "Diagonal", "Reverse Diagonal" -> 1.45;
            case "Top Line", "Bottom Line" -> 1.15;
            case "Mixed Line" -> 1.0;
            default -> 1.0;
        };
        return base * sizeScale * patternScale;
    }

    private List<Material> rowValues(List<List<Material>> grid, int rowIndex) {
        if (rowIndex < 0 || rowIndex >= grid.size()) {
            return List.of();
        }
        return List.copyOf(grid.get(rowIndex));
    }

    private List<Material> diagonalValues(List<List<Material>> grid, boolean leftToRightTop) {
        if (grid.size() < 2) {
            return List.of();
        }
        int width = grid.get(0).size();
        List<Material> values = new ArrayList<>();
        for (int col = 0; col < width; col++) {
            int row = leftToRightTop ? (col % 2 == 0 ? 0 : 1) : (col % 2 == 0 ? 1 : 0);
            values.add(grid.get(row).get(col));
        }
        return values;
    }

    private boolean allWinning(List<Material> values, Material winning) {
        if (values.isEmpty()) {
            return false;
        }
        for (Material material : values) {
            if (material != winning) {
                return false;
            }
        }
        return true;
    }

    private record Payline(String name, List<Material> values) {
        private boolean isWinning(Material winning) {
            for (Material material : values) {
                if (material != winning) {
                    return false;
                }
            }
            return !values.isEmpty();
        }
    }

    private record WinResult(int winCount, String patternName, List<SlotPos> winningPositions) {}

    private record SlotPos(int col, int row) {}

    private List<String> configuredLines(String path, String... fallback) {
        List<String> lines = plugin.getConfig().getStringList(path);
        return lines.isEmpty() ? List.of(fallback) : List.copyOf(lines);
    }

    private List<String> replaceLines(List<String> templates, Map<String, String> vars) {
        List<String> lines = new ArrayList<>(templates.size());
        for (String template : templates) {
            lines.add(replace(template, vars));
        }
        return lines;
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

    private String stationHoloTag(String stationKey) {
        String hash = UUID.nameUUIDFromBytes(stationKey.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return HOLO_STATION_TAG_PREFIX + hash;
    }

    private BlockFace yawToCardinal(float yaw) {
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 45 && normalized < 135) {
            return BlockFace.WEST;
        }
        if (normalized >= 135 && normalized < 225) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225 && normalized < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private BlockFace rotateLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.WEST;
        };
    }

    private enum Phase { IDLE, SPINNING, RESULT }

    private static final class SlotRuntime {
        private SlotStationData station;
        private Phase phase = Phase.IDLE;
        private UUID playerId;
        private double wager;
        private List<List<Material>> currentSymbols;
        private List<List<Material>> finalSymbols = List.of();
        private List<SlotPos> winningPositions = List.of();
        private int lockedReels;
        private int spinSecondsLeft;
        private int resultSecondsLeft;
        private int elapsedSpinTicks;
        private BukkitTask spinTask;
        private final List<String> resultLines = new ArrayList<>();

        private final SlotsManager manager;

        private SlotRuntime(SlotsManager manager, SlotStationData station) {
            this.manager = manager;
            this.station = station;
            this.currentSymbols = new ArrayList<>();
            for (int row = 0; row < station.rowCount(); row++) {
                List<Material> line = new ArrayList<>();
                for (int i = 0; i < station.reelCount(); i++) {
                    line.add(Material.STONE);
                }
                this.currentSymbols.add(line);
            }
        }

        private SlotsGeometry geometry() {
            return new SlotsGeometry(station, manager.leverPlacement);
        }
    }
}
