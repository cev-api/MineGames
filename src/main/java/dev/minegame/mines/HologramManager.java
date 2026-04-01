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
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String HOLOGRAM_STATION_TAG_PREFIX = "minegame_holo_station_";

    private final MinegamePlugin plugin;
    private final MinesManager minesManager;
    private final Map<String, List<UUID>> standIdsByStation = new HashMap<>();
    private final Map<String, List<String>> lastLinesByStation = new HashMap<>();
    private final NamespacedKey stationKeyDataKey;
    private BukkitTask task;

    public HologramManager(MinegamePlugin plugin, MinesManager minesManager) {
        this.plugin = plugin;
        this.minesManager = minesManager;
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
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
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
        double spacing = plugin.getConfig().getDouble("hologram.line-spacing", 0.28D);
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = findDisplayEntity(anchor.getWorld(), ids.get(i));
            if (!(entity instanceof TextDisplay display)) {
                return false;
            }
            if (!stationKey.equals(stationKeyFor(display))) {
                return false;
            }
            display.teleport(anchor.clone().add(0, -i * spacing, 0));
            display.text(component(lines.get(i)));
        }
        return true;
    }

    private void render(String stationKey, Location anchor, List<String> lines) {
        purgeDisplaysByStationKey(stationKey);
        purgeNearbyAnchorDisplays(anchor);
        deleteHologram(stationKey);
        double spacing = plugin.getConfig().getDouble("hologram.line-spacing", 0.28D);
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
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setSeeThrough(true);
                spawned.setShadowed(false);
                spawned.setDefaultBackground(false);
                spawned.setLineWidth(Integer.MAX_VALUE);
                spawned.text(component(lines.get(lineIndex)));
            });
            ids.add(stand.getUniqueId());
        }
        standIdsByStation.put(stationKey, ids);
    }

    private List<String> linesFor(StationData station) {
        ActiveGame game = minesManager.activeGameForStation(station);
        if (game == null) {
            return List.of(
                    "&6&lMINEGAME",
                    "&7Use &e/minegame &7to place a bet"
            );
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(game.playerId());
        String name = player.getName() == null ? "Player" : player.getName();
        int safeTarget = minesManager.safeTarget(game);
        double currentMult = minesManager.currentMultiplierFor(game);
        double nextMult = minesManager.nextMultiplierFor(game);
        double potential = minesManager.potentialPayoutFor(game);
        double potentialNext = minesManager.potentialNextPayoutFor(game);

        return List.of(
                "&f" + name + "'s &6&lMINEGAME",
                "&7Wager: &6$" + MONEY.format(game.wager()),
                "&7Revealed: &a" + game.revealedSafeCount() + "&7/" + safeTarget + " &7(Mines: &c" + game.mines() + "&7)",
                "&7Multiplier: &a" + MULT.format(currentMult) + "x &7-> &e" + MULT.format(nextMult) + "x",
                "&7Potential: &6$" + MONEY.format(potential) + " &7-> &e$" + MONEY.format(potentialNext),
                "&7Time Left: &f" + game.secondsLeft() + "s &7| &e/minegame cashout"
        );
    }

    private Location anchorLocation(StationData station) {
        Location beacon = station.beaconLocation();
        if (beacon == null) {
            return null;
        }
        Vector forward = faceToVector(station.facing());
        double backDistance = plugin.getConfig().getDouble("hologram.behind-beacon-distance", 1.4D);
        double baseHeight = plugin.getConfig().getDouble("hologram.base-height", 4.2D);
        return beacon.clone()
                .add(0.5, baseHeight, 0.5)
                .add(forward.multiply(-backDistance));
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
        return LEGACY.deserialize(text);
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
}
