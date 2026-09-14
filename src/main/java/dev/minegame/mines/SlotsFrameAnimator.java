package dev.minegame.mines;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.scheduler.BukkitTask;

public final class SlotsFrameAnimator {
    private final MinegamePlugin plugin;
    private final SlotsManager slotsManager;

    private int tick;
    private BukkitTask task;

    public SlotsFrameAnimator(MinegamePlugin plugin, SlotsManager slotsManager) {
        this.plugin = plugin;
        this.slotsManager = slotsManager;
    }

    public void start() {
        reloadFromCurrentConfig();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reloadFromCurrentConfig() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        this.tick = 0;
        int intervalTicks = Math.max(1, plugin.getConfig().getInt("slots.frame-animation.interval-ticks", 6));
        task = PlatformScheduler.runTaskTimer(plugin, this::animateTick, 1L, intervalTicks);
    }

    private void animateTick() {
        tick++;
        double activationDistance = Math.max(0.0, plugin.getConfig().getDouble("slots.activation-distance-from-frame", 20.0));
        for (SlotStationData station : slotsManager.stations()) {
            SlotsGeometry geometry;
            try {
                geometry = slotsManager.geometryForStation(station);
            } catch (IllegalStateException ex) {
                continue;
            }
            if (!hasNearbyPlayers(geometry, activationDistance)) {
                continue;
            }
            if (!slotsManager.isFrameAnimationEnabled(station)) {
                continue;
            }
            if (slotsManager.isStationSpinning(station)) {
                continue;
            }
            Material blockType = slotsManager.frameAnimationBlock(station);
            int pattern = clampPattern(slotsManager.frameAnimationPattern(station));
            String mode = normalizeMode(slotsManager.frameAnimationMode(station));
            boolean active = slotsManager.isStationActive(station);
            List<SlotsGeometry.FrameCell> cells = geometry.innerFrameCells();
            int width = station.reelCount();
            int perimeter = Math.max(1, width * 2);
            for (SlotsGeometry.FrameCell cell : cells) {
                boolean on;
                if (active && mode.equals("idle_only")) {
                    on = true;
                } else {
                    int p = perimeterIndex(cell.col() - 1, cell.row(), width);
                    on = isOn(p, perimeter, tick, pattern);
                }
                applyLightState(cell.block(), blockType, on);
            }
        }
    }

    private boolean hasNearbyPlayers(SlotsGeometry geometry, double activationDistance) {
        org.bukkit.Location center = geometry.centerAbove(2.5);
        org.bukkit.World world = center.getWorld();
        if (world == null) {
            return false;
        }
        double maxSq = activationDistance * activationDistance;
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= maxSq) {
                return true;
            }
        }
        return false;
    }

    private int perimeterIndex(int reelIndex, int row, int width) {
        if (row == 1) {
            return reelIndex;
        }
        return width + (width - 1 - reelIndex);
    }

    private boolean isOn(int position, int perimeter, int tick, int pattern) {
        int head = tick % perimeter;
        return switch (pattern) {
            case 1 -> position == head;
            case 2 -> position == head || position == (head + 2) % perimeter;
            case 3 -> ((position + tick) % 2) == 0;
            case 4 -> {
                List<Integer> seq = bounceSequence(perimeter);
                yield position == seq.get(tick % seq.size());
            }
            case 5 -> position == head || position == (head + (perimeter / 2)) % perimeter;
            case 6 -> {
                int d = Math.abs(position - head);
                int wrap = perimeter - d;
                yield Math.min(d, wrap) <= 1;
            }
            case 7 -> (position % 3) == (tick % 3);
            case 8 -> (position % 4) == (tick % 4);
            case 9 -> (tick / 2) % 2 == 0;
            case 10 -> {
                int phase = tick % (perimeter * 2);
                if (phase < perimeter) {
                    yield position <= phase;
                }
                yield position > (phase - perimeter);
            }
            default -> position == head;
        };
    }

    private List<Integer> bounceSequence(int length) {
        List<Integer> seq = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            seq.add(i);
            seq.add(i);
        }
        for (int i = length - 2; i >= 1; i--) {
            seq.add(i);
            seq.add(i);
        }
        return seq;
    }

    private void applyLightState(Block block, Material type, boolean on) {
        if (block.getType() != type) {
            block.setType(type, false);
        }
        BlockData data = block.getBlockData();
        boolean changed = false;
        if (data instanceof Lightable lightable) {
            lightable.setLit(on);
            changed = true;
        }
        if (data instanceof Powerable powerable) {
            powerable.setPowered(on);
            changed = true;
        }
        if (changed) {
            block.setBlockData(data, false);
        }
    }

    private int clampPattern(int raw) {
        return Math.max(1, Math.min(10, raw));
    }

    private String normalizeMode(String raw) {
        if (raw == null) {
            return "idle_only";
        }
        return raw.equalsIgnoreCase("always") ? "always" : "idle_only";
    }
}
