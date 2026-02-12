package dev.hunchclient.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import dev.hunchclient.render.MobGlow;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityRenderer Mixin for NameProtect and Custom Glow
 * Hooks nametag rendering to sanitize player names in EntityRenderState
 * Adds custom glow color support to entities
 * ONLY modifies during rendering - does NOT change actual data
 * Updated for 1.21.10: renderLabelIfPresent signature changed to use EntityRenderState
 * New signature: renderLabelIfPresent(S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState)
 * The displayName is now stored in state.displayName field
 */
@Mixin(EntityRenderer.class)
public class EntityRenderMixin {

    /**
     * Modifies the displayName field in EntityRenderState before rendering
     * In 1.21.10, the Text is no longer a parameter but a field in EntityRenderState
     */
    @Inject(
        method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD")
    )
    private void hunchclient$sanitizeNametag(
        EntityRenderState state,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState,
        CallbackInfo ci
    ) {
        if (state.nameTag == null) {
            return;
        }

        INameProtect module = ModuleBridge.nameProtect();
        if (module != null) {
            state.nameTag = module.sanitizeText(state.nameTag);
        }
    }

    /**
     * Apply custom glow color to entity render state
     * Based on Skyblocker's implementation for 1.21.9+
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hunchclient$customGlow(CallbackInfo ci, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) EntityRenderState state) {
        boolean customGlow = MobGlow.hasOrComputeMobGlow(entity);
        boolean allowGlow = state.appearsGlowing() || customGlow;

        if (allowGlow && customGlow) {
            // Only apply custom flag if it doesn't have vanilla glow
            if (!entity.isCurrentlyGlowing()) {
                state.setData(MobGlow.ENTITY_HAS_CUSTOM_GLOW, true);
            }

            // Set the custom glow color!
            state.outlineColor = MobGlow.getMobGlowOrDefault(entity, MobGlow.NO_GLOW);
        } else if (!allowGlow) {
            state.outlineColor = EntityRenderState.NO_OUTLINE;
        }
    }

    /**
     * Disable frustum culling for starred mobs - forces them to always render
     * This bypasses vanilla and mod culling (EntityCulling, MoreCulling)
     */
    @Inject(method = "affectedByCulling", at = @At("HEAD"), cancellable = true)
    private void hunchclient$disableCullingForStarredMobs(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (MobGlow.hasOrComputeMobGlow(entity)) {
            cir.setReturnValue(false); // false = NOT affected by culling = always render
        }
    }

    /**
     * Force shouldRender to return true for starred mobs
     * This ensures they render even when outside frustum or far away
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hunchclient$forceRenderStarredMobs(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (MobGlow.hasOrComputeMobGlow(entity)) {
            cir.setReturnValue(true); // Always render starred mobs
        }
    }
}
