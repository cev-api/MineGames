package dev.minegame.mines;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CasinoGuiHolder implements InventoryHolder {
    private final String game;
    private final String stationKey;
    private final int page;

    public CasinoGuiHolder(String game) { this(game, null, 0); }
    public CasinoGuiHolder(String game, String stationKey) { this(game, stationKey, 0); }
    public CasinoGuiHolder(String game, String stationKey, int page) {
        this.game = game; this.stationKey = stationKey; this.page = page;
    }
    public String game() { return game; }
    public String stationKey() { return stationKey; }
    public int page() { return page; }
    @Override public Inventory getInventory() { return null; }
}
