package dev.minegame.mines;

import java.util.Objects;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinegamePlugin extends JavaPlugin {
    private Economy economy;
    private StationStorage stationStorage;
    private RouletteStationStorage rouletteStationStorage;
    private BlockSnapshotStorage minesRestoreStorage;
    private BlockSnapshotStorage rouletteRestoreStorage;
    private HouseBalanceStorage houseBalanceStorage;
    private MinesManager minesManager;
    private RouletteManager rouletteManager;
    private HologramManager hologramManager;
    private FrameAnimator frameAnimator;
    private RouletteFrameAnimator rouletteFrameAnimator;

    @Override
    public void onEnable() {
        migrateLegacyDataFolder();
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider not found, disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.stationStorage = new StationStorage(this);
        stationStorage.load();
        this.rouletteStationStorage = new RouletteStationStorage(this);
        rouletteStationStorage.load();
        this.minesRestoreStorage = new BlockSnapshotStorage(this, "mines_restore.yml");
        minesRestoreStorage.load();
        this.rouletteRestoreStorage = new BlockSnapshotStorage(this, "roulette_restore.yml");
        rouletteRestoreStorage.load();
        this.houseBalanceStorage = new HouseBalanceStorage(this);
        houseBalanceStorage.load();

        this.minesManager = new MinesManager(this, economy, stationStorage, minesRestoreStorage, houseBalanceStorage);
        this.rouletteManager = new RouletteManager(this, economy, rouletteStationStorage, rouletteRestoreStorage, houseBalanceStorage);
        this.hologramManager = new HologramManager(this, minesManager);
        this.frameAnimator = new FrameAnimator(this, minesManager);
        this.rouletteFrameAnimator = new RouletteFrameAnimator(this, rouletteManager);
        hologramManager.start();
        frameAnimator.start();
        rouletteFrameAnimator.start();
        rouletteManager.start();
        CasinoFrameCommand casinoFrameCommand = new CasinoFrameCommand(minesManager, frameAnimator);
        RouletteCasinoFrameCommand rouletteCasinoFrameCommand = new RouletteCasinoFrameCommand(rouletteManager);

        MinesTabCompleter tabCompleter = new MinesTabCompleter();

        Objects.requireNonNull(getCommand("minegame")).setExecutor(new MineCommand(minesManager));
        Objects.requireNonNull(getCommand("minegame")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("minegameadmin")).setExecutor(new MineAdminCommand(minesManager, casinoFrameCommand));
        Objects.requireNonNull(getCommand("minegameadmin")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("roulette")).setExecutor(new RouletteCommand(rouletteManager));
        Objects.requireNonNull(getCommand("roulette")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("rouletteadmin")).setExecutor(new RouletteAdminCommand(rouletteManager, rouletteCasinoFrameCommand));
        Objects.requireNonNull(getCommand("rouletteadmin")).setTabCompleter(tabCompleter);

        getServer().getPluginManager().registerEvents(new MinesListener(minesManager), this);
        getServer().getPluginManager().registerEvents(new PlayerExitListener(minesManager), this);
        getServer().getPluginManager().registerEvents(new RouletteListener(rouletteManager), this);
    }

    @Override
    public void onDisable() {
        if (minesManager != null) {
            minesManager.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.shutdown();
        }
        if (frameAnimator != null) {
            frameAnimator.shutdown();
        }
        if (rouletteFrameAnimator != null) {
            rouletteFrameAnimator.shutdown();
        }
        if (rouletteManager != null) {
            rouletteManager.shutdown();
        }
        if (stationStorage != null) {
            stationStorage.save();
        }
        if (rouletteStationStorage != null) {
            rouletteStationStorage.save();
        }
        if (minesRestoreStorage != null) {
            minesRestoreStorage.save();
        }
        if (rouletteRestoreStorage != null) {
            rouletteRestoreStorage.save();
        }
        if (houseBalanceStorage != null) {
            houseBalanceStorage.save();
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void migrateLegacyDataFolder() {
        File current = getDataFolder();
        if (current.exists()) {
            return;
        }
        File parent = getDataFolder().getParentFile();
        if (parent == null) {
            return;
        }
        File legacy = new File(parent, "MineGame");
        if (!legacy.exists() || !legacy.isDirectory()) {
            return;
        }
        try {
            copyDirectory(legacy.toPath(), current.toPath());
            getLogger().info("Migrated data folder from MineGame to MineGames.");
        } catch (IOException ex) {
            getLogger().warning("Failed to migrate legacy MineGame data folder: " + ex.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path out = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
