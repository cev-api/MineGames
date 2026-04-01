package dev.minegame.mines;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

public final class ActiveGame {
    private final UUID playerId;
    private final StationData station;
    private final int mines;
    private final double wager;
    private final Set<Integer> mineIndices;
    private final Set<Integer> revealedSafe = new HashSet<>();
    private int secondsLeft;
    private BukkitTask tickerTask;

    public ActiveGame(UUID playerId, StationData station, int mines, double wager, Set<Integer> mineIndices, int secondsLeft) {
        this.playerId = playerId;
        this.station = station;
        this.mines = mines;
        this.wager = wager;
        this.mineIndices = mineIndices;
        this.secondsLeft = secondsLeft;
    }

    public UUID playerId() {
        return playerId;
    }

    public StationData station() {
        return station;
    }

    public int mines() {
        return mines;
    }

    public double wager() {
        return wager;
    }

    public boolean isMine(int index) {
        return mineIndices.contains(index);
    }

    public boolean revealSafe(int index) {
        return revealedSafe.add(index);
    }

    public int revealedSafeCount() {
        return revealedSafe.size();
    }

    public Set<Integer> revealedSafeIndices() {
        return Collections.unmodifiableSet(revealedSafe);
    }

    public int safeTargetCount(int gridSize) {
        return gridSize * gridSize - mines;
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    public void decrementSecond() {
        secondsLeft--;
    }

    public BukkitTask tickerTask() {
        return tickerTask;
    }

    public void setTickerTask(BukkitTask tickerTask) {
        this.tickerTask = tickerTask;
    }
}
