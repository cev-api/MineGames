package dev.minegame.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record RouletteStationData(
        String worldName,
        int x,
        int y,
        int z,
        String boardFrameBlock,
        String boardRedBlock,
        String boardBlackBlock,
        String boardGreenBlock,
        String boardSelectorBlock,
        Boolean frameAnimEnabled,
        String frameAnimBlock,
        Integer frameAnimPattern,
        String frameAnimMode
) {
    public RouletteStationData {
        if (boardFrameBlock != null) {
            boardFrameBlock = boardFrameBlock.toUpperCase();
        }
        if (boardRedBlock != null) {
            boardRedBlock = boardRedBlock.toUpperCase();
        }
        if (boardBlackBlock != null) {
            boardBlackBlock = boardBlackBlock.toUpperCase();
        }
        if (boardGreenBlock != null) {
            boardGreenBlock = boardGreenBlock.toUpperCase();
        }
        if (boardSelectorBlock != null) {
            boardSelectorBlock = boardSelectorBlock.toUpperCase();
        }
        if (frameAnimBlock != null) {
            frameAnimBlock = frameAnimBlock.toUpperCase();
        }
        if (frameAnimMode != null) {
            frameAnimMode = frameAnimMode.toLowerCase();
        }
    }

    public RouletteStationData(String worldName, int x, int y, int z) {
        this(worldName, x, y, z, null, null, null, null, null, null, null, null, null);
    }

    public String key() {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    public Location centerLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public RouletteStationData withBoardMaterials(
            String frame,
            String red,
            String black,
            String green,
            String selector
    ) {
        return new RouletteStationData(
                worldName,
                x,
                y,
                z,
                frame,
                red,
                black,
                green,
                selector,
                frameAnimEnabled,
                frameAnimBlock,
                frameAnimPattern,
                frameAnimMode
        );
    }

    public RouletteStationData clearBoardMaterialOverrides() {
        return new RouletteStationData(
                worldName,
                x,
                y,
                z,
                null,
                null,
                null,
                null,
                null,
                frameAnimEnabled,
                frameAnimBlock,
                frameAnimPattern,
                frameAnimMode
        );
    }

    public RouletteStationData withFrameAnimation(Boolean enabled, String block, Integer pattern, String mode) {
        return new RouletteStationData(
                worldName,
                x,
                y,
                z,
                boardFrameBlock,
                boardRedBlock,
                boardBlackBlock,
                boardGreenBlock,
                boardSelectorBlock,
                enabled,
                block,
                pattern,
                mode
        );
    }

    public RouletteStationData clearFrameAnimationOverrides() {
        return new RouletteStationData(
                worldName,
                x,
                y,
                z,
                boardFrameBlock,
                boardRedBlock,
                boardBlackBlock,
                boardGreenBlock,
                boardSelectorBlock,
                null,
                null,
                null,
                null
        );
    }
}
