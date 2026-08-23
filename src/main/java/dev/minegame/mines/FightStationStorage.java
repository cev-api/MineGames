package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class FightStationStorage {
    private final MinegamePlugin plugin; private final File file;
    private final Map<String, FightStationData> stations = new LinkedHashMap<>();
    public FightStationStorage(MinegamePlugin plugin) { this.plugin=plugin; file=new File(plugin.getDataFolder(), "fights_stations.yml"); }
    public void load() { stations.clear(); if (!file.exists()) return; for (Map<?,?> m : YamlConfiguration.loadConfiguration(file).getMapList("stations")) try {
        FightStationData s=new FightStationData(String.valueOf(m.get("world")), Integer.parseInt(String.valueOf(m.get("x"))), Integer.parseInt(String.valueOf(m.get("y"))), Integer.parseInt(String.valueOf(m.get("z"))), Integer.parseInt(String.valueOf(m.containsKey("size") ? m.get("size") : 9)), Integer.parseInt(String.valueOf(m.containsKey("fighterCount") ? m.get("fighterCount") : 2))); stations.put(s.key(),s);
    } catch (RuntimeException ex) { plugin.getLogger().warning("Ignoring invalid Fights station: " + ex.getMessage()); } }
    public void save() { YamlConfiguration y=new YamlConfiguration(); List<Map<String,Object>> list=new ArrayList<>(); for(FightStationData s:stations.values()){Map<String,Object> m=new LinkedHashMap<>();m.put("world",s.worldName());m.put("x",s.x());m.put("y",s.y());m.put("z",s.z());m.put("size",s.size());m.put("fighterCount",s.fighterCount());list.add(m);} y.set("stations",list); try{y.save(file);}catch(IOException ex){plugin.getLogger().severe("Failed to save fights_stations.yml: "+ex.getMessage());} }
    public Collection<FightStationData> all(){return stations.values();} public void upsert(FightStationData s){stations.put(s.key(),s);} public FightStationData remove(String key){return stations.remove(key);}
}