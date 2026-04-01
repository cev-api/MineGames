package dev.minegame.mines;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerExitListener implements Listener {
    private final MinesManager minesManager;

    public PlayerExitListener(MinesManager minesManager) {
        this.minesManager = minesManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        minesManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
