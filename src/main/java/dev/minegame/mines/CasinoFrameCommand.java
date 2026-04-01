package dev.minegame.mines;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CasinoFrameCommand {
    private final MinesManager minesManager;
    private final FrameAnimator frameAnimator;

    public CasinoFrameCommand(MinesManager minesManager, FrameAnimator frameAnimator) {
        this.minesManager = minesManager;
        this.frameAnimator = frameAnimator;
    }

    public boolean execute(Player player, String[] args) {
        if (!player.hasPermission("mine.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        boolean applyAll = args[0].equalsIgnoreCase("all");
        int offset = applyAll ? 1 : 0;
        if (applyAll && args.length < 2) {
            sendUsage(player);
            return true;
        }

        StationData station = null;
        if (!applyAll) {
            station = minesManager.stationFromPlayerBeacon(player);
            if (station == null) {
                player.sendMessage(ChatColor.RED + "Stand on a beacon station to edit that station's casino frame.");
                return true;
            }
        }

        String first = args[offset];
        if (first.equalsIgnoreCase("reset")) {
            if (applyAll) {
                applyResetAll(player);
            } else {
                minesManager.saveStation(station.clearFrameAnimationOverrides(), true);
                player.sendMessage(ChatColor.GREEN + "Casino frame overrides reset for this station.");
            }
            frameAnimator.reloadFromCurrentConfig();
            return true;
        }

        if (first.equalsIgnoreCase("off")) {
            if (applyAll) {
                applyAll(player, false, null, null, null);
            } else {
                StationData updated = station.withFrameAnimation(false, station.frameAnimBlock(), station.frameAnimPattern(), station.frameAnimMode());
                minesManager.saveStation(updated, true);
                player.sendMessage(ChatColor.YELLOW + "Casino frame animation disabled for this station.");
            }
            frameAnimator.reloadFromCurrentConfig();
            return true;
        }

        if (first.equalsIgnoreCase("mode")) {
            if (args.length <= offset + 1) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /minegameadmin casinoframe " + (applyAll ? "all " : "") + "mode <idle_only|always>");
                return true;
            }
            String mode = args[offset + 1].toLowerCase();
            if (!mode.equals("idle_only") && !mode.equals("always")) {
                player.sendMessage(ChatColor.RED + "Mode must be idle_only or always.");
                return true;
            }
            if (applyAll) {
                applyAll(player, true, null, null, mode);
            } else {
                StationData updated = station.withFrameAnimation(true, station.frameAnimBlock(), station.frameAnimPattern(), mode);
                minesManager.saveStation(updated, true);
                player.sendMessage(ChatColor.GREEN + "Casino frame mode set to " + mode + " for this station.");
            }
            frameAnimator.reloadFromCurrentConfig();
            return true;
        }

        if (args.length <= offset + 1) {
            sendUsage(player);
            return true;
        }

        Material block = Material.matchMaterial(first);
        if (block == null || !block.isBlock()) {
            player.sendMessage(ChatColor.RED + "Invalid block material.");
            return true;
        }
        int pattern;
        try {
            pattern = Integer.parseInt(args[offset + 1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Pattern must be a number (1-10).");
            return true;
        }
        if (pattern < 1 || pattern > 10) {
            player.sendMessage(ChatColor.RED + "Pattern must be between 1 and 10.");
            return true;
        }

        if (applyAll) {
            applyAll(player, true, block.name(), pattern, null);
        } else {
            StationData updated = station.withFrameAnimation(true, block.name(), pattern, station.frameAnimMode());
            minesManager.saveStation(updated, true);
            player.sendMessage(ChatColor.GREEN + "Casino frame updated for this station: " + block.name() + ", pattern " + pattern + ".");
        }
        frameAnimator.reloadFromCurrentConfig();
        return true;
    }

    private void applyResetAll(Player player) {
        java.util.List<StationData> updated = new java.util.ArrayList<>();
        for (StationData station : minesManager.stations()) {
            updated.add(station.clearFrameAnimationOverrides());
        }
        minesManager.saveAllStations(updated, true);
        player.sendMessage(ChatColor.GREEN + "Casino frame overrides reset for all stations.");
    }

    private void applyAll(Player player, boolean enabled, String block, Integer pattern, String mode) {
        java.util.List<StationData> updated = new java.util.ArrayList<>();
        for (StationData station : minesManager.stations()) {
            String resolvedBlock = block != null ? block : station.frameAnimBlock();
            Integer resolvedPattern = pattern != null ? pattern : station.frameAnimPattern();
            String resolvedMode = mode != null ? mode : station.frameAnimMode();
            updated.add(station.withFrameAnimation(enabled, resolvedBlock, resolvedPattern, resolvedMode));
        }
        minesManager.saveAllStations(updated, true);
        player.sendMessage(ChatColor.GREEN + "Casino frame settings applied to all stations.");
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage:");
        player.sendMessage(ChatColor.YELLOW + "/minegameadmin casinoframe <block> <pattern 1-10>  (this station)");
        player.sendMessage(ChatColor.YELLOW + "/minegameadmin casinoframe mode <idle_only|always>   (this station)");
        player.sendMessage(ChatColor.YELLOW + "/minegameadmin casinoframe off|reset                 (this station)");
        player.sendMessage(ChatColor.YELLOW + "/minegameadmin casinoframe all <block> <pattern 1-10>");
        player.sendMessage(ChatColor.YELLOW + "/minegameadmin casinoframe all mode <idle_only|always>");
        player.sendMessage(ChatColor.YELLOW + "/minegameadmin casinoframe all off|reset");
    }
}
