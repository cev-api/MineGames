package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StationNumberStorage {
    private final MinegamePlugin plugin;
    private final File file;
    private final Map<String, Integer> numbers = new LinkedHashMap<>();
    private final Map<String, Integer> nextNumbers = new HashMap<>();

    public StationNumberStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "station_numbers.yml");
    }

    public void load() {
        numbers.clear();
        nextNumbers.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String type : yaml.getKeys(false)) {
            if (type.equals("format-version") || yaml.getConfigurationSection(type) == null) continue;
            for (String key : yaml.getConfigurationSection(type).getKeys(false)) {
                int number = yaml.getInt(type + "." + key, -1);
                if (number > 0) numbers.put(type + "." + key, number);
            }
        }
        if (yaml.getInt("format-version", 0) < 2) migrateLegacyGlobalNumbers();
        rebuildNextNumbers();
    }

    private void migrateLegacyGlobalNumbers() {
        Map<String, Integer> minimums = new HashMap<>();
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            String type = entry.getKey().substring(0, entry.getKey().indexOf('.'));
            minimums.merge(type, entry.getValue(), Math::min);
        }
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            String type = entry.getKey().substring(0, entry.getKey().indexOf('.'));
            int offset = minimums.getOrDefault(type, 1) - 1;
            if (offset > 0) entry.setValue(entry.getValue() - offset);
        }
    }

    private void rebuildNextNumbers() {
        nextNumbers.clear();
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            String type = entry.getKey().substring(0, entry.getKey().indexOf('.'));
            nextNumbers.merge(type, entry.getValue() + 1, Math::max);
        }
    }

    public int ensure(String type, String stationKey) {
        String key = type + "." + stationKey;
        Integer number = numbers.get(key);
        if (number != null) return number;
        number = nextNumbers.getOrDefault(type, 1);
        nextNumbers.put(type, number + 1);
        numbers.put(key, number);
        return number;
    }

    public int number(String type, String stationKey) { return ensure(type, stationKey); }

    public void transfer(String type, String oldKey, String newKey) {
        if (oldKey.equals(newKey)) return;
        Integer value = numbers.remove(type + "." + oldKey);
        if (value != null) numbers.put(type + "." + newKey, value);
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", 2);
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {

            int split = entry.getKey().indexOf('.');
            yaml.set(entry.getKey().substring(0, split) + "." + entry.getKey().substring(split + 1), entry.getValue());
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save station_numbers.yml: " + ex.getMessage()); }
    }
}
