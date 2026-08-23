package dev.minegame.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;

/** Persistent anchor and rules for one Fights arena. */
public record FightStationData(String worldName, int x, int y, int z, int size, int fighterCount) {
    public String key() { return worldName + ":" + x + ":" + y + ":" + z; }
    public Location centerLocation() { var world=Bukkit.getWorld(worldName); return world==null?null:new Location(world,x+.5,y+1,z+.5); }
    public FightStationData withFighterCount(int count) { return new FightStationData(worldName,x,y,z,size,count); }
    public FightStationData withLocation(String world, int blockX, int blockY, int blockZ) { return new FightStationData(world, blockX, blockY, blockZ, size, fighterCount); }
}