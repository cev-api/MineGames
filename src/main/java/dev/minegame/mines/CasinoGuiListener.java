package dev.minegame.mines;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class CasinoGuiListener implements Listener {
    private final CasinoGuiCommand gui;
    public CasinoGuiListener(CasinoGuiCommand gui) { this.gui = gui; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof CasinoGuiHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (holder.game().equals("home")) {
            if (slot == 11) gui.openGame(player, "minegame");
            else if (slot == 13) gui.openGame(player, "roulette");
            else if (slot == 15) gui.openGame(player, "slots");
            return;
        }

        if (holder.game().startsWith("stations:")) {
            String game = holder.game().substring("stations:".length());
            if (slot == 49) { gui.openGame(player, game); return; }
            if (slot == 50) { gui.openGame(player, game); return; }
            if (slot >= 10 && slot < 44) gui.openStationBySlot(player, game, slot);
            return;
        }

        if (holder.stationKey() != null) {
            if (slot == 49) { gui.openStations(player, holder.game()); return; }
            if (slot == 50) { gui.openGame(player, holder.game()); return; }
            if (slot >= 10 && slot < 45) gui.handleSettingClick(player, holder, slot, event.isLeftClick());
            return;
        }

        if (slot == 45) { gui.openGame(player, holder.game(), holder.page() - 1); return; }
        if (slot == 53) { gui.openGame(player, holder.game(), holder.page() + 1); return; }
        if (slot == 49) { gui.openHome(player); return; }
        if (slot == 50) { gui.openStations(player, holder.game()); return; }
        if (slot >= 10 && slot < 45) gui.handleSettingClick(player, holder, slot, event.isLeftClick());
    }
}