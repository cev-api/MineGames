package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SlotStationStorage {
    private final MinegamePlugin plugin;
    private final Map<String, SlotStationData> stations = new LinkedHashMap<>();
    private final File file;

    public SlotStationStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "slot_stations.yml");
    }

    public void load() {
        stations.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> raw = yaml.getMapList("stations");
        for (Map<?, ?> item : raw) {
            String world = String.valueOf(item.get("world"));
            int x = Integer.parseInt(String.valueOf(item.get("x")));
            int y = Integer.parseInt(String.valueOf(item.get("y")));
            int z = Integer.parseInt(String.valueOf(item.get("z")));
            BlockFace facing = BlockFace.valueOf(String.valueOf(item.containsKey("facing") ? item.get("facing") : "NORTH"));
            int reelCount = Integer.parseInt(String.valueOf(item.containsKey("reelCount") ? item.get("reelCount") : 3));
            int rowCount = Integer.parseInt(String.valueOf(item.containsKey("rowCount") ? item.get("rowCount") : 1));
            String outerFrameBlock = item.containsKey("outerFrameBlock") ? String.valueOf(item.get("outerFrameBlock")) : null;
            String innerFrameBlock = item.containsKey("innerFrameBlock") ? String.valueOf(item.get("innerFrameBlock")) : null;
            String winningBlock = item.containsKey("winningBlock") ? String.valueOf(item.get("winningBlock")) : null;
            Boolean frameAnimEnabled = item.containsKey("frameAnimEnabled")
                    ? Boolean.parseBoolean(String.valueOf(item.get("frameAnimEnabled")))
                    : null;
            String frameAnimBlock = item.containsKey("frameAnimBlock") ? String.valueOf(item.get("frameAnimBlock")) : null;
            Integer frameAnimPattern = null;
            if (item.containsKey("frameAnimPattern")) {
                try {
                    frameAnimPattern = Integer.parseInt(String.valueOf(item.get("frameAnimPattern")));
                } catch (NumberFormatException ignored) {
                    frameAnimPattern = null;
                }
            }
            String frameAnimMode = item.containsKey("frameAnimMode") ? String.valueOf(item.get("frameAnimMode")) : null;
            Integer boardSize = null;
            if (item.containsKey("boardSize")) {
                try {
                    boardSize = Integer.parseInt(String.valueOf(item.get("boardSize")));
                } catch (NumberFormatException ignored) {
                    boardSize = null;
                }
            }
            Boolean betButtonsEnabled = item.containsKey("betButtonsEnabled") ? Boolean.parseBoolean(String.valueOf(item.get("betButtonsEnabled"))) : null;
            String betButtonMaterial = item.containsKey("betButtonMaterial") ? String.valueOf(item.get("betButtonMaterial")) : null;
            Double betAdjustPercent = item.containsKey("betAdjustPercent") ? Double.parseDouble(String.valueOf(item.get("betAdjustPercent"))) : null;
            Double currentBet = item.containsKey("currentBet") ? Double.parseDouble(String.valueOf(item.get("currentBet"))) : null;
            Double costPerSpin = null;
            if (item.containsKey("costPerSpin")) {
                try {
                    costPerSpin = Double.parseDouble(String.valueOf(item.get("costPerSpin")));
                } catch (NumberFormatException ignored) {
                    costPerSpin = null;
                }
            }
            SlotStationData station = new SlotStationData(
                    world,
                    x,
                    y,
                    z,
                    facing,
                    reelCount,
                    rowCount,
                    outerFrameBlock,
                    innerFrameBlock,
                    winningBlock,
                    frameAnimEnabled,
                    frameAnimBlock,
                    frameAnimPattern,
                    frameAnimMode,
                    boardSize,
                    costPerSpin,
                    betButtonsEnabled,
                    betButtonMaterial,
                    betAdjustPercent,
                    currentBet
            );
            stations.put(station.key(), station);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> raw = new ArrayList<>();
        for (SlotStationData station : stations.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", station.worldName());
            map.put("x", station.x());
            map.put("y", station.y());
            map.put("z", station.z());
            map.put("facing", station.facing().name());
            map.put("reelCount", station.reelCount());
            map.put("rowCount", station.rowCount());
            if (station.outerFrameBlock() != null) {
                map.put("outerFrameBlock", station.outerFrameBlock());
            }
            if (station.innerFrameBlock() != null) {
                map.put("innerFrameBlock", station.innerFrameBlock());
            }
            if (station.winningBlock() != null) {
                map.put("winningBlock", station.winningBlock());
            }
            if (station.frameAnimEnabled() != null) {
                map.put("frameAnimEnabled", station.frameAnimEnabled());
            }
            if (station.frameAnimBlock() != null) {
                map.put("frameAnimBlock", station.frameAnimBlock());
            }
            if (station.frameAnimPattern() != null) {
                map.put("frameAnimPattern", station.frameAnimPattern());
            }
            if (station.frameAnimMode() != null) {
                map.put("frameAnimMode", station.frameAnimMode());
            }
            if (station.boardSize() != null) {
                map.put("boardSize", station.boardSize());
            }
            if (station.costPerSpin() != null) {
                map.put("costPerSpin", station.costPerSpin());
            }
            if (station.betButtonsEnabled() != null) map.put("betButtonsEnabled", station.betButtonsEnabled());
            if (station.betButtonMaterial() != null) map.put("betButtonMaterial", station.betButtonMaterial());
            if (station.betAdjustPercent() != null) map.put("betAdjustPercent", station.betAdjustPercent());
            if (station.currentBet() != null) map.put("currentBet", station.currentBet());
            raw.add(map);
        }
        yaml.set("stations", raw);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save slot_stations.yml: " + e.getMessage());
        }
    }

    public Collection<SlotStationData> all() {
        return stations.values();
    }

    public void upsert(SlotStationData station) {
        stations.put(station.key(), station);
    }

    public SlotStationData get(String key) {
        return stations.get(key);
    }

    public SlotStationData remove(String key) {
        return stations.remove(key);
    }
}
