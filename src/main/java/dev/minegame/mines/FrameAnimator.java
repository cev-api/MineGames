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

public final class FrameAnimator {
    private final MinegamePlugin plugin;
    private final MinesManager minesManager;

    private boolean defaultEnabled;
    private Material defaultBlockType;
    private int defaultPattern;
    private int intervalTicks;
    private String defaultMode;
    private int tick;
    private BukkitTask task;

    public FrameAnimator(MinegamePlugin plugin, MinesManager minesManager) {
        this.plugin = plugin;
        this.minesManager = minesManager;
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
        this.defaultEnabled = plugin.getConfig().getBoolean("frame-animation.enabled", false);
        this.defaultBlockType = parseBlock(plugin.getConfig().getString("frame-animation.block"), Material.REDSTONE_LAMP);
        this.defaultPattern = clampPattern(plugin.getConfig().getInt("frame-animation.pattern", 1));
        this.defaultMode = normalizeMode(plugin.getConfig().getString("frame-animation.mode", "idle_only"));
        this.intervalTicks = Math.max(1, plugin.getConfig().getInt("frame-animation.interval-ticks", 6));

        if (task != null) {
            task.cancel();
            task = null;
        }
        this.tick = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::animateTick, 1L, intervalTicks);
    }

    private void animateTick() {
        tick++;
        double activationDistance = Math.max(0.0, plugin.getConfig().getDouble("casino-frame-activation-distance", 20.0));
        for (StationData station : minesManager.stations()) {
            BoardGeometry geometry;
            try {
                geometry = minesManager.geometryForStation(station);
            } catch (IllegalStateException ex) {
                continue;
            }
            if (!hasNearbyPlayers(geometry, activationDistance)) {
                continue;
            }
            List<BoardGeometry.FrameCell> cells = geometry.frameCells();
            int width = geometry.frameWidth();
            int height = geometry.frameHeight();
            int perimeter = Math.max(1, (2 * width) + (2 * height) - 4);
            boolean enabled = station.frameAnimEnabled() != null ? station.frameAnimEnabled() : defaultEnabled;
            if (!enabled) {
                continue;
            }
            Material blockType = parseBlock(station.frameAnimBlock(), defaultBlockType);
            int pattern = station.frameAnimPattern() != null
                    ? clampPattern(station.frameAnimPattern())
                    : defaultPattern;
            String mode = normalizeMode(station.frameAnimMode() != null ? station.frameAnimMode() : defaultMode);
            boolean active = minesManager.activeGameForStation(station) != null;
            for (BoardGeometry.FrameCell cell : cells) {
                boolean on;
                if (active && mode.equals("idle_only")) {
                    on = true;
                } else {
                    int p = perimeterIndex(cell.col(), cell.row(), width, height);
                    on = isOn(p, perimeter, tick, pattern);
                }
                applyLightState(cell.block(), blockType, on);
            }
        }
    }

    private boolean hasNearbyPlayers(BoardGeometry geometry, double activationDistance) {
        org.bukkit.block.Block center = geometry.topCenterFrameBlock();
        org.bukkit.World world = center.getWorld();
        if (world == null) {
            return false;
        }
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        double maxSq = activationDistance * activationDistance;
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(new org.bukkit.Location(world, cx, cy, cz)) <= maxSq) {
                return true;
            }
        }
        return false;
    }

    private int perimeterIndex(int col, int row, int width, int height) {
        if (row == 0) {
            return col; // top L->R
        }
        if (col == width - 1) {
            return width + (row - 1); // right top->bottom
        }
        if (row == height - 1) {
            return width + (height - 1) + (width - 1 - col); // bottom R->L
        }
        return width + (height - 1) + (width - 1) + (height - 1 - row); // left bottom->top
    }

    private boolean isOn(int position, int perimeter, int tick, int pattern) {
        int head = tick % perimeter;
        return switch (pattern) {
            case 1 -> position == head; // single runner
            case 2 -> position == head || position == (head + 2) % perimeter; // two lights with one gap
            case 3 -> ((position + tick) % 2) == 0; // alternating on/off (left-right feel)
            case 4 -> { // bounce runner
                List<Integer> seq = bounceSequence(perimeter);
                yield position == seq.get(tick % seq.size());
            }
            case 5 -> position == head || position == (head + (perimeter / 2)) % perimeter; // opposite pair
            case 6 -> { // 3-wide scanner around perimeter
                int d = Math.abs(position - head);
                int wrap = perimeter - d;
                yield Math.min(d, wrap) <= 1;
            }
            case 7 -> (position % 3) == (tick % 3); // theater chase 3-phase
            case 8 -> (position % 4) == (tick % 4); // theater chase 4-phase
            case 9 -> (tick / 2) % 2 == 0; // all flash
            case 10 -> { // wipe fill/drain
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
            return "idle_only";
        }
        String lower = raw.toLowerCase();
        if (lower.equals("always")) {
            return "always";
        }
        return "idle_only";
    }
}
