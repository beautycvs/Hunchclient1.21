package dev.hunchclient.util;

import net.minecraft.client.Minecraft;

/** Mouse utilities for screen-space position and hover checks. */
public class MouseUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static float getMouseX() {
        return (float) mc.mouseHandler.xpos();
    }

    public static float getMouseY() {
        return (float) mc.mouseHandler.ypos();
    }

    public static boolean isAreaHovered(float x, float y, float w, float h) {
        float mouseX = getMouseX();
        float mouseY = getMouseY();
        return mouseX >= x && mouseX <= (x + w) && mouseY >= y && mouseY <= (y + h);
    }

    public static boolean isAreaHovered(float x, float y, float w) {
        float mouseX = getMouseX();
        float mouseY = getMouseY();
        return mouseX >= x && mouseX <= (x + w) && mouseY >= y;
    }
}
