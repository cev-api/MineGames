package dev.minegame.mines;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

final class DiceItemFactory {
    // Mojang-hosted player-head skins with all six pip faces on the standard head UV layout.
    private static final Map<String, String> TEXTURE_HASHES = textureHashes();

    private final NamespacedKey diceItemKey;
    private final NamespacedKey colorKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey dieNumberKey;
    private final NamespacedKey dieIdKey;

    DiceItemFactory(MinegamePlugin plugin) {
        this.diceItemKey = new NamespacedKey(plugin, "dice_item");
        this.colorKey = new NamespacedKey(plugin, "dice_color");
        this.sessionKey = new NamespacedKey(plugin, "dice_session");
        this.dieNumberKey = new NamespacedKey(plugin, "dice_number");
        this.dieIdKey = new NamespacedKey(plugin, "dice_id");
    }

    ItemStack create() {
        return create("red", null, 0);
    }

    ItemStack create(String color) {
        return create(color, null, 0);
    }

    ItemStack createCraps(String color, UUID sessionId, int dieNumber) {
        return create(color, sessionId, dieNumber);
    }

    ItemStack refresh(ItemStack item) {
        if (!isDice(item)) {
            return item;
        }
        return create(color(item), sessionId(item), dieNumber(item));
    }

    ItemStack markCraps(ItemStack item, UUID sessionId, int dieNumber) {
        ItemStack marked = item.clone();
        marked.setAmount(1);
        ItemMeta meta = marked.getItemMeta();
        if (meta == null) {
            return createCraps(color(item), sessionId, dieNumber);
        }
        meta.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, sessionId.toString());
        meta.getPersistentDataContainer().set(dieNumberKey, PersistentDataType.INTEGER, dieNumber);
        marked.setItemMeta(meta);
        return marked;
    }

    private ItemStack create(String color, UUID sessionId, int dieNumber) {
        String selectedColor = normalizeColor(color);
        String textureUrl = "https://textures.minecraft.net/texture/" + TEXTURE_HASHES.get(selectedColor);
        // Include the texture hash in the profile id so clients do not reuse a cached
        // skin when a dice texture is corrected or replaced between plugin versions.
        UUID profileId = UUID.nameUUIDFromBytes(("minegames-dice-texture-" + textureUrl).getBytes(StandardCharsets.UTF_8));

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createPlayerProfile(profileId, "MineGamesDice");
        try {
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException ignored) {
            // The texture URL is a fixed, server-controlled value.
        }
        meta.setDisplayName("Dice");
        meta.getPersistentDataContainer().set(diceItemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(colorKey, PersistentDataType.STRING, selectedColor);
        meta.getPersistentDataContainer().set(dieIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        if (sessionId != null) {
            meta.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, sessionId.toString());
            meta.getPersistentDataContainer().set(dieNumberKey, PersistentDataType.INTEGER, dieNumber);
        }
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

    String color(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return "red";
        }
        String color = item.getItemMeta().getPersistentDataContainer().get(colorKey, PersistentDataType.STRING);
        return normalizeColor(color);
    }

    UUID sessionId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    int dieNumber(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(dieNumberKey, PersistentDataType.INTEGER, 0);
    }

    boolean isColor(String color) {
        return color != null && TEXTURE_HASHES.containsKey(color.toLowerCase(java.util.Locale.ROOT));
    }

    String normalizeColor(String color) {
        String normalized = color == null ? "red" : color.toLowerCase(java.util.Locale.ROOT);
        return isColor(normalized) ? normalized : "red";
    }

    java.util.Set<String> colors() {
        return TEXTURE_HASHES.keySet();
    }

    private static Map<String, String> textureHashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("red", "dcf3b0e748c0f0a33ba334327481c3646e1760f4eaf7125a52b8b14ff3a414f");
        hashes.put("black", "915f7c313bca9c2f958e68ab14ab393867d67503affff8f20cb13fbe917fd31");
        hashes.put("white", "797955462e4e576664499ac4a1c572f6143f19ad2d6194776198f8d136fdb2");
        hashes.put("blue", "2328f378f28a9872226f5ce04d6e1dfa111618587f48dfa1fe82d043216a5cf");
        hashes.put("green", "1c3cec68769fe9c971291edb7ef96a4e3b60462cfd5fb5baa1cbb3a71513e7b");
        hashes.put("yellow", "a02fc85f808b3fd9c1584c1869c1efcdd3baeaffe2c67d544fc98524984aa");
        hashes.put("orange", "a4efb34417d95faa94f25769a21676a022d263346c8553eb5525658b34269");
        hashes.put("cyan", "f88fa973ea444cc808768ea48bf2657ae9a56c0af60275e84463b195629bce");
        hashes.put("magenta", "5339838139264de26665fc1c5b85d658989262692d385a2e5af7b8aea2bdc6");
        hashes.put("lime", "2ceb713cd9cf919264b631e70f528bd20c43e79241695d6bfc9cfc7dcd63d");
        hashes.put("light_blue", "a5a6adc1af3d97863af0a70928ea5da6f6d663913c8f8af456f31e1c964");
        return Map.copyOf(hashes);
    }
}
