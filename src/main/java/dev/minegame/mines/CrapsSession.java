package dev.minegame.mines;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

final class CrapsSession {
    private final UUID id = UUID.randomUUID();
    private final UUID playerId;
    private final double wager;
    private final String color;
    private final Map<UUID, DiceInstance> dice = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> dieItems = new LinkedHashMap<>();
    private int point;
    private int lastActivityTick;
    private boolean resolving;

    CrapsSession(UUID playerId, double wager, String color, int currentTick) {
        this.playerId = playerId;
        this.wager = wager;
        this.color = color;
        this.lastActivityTick = currentTick;
    }

    UUID id() {
        return id;
    }

    UUID playerId() {
        return playerId;
    }

    double wager() {
        return wager;
    }

    String color() {
        return color;
    }

    Map<UUID, DiceInstance> dice() {
        return dice;
    }

    void setDieItem(int dieNumber, ItemStack item) {
        dieItems.put(dieNumber, item.clone());
    }

    ItemStack dieItem(int dieNumber) {
        ItemStack item = dieItems.get(dieNumber);
        return item == null ? null : item.clone();
    }

    int point() {
        return point;
    }

    void setPoint(int point) {
        this.point = point;
    }

    int lastActivityTick() {
        return lastActivityTick;
    }

    void touch(int currentTick) {
        this.lastActivityTick = currentTick;
    }

    boolean resolving() {
        return resolving;
    }

    void setResolving(boolean resolving) {
        this.resolving = resolving;
    }
}
