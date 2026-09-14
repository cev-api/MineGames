package dev.minegame.mines;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
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
import org.bukkit.util.Vector;

public final class DiceManager implements Listener {
    private static final double DIE_HALF_SIZE = 0.18D;
    private static final double TICK_GRAVITY = 0.09D;
    private static final double AIR_DRAG = 0.98D;
    private static final double BOUNCE_VERTICAL_DAMPING = 0.60D;
    private static final double BOUNCE_HORIZONTAL_DAMPING = 0.76D;
    private static final int MAX_SETTLE_TICKS = 14;
    private static final int CRAPS_TIMEOUT_TICKS = 6000;
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");

    private final MinegamePlugin plugin;
    private final Economy economy;
    private final HouseBalanceStorage houseBalanceStorage;
    private final DiceRateLimitStorage rateLimitStorage;
    private final DiceItemFactory itemFactory;
    private final Map<UUID, DiceInstance> activeDice = new HashMap<>();
    private final Map<UUID, Integer> lastThrowTick = new HashMap<>();
    private final Map<UUID, EquipmentSlot> lastThrowHand = new HashMap<>();
    private final Map<UUID, RollGroup> rollGroups = new HashMap<>();
    private final Map<UUID, UUID> currentRollGroupByPlayer = new HashMap<>();
    private final Map<UUID, CrapsSession> crapsById = new HashMap<>();
    private final Map<UUID, CrapsSession> crapsByPlayer = new HashMap<>();
    private final org.bukkit.NamespacedKey entityKey;
    private BukkitTask task;

    public DiceManager(MinegamePlugin plugin, Economy economy, HouseBalanceStorage houseBalanceStorage,
                       DiceRateLimitStorage rateLimitStorage) {
        this.plugin = plugin;
        this.economy = economy;
        this.houseBalanceStorage = houseBalanceStorage;
        this.rateLimitStorage = rateLimitStorage;
        this.itemFactory = new DiceItemFactory(plugin);
        this.entityKey = new org.bukkit.NamespacedKey(plugin, "dice_entity");
    }

    public void start() {
        removeOrphanedEntities();
        if (task != null) {
            task.cancel();
        }
        task = PlatformScheduler.runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (DiceInstance dice : new ArrayList<>(activeDice.values())) {
            remove(dice, !dice.expired());
        }
        for (CrapsSession session : new ArrayList<>(crapsById.values())) {
            abortCraps(session, "messages.craps.gameplay.timeout", "Craps timed out; your bet was refunded.");
        }
        lastThrowTick.clear();
        lastThrowHand.clear();
        rollGroups.clear();
        currentRollGroupByPlayer.clear();
    }

    public boolean isDice(ItemStack item) {
        return itemFactory.isDice(item);
    }

    public boolean giveDice(Player player, int count) {
        int amount = Math.max(1, Math.min(64, count));
        if (crapsByPlayer.containsKey(player.getUniqueId())) {
            send(player, "messages.dice.gameplay.already-active", "&eYou already have an active dice game. Finish it first.");
            return false;
        }
        if (hasOwnedActiveDice(player.getUniqueId())) {
            send(player, "messages.dice.gameplay.existing-ground", "&eYou already have dice on the ground. Collect them or wait 30 seconds for them to despawn.");
            return false;
        }
        refreshInventoryDice(player);
        int existing = countDiceInInventory(player);
        int missing = amount - existing;
        if (missing <= 0) {
            send(player, "messages.dice.gameplay.already-have", "&eYou already have enough dice.");
            return false;
        }
        DiceRateLimitStorage.RateLimitResult rateLimit = consumeRateLimit(player);
        if (!rateLimit.allowed()) {
            sendRateLimitMessage(player, rateLimit);
            return false;
        }
        String color = diceColor(false);
        for (int i = 0; i < missing; i++) {
            giveOrDrop(player, itemFactory.create(color));
        }
        return true;
    }

    public boolean diceCommandEnabled() {
        return configuredBoolean("dice.command-enabled", true);
    }

    public int defaultDiceAmount() {
        return Math.max(1, Math.min(64, plugin.getConfig().getInt("dice.default-amount", 2)));
    }

    public boolean crapsEnabled() {
        return configuredBoolean("dice.craps.enabled", true);
    }

    List<String> colors() {
        return new ArrayList<>(itemFactory.colors());
    }

    String diceColor(boolean craps) {
        String value = plugin.getConfig().getString(craps ? "dice.craps.color" : "dice.color", craps ? "inherit" : "red");
        if (craps && "inherit".equalsIgnoreCase(value)) {
            value = plugin.getConfig().getString("dice.color", "red");
        }
        return itemFactory.normalizeColor(value);
    }

    boolean playerAlerts(boolean craps) {
        return effectiveBoolean("player-alert", craps, true);
    }

    boolean serverAlerts(boolean craps) {
        return effectiveBoolean("server-alert", craps, false);
    }

    boolean glowing(boolean craps) {
        return effectiveBoolean("glow", craps, false);
    }

    boolean particles(boolean craps) {
        return effectiveBoolean("particles", craps, false);
    }

    public boolean throwDie(Player player, EquipmentSlot hand) {
        if (hand == null) {
            return false;
        }
        int currentTick = Bukkit.getCurrentTick();
        if (lastThrowTick.getOrDefault(player.getUniqueId(), Integer.MIN_VALUE) == currentTick
                && hand == lastThrowHand.get(player.getUniqueId())) {
            return false;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!itemFactory.isDice(item)) {
            return false;
        }

        ItemStack refreshed = itemFactory.refresh(item);
        refreshed.setAmount(item.getAmount());
        item = refreshed;
        setHandItem(player, hand, item);

        UUID markedSessionId = itemFactory.sessionId(item);
        CrapsSession session = markedSessionId == null ? null : crapsById.get(markedSessionId);
        if (session != null && !session.playerId().equals(player.getUniqueId())) {
            return false;
        }
        int requestedDieNumber = itemFactory.dieNumber(item);
        if (session != null && session.dice().values().stream().anyMatch(dice -> dice.dieNumber() == requestedDieNumber)) {
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
        boolean craps = session != null;
        RollGroup group = craps ? null : groupFor(player.getUniqueId(), currentTick);
        int dieNumber = session == null ? group.nextDieNumber() : itemFactory.dieNumber(item);
        DiceInstance dice = new DiceInstance(
                entityKey,
                player.getUniqueId(),
                spawnLocation,
                direction.clone().multiply(0.52D).setY(0.38D),
                ThreadLocalRandom.current().nextInt(1, 7),
                thrownItem,
                itemFactory.color(thrownItem),
                craps,
                session == null ? null : session.id(),
                dieNumber,
                group == null ? null : group.id,
                glowing(craps),
                particles(craps)
        );
        try {
            dice.spawn();
            activeDice.put(dice.id(), dice);
            if (session != null) {
                session.dice().put(dice.id(), dice);
                session.touch(currentTick);
            } else if (group != null) {
                group.dice.put(dice.id(), dice);
            }
            lastThrowTick.put(player.getUniqueId(), currentTick);
            lastThrowHand.put(player.getUniqueId(), hand);
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
        if (dice.craps()) {
            return true;
        }
        if (dice.state() != DiceState.FINISHED) {
            return true;
        }
        activeDice.remove(dice.id());
        removeFromRollGroup(dice);
        dice.removeEntities();
        giveOrDrop(player, dice.sourceItem());
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
        UUID playerId = event.getPlayer().getUniqueId();
        lastThrowTick.remove(playerId);
        lastThrowHand.remove(playerId);
        CrapsSession session = crapsByPlayer.get(playerId);
        if (session != null) {
            abortCraps(session, "messages.craps.gameplay.timeout", "Craps timed out; your bet was refunded.");
        }
    }

    public void startCraps(Player player, double wager) {
        if (!crapsEnabled()) {
            send(player, "messages.craps.gameplay.disabled", "&cCraps is disabled.");
            return;
        }
        if (crapsByPlayer.containsKey(player.getUniqueId())) {
            send(player, "messages.craps.gameplay.already-active", "&cYou already have an active craps game.");
            return;
        }
        if (hasOwnedActiveDice(player.getUniqueId())) {
            send(player, "messages.craps.gameplay.existing-ground", "&eYou already have dice on the ground. Collect them or wait 30 seconds for them to despawn.");
            return;
        }
        refreshInventoryDice(player);
        double min = plugin.getConfig().getDouble("dice.craps.min-bet", 1.0D);
        double max = plugin.getConfig().getDouble("dice.craps.max-bet", -1.0D);
        if (!Double.isFinite(wager) || wager < Math.max(0.0D, min) || max >= 0.0D && wager > max) {
            send(player, "messages.craps.gameplay.invalid-bet", "&cInvalid craps bet.");
            return;
        }
        EconomyResponse withdraw = economy.withdrawPlayer(player, wager);
        if (!withdraw.transactionSuccess()) {
            send(player, "messages.craps.gameplay.no-money", "&cYou do not have enough money.");
            return;
        }
        DiceRateLimitStorage.RateLimitResult rateLimit = consumeRateLimit(player);
        if (!rateLimit.allowed()) {
            EconomyResponse refund = economy.depositPlayer(player, wager);
            if (!refund.transactionSuccess()) {
                plugin.getLogger().warning("Unable to refund rate-limited craps wager for " + player.getUniqueId());
            }
            sendRateLimitMessage(player, rateLimit);
            return;
        }
        CrapsSession session = new CrapsSession(player.getUniqueId(), wager, diceColor(true), Bukkit.getCurrentTick());
        List<ItemStack> existingDice = takeDiceFromInventory(player, 2);
        for (int dieNumber = 1; dieNumber <= 2; dieNumber++) {
            ItemStack item = dieNumber <= existingDice.size()
                    ? itemFactory.markCraps(existingDice.get(dieNumber - 1), session.id(), dieNumber)
                    : itemFactory.createCraps(session.color(), session.id(), dieNumber);
            session.setDieItem(dieNumber, item);
        }
        crapsById.put(session.id(), session);
        crapsByPlayer.put(player.getUniqueId(), session);
        giveCrapsDice(player, session);
        send(player, "messages.craps.gameplay.started", "&aCraps started with a &6$%wager% &abet. Throw both dice.",
                Map.of("%wager%", MONEY.format(wager)));
    }

    public void cancelCraps(Player player) {
        CrapsSession session = crapsByPlayer.get(player.getUniqueId());
        if (session == null) {
            send(player, "messages.craps.gameplay.not-active", "&eYou do not have an active craps game.");
            return;
        }
        abortCraps(session, "messages.craps.gameplay.cancelled", "Craps cancelled; your bet was refunded.");
    }

    public void showSettings(CommandSender sender) {
        sender.sendMessage(colorize(text("messages.dice.admin.settings-header", "&6[Dice] &fSettings")));
        sender.sendMessage(colorize("&7dice.command-enabled: &f" + diceCommandEnabled()));
        sender.sendMessage(colorize("&7dice.default-amount: &f" + defaultDiceAmount()));
        sender.sendMessage(colorize("&7dice.color: &f" + diceColor(false)));
        sender.sendMessage(colorize("&7dice.player-alert: &f" + playerAlerts(false)));
        sender.sendMessage(colorize("&7dice.server-alert: &f" + serverAlerts(false)));
        sender.sendMessage(colorize("&7dice.glow: &f" + glowing(false)));
        sender.sendMessage(colorize("&7dice.particles: &f" + particles(false)));
        sender.sendMessage(colorize("&7dice.rate-limit: &f" + (rateLimitAmount() <= 0 ? "off" : rateLimitAmount()) + " per " + rateLimitPeriod()));
        sender.sendMessage(colorize("&7dice.craps.enabled: &f" + crapsEnabled()));
        sender.sendMessage(colorize("&7dice.craps.color: &f" + scopedValue("color")));
        sender.sendMessage(colorize("&7dice.craps.player-alert: &f" + scopedValue("player-alert")));
        sender.sendMessage(colorize("&7dice.craps.server-alert: &f" + scopedValue("server-alert")));
        sender.sendMessage(colorize("&7dice.craps.glow: &f" + scopedValue("glow")));
        sender.sendMessage(colorize("&7dice.craps.particles: &f" + scopedValue("particles")));
        sender.sendMessage(colorize("&7dice.craps.min-bet/max-bet: &f"
                + MONEY.format(plugin.getConfig().getDouble("dice.craps.min-bet", 1.0D)) + "/"
                + MONEY.format(plugin.getConfig().getDouble("dice.craps.max-bet", -1.0D))));
    }

    public void configureRateLimit(CommandSender sender, String rawAmount, String rawPeriod) {
        int amount;
        if (rawAmount.equalsIgnoreCase("off")) {
            amount = -1;
        } else {
            try {
                amount = Integer.parseInt(rawAmount);
                if (amount < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                send(sender, "messages.dice.admin.invalid-rate-limit", "&cRate limit must be a non-negative number or off.");
                return;
            }
        }
        String period = rawPeriod == null ? rateLimitPeriod() : rawPeriod.toLowerCase(Locale.ROOT);
        if (!period.equals("24h") && !period.equals("day")) {
            send(sender, "messages.dice.admin.invalid-rate-period", "&cRate period must be 24h or day.");
            return;
        }
        plugin.getConfig().set("dice.rate-limit.amount", amount);
        plugin.getConfig().set("dice.rate-limit.period", period);
        saveSetting(sender, "dice.rate-limit.amount", Integer.toString(amount));
        saveSetting(sender, "dice.rate-limit.period", period);
    }

    public void adminSet(CommandSender sender, String setting, String rawValue, boolean crapsScope) {
        String normalized = setting.toLowerCase(Locale.ROOT);
        String key = switch (normalized) {
            case "amount", "default-amount" -> "default-amount";
            case "players", "command", "command-enabled" -> "command-enabled";
            case "alert-player", "player-alert" -> "player-alert";
            case "alert-server", "server-alert" -> "server-alert";
            case "craps" -> "craps-enabled";
            default -> normalized;
        };
        boolean scopedSetting = key.equals("color") || key.equals("player-alert") || key.equals("server-alert")
                || key.equals("glow") || key.equals("particles");
        if (crapsScope && !scopedSetting) {
            send(sender, "messages.dice.admin.invalid-setting", "&cThat setting cannot be scoped to craps.");
            return;
        }
        if (key.equals("craps-enabled")) {
            Boolean parsed = parseBoolean(rawValue);
            if (parsed == null) {
                send(sender, "messages.dice.admin.invalid-value", "&cUse on or off.");
                return;
            }
            plugin.getConfig().set("dice.craps.enabled", parsed);
            saveSetting(sender, "dice.craps.enabled", parsed.toString());
            return;
        }
        if (key.equals("default-amount")) {
            try {
                int amount = Integer.parseInt(rawValue);
                if (amount < 1 || amount > 64) {
                    throw new NumberFormatException();
                }
                plugin.getConfig().set("dice.default-amount", amount);
                saveSetting(sender, "dice.default-amount", Integer.toString(amount));
            } catch (NumberFormatException ex) {
                send(sender, "messages.dice.admin.invalid-amount", "&cAmount must be a whole number from 1 to 64.");
            }
            return;
        }
        if (key.equals("command-enabled")) {
            Boolean parsed = parseBoolean(rawValue);
            if (parsed == null) {
                send(sender, "messages.dice.admin.invalid-value", "&cUse on or off.");
                return;
            }
            plugin.getConfig().set("dice.command-enabled", parsed);
            saveSetting(sender, "dice.command-enabled", parsed.toString());
            return;
        }
        if (!scopedSetting) {
            send(sender, "messages.dice.admin.invalid-setting", "&cUnknown dice setting.");
            return;
        }
        String path = "dice." + (crapsScope ? "craps." : "") + key;
        if (key.equals("color")) {
            if (crapsScope && rawValue.equalsIgnoreCase("inherit")) {
                plugin.getConfig().set(path, "inherit");
                saveSetting(sender, path, "inherit");
            } else if (itemFactory.isColor(rawValue)) {
                String value = rawValue.toLowerCase(Locale.ROOT);
                plugin.getConfig().set(path, value);
                saveSetting(sender, path, value);
            } else {
                send(sender, "messages.dice.admin.invalid-color", "&cUnknown dice color. Available: " + String.join(", ", itemFactory.colors()));
            }
            return;
        }
        if (crapsScope && rawValue.equalsIgnoreCase("inherit")) {
            plugin.getConfig().set(path, "inherit");
            saveSetting(sender, path, "inherit");
            return;
        }
        Boolean parsed = parseBoolean(rawValue);
        if (parsed == null) {
            send(sender, "messages.dice.admin.invalid-value", "&cUse on, off, or inherit for craps settings.");
            return;
        }
        plugin.getConfig().set(path, parsed);
        saveSetting(sender, path, parsed.toString());
    }

    public void setGuiConfigValue(Player player, String path, String rawValue) {
        if (!path.startsWith("dice.") || rawValue == null) {
            return;
        }
        try {
            Object value;
            if (path.equals("dice.default-amount")) {
                int amount = Integer.parseInt(rawValue);
                if (amount < 1 || amount > 64) return;
                value = amount;
            } else if (path.equals("dice.rate-limit.amount")) {
                int amount = Integer.parseInt(rawValue);
                if (amount < -1) return;
                value = amount;
            } else if (path.equals("dice.rate-limit.period")) {
                if (!rawValue.equals("24h") && !rawValue.equals("day")) return;
                value = rawValue;
            } else if (path.equals("dice.craps.min-bet") || path.equals("dice.craps.max-bet")) {
                double amount = Double.parseDouble(rawValue);
                if (!Double.isFinite(amount)) return;
                if (path.endsWith("min-bet") && amount <= 0.0D) return;
                if (path.endsWith("max-bet") && amount != -1.0D && amount <= 0.0D) return;
                value = amount;
            } else if (path.equals("dice.color")) {
                if (!itemFactory.isColor(rawValue)) return;
                value = rawValue.toLowerCase(Locale.ROOT);
            } else if (path.equals("dice.craps.color")) {
                if (!rawValue.equalsIgnoreCase("inherit") && !itemFactory.isColor(rawValue)) return;
                value = rawValue.toLowerCase(Locale.ROOT);
            } else if (path.startsWith("dice.craps.") &&
                    (path.endsWith(".player-alert") || path.endsWith(".server-alert")
                            || path.endsWith(".glow") || path.endsWith(".particles"))) {
                if (!rawValue.equalsIgnoreCase("inherit") && parseBoolean(rawValue) == null) return;
                value = rawValue.equalsIgnoreCase("inherit") ? "inherit" : parseBoolean(rawValue);
            } else if (path.equals("dice.command-enabled") || path.equals("dice.player-alert")
                    || path.equals("dice.server-alert") || path.equals("dice.glow")
                    || path.equals("dice.particles") || path.equals("dice.craps.enabled")) {
                Boolean parsed = parseBoolean(rawValue);
                if (parsed == null) return;
                value = parsed;
            } else {
                return;
            }
            plugin.getConfig().set(path, value);
            saveSetting(player, path, String.valueOf(value));
        } catch (NumberFormatException ignored) {
        }
    }

    public void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        send(sender, "messages.dice.admin.reloaded", "&aDice settings reloaded.");
    }

    public void showHouseBalance(Player player) {
        double balance = houseBalanceStorage.crapsBalance();
        double wagered = houseBalanceStorage.crapsTotalWagered();
        double paid = houseBalanceStorage.crapsTotalPayout();
        send(player, "messages.craps.admin.house-balance-header", "&6[Craps] &eHouse Balance: &a$%balance%",
                Map.of("%balance%", MONEY.format(balance)));
        send(player, "messages.craps.admin.house-balance-details", "&7Wagered: &f$%wagered% &7| Paid: &f$%paid%",
                Map.of("%wagered%", MONEY.format(wagered), "%paid%", MONEY.format(paid)));
    }

    public void withdrawHouseBalance(Player player, String rawAmount) {
        double current = houseBalanceStorage.crapsBalance();
        if (current <= 0.0D) {
            send(player, "messages.craps.admin.house-no-balance", "&cNo craps house balance available to withdraw.");
            return;
        }
        double wanted;
        if (rawAmount.equalsIgnoreCase("all")) {
            wanted = current;
        } else {
            try {
                wanted = Double.parseDouble(rawAmount);
            } catch (NumberFormatException ex) {
                send(player, "messages.craps.admin.housewithdraw-usage", "&6Usage: &f/diceadmin housewithdraw <amount|all>");
                return;
            }
        }
        if (!Double.isFinite(wanted) || wanted <= 0.0D) {
            send(player, "messages.craps.admin.house-nothing-to-withdraw", "&cNothing to withdraw.");
            return;
        }
        double withdrawn = houseBalanceStorage.withdrawCraps(wanted);
        EconomyResponse deposit = economy.depositPlayer(player, withdrawn);
        if (!deposit.transactionSuccess()) {
            houseBalanceStorage.refundCrapsWithdrawal(withdrawn);
            send(player, "messages.shared.economy-payout-failed", "&cEconomy error: payout failed. Contact staff.");
            return;
        }
        send(player, "messages.craps.admin.house-withdraw-success", "&aWithdrew &6$%amount% &afrom craps house balance.",
                Map.of("%amount%", MONEY.format(withdrawn)));
        send(player, "messages.craps.admin.house-remaining-balance", "&7Remaining craps house balance: &a$%balance%",
                Map.of("%balance%", MONEY.format(houseBalanceStorage.crapsBalance())));
    }

    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public String text(String path, String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }

    private void tick() {
        int currentTick = Bukkit.getCurrentTick();
        for (DiceInstance dice : new ArrayList<>(activeDice.values())) {
            DiceState before = dice.state();
            if (!dice.tick()) {
                remove(dice, !dice.expired());
                continue;
            }
            if (before != DiceState.FINISHED && dice.state() == DiceState.FINISHED) {
                onDieFinished(dice);
            }
        }
        for (CrapsSession session : new ArrayList<>(crapsById.values())) {
            if (currentTick - session.lastActivityTick() > CRAPS_TIMEOUT_TICKS) {
                abortCraps(session, "messages.craps.gameplay.timeout", "Craps timed out; your bet was refunded.");
            }
        }
        for (RollGroup group : new ArrayList<>(rollGroups.values())) {
            if (currentTick - group.createdTick > DiceManager.maxAgeTicks()) {
                rollGroups.remove(group.id);
                currentRollGroupByPlayer.remove(group.ownerId, group.id);
            }
        }
    }

    private void onDieFinished(DiceInstance dice) {
        boolean craps = dice.craps();
        UUID ownerId = dice.ownerId();
        Player owner = Bukkit.getPlayer(ownerId);
        if (playerAlerts(craps) && owner != null && owner.isOnline()) {
            String path = craps ? "messages.craps.gameplay.die-rolled" : "messages.dice.gameplay.die-rolled";
            String fallback = craps ? "&6[Craps] &fDie %die% rolled &e%result%&f." : "&6[Dice] &fDie %die% rolled &e%result%&f.";
            send(owner, path, fallback, Map.of("%die%", Integer.toString(displayDieNumber(dice)), "%result%", Integer.toString(dice.result())));
        }
        if (serverAlerts(craps)) {
            String path = craps ? "messages.craps.gameplay.server-die-rolled" : "messages.dice.gameplay.server-die-rolled";
            String fallback = craps ? "&6[Craps] &f%player%'s die %die% rolled &e%result%&f." : "&6[Dice] &f%player%'s die %die% rolled &e%result%&f.";
            Bukkit.broadcastMessage(colorize(replace(text(path, fallback), Map.of(
                    "%player%", owner == null ? "A player" : owner.getName(),
                    "%die%", Integer.toString(displayDieNumber(dice)),
                    "%result%", Integer.toString(dice.result())
            ))));
        }
        if (craps) {
            CrapsSession session = crapsById.get(dice.sessionId());
            if (session != null && !session.resolving()
                    && session.dice().size() == 2
                    && session.dice().values().stream().allMatch(current -> current.state() == DiceState.FINISHED)) {
                resolveCrapsRoll(session);
            }
            return;
        }
        RollGroup group = dice.rollGroupId() == null ? null : rollGroups.get(dice.rollGroupId());
        if (group != null && !group.totalSent && group.dice.size() > 1
                && group.dice.values().stream().allMatch(current -> current.state() == DiceState.FINISHED)) {
            group.totalSent = true;
            sendGenericTotal(group);
            rollGroups.remove(group.id);
            currentRollGroupByPlayer.remove(group.ownerId, group.id);
        }
    }

    private void sendGenericTotal(RollGroup group) {
        List<DiceInstance> dice = new ArrayList<>(group.dice.values());
        dice.sort(Comparator.comparingInt(DiceInstance::dieNumber));
        int total = dice.stream().mapToInt(DiceInstance::result).sum();
        String rolls = dice.stream().map(diceInstance -> Integer.toString(diceInstance.result())).reduce((a, b) -> a + " + " + b).orElse("");
        Player owner = Bukkit.getPlayer(group.ownerId);
        if (playerAlerts(false) && owner != null && owner.isOnline()) {
            send(owner, "messages.dice.gameplay.total", "&6[Dice] &fYou rolled &e%rolls% &f= &e%total%&f.",
                    Map.of("%player%", owner.getName(), "%rolls%", rolls, "%total%", Integer.toString(total)));
        }
        if (serverAlerts(false)) {
            Bukkit.broadcastMessage(colorize(replace(text("messages.dice.gameplay.server-total", "&6[Dice] &f%player% rolled &e%rolls% &f= &e%total%&f."), Map.of(
                    "%player%", owner == null ? "A player" : owner.getName(), "%rolls%", rolls, "%total%", Integer.toString(total)
            ))));
        }
    }

    private void resolveCrapsRoll(CrapsSession session) {
        session.setResolving(true);
        List<DiceInstance> dice = new ArrayList<>(session.dice().values());
        dice.sort(Comparator.comparingInt(DiceInstance::dieNumber));
        int total = dice.stream().mapToInt(DiceInstance::result).sum();
        int first = dice.size() > 0 ? dice.get(0).result() : 0;
        int second = dice.size() > 1 ? dice.get(1).result() : 0;
        Location dropLocation = dice.isEmpty() ? null : dice.get(0).location();
        for (DiceInstance diceInstance : dice) {
            removeThrownWithoutReturn(diceInstance);
        }
        sendCrapsTotal(session, first, second, total);
        Player owner = Bukkit.getPlayer(session.playerId());
        if (session.point() == 0) {
            if (total == 7 || total == 11) {
                finishCraps(session, true, first, second, total, 0, dropLocation);
            } else if (total == 2 || total == 3 || total == 12) {
                finishCraps(session, false, first, second, total, 0, dropLocation);
            } else {
                session.setPoint(total);
                if (owner != null && owner.isOnline()) {
                    send(owner, "messages.craps.gameplay.point", "&e[Craps] &fPoint is &6%point%&f. Throw both dice again.",
                            Map.of("%point%", Integer.toString(total)));
                }
                returnCrapsDice(session, dropLocation);
            }
        } else if (total == session.point()) {
            finishCraps(session, true, first, second, total, session.point(), dropLocation);
        } else if (total == 7) {
            finishCraps(session, false, first, second, total, session.point(), dropLocation);
        } else {
            if (owner != null && owner.isOnline()) {
                send(owner, "messages.craps.gameplay.continued", "&7[Craps] &f%first% + %second% = %total%&7. Point remains &6%point%&7.",
                        Map.of("%first%", Integer.toString(first), "%second%", Integer.toString(second), "%total%", Integer.toString(total), "%point%", Integer.toString(session.point())));
            }
            returnCrapsDice(session, dropLocation);
        }
    }

    private void sendCrapsTotal(CrapsSession session, int first, int second, int total) {
        Player owner = Bukkit.getPlayer(session.playerId());
        Map<String, String> values = new HashMap<>();
        values.put("%player%", owner == null ? "A player" : owner.getName());
        values.put("%first%", Integer.toString(first));
        values.put("%second%", Integer.toString(second));
        values.put("%total%", Integer.toString(total));
        if (playerAlerts(true) && owner != null && owner.isOnline()) {
            send(owner, "messages.craps.gameplay.total", "&6[Craps] &fYou rolled &e%first% + %second% = %total%&f.", values);
        }
        if (serverAlerts(true)) {
            Bukkit.broadcastMessage(colorize(replace(text("messages.craps.gameplay.server-roll", "&6[Craps] &f%player% rolled &e%first% + %second% = %total%&f."), values)));
        }
    }

    private void finishCraps(CrapsSession session, boolean won, int first, int second, int total, int point, Location dropLocation) {
        double payout = won ? session.wager() * 2.0D : 0.0D;
        if (won) {
            EconomyResponse deposit = economy.depositPlayer(Bukkit.getOfflinePlayer(session.playerId()), payout);
            if (!deposit.transactionSuccess()) {
                payout = 0.0D;
                Player owner = Bukkit.getPlayer(session.playerId());
                if (owner != null && owner.isOnline()) {
                    send(owner, "messages.craps.gameplay.economy-payout-failed", "&cEconomy error: payout failed. Contact staff.");
                }
            }
        }
        houseBalanceStorage.recordCrapsResult(session.wager(), payout);
        Player owner = Bukkit.getPlayer(session.playerId());
        String path = won ? "messages.craps.gameplay.win" : "messages.craps.gameplay.lose";
        String fallback = won ? "&a[Craps] &f%first% + %second% = %total% &7| &aWon $%payout%" : "&c[Craps] &f%first% + %second% = %total% &7| &cLost $%wager%";
        Map<String, String> values = new HashMap<>();
        values.put("%first%", Integer.toString(first));
        values.put("%second%", Integer.toString(second));
        values.put("%total%", Integer.toString(total));
        values.put("%point%", Integer.toString(point));
        values.put("%wager%", MONEY.format(session.wager()));
        values.put("%payout%", MONEY.format(payout));
        values.put("%player%", owner == null ? "A player" : owner.getName());
        if (owner != null && owner.isOnline()) {
            send(owner, path, fallback, values);
        }
        if (serverAlerts(true)) {
            String serverPath = won ? "messages.craps.gameplay.server-win" : "messages.craps.gameplay.server-lose";
            String serverFallback = won ? "&6[Craps] &f%player% won &a$%payout%&f." : "&6[Craps] &f%player% lost &c$%wager%&f.";
            Bukkit.broadcastMessage(colorize(replace(text(serverPath, serverFallback), values)));
        }
        returnCrapsDice(session, dropLocation);
        removeSession(session);
    }

    private void returnCrapsDice(CrapsSession session, Location dropLocation) {
        session.dice().clear();
        session.setResolving(false);
        session.touch(Bukkit.getCurrentTick());
        Player owner = Bukkit.getPlayer(session.playerId());
        ItemStack first = session.dieItem(1);
        ItemStack second = session.dieItem(2);
        if (owner != null && owner.isOnline()) {
            giveOrDrop(owner, first);
            giveOrDrop(owner, second);
        } else if (dropLocation != null && dropLocation.getWorld() != null && dropLocation.getChunk().isLoaded()) {
            dropLocation.getWorld().dropItemNaturally(dropLocation, first);
            dropLocation.getWorld().dropItemNaturally(dropLocation, second);
        }
    }

    private void abortCraps(CrapsSession session, String messagePath, String fallback) {
        abortCraps(session, messagePath, fallback, true);
    }

    private void abortCraps(CrapsSession session, String messagePath, String fallback, boolean returnDice) {
        if (!crapsById.containsKey(session.id())) {
            return;
        }
        session.setResolving(true);
        Player owner = Bukkit.getPlayer(session.playerId());
        for (DiceInstance dice : new ArrayList<>(session.dice().values())) {
            activeDice.remove(dice.id());
            dice.removeEntities();
            if (returnDice) {
                ItemStack item = dice.sourceItem();
                if (owner != null && owner.isOnline()) {
                    giveOrDrop(owner, item);
                } else if (dice.world() != null && dice.world().isChunkLoaded(dice.chunkX(), dice.chunkZ())) {
                    dice.world().dropItemNaturally(dice.location(), item);
                }
            }
        }
        session.dice().clear();
        EconomyResponse refund = economy.depositPlayer(Bukkit.getOfflinePlayer(session.playerId()), session.wager());
        if (!refund.transactionSuccess()) {
            plugin.getLogger().warning("Unable to refund craps wager for " + session.playerId());
        }
        if (owner != null && owner.isOnline()) {
            send(owner, messagePath, fallback);
        }
        removeSession(session);
    }

    private RollGroup groupFor(UUID ownerId, int currentTick) {
        UUID currentId = currentRollGroupByPlayer.get(ownerId);
        RollGroup current = currentId == null ? null : rollGroups.get(currentId);
        if (current != null && !current.totalSent && !current.dice.isEmpty()) {
            return current;
        }
        RollGroup group = new RollGroup(ownerId, currentTick);
        rollGroups.put(group.id, group);
        currentRollGroupByPlayer.put(ownerId, group.id);
        return group;
    }

    private void remove(DiceInstance dice, boolean returnItem) {
        if (!activeDice.containsKey(dice.id())) {
            return;
        }
        if (dice.craps()) {
            CrapsSession session = dice.sessionId() == null ? null : crapsById.get(dice.sessionId());
            if (session != null && !session.resolving()) {
                abortCraps(session, "messages.craps.gameplay.timeout", "Craps timed out; your bet was refunded.", returnItem);
            } else {
                activeDice.remove(dice.id());
                if (session != null) {
                    session.dice().remove(dice.id());
                }
                dice.removeEntities();
            }
            return;
        }
        activeDice.remove(dice.id());
        removeFromRollGroup(dice);
        dice.removeEntities();
        if (returnItem) {
            Player owner = Bukkit.getPlayer(dice.ownerId());
            ItemStack item = dice.sourceItem();
            if (owner != null && owner.isOnline()) {
                giveOrDrop(owner, item);
            } else if (dice.world() != null && dice.world().isChunkLoaded(dice.chunkX(), dice.chunkZ())) {
                dice.world().dropItemNaturally(dice.location(), item);
            }
        }
    }

    private void removeThrownWithoutReturn(DiceInstance dice) {
        activeDice.remove(dice.id());
        CrapsSession session = dice.sessionId() == null ? null : crapsById.get(dice.sessionId());
        if (session != null) {
            session.dice().remove(dice.id());
        }
        dice.removeEntities();
    }

    private void removeFromRollGroup(DiceInstance dice) {
        if (dice.rollGroupId() == null) {
            return;
        }
        RollGroup group = rollGroups.get(dice.rollGroupId());
        if (group == null) {
            return;
        }
        group.dice.remove(dice.id());
        if (group.dice.isEmpty()) {
            rollGroups.remove(group.id);
            currentRollGroupByPlayer.remove(group.ownerId, group.id);
        }
    }

    private void removeSession(CrapsSession session) {
        crapsById.remove(session.id());
        crapsByPlayer.remove(session.playerId(), session);
    }

    private void giveCrapsDice(Player player, CrapsSession session) {
        giveOrDrop(player, session.dieItem(1));
        giveOrDrop(player, session.dieItem(2));
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
        for (ItemStack leftover : remaining.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private int countDiceInInventory(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (itemFactory.isDice(item)) {
                count += item.getAmount();
            }
        }
        if (itemFactory.isDice(player.getInventory().getItemInOffHand())) {
            count += player.getInventory().getItemInOffHand().getAmount();
        }
        return count;
    }

    private void refreshInventoryDice(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (!itemFactory.isDice(item)) {
                continue;
            }
            ItemStack refreshed = itemFactory.refresh(item);
            refreshed.setAmount(item.getAmount());
            player.getInventory().setItem(slot, refreshed);
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (itemFactory.isDice(offHand)) {
            ItemStack refreshed = itemFactory.refresh(offHand);
            refreshed.setAmount(offHand.getAmount());
            player.getInventory().setItemInOffHand(refreshed);
        }
    }

    private List<ItemStack> takeDiceFromInventory(Player player, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = amount;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
            ItemStack item = storage[slot];
            if (!itemFactory.isDice(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            for (int i = 0; i < take; i++) {
                ItemStack single = item.clone();
                single.setAmount(1);
                taken.add(single);
            }
            if (item.getAmount() == take) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - take);
                player.getInventory().setItem(slot, item);
            }
            remaining -= take;
        }
        if (remaining > 0 && itemFactory.isDice(player.getInventory().getItemInOffHand())) {
            ItemStack item = player.getInventory().getItemInOffHand();
            int take = Math.min(remaining, item.getAmount());
            for (int i = 0; i < take; i++) {
                ItemStack single = item.clone();
                single.setAmount(1);
                taken.add(single);
            }
            if (item.getAmount() == take) {
                player.getInventory().setItemInOffHand(null);
            } else {
                item.setAmount(item.getAmount() - take);
                player.getInventory().setItemInOffHand(item);
            }
        }
        return taken;
    }

    private boolean hasOwnedActiveDice(UUID playerId) {
        return activeDice.values().stream().anyMatch(dice -> dice.ownerId().equals(playerId));
    }

    private DiceRateLimitStorage.RateLimitResult consumeRateLimit(Player player) {
        return rateLimitStorage.tryConsume(player, rateLimitAmount(), rateLimitPeriod());
    }

    private void sendRateLimitMessage(Player player, DiceRateLimitStorage.RateLimitResult result) {
        String time = rateLimitPeriod().equals("day") ? "the next Minecraft day" : formatDuration(result.remainingMillis());
        send(player, "messages.dice.gameplay.rate-limited", "&cYou have reached the dice limit. Try again in %time%.",
                Map.of("%time%", time));
    }

    private int rateLimitAmount() {
        return plugin.getConfig().getInt("dice.rate-limit.amount", -1);
    }

    private String rateLimitPeriod() {
        String period = plugin.getConfig().getString("dice.rate-limit.period", "24h");
        return period.equalsIgnoreCase("day") ? "day" : "24h";
    }

    private String formatDuration(long milliseconds) {
        long minutes = Math.max(1L, (milliseconds + 59_999L) / 60_000L);
        if (minutes >= 60L) {
            return (minutes / 60L) + "h " + (minutes % 60L) + "m";
        }
        return minutes + "m";
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

    private int displayDieNumber(DiceInstance dice) {
        return dice.dieNumber() > 0 ? dice.dieNumber() : 1;
    }

    private String scopedValue(String key) {
        return plugin.getConfig().getString("dice.craps." + key, "inherit");
    }

    private boolean effectiveBoolean(String key, boolean craps, boolean fallback) {
        if (craps) {
            Object override = plugin.getConfig().get("dice.craps." + key);
            if (override != null && !(override instanceof String string && string.equalsIgnoreCase("inherit"))) {
                return parseBoolean(override.toString(), fallback);
            }
        }
        return configuredBoolean("dice." + key, fallback);
    }

    private boolean configuredBoolean(String path, boolean fallback) {
        Object value = plugin.getConfig().get(path);
        return value == null ? fallback : parseBoolean(value.toString(), fallback);
    }

    private Boolean parseBoolean(String value) {
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")) {
            return false;
        }
        return null;
    }

    private boolean parseBoolean(String value, boolean fallback) {
        Boolean parsed = parseBoolean(value);
        return parsed == null ? fallback : parsed;
    }

    private void saveSetting(CommandSender sender, String path, String value) {
        plugin.saveConfig();
        send(sender, "messages.dice.admin.setting-set", "&aSet &f%path% &ato &f%value%&a.",
                Map.of("%path%", path, "%value%", value));
    }

    private void send(CommandSender sender, String path, String fallback) {
        sender.sendMessage(colorize(text(path, fallback)));
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> values) {
        sender.sendMessage(colorize(replace(text(path, fallback), values)));
    }

    private String replace(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
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

    private static int maxAgeTicks() {
        return 600;
    }

    private static final class RollGroup {
        private final UUID id = UUID.randomUUID();
        private final UUID ownerId;
        private final int createdTick;
        private final Map<UUID, DiceInstance> dice = new LinkedHashMap<>();
        private boolean totalSent;
        private int nextDieNumber = 1;

        private RollGroup(UUID ownerId, int createdTick) {
            this.ownerId = ownerId;
            this.createdTick = createdTick;
        }

        private int nextDieNumber() {
            return nextDieNumber++;
        }
    }
}
