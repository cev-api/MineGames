package dev.minegame.mines;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiceOrientationTest {
    @Test
    public void everyOutcomeMatchesActualSkinPipsAtEveryYaw() throws Exception {
        String[] colors = {"red", "black", "white", "blue", "green", "yellow",
                "orange", "cyan", "magenta", "lime", "light_blue"};
        int[][] uv = {{8, 0}, {16, 0}, {0, 8}, {8, 8}, {16, 8}, {24, 8}};
        // Raw ModelPart cube normals, independent of production orientation table.
        Vector3f[] raw = {new Vector3f(0, -1, 0), new Vector3f(0, 1, 0),
                new Vector3f(-1, 0, 0), new Vector3f(0, 0, -1),
                new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)};
        for (String color : colors) {
            BufferedImage skin = ImageIO.read(getClass().getResource("/dice/" + color + ".png"));
            for (int result = 1; result <= 6; result++) {
                for (int degrees = 0; degrees < 360; degrees += 15) {
                    Quaternionf rotation = DiceOrientation.finalRotation(color, result, (float) Math.toRadians(degrees));
                    int visible = 0;
                    for (int slot = 0; slot < uv.length; slot++) {
                        Vector3f normal = new Vector3f(raw[slot]);
                        normal.rotateX((float) Math.PI); // player_head item local transform
                        normal.rotateY((float) Math.PI); // ItemDisplayRenderer
                        rotation.transform(normal);
                        if (normal.y > 0.999F) {
                            assertEquals(color + " result=" + result + " yaw=" + degrees,
                                    result, countPips(skin, color, uv[slot]));
                            visible++;
                        }
                    }
                    assertEquals("Exactly one upward face", 1, visible);
                }
            }
        }
    }

    private int countPips(BufferedImage skin, String color, int[] uv) {
        int count = 0;
        for (int y = 2; y <= 6; y += 2) {
            for (int x = 2; x <= 6; x += 2) {
                int rgb = skin.getRGB(uv[0] + x, uv[1] + y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                boolean pip = switch (color) {
                    case "black" -> r + g + b > 400;
                    case "white" -> r + g + b < 400;
                    default -> Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 15;
                };
                if (pip) count++;
            }
        }
        return count;
    }
}
