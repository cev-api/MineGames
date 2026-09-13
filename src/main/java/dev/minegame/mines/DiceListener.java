package dev.minegame.mines;

import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class DiceListener implements Listener {
    private final DiceManager diceManager;

    public DiceListener(DiceManager diceManager) {
        this.diceManager = diceManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!diceManager.isDice(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        diceManager.throwDie(event.getPlayer(), event.getHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction)) {
            return;
        }
        if (diceManager.pickup(event.getRightClicked(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
