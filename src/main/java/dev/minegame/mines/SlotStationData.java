package dev.minegame.mines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

public record SlotStationData(
        String worldName, int x, int y, int z, BlockFace facing,
        int reelCount, int rowCount,
        String outerFrameBlock, String innerFrameBlock, String winningBlock,
        Boolean frameAnimEnabled, String frameAnimBlock, Integer frameAnimPattern,
        String frameAnimMode, Integer boardSize, Double costPerSpin,
        Boolean betButtonsEnabled, String betButtonMaterial, Double betAdjustPercent,
        Double currentBet, boolean shelfMode, int shelfCount
) {
    public SlotStationData {
        if (outerFrameBlock != null) outerFrameBlock = outerFrameBlock.toUpperCase();
        if (innerFrameBlock != null) innerFrameBlock = innerFrameBlock.toUpperCase();
        if (winningBlock != null) winningBlock = winningBlock.toUpperCase();
        if (frameAnimBlock != null) frameAnimBlock = frameAnimBlock.toUpperCase();
        if (betButtonMaterial != null) betButtonMaterial = betButtonMaterial.toUpperCase();
        if (frameAnimMode != null) frameAnimMode = frameAnimMode.toLowerCase();
        if (shelfMode) { shelfCount = Math.max(1, Math.min(32, shelfCount)); reelCount = Math.max(3, Math.min(96, reelCount)); rowCount = Math.max(1, Math.min(16, rowCount)); } else { shelfCount = 0; reelCount = Math.max(3, Math.min(8, reelCount)); rowCount = Math.max(1, Math.min(2, rowCount)); }
        if (boardSize != null) boardSize = Math.max(2, boardSize);
        if (costPerSpin != null) costPerSpin = Math.max(0.0, costPerSpin);
        if (betAdjustPercent != null) betAdjustPercent = Math.max(0.0, Math.min(100.0, betAdjustPercent));
        if (currentBet != null) currentBet = Math.max(0.01, currentBet);
    }

    public SlotStationData(String worldName, int x, int y, int z, BlockFace facing, int reelCount) {
        this(worldName, x, y, z, facing, reelCount, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, false, 0);
    }

    public String key() { return worldName + ":" + x + ":" + y + ":" + z; }
    public Location originLocation() { World world = Bukkit.getWorld(worldName); return world == null ? null : new Location(world, x, y, z); }

    private SlotStationData copy(String outer, String inner, String winning, Boolean animEnabled, String animBlock, Integer animPattern, String animMode, Integer size, Double cost, Boolean buttons, String buttonMaterial, Double percent, Double bet) {
        return new SlotStationData(worldName, x, y, z, facing, reelCount, rowCount, outer, inner, winning, animEnabled, animBlock, animPattern, animMode, size, cost, buttons, buttonMaterial, percent, bet, shelfMode, shelfCount);
    }

    public SlotStationData withBoardMaterials(String outer, String inner, String winning) { return copy(outer, inner, winning, frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, boardSize, costPerSpin, betButtonsEnabled, betButtonMaterial, betAdjustPercent, currentBet); }
    public SlotStationData clearBoardMaterialOverrides() { return withBoardMaterials(null, null, null); }
    public SlotStationData withFrameAnimation(Boolean enabled, String block, Integer pattern, String mode) { return copy(outerFrameBlock, innerFrameBlock, winningBlock, enabled, block, pattern, mode, boardSize, costPerSpin, betButtonsEnabled, betButtonMaterial, betAdjustPercent, currentBet); }
    public SlotStationData clearFrameAnimationOverrides() { return withFrameAnimation(null, null, null, null); }
    public SlotStationData withDimensions(int reels, int rows) { return new SlotStationData(worldName, x, y, z, facing, reels, rows, outerFrameBlock, innerFrameBlock, winningBlock, frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, boardSize, costPerSpin, betButtonsEnabled, betButtonMaterial, betAdjustPercent, currentBet, shelfMode, shelfCount); }
    public SlotStationData withBoardSize(Integer size) { return copy(outerFrameBlock, innerFrameBlock, winningBlock, frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, size, costPerSpin, betButtonsEnabled, betButtonMaterial, betAdjustPercent, currentBet); }
    public SlotStationData clearBoardSizeOverride() { return withBoardSize(null); }
    public SlotStationData withCostPerSpin(Double price) { return copy(outerFrameBlock, innerFrameBlock, winningBlock, frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, boardSize, price, betButtonsEnabled, betButtonMaterial, betAdjustPercent, currentBet); }
    public SlotStationData clearCostPerSpinOverride() { return withCostPerSpin(null); }
    public SlotStationData withBetSettings(Boolean enabled, String material, Double percent) { return copy(outerFrameBlock, innerFrameBlock, winningBlock, frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, boardSize, costPerSpin, enabled, material, percent, currentBet); }
    public SlotStationData clearBetSettingsOverrides() { return withBetSettings(null, null, null); }
    public SlotStationData withCurrentBet(Double bet) { return copy(outerFrameBlock, innerFrameBlock, winningBlock, frameAnimEnabled, frameAnimBlock, frameAnimPattern, frameAnimMode, boardSize, costPerSpin, betButtonsEnabled, betButtonMaterial, betAdjustPercent, bet); }
}