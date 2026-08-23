package dev.minegame.mines;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.entity.Player;

/** Prevents non-admin building above any registered casino station. */
public final class CasinoBuildProtectionListener implements Listener {
    private final MinegamePlugin plugin;
    private final MinesManager mines;
    private final RouletteManager roulette;
    private final SlotsManager slots;
    private final FightsManager fights;

    public CasinoBuildProtectionListener(MinegamePlugin plugin, MinesManager mines, RouletteManager roulette, SlotsManager slots, FightsManager fights) {
        this.plugin = plugin;
        this.mines = mines;
        this.roulette = roulette;
        this.slots = slots;
        this.fights = fights;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer()) && protectedAirspace(event.getBlockPlaced())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void emptyBucket(PlayerBucketEmptyEvent event) {
        Block destination = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!canBuild(event.getPlayer()) && protectedAirspace(destination)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void flow(BlockFromToEvent event) {
        if (protectedAirspace(event.getToBlock())) event.setCancelled(true);
    }

    private boolean canBuild(Player player) {
        return player.isOp()
                || player.hasPermission("minegame.admin")
                || player.hasPermission("roulette.admin")
                || player.hasPermission("slots.admin")
                || player.hasPermission("fights.admin");
    }

    private boolean protectedAirspace(Block block) {
        String world = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        for (StationData station : mines.stations()) {
            int size = station.boardSize() != null ? station.boardSize() : plugin.getConfig().getInt("minegame.board.grid-size", 5);
            if (aboveSquare(world, x, y, z, station.worldName(), station.x(), station.y(), station.z(), size + 3)) return true;
        }
        for (RouletteStationData station : roulette.stations()) {
            int size = station.boardSize() != null ? station.boardSize() : plugin.getConfig().getInt("roulette.board-size", 12);
            if (aboveSquare(world, x, y, z, station.worldName(), station.x(), station.y(), station.z(), size + 2)) return true;
        }
        for (SlotStationData station : slots.stations()) {
            int radius = Math.max(station.reelCount(), station.rowCount()) + 3;
            if (aboveSquare(world, x, y, z, station.worldName(), station.x(), station.y(), station.z(), radius)) return true;
        }
        for (FightStationData station : fights.stations()) {
            if (aboveSquare(world, x, y, z, station.worldName(), station.x(), station.y(), station.z(), station.size() / 2 + 1)) return true;
        }
        return false;
    }

    private boolean aboveSquare(String world, int x, int y, int z, String stationWorld, int stationX, int stationY, int stationZ, int radius) {
        return world.equals(stationWorld)
                && y >= stationY
                && Math.abs(x - stationX) <= radius
                && Math.abs(z - stationZ) <= radius;
    }
}