package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

public final class BlockSnapshotStorage {
    private final MinegamePlugin plugin;
    private final File file;
    private final Map<String, List<BlockSnapshot>> snapshots = new HashMap<>();

    public BlockSnapshotStorage(MinegamePlugin plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    public void load() {
        snapshots.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> raw = yaml.getMapList("snapshots");
        for (Map<?, ?> entry : raw) {
            String key = String.valueOf(entry.get("key"));
            List<BlockSnapshot> list = new ArrayList<>();
            Object blocksRaw = entry.get("blocks");
            if (blocksRaw instanceof List<?> blocksList) {
                for (Object blockRaw : blocksList) {
                    if (!(blockRaw instanceof Map<?, ?> blockMap)) {
                        continue;
                    }
                    String world = String.valueOf(blockMap.get("world"));
                    int x = Integer.parseInt(String.valueOf(blockMap.get("x")));
                    int y = Integer.parseInt(String.valueOf(blockMap.get("y")));
                    int z = Integer.parseInt(String.valueOf(blockMap.get("z")));
                    Material material = Material.matchMaterial(String.valueOf(blockMap.get("type")));
                    if (material == null) {
                        material = Material.AIR;
                    }
                    list.add(new BlockSnapshot(world, x, y, z, material));
                }
            }
            snapshots.put(key, list);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> raw = new ArrayList<>();
        for (Map.Entry<String, List<BlockSnapshot>> entry : snapshots.entrySet()) {
            Map<String, Object> station = new LinkedHashMap<>();
            station.put("key", entry.getKey());
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (BlockSnapshot snapshot : entry.getValue()) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("world", snapshot.worldName());
                block.put("x", snapshot.x());
                block.put("y", snapshot.y());
                block.put("z", snapshot.z());
                block.put("type", snapshot.type().name());
                blocks.add(block);
            }
            station.put("blocks", blocks);
            raw.add(station);
        }
        yaml.set("snapshots", raw);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + file.getName() + ": " + e.getMessage());
        }
    }

    public boolean has(String key) {
        return snapshots.containsKey(key);
    }

    public void captureIfAbsent(String key, List<Block> blocks) {
        if (has(key)) {
            return;
        }
        Map<String, BlockSnapshot> unique = new LinkedHashMap<>();
        for (Block block : blocks) {
            Location l = block.getLocation();
            String id = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
            unique.put(id, new BlockSnapshot(
                    l.getWorld().getName(),
                    l.getBlockX(),
                    l.getBlockY(),
                    l.getBlockZ(),
                    block.getType()
            ));
        }
        snapshots.put(key, new ArrayList<>(unique.values()));
        save();
    }

    public void restoreAndForget(String key) {
        List<BlockSnapshot> list = snapshots.remove(key);
        if (list == null) {
            return;
        }
        for (BlockSnapshot snapshot : list) {
            World world = plugin.getServer().getWorld(snapshot.worldName());
            if (world == null) {
                continue;
            }
            world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z()).setType(snapshot.type(), false);
        }
        save();
    }

    public void remove(String key) {
        if (snapshots.remove(key) != null) {
            save();
        }
    }

    private record BlockSnapshot(String worldName, int x, int y, int z, Material type) {}
}

