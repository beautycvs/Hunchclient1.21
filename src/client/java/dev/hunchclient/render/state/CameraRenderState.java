package dev.hunchclient.render.state;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Camera render state
 * 1:1 from Skyblocker
 */
public class CameraRenderState {
    public Vec3 pos;
    public Quaternionf rotation;
    public float pitch;
    public float yaw;
}
