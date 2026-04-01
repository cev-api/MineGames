package dev.minegame.mines;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HouseBalanceStorage {
    private final MinegamePlugin plugin;
    private final File file;

    private double minesBalance;
    private double minesTotalWagered;
    private double minesTotalPayout;

    private double rouletteBalance;
    private double rouletteTotalWagered;
    private double rouletteTotalPayout;

    public HouseBalanceStorage(MinegamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "house_balances.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        this.minesBalance = yaml.getDouble("minegame.balance", 0.0);
        this.minesTotalWagered = yaml.getDouble("minegame.total-wagered", 0.0);
        this.minesTotalPayout = yaml.getDouble("minegame.total-payout", 0.0);

        this.rouletteBalance = yaml.getDouble("roulette.balance", 0.0);
        this.rouletteTotalWagered = yaml.getDouble("roulette.total-wagered", 0.0);
        this.rouletteTotalPayout = yaml.getDouble("roulette.total-payout", 0.0);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("minegame.balance", minesBalance);
        yaml.set("minegame.total-wagered", minesTotalWagered);
        yaml.set("minegame.total-payout", minesTotalPayout);
        yaml.set("roulette.balance", rouletteBalance);
        yaml.set("roulette.total-wagered", rouletteTotalWagered);
        yaml.set("roulette.total-payout", rouletteTotalPayout);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save house_balances.yml: " + ex.getMessage());
        }
    }

    public void recordMinesResult(double wager, double payout) {
        double cleanWager = Math.max(0.0, wager);
        double cleanPayout = Math.max(0.0, payout);
        minesBalance += cleanWager - cleanPayout;
        minesTotalWagered += cleanWager;
        minesTotalPayout += cleanPayout;
        save();
    }

    public void recordRouletteResult(double wager, double payout) {
        double cleanWager = Math.max(0.0, wager);
        double cleanPayout = Math.max(0.0, payout);
        rouletteBalance += cleanWager - cleanPayout;
        rouletteTotalWagered += cleanWager;
        rouletteTotalPayout += cleanPayout;
        save();
    }

    public double withdrawMines(double amount) {
        double allowed = Math.max(0.0, Math.min(amount, minesBalance));
        minesBalance -= allowed;
        save();
        return allowed;
    }

    public void refundMinesWithdrawal(double amount) {
        if (amount <= 0.0) {
            return;
        }
        minesBalance += amount;
        save();
    }

    public double withdrawRoulette(double amount) {
        double allowed = Math.max(0.0, Math.min(amount, rouletteBalance));
        rouletteBalance -= allowed;
        save();
        return allowed;
    }

    public void refundRouletteWithdrawal(double amount) {
        if (amount <= 0.0) {
            return;
        }
        rouletteBalance += amount;
        save();
    }

    public double minesBalance() {
        return minesBalance;
    }

    public double minesTotalWagered() {
        return minesTotalWagered;
    }

    public double minesTotalPayout() {
        return minesTotalPayout;
    }

    public double rouletteBalance() {
        return rouletteBalance;
    }

    public double rouletteTotalWagered() {
        return rouletteTotalWagered;
    }

    public double rouletteTotalPayout() {
        return rouletteTotalPayout;
    }
}
