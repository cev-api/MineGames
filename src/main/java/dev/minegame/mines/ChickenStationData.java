package dev.minegame.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record ChickenStationData(String worldName, int x, int y, int z, int size) {
    public ChickenStationData { size = Math.max(5, Math.min(15, size | 1)); }
    public String key() { return worldName + ":" + x + ":" + y + ":" + z; }
    public Location centerLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x + .5, y + 1, z + .5);
    }
}