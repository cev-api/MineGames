package dev.minegame.mines;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class DiceManager implements Listener {
    private static final double DIE_HALF_SIZE = 0.18D;
    private static final double TICK_GRAVITY = 0.09D;
    private static final double AIR_DRAG = 0.98D;
    private static final double BOUNCE_VERTICAL_DAMPING = 0.60D;
    private static final double BOUNCE_HORIZONTAL_DAMPING = 0.76D;
    private static final int MAX_SETTLE_TICKS = 14;

    private final MinegamePlugin plugin;
    private final DiceItemFactory itemFactory;
    private final Map<UUID, DiceInstance> activeDice = new HashMap<>();
    private final Map<UUID, Integer> lastThrowTick = new HashMap<>();
    private final org.bukkit.NamespacedKey entityKey;
    private BukkitTask task;

    public DiceManager(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.itemFactory = new DiceItemFactory(plugin);
        this.entityKey = new org.bukkit.NamespacedKey(plugin, "dice_entity");
    }

    public void start() {
        removeOrphanedEntities();
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (DiceInstance dice : new ArrayList<>(activeDice.values())) {
            remove(dice, true);
        }
        lastThrowTick.clear();
    }

    public boolean isDice(ItemStack item) {
        return itemFactory.isDice(item);
    }

    public void giveDice(Player player, int count) {
        for (int i = 0; i < count; i++) {
            giveOrDrop(player, itemFactory.create());
        }
    }

    public boolean throwDie(Player player, EquipmentSlot hand) {
        if (hand == null) {
            return false;
        }
        int currentTick = Bukkit.getCurrentTick();
        if (lastThrowTick.getOrDefault(player.getUniqueId(), Integer.MIN_VALUE) == currentTick) {
            return false;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!itemFactory.isDice(item)) {
            return false;
        }

        ItemStack thrownItem = item.clone();
        thrownItem.setAmount(1);
        if (item.getAmount() <= 1) {
            setHandItem(player, hand, null);
        } else {
            item.setAmount(item.getAmount() - 1);
            setHandItem(player, hand, item);
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location spawnLocation = eye.clone().add(direction.clone().multiply(0.58D));
        spawnLocation.add(0.0D, -0.12D, 0.0D);
        DiceInstance dice = new DiceInstance(
                entityKey,
                player.getUniqueId(),
                spawnLocation,
                direction.clone().multiply(0.52D).setY(0.38D),
                ThreadLocalRandom.current().nextInt(1, 7),
                thrownItem
        );
        try {
            dice.spawn();
            activeDice.put(dice.id(), dice);
            lastThrowTick.put(player.getUniqueId(), currentTick);
            return true;
        } catch (RuntimeException ex) {
            dice.removeEntities();
            giveOrDrop(player, thrownItem);
            plugin.getLogger().warning("Unable to spawn a physical die: " + ex.getMessage());
            return false;
        }
    }

    public boolean pickup(Entity clicked, Player player) {
        String id = clicked.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (id == null) {
            return false;
        }
        DiceInstance dice = find(id);
        if (dice == null) {
            clicked.remove();
            return true;
        }
        if (dice.state() != DiceState.FINISHED) {
            return true;
        }
        activeDice.remove(dice.id());
        dice.removeEntities();
        giveOrDrop(player, itemFactory.create());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.35F, 1.15F);
        return true;
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        String id = event.getEntity().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        DiceInstance dice = find(id);
        if (dice != null) {
            remove(dice, true);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (DiceInstance dice : new ArrayList<>(activeDice.values())) {
            if (dice.isInChunk(chunk)) {
                remove(dice, true);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (DiceInstance dice : new ArrayList<>(activeDice.values())) {
            if (dice.world().equals(event.getWorld())) {
                remove(dice, true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastThrowTick.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (DiceInstance dice : new ArrayList<>(activeDice.values())) {
            if (!dice.tick()) {
                remove(dice, true);
            }
        }
    }

    private void remove(DiceInstance dice, boolean returnItem) {
        if (activeDice.remove(dice.id()) == null) {
            return;
        }
        dice.removeEntities();
        if (returnItem) {
            Player owner = Bukkit.getPlayer(dice.ownerId());
            if (owner != null && owner.isOnline()) {
                giveOrDrop(owner, itemFactory.create());
            } else if (dice.world() != null && dice.world().isChunkLoaded(dice.chunkX(), dice.chunkZ())) {
                dice.world().dropItemNaturally(dice.location(), itemFactory.create());
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
        for (ItemStack leftover : remaining.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void setHandItem(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private DiceInstance find(String id) {
        try {
            return activeDice.get(UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void removeOrphanedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    static double dieHalfSize() {
        return DIE_HALF_SIZE;
    }

    static double gravity() {
        return TICK_GRAVITY;
    }

    static double airDrag() {
        return AIR_DRAG;
    }

    static double bounceVerticalDamping() {
        return BOUNCE_VERTICAL_DAMPING;
    }

    static double bounceHorizontalDamping() {
        return BOUNCE_HORIZONTAL_DAMPING;
    }

    static int maxSettleTicks() {
        return MAX_SETTLE_TICKS;
    }
}
