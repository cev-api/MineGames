package dev.minegame.mines;

import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                && diceManager.isDice(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY
                && event.getSlotType() == InventoryType.SlotType.ARMOR
                && diceManager.isDice(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND
                && event.getSlotType() == InventoryType.SlotType.ARMOR
                && diceManager.isDice(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()
                && diceManager.isDice(event.getCurrentItem())
                && player.getInventory().getHelmet() == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!diceManager.isDice(event.getOldCursor())) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
