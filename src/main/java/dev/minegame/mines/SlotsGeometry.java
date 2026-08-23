package dev.minegame.mines;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public final class SlotsGeometry {
    public record FrameCell(Block block, int col, int row) {}

    private final SlotStationData station;
    private final World world;
    private final Location origin;
    private final Vector right;
    private final Vector front;
    private final String leverPlacement;
    private final int reelTopRow;
    private final int reelBottomRow;
    private final Set<String> outerFrameKeys = new HashSet<>();
    private final Set<String> innerFrameKeys = new HashSet<>();
    private final Set<String> reelKeys = new HashSet<>();
    private final Set<String> machineKeys = new HashSet<>();
    private final String leverKey;
    private final String increaseButtonKey;
    private final String decreaseButtonKey;

    public SlotsGeometry(SlotStationData station, String leverPlacement) {
        this.station = station;
        this.leverPlacement = leverPlacement == null ? "front_right_middle" : leverPlacement.toLowerCase();
        Location location = station.originLocation();
        if (location == null) {
            throw new IllegalStateException("World missing for slots station " + station.key());
        }
        this.world = location.getWorld();
        this.origin = new Location(world, station.x(), station.y(), station.z());
        this.front = blockVector(station.facing());
        this.right = blockVector(rotateRight(station.facing()));
        int height = totalHeight();
        if (station.rowCount() == 1) {
            this.reelTopRow = 2;
            this.reelBottomRow = 2;
        } else {
            this.reelTopRow = 2;
            this.reelBottomRow = 3;
        }
        buildKeys();
        this.leverKey = key(leverBlock());
        this.increaseButtonKey = key(increaseButton());
        this.decreaseButtonKey = key(decreaseButton());
    }

    public int totalWidth() {
        return station.reelCount() + 2;
    }

    public int totalHeight() {
        return 4 + station.rowCount();
    }

    public int rowCount() {
        return station.rowCount();
    }

    public Block blockAt(int col, int row) {
        return world.getBlockAt(
                origin.getBlockX() + (int) (right.getX() * col),
                origin.getBlockY() + row,
                origin.getBlockZ() + (int) (right.getZ() * col)
        );
    }

    public Block reelBlock(int reelIndex, int rowIndex) {
        int row = rowIndex == 0 ? reelTopRow : reelBottomRow;
        return blockAt(reelIndex + 1, row);
    }

    public List<Block> reelBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (int row = 0; row < station.rowCount(); row++) {
            for (int i = 0; i < station.reelCount(); i++) {
                blocks.add(reelBlock(i, row));
            }
        }
        return blocks;
    }

    public List<FrameCell> reelCells() {
        List<FrameCell> cells = new ArrayList<>();
        for (int row = 0; row < station.rowCount(); row++) {
            for (int i = 0; i < station.reelCount(); i++) {
                cells.add(new FrameCell(reelBlock(i, row), i + 1, row == 0 ? reelTopRow : reelBottomRow));
            }
        }
        return cells;
    }

    public List<Block> outerFrameBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (FrameCell cell : outerFrameCells()) {
            blocks.add(cell.block());
        }
        return blocks;
    }

    public List<FrameCell> outerFrameCells() {
        List<FrameCell> cells = new ArrayList<>();
        for (int row = 0; row < totalHeight(); row++) {
            for (int col = 0; col < totalWidth(); col++) {
                boolean border = row == 0 || row == totalHeight() - 1 || col == 0 || col == totalWidth() - 1;
                if (border) {
                    cells.add(new FrameCell(blockAt(col, row), col, row));
                }
            }
        }
        return cells;
    }

    public List<FrameCell> innerFrameCells() {
        List<FrameCell> cells = new ArrayList<>();
        for (int row = 1; row < totalHeight() - 1; row++) {
            boolean reelRow = row == reelTopRow || row == reelBottomRow;
            for (int col = 1; col < totalWidth() - 1; col++) {
                if (!reelRow) {
                    cells.add(new FrameCell(blockAt(col, row), col, row));
                }
            }
        }
        return cells;
    }

    public Block leverBlock() {
        int col = leverAnchorColumn();
        int row = reelTopRow;
        Block anchor = blockAt(col, row);
        Vector offset = leverOffset();
        return world.getBlockAt(
                anchor.getX() + (int) offset.getX(),
                anchor.getY(),
                anchor.getZ() + (int) offset.getZ()
        );
    }

    public List<Block> allBlocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.addAll(outerFrameBlocks());
        for (FrameCell cell : innerFrameCells()) {
            blocks.add(cell.block());
        }
        blocks.addAll(reelBlocks());
        blocks.add(leverBlock());
        blocks.add(increaseButton());
        blocks.add(decreaseButton());
        return blocks;
    }

    public boolean isMachineBlock(Block block) {
        return machineKeys.contains(key(block));
    }

    public boolean isLeverBlock(Block block) {
        return leverKey.equals(key(block));
    }

    public boolean isIncreaseButton(Block block) { return increaseButtonKey.equals(key(block)); }
    public boolean isDecreaseButton(Block block) { return decreaseButtonKey.equals(key(block)); }
    public Block increaseButton() { return leverBlock().getRelative(BlockFace.UP); }
    public Block decreaseButton() { return leverBlock().getRelative(BlockFace.DOWN); }

    public Location centerAbove(double y) {
        double centerCol = (totalWidth() - 1) / 2.0D;
        return new Location(
                world,
                origin.getX() + (right.getX() * centerCol) + 0.5,
                origin.getY() + y,
                origin.getZ() + (right.getZ() * centerCol) + 0.5
        );
    }

    private void buildKeys() {
        for (FrameCell cell : outerFrameCells()) {
            String key = key(cell.block());
            outerFrameKeys.add(key);
            machineKeys.add(key);
        }
        for (FrameCell cell : innerFrameCells()) {
            String key = key(cell.block());
            innerFrameKeys.add(key);
            machineKeys.add(key);
        }
        for (Block block : reelBlocks()) {
            String key = key(block);
            reelKeys.add(key);
            machineKeys.add(key);
        }
        machineKeys.add(key(leverBlock()));
        machineKeys.add(increaseButtonKey);
        machineKeys.add(decreaseButtonKey);
    }

    private Vector blockVector(BlockFace face) {
        return new Vector(face.getModX(), 0, face.getModZ());
    }

    private BlockFace rotateRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    public BlockFace leverFacing() {
        Vector offset = leverOffset();
        return blockFaceForOffset(offset).getOppositeFace();
    }

    private int leverAnchorColumn() {
        if (leverPlacement.equals("middle") || leverPlacement.equals("front_middle")) {
            return totalWidth() / 2;
        }
        return totalWidth() - 1;
    }

    private Vector leverOffset() {
        if (leverPlacement.equals("right_middle")) {
            return right;
        }
        return new Vector(-front.getX(), 0, -front.getZ());
    }

    private BlockFace blockFaceForOffset(Vector offset) {
        if (offset.getX() > 0) {
            return BlockFace.EAST;
        }
        if (offset.getX() < 0) {
            return BlockFace.WEST;
        }
        if (offset.getZ() > 0) {
            return BlockFace.SOUTH;
        }
        return BlockFace.NORTH;
    }

    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
