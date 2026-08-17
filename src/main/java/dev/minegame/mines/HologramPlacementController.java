package dev.minegame.mines;

import java.util.List;
import org.bukkit.entity.Player;

public final class HologramPlacementController {
    private final HologramPlacementListener listener;
    private final HologramPlacementStorage storage;
    private final MinesManager mines;
    private final SlotsManager slots;
    private final RouletteManager roulette;
    private final StationNumberStorage stationNumbers;

    public HologramPlacementController(MinegamePlugin plugin, HologramPlacementStorage storage,
            MinesManager mines, SlotsManager slots, RouletteManager roulette) {
        this.storage = storage;
        this.mines = mines;
        this.slots = slots;
        this.roulette = roulette;
        this.stationNumbers = plugin.stationNumberStorage();
        this.listener = new HologramPlacementListener(plugin, storage);
    }

    public HologramPlacementListener listener() { return listener; }

    public void arm(Player player, String type, int index, boolean remove) {
        String stationKey = switch (type) {
            case "minegame" -> stationKey("minegame", mines.stations(), index);
            case "slots" -> stationKey("slots", slots.stations(), index);
            case "roulette" -> stationKey("roulette", roulette.stations(), index);
            default -> null;
        };
        if (stationKey == null) {
            player.sendMessage(mines.colorize("&cNo station exists at that number."));
            return;
        }
        if (remove) {
            storage.remove(type, stationKey);
            player.sendMessage(mines.colorize("&aHologram restored to its default location."));
            return;
        }
        listener.arm(player, type, stationKey, false);
    }

    private String stationKey(String type, Iterable<?> stations, int index) {
        if (index < 1) return null;
        for (Object station : stations) {
            String key = null;
            if (station instanceof StationData s) key = s.key();
            if (station instanceof SlotStationData s) key = s.key();
            if (station instanceof RouletteStationData s) key = s.key();
            if (key != null && stationNumbers.number(type, key) == index) return key;
        }
        return null;
    }

    public HologramPlacementStorage storage() { return storage; }
}