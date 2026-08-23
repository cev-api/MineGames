package dev.minegame.mines;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class SlotsListener implements Listener {
    private final SlotsManager slotsManager;

    public SlotsListener(SlotsManager slotsManager) {
        this.slotsManager = slotsManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (slotsManager.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (slotsManager.isBetButton(event.getClickedBlock())) {
            event.setCancelled(true);
            slotsManager.adjustBet(event.getPlayer(), event.getClickedBlock());
            return;
        }
        if (slotsManager.isLeverBlock(event.getClickedBlock())) {
            event.setCancelled(true);
            slotsManager.pullLever(event.getPlayer(), event.getClickedBlock());
            return;
        }
        if (slotsManager.isProtectedBlock(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(slotsManager::isProtectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(slotsManager::isProtectedBlock);
    }
}
