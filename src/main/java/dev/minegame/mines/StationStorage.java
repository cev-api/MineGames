package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StationStorage {
    private final MinegamePlugin plugin;
    private final Map<String, StationData> stations = new LinkedHashMap<>();
    private final File file;

    public StationStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stations.yml");
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
            BlockFace facing = BlockFace.valueOf(String.valueOf(item.get("facing")));
            String boardHiddenBlock = item.containsKey("boardHiddenBlock")
                    ? String.valueOf(item.get("boardHiddenBlock"))
                    : null;
            String boardSafeRevealBlock = item.containsKey("boardSafeRevealBlock")
                    ? String.valueOf(item.get("boardSafeRevealBlock"))
                    : null;
            String boardMineRevealBlock = item.containsKey("boardMineRevealBlock")
                    ? String.valueOf(item.get("boardMineRevealBlock"))
                    : null;
            String boardFrameBlock = item.containsKey("boardFrameBlock")
                    ? String.valueOf(item.get("boardFrameBlock"))
                    : null;
            Boolean frameAnimEnabled = item.containsKey("frameAnimEnabled")
                    ? Boolean.parseBoolean(String.valueOf(item.get("frameAnimEnabled")))
                    : null;
            String frameAnimBlock = item.containsKey("frameAnimBlock")
                    ? String.valueOf(item.get("frameAnimBlock"))
                    : null;
            Integer frameAnimPattern = null;
            if (item.containsKey("frameAnimPattern")) {
                try {
                    frameAnimPattern = Integer.parseInt(String.valueOf(item.get("frameAnimPattern")));
                } catch (NumberFormatException ignored) {
                    frameAnimPattern = null;
                }
            }
            String frameAnimMode = item.containsKey("frameAnimMode")
                    ? String.valueOf(item.get("frameAnimMode"))
                    : null;
            StationData station = new StationData(
                    world,
                    x,
                    y,
                    z,
                    facing,
                    boardHiddenBlock,
                    boardSafeRevealBlock,
                    boardMineRevealBlock,
                    boardFrameBlock,
                    frameAnimEnabled,
                    frameAnimBlock,
                    frameAnimPattern,
                    frameAnimMode
            );
            stations.put(station.key(), station);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> raw = new ArrayList<>();
        for (StationData station : stations.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", station.worldName());
            map.put("x", station.x());
            map.put("y", station.y());
            map.put("z", station.z());
            map.put("facing", station.facing().name());
            if (station.boardHiddenBlock() != null) {
                map.put("boardHiddenBlock", station.boardHiddenBlock());
            }
            if (station.boardSafeRevealBlock() != null) {
                map.put("boardSafeRevealBlock", station.boardSafeRevealBlock());
            }
            if (station.boardMineRevealBlock() != null) {
                map.put("boardMineRevealBlock", station.boardMineRevealBlock());
            }
            if (station.boardFrameBlock() != null) {
                map.put("boardFrameBlock", station.boardFrameBlock());
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
            raw.add(map);
        }
        yaml.set("stations", raw);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stations.yml: " + e.getMessage());
        }
    }

    public Collection<StationData> all() {
        return stations.values();
    }

    public StationData get(String key) {
        return stations.get(key);
    }

    public void upsert(StationData station) {
        stations.put(station.key(), station);
    }

    public StationData remove(String key) {
        return stations.remove(key);
    }
}
