package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DiceRateLimitStorage {
    private static final long DAY_MILLIS = 86_400_000L;
    private final MinegamePlugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public DiceRateLimitStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dice_rate_limits.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getConfigurationSection("players") == null
                ? java.util.List.<String>of()
                : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String path = "players." + key;
                entries.put(playerId, new Entry(
                        yaml.getInt(path + ".count", 0),
                        yaml.getLong(path + ".window-start", 0L),
                        yaml.getLong(path + ".world-day", Long.MIN_VALUE)
                ));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid historical player keys.
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".count", entry.getValue().count());
            yaml.set(path + ".window-start", entry.getValue().windowStart());
            yaml.set(path + ".world-day", entry.getValue().worldDay());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save dice_rate_limits.yml: " + ex.getMessage());
        }
    }

    public synchronized RateLimitResult tryConsume(Player player, int limit, String period) {
        if (limit <= 0) {
            return new RateLimitResult(true, 0L);
        }
        long now = System.currentTimeMillis();
        long worldDay = player.getWorld().getFullTime() / 24000L;
        Entry entry = entries.computeIfAbsent(player.getUniqueId(), ignored -> new Entry(0, now, worldDay));
        boolean newWindow;
        if (period.equalsIgnoreCase("day")) {
            newWindow = entry.worldDay() != worldDay;
        } else {
            newWindow = now - entry.windowStart() >= DAY_MILLIS || now < entry.windowStart();
        }
        if (newWindow) {
            entry = new Entry(0, now, worldDay);
            entries.put(player.getUniqueId(), entry);
        }
        if (entry.count() >= limit) {
            long remaining = period.equalsIgnoreCase("day")
                    ? Math.max(0L, (24000L - player.getWorld().getFullTime() % 24000L) * 50L)
                    : Math.max(0L, DAY_MILLIS - (now - entry.windowStart()));
            return new RateLimitResult(false, remaining);
        }
        entries.put(player.getUniqueId(), new Entry(entry.count() + 1, entry.windowStart(), worldDay));
        save();
        return new RateLimitResult(true, 0L);
    }

    public record RateLimitResult(boolean allowed, long remainingMillis) { }

    private record Entry(int count, long windowStart, long worldDay) { }
}
