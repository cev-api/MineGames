package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RouletteStationStorage {
    private final MinegamePlugin plugin;
    private final Map<String, RouletteStationData> stations = new LinkedHashMap<>();
    private final File file;

    public RouletteStationStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "roulette_stations.yml");
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
            String boardFrameBlock = item.containsKey("boardFrameBlock")
                    ? String.valueOf(item.get("boardFrameBlock"))
                    : null;
            String boardRedBlock = item.containsKey("boardRedBlock")
                    ? String.valueOf(item.get("boardRedBlock"))
                    : null;
            String boardBlackBlock = item.containsKey("boardBlackBlock")
                    ? String.valueOf(item.get("boardBlackBlock"))
                    : null;
            String boardGreenBlock = item.containsKey("boardGreenBlock")
                    ? String.valueOf(item.get("boardGreenBlock"))
                    : null;
            String boardSelectorBlock = item.containsKey("boardSelectorBlock")
                    ? String.valueOf(item.get("boardSelectorBlock"))
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
            RouletteStationData station = new RouletteStationData(
                    world,
                    x,
                    y,
                    z,
                    boardFrameBlock,
                    boardRedBlock,
                    boardBlackBlock,
                    boardGreenBlock,
                    boardSelectorBlock,
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
        for (RouletteStationData station : stations.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", station.worldName());
            map.put("x", station.x());
            map.put("y", station.y());
            map.put("z", station.z());
            if (station.boardFrameBlock() != null) {
                map.put("boardFrameBlock", station.boardFrameBlock());
            }
            if (station.boardRedBlock() != null) {
                map.put("boardRedBlock", station.boardRedBlock());
            }
            if (station.boardBlackBlock() != null) {
                map.put("boardBlackBlock", station.boardBlackBlock());
            }
            if (station.boardGreenBlock() != null) {
                map.put("boardGreenBlock", station.boardGreenBlock());
            }
            if (station.boardSelectorBlock() != null) {
                map.put("boardSelectorBlock", station.boardSelectorBlock());
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
            plugin.getLogger().severe("Failed to save roulette_stations.yml: " + e.getMessage());
        }
    }

    public Collection<RouletteStationData> all() {
        return stations.values();
    }

    public RouletteStationData get(String key) {
        return stations.get(key);
    }

    public void upsert(RouletteStationData station) {
        stations.put(station.key(), station);
    }

    public RouletteStationData remove(String key) {
        return stations.remove(key);
    }
}
