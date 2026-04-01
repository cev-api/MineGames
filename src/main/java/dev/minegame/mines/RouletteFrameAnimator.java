package dev.minegame.mines;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.scheduler.BukkitTask;

public final class RouletteFrameAnimator {
    private final MinegamePlugin plugin;
    private final RouletteManager rouletteManager;

    private boolean enabled;
    private Material blockType;
    private int pattern;
    private int intervalTicks;
    private String mode;
    private int tick;
    private BukkitTask task;

    public RouletteFrameAnimator(MinegamePlugin plugin, RouletteManager rouletteManager) {
        this.plugin = plugin;
        this.rouletteManager = rouletteManager;
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
        this.enabled = plugin.getConfig().getBoolean("roulette-frame-animation.enabled", false);
        this.blockType = parseBlock(plugin.getConfig().getString("roulette-frame-animation.block"), Material.REDSTONE_LAMP);
        this.pattern = clampPattern(plugin.getConfig().getInt("roulette-frame-animation.pattern", 1));
        this.mode = normalizeMode(plugin.getConfig().getString("roulette-frame-animation.mode", "always"));
        this.intervalTicks = Math.max(1, plugin.getConfig().getInt("roulette-frame-animation.interval-ticks", 6));

        if (task != null) {
            task.cancel();
            task = null;
        }
        this.tick = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::animateTick, 1L, intervalTicks);
    }

    private void animateTick() {
        tick++;
        for (RouletteStationData station : rouletteManager.stations()) {
            RouletteBoardGeometry geometry;
            try {
                geometry = rouletteManager.geometryForStation(station);
            } catch (IllegalStateException ex) {
                continue;
            }
            boolean stationEnabled = rouletteManager.isFrameAnimationEnabled(station);
            if (!stationEnabled) {
                continue;
            }
            Material stationBlockType = rouletteManager.frameAnimationBlock(station);
            int stationPattern = clampPattern(rouletteManager.frameAnimationPattern(station));
            String stationMode = normalizeMode(rouletteManager.frameAnimationMode(station));
            List<RouletteBoardGeometry.FrameCell> cells = geometry.frameCells();
            int width = geometry.frameWidth();
            int height = geometry.frameHeight();
            int perimeter = Math.max(1, (2 * width) + (2 * height) - 4);
            boolean betting = rouletteManager.isBettingPhase(station);
            for (RouletteBoardGeometry.FrameCell cell : cells) {
                boolean on;
                if (stationMode.equals("betting_only") && !betting) {
                    on = true;
                } else {
                    int p = perimeterIndex(cell.col(), cell.row(), width, height);
                    on = isOn(p, perimeter, tick, stationPattern);
                }
                applyLightState(cell.block(), stationBlockType, on);
            }
        }
    }

    private int perimeterIndex(int col, int row, int width, int height) {
        if (row == 0) {
            return col;
        }
        if (col == width - 1) {
            return width + (row - 1);
        }
        if (row == height - 1) {
            return width + (height - 1) + (width - 1 - col);
        }
        return width + (height - 1) + (width - 1) + (height - 1 - row);
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

    private Material parseBlock(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(raw);
        if (parsed == null || !parsed.isBlock()) {
            return fallback;
        }
        return parsed;
    }

    private int clampPattern(int raw) {
        return Math.max(1, Math.min(10, raw));
    }

    private String normalizeMode(String raw) {
        if (raw == null) {
            return "always";
        }
        String lower = raw.toLowerCase();
        if (lower.equals("betting_only")) {
            return "betting_only";
        }
        return "always";
    }
}
