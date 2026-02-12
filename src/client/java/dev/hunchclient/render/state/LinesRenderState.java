package dev.hunchclient.render.state;

import net.minecraft.world.phys.Vec3;

/**
 * Render state for lines
 * Based on Skyblocker
 */
public class LinesRenderState {
    public Vec3[] points;
    public float[] colourComponents;
    public float alpha;
    public float lineWidth;
    public boolean throughWalls;
}
