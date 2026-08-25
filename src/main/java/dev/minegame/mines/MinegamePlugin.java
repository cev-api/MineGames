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
    private SlotStationStorage slotStationStorage;
    private FightStationStorage fightStationStorage;
    private ChickenStationStorage chickenStationStorage;
    private JoinGiftStorage joinGiftStorage;
    private BlockSnapshotStorage minesRestoreStorage;
    private BlockSnapshotStorage rouletteRestoreStorage;
    private BlockSnapshotStorage slotsRestoreStorage;
    private BlockSnapshotStorage fightsRestoreStorage;
    private BlockSnapshotStorage chickenRestoreStorage;
    private HouseBalanceStorage houseBalanceStorage;
    private MinesManager minesManager;
    private RouletteManager rouletteManager;
    private SlotsManager slotsManager;
    private FightsManager fightsManager;
    private ChickenManager chickenManager;
    private JoinGiftManager joinGiftManager;
    private HologramManager hologramManager;
    private FrameAnimator frameAnimator;
    private RouletteFrameAnimator rouletteFrameAnimator;
    private SlotsFrameAnimator slotsFrameAnimator;
    private HologramPlacementStorage hologramPlacementStorage;
    private HologramPlacementController hologramPlacementController;
    private StationNumberStorage stationNumberStorage;

    @Override
    public void onEnable() {
        boolean migrateFightFramePattern = !getConfig().contains("fights.defaults-version");
        migrateLegacyDataFolder();
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        if (migrateFightFramePattern && getConfig().getInt("fights.casino-frame-animation.pattern", 3) == 1) { getConfig().set("fights.casino-frame-animation.pattern", 3); }
        getConfig().set("fights.defaults-version", 2);
        if (getConfig().getInt("chicken.defaults-version", 0) < 2) { getConfig().set("chicken.blocks.gold", "GOLD_BLOCK"); }
        if (getConfig().getInt("chicken.defaults-version", 0) < 3) { getConfig().set("chicken.hologram-line-spacing", 0.65); }
        if (getConfig().getInt("chicken.defaults-version", 0) < 4) { getConfig().set("chicken.min-steps", 8); getConfig().set("chicken.max-steps", 40); getConfig().set("chicken.multiplier-increase-per-second", 0.03); getConfig().set("chicken.pickup-multiplier", 0.10); }
        if (getConfig().getInt("chicken.defaults-version", 0) < 5) { getConfig().set("chicken.min-lightning-seconds", 5); getConfig().set("chicken.max-lightning-seconds", 45); }
        if (getConfig().getInt("chicken.defaults-version", 0) < 6) { getConfig().set("chicken.pickup-multiplier", 0.25); }
        if (getConfig().getInt("chicken.defaults-version", 0) < 7) { getConfig().set("chicken.lightning-randomness-curve", 1.8); }
        getConfig().set("chicken.defaults-version", 7);
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
        this.slotStationStorage = new SlotStationStorage(this);
        slotStationStorage.load();
        this.fightStationStorage = new FightStationStorage(this);
        fightStationStorage.load();
        this.chickenStationStorage = new ChickenStationStorage(this);
        chickenStationStorage.load();
        this.joinGiftStorage = new JoinGiftStorage(this);
        joinGiftStorage.load();
        this.minesRestoreStorage = new BlockSnapshotStorage(this, "mines_restore.yml");
        minesRestoreStorage.load();
        this.rouletteRestoreStorage = new BlockSnapshotStorage(this, "roulette_restore.yml");
        rouletteRestoreStorage.load();
        this.slotsRestoreStorage = new BlockSnapshotStorage(this, "slots_restore.yml");
        slotsRestoreStorage.load();
        this.fightsRestoreStorage = new BlockSnapshotStorage(this, "fights_restore.yml");
        fightsRestoreStorage.load();
        this.chickenRestoreStorage = new BlockSnapshotStorage(this, "chicken_restore.yml");
        chickenRestoreStorage.load();
        this.houseBalanceStorage = new HouseBalanceStorage(this);
        houseBalanceStorage.load();

        this.stationNumberStorage = new StationNumberStorage(this);
        stationNumberStorage.load();

        this.hologramPlacementStorage = new HologramPlacementStorage(this);
        hologramPlacementStorage.load();
        this.minesManager = new MinesManager(this, economy, stationStorage, minesRestoreStorage, houseBalanceStorage);
        this.rouletteManager = new RouletteManager(this, economy, rouletteStationStorage, hologramPlacementStorage, rouletteRestoreStorage, houseBalanceStorage);
        this.slotsManager = new SlotsManager(this, economy, slotStationStorage, hologramPlacementStorage, slotsRestoreStorage, houseBalanceStorage);
        this.fightsManager = new FightsManager(this, economy, fightStationStorage, hologramPlacementStorage, fightsRestoreStorage, houseBalanceStorage);
        this.chickenManager = new ChickenManager(this, economy, chickenStationStorage, chickenRestoreStorage);
        this.joinGiftManager = new JoinGiftManager(this, economy, joinGiftStorage);
        this.hologramManager = new HologramManager(this, minesManager, hologramPlacementStorage);
        this.hologramPlacementController = new HologramPlacementController(this, hologramPlacementStorage, minesManager, slotsManager, rouletteManager);
        this.frameAnimator = new FrameAnimator(this, minesManager);
        this.rouletteFrameAnimator = new RouletteFrameAnimator(this, rouletteManager);
        this.slotsFrameAnimator = new SlotsFrameAnimator(this, slotsManager);
        hologramManager.start();
        frameAnimator.start();
        rouletteFrameAnimator.start();
        slotsFrameAnimator.start();
        rouletteManager.start();
        slotsManager.start();
        fightsManager.start();
        chickenManager.start();
        CasinoFrameCommand casinoFrameCommand = new CasinoFrameCommand(minesManager, frameAnimator);
        RouletteCasinoFrameCommand rouletteCasinoFrameCommand = new RouletteCasinoFrameCommand(rouletteManager);
        SlotsCasinoFrameCommand slotsCasinoFrameCommand = new SlotsCasinoFrameCommand(slotsManager);

        MinesTabCompleter tabCompleter = new MinesTabCompleter();

        Objects.requireNonNull(getCommand("minegame")).setExecutor(new MineCommand(minesManager));
        Objects.requireNonNull(getCommand("minegame")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("minegameadmin")).setExecutor(new MineAdminCommand(minesManager, casinoFrameCommand, hologramPlacementController));
        Objects.requireNonNull(getCommand("minegameadmin")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("roulette")).setExecutor(new RouletteCommand(rouletteManager));
        Objects.requireNonNull(getCommand("roulette")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("rouletteadmin")).setExecutor(new RouletteAdminCommand(rouletteManager, rouletteCasinoFrameCommand, hologramPlacementController));
        Objects.requireNonNull(getCommand("rouletteadmin")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("slotsadmin")).setExecutor(new SlotsAdminCommand(slotsManager, slotsCasinoFrameCommand, hologramPlacementController));
        Objects.requireNonNull(getCommand("slotsadmin")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("fighter")).setExecutor(new FighterCommand(fightsManager));
        Objects.requireNonNull(getCommand("fighter")).setTabCompleter(new FightsTabCompleter(fightsManager));
        Objects.requireNonNull(getCommand("fightadmin")).setExecutor(new FightsAdminCommand(fightsManager));
        Objects.requireNonNull(getCommand("fightadmin")).setTabCompleter(new FightsTabCompleter(fightsManager));
        Objects.requireNonNull(getCommand("chicken")).setExecutor(new ChickenCommand(chickenManager));
        Objects.requireNonNull(getCommand("chicken")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("chickenadmin")).setExecutor(new ChickenAdminCommand(chickenManager));
        Objects.requireNonNull(getCommand("chickenadmin")).setTabCompleter(tabCompleter);
        Objects.requireNonNull(getCommand("minegamesjoin")).setExecutor(new MinegamesJoinCommand(joinGiftManager));
        Objects.requireNonNull(getCommand("minegamesjoin")).setTabCompleter(tabCompleter);
        CasinoGuiCommand casinoGuiCommand = new CasinoGuiCommand(this, minesManager, rouletteManager, slotsManager, fightsManager);
        Objects.requireNonNull(getCommand("casinogui")).setExecutor(casinoGuiCommand);
        getServer().getPluginManager().registerEvents(new CasinoGuiListener(casinoGuiCommand), this);

        getServer().getPluginManager().registerEvents(hologramPlacementController.listener(), this);
        getServer().getPluginManager().registerEvents(new MinesListener(minesManager), this);
        getServer().getPluginManager().registerEvents(new PlayerExitListener(minesManager), this);
        getServer().getPluginManager().registerEvents(new RouletteListener(rouletteManager), this);
        getServer().getPluginManager().registerEvents(new SlotsListener(slotsManager), this);
        getServer().getPluginManager().registerEvents(fightsManager, this);
        getServer().getPluginManager().registerEvents(chickenManager, this);
        getServer().getPluginManager().registerEvents(new CasinoBuildProtectionListener(this, minesManager, rouletteManager, slotsManager, fightsManager), this);
        getServer().getPluginManager().registerEvents(new MinegamesJoinListener(joinGiftManager), this);
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
        if (slotsFrameAnimator != null) {
            slotsFrameAnimator.shutdown();
        }
        if (rouletteManager != null) {
            rouletteManager.shutdown();
        }
        if (fightsManager != null) { fightsManager.shutdown(); }
        if (chickenManager != null) { chickenManager.shutdown(); }
        if (slotsManager != null) {
            slotsManager.shutdown();
        }
        if (joinGiftStorage != null) {
            joinGiftStorage.save();
        }
        if (stationStorage != null) {
            stationStorage.save();
        }
        if (rouletteStationStorage != null) {
            rouletteStationStorage.save();
        }
        if (fightStationStorage != null) { fightStationStorage.save(); }
        if (chickenStationStorage != null) { chickenStationStorage.save(); }
        if (slotStationStorage != null) {
            slotStationStorage.save();
        }
        if (minesRestoreStorage != null) {
            minesRestoreStorage.save();
        }
        if (rouletteRestoreStorage != null) {
            rouletteRestoreStorage.save();
        }
        if (slotsRestoreStorage != null) {
            slotsRestoreStorage.save();
        }
        if (fightsRestoreStorage != null) {
            fightsRestoreStorage.save();
        }
        if (chickenRestoreStorage != null) {
            chickenRestoreStorage.save();
        }
        if (stationNumberStorage != null) {
            stationNumberStorage.save();
        }
        if (hologramPlacementStorage != null) {
            hologramPlacementStorage.save();
        }
        if (houseBalanceStorage != null) {
            houseBalanceStorage.save();
        }
    }

    public StationNumberStorage stationNumberStorage() { return stationNumberStorage; }

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
