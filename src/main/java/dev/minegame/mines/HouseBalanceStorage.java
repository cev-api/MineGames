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

    private double fightsBalance;
    private double fightsTotalWagered;
    private double fightsTotalPayout;

    private double slotsBalance;
    private double slotsTotalWagered;
    private double slotsTotalPayout;

    private double crapsBalance;
    private double crapsTotalWagered;
    private double crapsTotalPayout;

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

        this.fightsBalance = yaml.getDouble("fights.balance", 0.0);
        this.fightsTotalWagered = yaml.getDouble("fights.total-wagered", 0.0);
        this.fightsTotalPayout = yaml.getDouble("fights.total-payout", 0.0);

        this.slotsBalance = yaml.getDouble("slots.balance", 0.0);
        this.slotsTotalWagered = yaml.getDouble("slots.total-wagered", 0.0);
        this.slotsTotalPayout = yaml.getDouble("slots.total-payout", 0.0);

        this.crapsBalance = yaml.getDouble("craps.balance", 0.0);
        this.crapsTotalWagered = yaml.getDouble("craps.total-wagered", 0.0);
        this.crapsTotalPayout = yaml.getDouble("craps.total-payout", 0.0);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("minegame.balance", minesBalance);
        yaml.set("minegame.total-wagered", minesTotalWagered);
        yaml.set("minegame.total-payout", minesTotalPayout);
        yaml.set("roulette.balance", rouletteBalance);
        yaml.set("roulette.total-wagered", rouletteTotalWagered);
        yaml.set("roulette.total-payout", rouletteTotalPayout);
        yaml.set("fights.balance", fightsBalance);
        yaml.set("fights.total-wagered", fightsTotalWagered);
        yaml.set("fights.total-payout", fightsTotalPayout);
        yaml.set("slots.balance", slotsBalance);
        yaml.set("slots.total-wagered", slotsTotalWagered);
        yaml.set("slots.total-payout", slotsTotalPayout);
        yaml.set("craps.balance", crapsBalance);
        yaml.set("craps.total-wagered", crapsTotalWagered);
        yaml.set("craps.total-payout", crapsTotalPayout);
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

    public void recordFightsResult(double wager, double payout) {
        double cleanWager = Math.max(0.0, wager);
        double cleanPayout = Math.max(0.0, payout);
        fightsBalance += cleanWager - cleanPayout;
        fightsTotalWagered += cleanWager;
        fightsTotalPayout += cleanPayout;
        save();
    }

    public void recordSlotsResult(double wager, double payout) {
        double cleanWager = Math.max(0.0, wager);
        double cleanPayout = Math.max(0.0, payout);
        slotsBalance += cleanWager - cleanPayout;
        slotsTotalWagered += cleanWager;
        slotsTotalPayout += cleanPayout;
        save();
    }

    public void recordCrapsResult(double wager, double payout) {
        double cleanWager = Math.max(0.0, wager);
        double cleanPayout = Math.max(0.0, payout);
        crapsBalance += cleanWager - cleanPayout;
        crapsTotalWagered += cleanWager;
        crapsTotalPayout += cleanPayout;
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

    public double withdrawFights(double amount) {
        double allowed = Math.max(0.0, Math.min(amount, fightsBalance));
        fightsBalance -= allowed;
        save();
        return allowed;
    }

    public void refundFightsWithdrawal(double amount) {
        if (amount <= 0.0) return;
        fightsBalance += amount;
        save();
    }

    public double withdrawSlots(double amount) {
        double allowed = Math.max(0.0, Math.min(amount, slotsBalance));
        slotsBalance -= allowed;
        save();
        return allowed;
    }

    public void refundSlotsWithdrawal(double amount) {
        if (amount <= 0.0) {
            return;
        }
        slotsBalance += amount;
        save();
    }

    public double withdrawCraps(double amount) {
        double allowed = Math.max(0.0, Math.min(amount, crapsBalance));
        crapsBalance -= allowed;
        save();
        return allowed;
    }

    public void refundCrapsWithdrawal(double amount) {
        if (amount <= 0.0) {
            return;
        }
        crapsBalance += amount;
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

    public double fightsBalance() { return fightsBalance; }
    public double fightsTotalWagered() { return fightsTotalWagered; }
    public double fightsTotalPayout() { return fightsTotalPayout; }

    public double slotsBalance() {
        return slotsBalance;
    }

    public double slotsTotalWagered() {
        return slotsTotalWagered;
    }

    public double slotsTotalPayout() {
        return slotsTotalPayout;
    }

    public double crapsBalance() {
        return crapsBalance;
    }

    public double crapsTotalWagered() {
        return crapsTotalWagered;
    }

    public double crapsTotalPayout() {
        return crapsTotalPayout;
    }
}
