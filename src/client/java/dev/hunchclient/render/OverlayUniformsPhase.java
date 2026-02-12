package dev.hunchclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hunchclient.module.impl.ViewmodelOverlayModule;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;

/**
 * Manager for shader uniforms for the overlay system
 * Updates: GameTime, UVOffset, OverlayRotation, OverlayBlendMode
 *
 * This is called before rendering first-person items to update dynamic uniform values
 */
public class OverlayUniformsPhase {

    // Cached values to compute parallax
    private static float smoothYaw = 0.0f;
    private static float smoothPitch = 0.0f;
    private static float rotation = 0.0f;
    private static float animationTime = 0.0f;

    /**
     * Update shader uniforms before rendering
     * Call this from HeldItemRendererMixin before renderItem
     *
     * NOTE: Uniform updates currently disabled - the RenderSystem.getShader() API
     * doesn't work in this Minecraft version. Uniforms are set via shader JSON defaults.
     */
    public static void updateUniforms() {
        ViewmodelOverlayModule module = ViewmodelOverlayModule.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        // Update parallax values (for future use)
        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();

        float smoothing = 0.3f;
        smoothYaw = smoothYaw * (1.0f - smoothing) + yaw * smoothing;
        smoothPitch = smoothPitch * (1.0f - smoothing) + pitch * smoothing;

        // Update rotation
        rotation += module.getRotationSpeed() * 0.01f;

        // Update animation time
        animationTime += module.getAnimationSpeed() * 0.05f;

        // TODO: Find alternative way to set shader uniforms in 1.21.10
        // The RenderSystem.getShader() method doesn't exist in this version
        // Possible alternatives:
        // 1. Use a custom mixin to inject into shader loading
        // 2. Use GameRenderer hooks
        // 3. Set uniforms in a custom RenderPhase
    }

    /**
     * Reset all state
     */
    public static void reset() {
        smoothYaw = 0.0f;
        smoothPitch = 0.0f;
        rotation = 0.0f;
        animationTime = 0.0f;
    }
}
