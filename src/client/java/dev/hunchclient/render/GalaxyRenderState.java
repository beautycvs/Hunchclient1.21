package dev.hunchclient.render;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Shared state for galaxy texture rendering between mixins
 */
public class GalaxyRenderState {

    private static final ThreadLocal<Boolean> replacingTexture = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> usingStencilMode = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<ResourceLocation> galaxyTextureId = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<Float> cameraYaw = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<Float> cameraPitch = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<Matrix4f> viewMatrix = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<Integer> layerCount = ThreadLocal.withInitial(() -> 0);

    public static boolean isReplacingTexture() {
        return Boolean.TRUE.equals(replacingTexture.get());
    }

    public static boolean isUsingStencilMode() {
        return Boolean.TRUE.equals(usingStencilMode.get());
    }

    public static void setUsingStencilMode(boolean usingStencil) {
        usingStencilMode.set(usingStencil);
    }

    public static ResourceLocation getGalaxyTextureId() {
        return galaxyTextureId.get();
    }

    public static void setReplacingTexture(boolean replacing) {
        replacingTexture.set(replacing);
    }

    public static void setGalaxyTextureId(ResourceLocation textureId) {
        galaxyTextureId.set(textureId);
    }

    public static float getCameraYaw() {
        Float yaw = cameraYaw.get();
        return yaw != null ? yaw : 0.0f;
    }

    public static void setCameraYaw(float yaw) {
        cameraYaw.set(yaw);
    }

    public static float getCameraPitch() {
        Float pitch = cameraPitch.get();
        return pitch != null ? pitch : 0.0f;
    }

    public static void setCameraPitch(float pitch) {
        cameraPitch.set(pitch);
    }

    public static Matrix4f getViewMatrix() {
        return viewMatrix.get();
    }

    public static void setViewMatrix(Matrix4f matrix) {
        viewMatrix.set(matrix != null ? new Matrix4f(matrix) : null);
    }

    public static int getLayerCount() {
        Integer count = layerCount.get();
        return count != null ? count : 0;
    }

    public static void incrementLayerCount() {
        layerCount.set(getLayerCount() + 1);
    }

    public static void resetLayerCount() {
        layerCount.set(0);
    }

    public static void clear() {
        replacingTexture.remove();
        usingStencilMode.remove();
        galaxyTextureId.remove();
        cameraYaw.remove();
        cameraPitch.remove();
        viewMatrix.remove();
        layerCount.remove();
    }
}
