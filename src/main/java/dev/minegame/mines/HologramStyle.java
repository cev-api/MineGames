package dev.minegame.mines;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;

final class HologramStyle {
    private HologramStyle() {}

    static Component text(MinegamePlugin plugin, String value) {
        Component component = HologramManager.LEGACY.deserialize(value);
        String raw = plugin.getConfig().getString("hologram.foreground-color", "");
        if (raw != null && !raw.isBlank()) {
            TextColor color = TextColor.fromHexString(raw.trim());
            if (color != null) component = component.color(color);
        }
        return component;
    }

    static void apply(MinegamePlugin plugin, TextDisplay display) {
        display.setDefaultBackground(false);
        int backgroundOpacity = Math.max(0, Math.min(255, plugin.getConfig().getInt("hologram.background-opacity", 128)));
        display.setBackgroundColor(parseColor(plugin.getConfig().getString("hologram.background-color", "#000000"), backgroundOpacity));
        int opacity = Math.max(0, Math.min(255, plugin.getConfig().getInt("hologram.foreground-opacity", 255)));
        display.setTextOpacity((byte) opacity);
    }

    private static Color parseColor(String raw, int alpha) {
        try {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            int rgb = Integer.parseInt(hex, 16);
            return Color.fromARGB(alpha, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        } catch (RuntimeException ignored) { return Color.BLACK; }
    }
}