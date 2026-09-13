package dev.minegame.mines;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

final class DiceItemFactory {
    private static final UUID PROFILE_ID = UUID.nameUUIDFromBytes("minegames-dice-profile".getBytes(StandardCharsets.UTF_8));

    // Red cube texture by Archfiend_Dice, hosted by Mojang's texture service.
    // The standard 64x32 player-head UV layout contains all six pip faces.
    private static final String TEXTURE_URL = "https://textures.minecraft.net/texture/dcf3b0e748c0f0a33ba334327481c3646e1760f4eaf7125a52b8b14ff3a414f";
    private static final String TEXTURE_VALUE = Base64.getEncoder().encodeToString((
            "{\"textures\":{\"SKIN\":{\"url\":\"" + TEXTURE_URL + "\"}}}"
    ).getBytes(StandardCharsets.UTF_8));

    private final NamespacedKey diceItemKey;

    DiceItemFactory(MinegamePlugin plugin) {
        this.diceItemKey = new NamespacedKey(plugin, "dice_item");
    }

    ItemStack create() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(PROFILE_ID, "MineGamesDice");
        profile.setProperty(new ProfileProperty("textures", TEXTURE_VALUE));
        meta.setPlayerProfile(profile);
        meta.displayName(Component.text("Dice"));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(diceItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    boolean isDice(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(diceItemKey, PersistentDataType.BYTE);
    }
}
