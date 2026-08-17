package dev.minegame.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

public record StationData(
        String worldName,
        int x,
        int y,
        int z,
        BlockFace facing,
        String boardHiddenBlock,
        String boardSafeRevealBlock,
        String boardMineRevealBlock,
        String boardFrameBlock,
        Boolean frameAnimEnabled,
        String frameAnimBlock,
        Integer frameAnimPattern,
        String frameAnimMode,
        Integer boardSize
) {
    public StationData {
        if (boardHiddenBlock != null) {
            boardHiddenBlock = boardHiddenBlock.toUpperCase();
        }
        if (boardSafeRevealBlock != null) {
            boardSafeRevealBlock = boardSafeRevealBlock.toUpperCase();
        }
        if (boardMineRevealBlock != null) {
            boardMineRevealBlock = boardMineRevealBlock.toUpperCase();
        }
        if (boardFrameBlock != null) {
            boardFrameBlock = boardFrameBlock.toUpperCase();
        }
        if (frameAnimBlock != null) {
            frameAnimBlock = frameAnimBlock.toUpperCase();
        }
        if (frameAnimMode != null) {
            frameAnimMode = frameAnimMode.toLowerCase();
        }
        if (boardSize != null) {
            boardSize = Math.max(2, boardSize);
        }
    }

    public StationData(String worldName, int x, int y, int z, BlockFace facing) {
        this(worldName, x, y, z, facing, null, null, null, null, null, null, null, null, null);
    }

    public String key() {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    public Location beaconLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public StationData moved(int dx, int dy, int dz) {
        return new StationData(worldName, x + dx, y + dy, z + dz, facing,
                boardHiddenBlock, boardSafeRevealBlock, boardMineRevealBlock, boardFrameBlock,
                frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, boardSize);
    }
    public StationData withFrameAnimation(Boolean enabled, String block, Integer pattern, String mode) {
        return new StationData(
                worldName,
                x,
                y,
                z,
                facing,
                boardHiddenBlock,
                boardSafeRevealBlock,
                boardMineRevealBlock,
                boardFrameBlock,
                enabled,
                block,
                pattern,
                mode,
                boardSize
        );
    }

    public StationData clearFrameAnimationOverrides() {
        return new StationData(
                worldName,
                x,
                y,
                z,
                facing,
                boardHiddenBlock,
                boardSafeRevealBlock,
                boardMineRevealBlock,
                boardFrameBlock,
                null,
                null,
                null,
                null,
                boardSize
        );
    }

    public StationData withBoardMaterials(String hidden, String safe, String mine, String frame) {
        return new StationData(
                worldName,
                x,
                y,
                z,
                facing,
                hidden,
                safe,
                mine,
                frame,
                frameAnimEnabled,
                frameAnimBlock,
                frameAnimPattern,
                frameAnimMode,
                boardSize
        );
    }

    public StationData clearBoardMaterialOverrides() {
        return new StationData(
                worldName,
                x,
                y,
                z,
                facing,
                null,
                null,
                null,
                null,
                frameAnimEnabled,
                frameAnimBlock,
                frameAnimPattern,
                frameAnimMode,
                boardSize
        );
    }

    public StationData withBoardSize(Integer size) {
        return new StationData(
                worldName,
                x,
                y,
                z,
                facing,
                boardHiddenBlock,
                boardSafeRevealBlock,
                boardMineRevealBlock,
                boardFrameBlock,
                frameAnimEnabled,
                frameAnimBlock,
                frameAnimPattern,
                frameAnimMode,
                size
        );
    }

    public StationData clearBoardSizeOverride() {
        return withBoardSize(null);
    }
}
