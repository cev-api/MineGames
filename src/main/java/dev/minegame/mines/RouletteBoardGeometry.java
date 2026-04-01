package dev.minegame.mines;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class RouletteBoardGeometry {
    public record FrameCell(Block block, int col, int row) {}
    private final RouletteStationData station;
    private final int size;
    private final World world;
    private final Location center;
    private final int half;
    private final Set<String> boardKeys = new HashSet<>();
    private final Set<String> frameKeys = new HashSet<>();

    public RouletteBoardGeometry(RouletteStationData station, int size) {
        this.station = station;
        this.size = size;
        this.half = size / 2;
        Location location = station.centerLocation();
        if (location == null) {
            throw new IllegalStateException("World missing for roulette station " + station.key());
        }
        this.world = location.getWorld();
        this.center = new Location(world, station.x(), station.y(), station.z());
        buildKeys();
    }

    public List<Block> boardBlocks() {
        List<Block> blocks = new ArrayList<>(size * size);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                blocks.add(cellBlock(col, row));
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
        int min = -half - 1;
        int max = min + size + 1;
        List<FrameCell> cells = new ArrayList<>();
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                boolean border = x == min || x == max || z == min || z == max;
                if (!border) {
                    continue;
                }
                Block block = world.getBlockAt(center.clone().add(x, 0, z));
                int col = x - min;
                int row = z - min;
                cells.add(new FrameCell(block, col, row));
            }
        }
        return cells;
    }

    public Block cellBlock(int col, int row) {
        int start = -half;
        int xOffset = start + col;
        int zOffset = start + row;
        return world.getBlockAt(center.clone().add(xOffset, 0, zOffset));
    }

    public int toIndex(Block block) {
        int start = -half;
        int xOffset = block.getX() - center.getBlockX();
        int zOffset = block.getZ() - center.getBlockZ();
        int col = xOffset - start;
        int row = zOffset - start;
        if (row < 0 || row >= size || col < 0 || col >= size || block.getY() != center.getBlockY()) {
            return -1;
        }
        return row * size + col;
    }

    public boolean isBoardBlock(Block block) {
        return boardKeys.contains(key(block));
    }

    public boolean isFrameBlock(Block block) {
        return frameKeys.contains(key(block));
    }

    public Location centerAbove(double y) {
        return center.clone().add(0.5, y, 0.5);
    }

    public int frameWidth() {
        return size + 2;
    }

    public int frameHeight() {
        return size + 2;
    }

    public int size() {
        return size;
    }

    public RouletteStationData station() {
        return station;
    }

    private void buildKeys() {
        for (Block block : boardBlocks()) {
            boardKeys.add(key(block));
        }
        for (Block block : frameBlocks()) {
            frameKeys.add(key(block));
        }
    }

    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
