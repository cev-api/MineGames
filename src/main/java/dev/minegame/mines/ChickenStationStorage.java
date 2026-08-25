package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ChickenStationStorage {
    private final MinegamePlugin plugin; private final File file; private final Map<String, ChickenStationData> stations = new LinkedHashMap<>();
    public ChickenStationStorage(MinegamePlugin plugin) { this.plugin=plugin; file=new File(plugin.getDataFolder(),"chicken_stations.yml"); }
    public void load(){stations.clear();if(!file.exists())return;for(Map<?,?> raw:YamlConfiguration.loadConfiguration(file).getMapList("stations")){try{ChickenStationData s=new ChickenStationData(String.valueOf(raw.get("world")),Integer.parseInt(String.valueOf(raw.get("x"))),Integer.parseInt(String.valueOf(raw.get("y"))),Integer.parseInt(String.valueOf(raw.get("z"))), raw.containsKey("size") ? Integer.parseInt(String.valueOf(raw.get("size"))) : 7);stations.put(s.key(),s);}catch(Exception ignored){}}}
    public void save(){YamlConfiguration yaml=new YamlConfiguration();List<Map<String,Object>> out=new ArrayList<>();for(ChickenStationData s:stations.values()){Map<String,Object> m=new LinkedHashMap<>();m.put("world",s.worldName());m.put("x",s.x());m.put("y",s.y());m.put("z",s.z());m.put("size",s.size());out.add(m);}yaml.set("stations",out);try{yaml.save(file);}catch(IOException e){plugin.getLogger().warning("Failed to save chicken_stations.yml: "+e.getMessage());}}
    public Collection<ChickenStationData> all(){return stations.values();} public void upsert(ChickenStationData s){stations.put(s.key(),s);} public ChickenStationData remove(String key){return stations.remove(key);}
}