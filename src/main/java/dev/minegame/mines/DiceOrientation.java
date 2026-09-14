package dev.minegame.mines;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Face placement for the bundled Mojang skins in an item display with context NONE. */
final class DiceOrientation {
    // UV order: top, bottom, left strip, front, right strip, back.
    // Red is NOT a recolour of the other skins: four of its faces move.
    private static final int[] RED = {6, 1, 5, 3, 2, 4};
    private static final int[] OTHER = {5, 2, 6, 3, 1, 4};
    // ModelPart skull normals after item-display Y(pi) and player-head X(pi).
    private static final Vector3f[] NORMALS = {
            new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
            new Vector3f(1, 0, 0), new Vector3f(0, 0, -1),
            new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1)
    };

    private DiceOrientation() { }

    static Quaternionf finalRotation(String color, int face, float yaw) {
        int[] layout = "red".equals(color) ? RED : OTHER;
        for (int slot = 0; slot < layout.length; slot++) {
            if (layout[slot] == face) {
                Quaternionf faceToUp = new Quaternionf().rotationTo(NORMALS[slot], new Vector3f(0, 1, 0));
                return new Quaternionf().rotateY(yaw).mul(faceToUp).normalize();
            }
        }
        throw new IllegalArgumentException("Die face must be 1..6: " + face);
    }
}
