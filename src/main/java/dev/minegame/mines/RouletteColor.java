package dev.minegame.mines;

public enum RouletteColor {
    RED,
    BLACK,
    GREEN;

    public static RouletteColor fromInput(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase()) {
            case "red", "r" -> RED;
            case "black", "b" -> BLACK;
            case "green", "g" -> GREEN;
            default -> null;
        };
    }

    public String displayName() {
        return switch (this) {
            case RED -> "Red";
            case BLACK -> "Black";
            case GREEN -> "Green";
        };
    }

    public String colorCode() {
        return switch (this) {
            case RED -> "&c";
            case BLACK -> "&8";
            case GREEN -> "&a";
        };
    }
}
