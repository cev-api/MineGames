package dev.minegame.mines;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

public final class HologramPlacementListener implements Listener {
    private record Pending(String type, String stationKey, boolean remove) {}
    private final MinegamePlugin plugin;
    private final HologramPlacementStorage storage;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public HologramPlacementListener(MinegamePlugin plugin, HologramPlacementStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void arm(Player player, String type, String stationKey, boolean remove) {
        pending.put(player.getUniqueId(), new Pending(type, stationKey, remove));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eRight-click the surface where the hologram should be placed."));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Pending selection = pending.remove(event.getPlayer().getUniqueId());
        if (selection == null) return;
        event.setCancelled(true);
        if (selection.remove()) {
            storage.remove(selection.type(), selection.stationKey());
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&aHologram restored to its default location."));
            return;
        }
        Location block = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        Vector normal = event.getBlockFace().getDirection();
        Location surface = block.clone().add(normal.clone().multiply(1.0));
        surface.setDirection(normal);
        storage.set(selection.type(), selection.stationKey(), surface);
        event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&aHologram location saved."));
    }
}