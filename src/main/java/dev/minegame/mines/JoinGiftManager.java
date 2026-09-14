package dev.minegame.mines;

import java.text.DecimalFormat;
import java.util.Map;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class JoinGiftManager {
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");

    private final MinegamePlugin plugin;
    private final Economy economy;
    private final JoinGiftStorage storage;

    private boolean enabled;
    private double amount;

    public JoinGiftManager(MinegamePlugin plugin, Economy economy, JoinGiftStorage storage) {
        this.plugin = plugin;
        this.economy = economy;
        this.storage = storage;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        this.enabled = plugin.getConfig().getBoolean("join-gift.enabled", true);
        this.amount = Math.max(0.0, plugin.getConfig().getDouble("join-gift.amount", 1000.0));
    }

    public void handleJoin(Player player) {
        boolean firstTime = !storage.hasSeen(player.getUniqueId());
        storage.markSeen(player.getUniqueId(), player.getName());
        if (!firstTime || !enabled || amount <= 0.0) {
            return;
        }
        PlatformScheduler.runTaskLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                plugin.getLogger().warning("Failed to deposit join gift to " + player.getName() + ": " + response.errorMessage);
                return;
            }
            player.sendMessage(color(replace(text(
                    "messages.join-gift.welcome",
                    "&aWelcome to the server, you've been gifted &6$%amount%&a! Find your way to the casino!"
            ), Map.of(
                    "%amount%", MONEY.format(amount),
                    "%player%", player.getName()
            ))));
        }, 20L);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double amount() {
        return amount;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("join-gift.enabled", enabled);
        plugin.saveConfig();
    }

    public void setAmount(double amount) {
        this.amount = Math.max(0.0, amount);
        plugin.getConfig().set("join-gift.amount", this.amount);
        plugin.saveConfig();
    }

    public int seenCount() {
        return storage.seenCount();
    }

    public String text(String path, String fallback) {
        String v = plugin.getConfig().getString(path);
        if (v == null && plugin.getConfig().getDefaults() != null) {
            v = plugin.getConfig().getDefaults().getString(path);
        }
        return v == null ? fallback : v;
    }

    public String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String replace(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }
}
