package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HologramPlacementStorage {
    private final MinegamePlugin plugin;
    private final Map<String, Location> placements = new LinkedHashMap<>();
    private final java.io.File file;

    public HologramPlacementStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new java.io.File(plugin.getDataFolder(), "holograms.yml");
    }

    public void load() {
        placements.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> item : yaml.getMapList("placements")) {
            String key = String.valueOf(item.get("key"));
            World world = Bukkit.getWorld(String.valueOf(item.get("world")));
            if (world == null) continue;
            placements.put(key, new Location(world,
                    Double.parseDouble(String.valueOf(item.get("x"))),
                    Double.parseDouble(String.valueOf(item.get("y"))),
                    Double.parseDouble(String.valueOf(item.get("z"))),
                    Float.parseFloat(String.valueOf(item.get("yaw"))),
                    Float.parseFloat(String.valueOf(item.get("pitch")))));
        }
    }
    public Location get(String type, String stationKey) {
        Location location = placements.get(type + "." + stationKey);
        return location == null ? null : location.clone();
    }

    public void set(String type, String stationKey, Location location) {
        placements.put(type + "." + stationKey, location.clone());
        save();
    }

    public boolean remove(String type, String stationKey) {
        boolean removed = placements.remove(type + "." + stationKey) != null;
        if (removed) save();
        return removed;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        java.util.List<Map<String, Object>> raw = new java.util.ArrayList<>();
        for (Map.Entry<String, Location> entry : placements.entrySet()) {
            Location location = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("world", location.getWorld().getName());
            item.put("x", location.getX());
            item.put("y", location.getY());
            item.put("z", location.getZ());
            item.put("yaw", location.getYaw());
            item.put("pitch", location.getPitch());
            raw.add(item);
        }
        yaml.set("placements", raw);
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save holograms.yml: " + ex.getMessage()); }
    }
}