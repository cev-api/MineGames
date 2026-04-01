package dev.minegame.mines;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public final class BoardGeometry {
    public record FrameCell(Block block, int col, int row) {}
    private final StationData station;
    private final int wallDistance;
    private final int gridSize;
    private final int frameVerticalOffset;
    private final World world;
    private final Vector forward;
    private final Vector right;
    private final Location centerBottom;
    private final Map<String, Integer> indexByBlock = new HashMap<>();
    private final Set<String> frameKeys = new HashSet<>();

    public BoardGeometry(StationData station, int wallDistance, int gridSize, int frameVerticalOffset) {
        this.station = station;
        this.wallDistance = wallDistance;
        this.gridSize = gridSize;
        this.frameVerticalOffset = frameVerticalOffset;
        Location beacon = station.beaconLocation();
        if (beacon == null) {
            throw new IllegalStateException("World missing for station " + station.key());
        }
        this.world = beacon.getWorld();
        this.forward = faceToVector(station.facing());
        this.right = rightFromForward(forward);
        // grid starts at beacon + 1 when false, or beacon + 2 when true
        this.centerBottom = beacon.clone().add(0, 1 + this.frameVerticalOffset, 0).add(forward.clone().multiply(wallDistance));
        buildIndex();
        buildFrameKeys();
    }

    public List<Block> gridBlocks() {
        List<Block> blocks = new ArrayList<>(gridSize * gridSize);
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                blocks.add(gridBlock(col, row));
            }
        }
        return blocks;
    }

    public List<Block> frameBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (FrameCell cell : frameCells()) {
            blocks.add(cell.block());
        }
        return blocks;
    }

    public List<FrameCell> frameCells() {
        List<FrameCell> cells = new ArrayList<>();
        int half = gridSize / 2;
        int minX = -half - 1;
        int maxX = half + 1;
        int minY = -1;
        int maxY = gridSize;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                boolean border = x == minX || x == maxX || y == minY || y == maxY;
                if (!border) {
                    continue;
                }
                Location loc = centerBottom.clone()
                        .add(right.clone().multiply(x))
                        .add(0, y, 0);
                int col = x - minX;
                int row = y - minY;
                cells.add(new FrameCell(world.getBlockAt(loc), col, row));
            }
        }
        return cells;
    }

    public Block topCenterFrameBlock() {
        return world.getBlockAt(centerBottom.clone().add(0, gridSize, 0));
    }

    public int frameWidth() {
        return gridSize + 2;
    }

    public int frameHeight() {
        return gridSize + 2;
    }

    public List<Location> frontCelebrationLocations() {
        List<Location> locations = new ArrayList<>();
        int half = gridSize / 2;
        for (int x = -half; x <= half; x++) {
            Location loc = centerBottom.clone()
                    .add(right.clone().multiply(x))
                    .add(forward.clone().multiply(-1.0))
                    .add(0.5, (gridSize / 2.0), 0.5);
            locations.add(loc);
        }
        return locations;
    }

    public Block gridBlock(int col, int row) {
        int half = gridSize / 2;
        int xOffset = col - half;
        Location loc = centerBottom.clone()
                .add(right.clone().multiply(xOffset))
                .add(0, row, 0);
        return world.getBlockAt(loc);
    }

    public Integer toIndex(Block block) {
        return indexByBlock.get(key(block.getLocation()));
    }

    public boolean isBoardBlock(Block block) {
        return toIndex(block) != null || isFrameBlock(block);
    }

    public boolean isFrameBlock(Block block) {
        return frameKeys.contains(key(block.getLocation()));
    }

    private void buildIndex() {
        int idx = 0;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                Block block = gridBlock(col, row);
                indexByBlock.put(key(block.getLocation()), idx++);
            }
        }
    }

    private void buildFrameKeys() {
        for (Block frame : frameBlocks()) {
            frameKeys.add(key(frame.getLocation()));
        }
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static Vector faceToVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            default -> new Vector(-1, 0, 0);
        };
    }

    private static Vector rightFromForward(Vector forward) {
        return new Vector(-forward.getZ(), 0, forward.getX());
    }

    public static BlockFace cardinalFromYaw(float yaw) {
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 45 && normalized < 135) {
            return BlockFace.WEST;
        }
        if (normalized >= 135 && normalized < 225) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225 && normalized < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }
}
