package dev.minegame.mines;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class HologramManager {
    public static final String HOLOGRAM_TAG = "minegame_holo";

    private static final DecimalFormat MONEY = new DecimalFormat("0.00");
    private static final DecimalFormat MULT = new DecimalFormat("0.000");
    static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String HOLOGRAM_STATION_TAG_PREFIX = "minegame_holo_station_";

    private final MinegamePlugin plugin;
    private final MinesManager minesManager;
    private final Map<String, List<UUID>> standIdsByStation = new HashMap<>();
    private final Map<String, List<String>> lastLinesByStation = new HashMap<>();
    private final NamespacedKey stationKeyDataKey;
    private final HologramPlacementStorage placementStorage;
    private BukkitTask task;

    public HologramManager(MinegamePlugin plugin, MinesManager minesManager, HologramPlacementStorage placementStorage) {
        this.plugin = plugin;
        this.minesManager = minesManager;
        this.placementStorage = placementStorage;
        this.stationKeyDataKey = new NamespacedKey(plugin, "station_key");
    }

    public void start() {
        removeOrphanDisplays();
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("minegame.hologram.enabled", true)) {
            clearAll();
            return;
        }

        Set<String> activeKeys = new HashSet<>();
        for (StationData station : minesManager.stations()) {
            activeKeys.add(station.key());
            updateStation(station);
        }

        List<String> toRemove = new ArrayList<>();
        for (String key : standIdsByStation.keySet()) {
            if (!activeKeys.contains(key)) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            deleteHologram(key);
        }
    }

    private void updateStation(StationData station) {
        Location anchor = anchorLocation(station);
        if (anchor == null) {
            return;
        }
        double viewRange = plugin.getConfig().getDouble("minegame.hologram.view-range", 8.0D);
        if (!hasNearbyViewer(anchor, viewRange)) {
            deleteHologram(station.key());
            return;
        }
        List<String> lines = linesFor(station);
        if (updateExisting(station.key(), anchor, lines)) {
            lastLinesByStation.put(station.key(), lines);
            return;
        }
        render(station.key(), anchor, lines);
        lastLinesByStation.put(station.key(), lines);
    }

    private boolean updateExisting(String stationKey, Location anchor, List<String> lines) {
        List<UUID> ids = standIdsByStation.get(stationKey);
        if (ids == null || ids.size() != lines.size()) {
            return false;
        }
        double spacing = plugin.getConfig().getDouble("minegame.hologram.line-spacing", 0.28D);
        float viewRange = (float) plugin.getConfig().getDouble("minegame.hologram.view-range", 8.0D);
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = findDisplayEntity(anchor.getWorld(), ids.get(i));
            if (!(entity instanceof TextDisplay display)) {
                return false;
            }
            if (!stationKey.equals(stationKeyFor(display))) {
                return false;
            }
            display.teleport(anchor.clone().add(0, -i * spacing, 0));
            display.setRotation(anchor.getYaw(), anchor.getPitch());
            display.setBillboard(placementStorage.get("minegame", stationKey) == null ? Display.Billboard.CENTER : Display.Billboard.FIXED);
            display.setSeeThrough(plugin.getConfig().getBoolean("hologram.see-through-walls", true));
            display.setViewRange(viewRange);
            HologramStyle.apply(plugin, display);
            display.text(HologramStyle.text(plugin, lines.get(i)));
        }
        return true;
    }

    private void render(String stationKey, Location anchor, List<String> lines) {
        purgeDisplaysByStationKey(stationKey);
        purgeNearbyAnchorDisplays(anchor);
        deleteHologram(stationKey);
        double spacing = plugin.getConfig().getDouble("minegame.hologram.line-spacing", 0.28D);
        float viewRange = (float) plugin.getConfig().getDouble("minegame.hologram.view-range", 8.0D);
        List<UUID> ids = new ArrayList<>();
        String stationTag = stationTag(stationKey);
        for (int i = 0; i < lines.size(); i++) {
            Location loc = anchor.clone().add(0, -i * spacing, 0);
            int lineIndex = i;
            TextDisplay stand = loc.getWorld().spawn(loc, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.addScoreboardTag(HOLOGRAM_TAG);
                spawned.addScoreboardTag(stationTag);
                spawned.getPersistentDataContainer().set(stationKeyDataKey, PersistentDataType.STRING, stationKey);
                spawned.setBillboard(placementStorage.get("minegame", stationKey) == null ? Display.Billboard.CENTER : Display.Billboard.FIXED);
                spawned.setRotation(anchor.getYaw(), anchor.getPitch());
                spawned.setSeeThrough(plugin.getConfig().getBoolean("hologram.see-through-walls", true));
                spawned.setShadowed(false);
                HologramStyle.apply(plugin, spawned);
                spawned.setLineWidth(Integer.MAX_VALUE);
                spawned.setViewRange(viewRange);
                spawned.text(HologramStyle.text(plugin, lines.get(lineIndex)));
            });
            ids.add(stand.getUniqueId());
        }
        standIdsByStation.put(stationKey, ids);
    }

    private List<String> linesFor(StationData station) {
        ActiveGame game = minesManager.activeGameForStation(station);
        if (game == null) {
            return configuredLines("messages.minegame.hologram.idle-lines",
                    "&6&lMINEGAME",
                    "&7Use &e/minegame &7to place a bet");
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(game.playerId());
        String name = player.getName() == null ? "Player" : player.getName();
        int safeTarget = minesManager.safeTarget(game);
        double currentMult = minesManager.currentMultiplierFor(game);
        double nextMult = minesManager.nextMultiplierFor(game);
        double potential = minesManager.potentialPayoutFor(game);
        double potentialNext = minesManager.potentialNextPayoutFor(game);

        return replaceLines(configuredLines("messages.minegame.hologram.active-lines",
                        "&f%player%'s &6&lMINEGAME",
                        "&7Wager: &6$%wager%",
                        "&7Revealed: &a%revealed%&7/%target% &7(Mines: &c%mines%&7)",
                        "&7Multiplier: &a%current_mult%x &7-> &e%next_mult%x",
                        "&7Potential: &6$%potential% &7-> &e$%next_potential%",
                        "&7Time Left: &f%seconds_left%s &7| &e/minegame cashout"),
                Map.of(
                        "%player%", name,
                        "%wager%", MONEY.format(game.wager()),
                        "%revealed%", String.valueOf(game.revealedSafeCount()),
                        "%target%", String.valueOf(safeTarget),
                        "%mines%", String.valueOf(game.mines()),
                        "%current_mult%", MULT.format(currentMult),
                        "%next_mult%", MULT.format(nextMult),
                        "%potential%", MONEY.format(potential),
                        "%next_potential%", MONEY.format(potentialNext),
                        "%seconds_left%", String.valueOf(game.secondsLeft())
                ));
    }

    private List<String> configuredLines(String path, String... fallback) {
        List<String> lines = plugin.getConfig().getStringList(path);
        return lines.isEmpty() ? List.of(fallback) : List.copyOf(lines);
    }

    private List<String> replaceLines(List<String> templates, Map<String, String> vars) {
        List<String> lines = new ArrayList<>(templates.size());
        for (String template : templates) {
            String line = template;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                line = line.replace(entry.getKey(), entry.getValue());
            }
            lines.add(line);
        }
        return lines;
    }

    private Location anchorLocation(StationData station) {
        Location beacon = station.beaconLocation();
        if (beacon == null) {
            return null;
        }
        Vector forward = faceToVector(station.facing());
        Location placed = placementStorage.get("minegame", station.key());
        if (placed != null) return placed;
        if (!plugin.getConfig().getBoolean("minegame.hologram.affix-to-wall", false)) {
            return beacon.clone().add(0.5, 2.2, 0.5);
        }
        int wallDistance = plugin.getConfig().getInt("minegame.board.wall-distance", 4);
        int gridSize = plugin.getConfig().getInt("minegame.board.grid-size", 5);
        int frameOffset = plugin.getConfig().getBoolean("minegame.board.frame-one-higher", true) ? 1 : 0;
        double wallHeight = 2.0D + gridSize + frameOffset;
        return beacon.clone().add(0.5, wallHeight, 0.5).add(forward.multiply(wallDistance - 0.25D));
    }

    private Vector faceToVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            default -> new Vector(-1, 0, 0);
        };
    }

    private void deleteHologram(String stationKey) {
        purgeDisplaysByStationKey(stationKey);
        List<UUID> ids = standIdsByStation.remove(stationKey);
        lastLinesByStation.remove(stationKey);
        if (ids == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (UUID id : ids) {
                Entity entity = findDisplayEntity(world, id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
    }

    private void clearAll() {
        for (String key : new ArrayList<>(standIdsByStation.keySet())) {
            deleteHologram(key);
        }
        removeOrphanDisplays();
    }

    private void removeOrphanDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private Entity findDisplayEntity(World world, UUID id) {
        if (world == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity instanceof TextDisplay && entity.getWorld().equals(world)) {
            return entity;
        }
        return null;
    }

    private Component component(String text) {
        return HologramStyle.text(plugin, text);
    }

    private void purgeDisplaysByStationKey(String stationKey) {
        String stationTag = stationTag(stationKey);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay display)) {
                    continue;
                }
                if (!display.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                    continue;
                }
                if (display.getScoreboardTags().contains(stationTag) || stationKey.equals(stationKeyFor(display))) {
                    display.remove();
                }
            }
        }
    }

    private void purgeNearbyAnchorDisplays(Location anchor) {
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        Collection<Entity> nearby = anchor.getWorld().getNearbyEntities(anchor, 1.8, 3.0, 1.8);
        for (Entity entity : nearby) {
            if (entity instanceof TextDisplay display && display.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                display.remove();
            }
        }
    }

    private String stationTag(String stationKey) {
        return HOLOGRAM_STATION_TAG_PREFIX + Integer.toHexString(stationKey.hashCode());
    }

    private String stationKeyFor(TextDisplay display) {
        return display.getPersistentDataContainer().get(stationKeyDataKey, PersistentDataType.STRING);
    }

    private boolean hasNearbyViewer(Location anchor, double range) {
        if (anchor == null || anchor.getWorld() == null) {
            return false;
        }
        double maxSq = range * range;
        for (org.bukkit.entity.Player player : anchor.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(anchor) <= maxSq) {
                return true;
            }
        }
        return false;
    }
}
