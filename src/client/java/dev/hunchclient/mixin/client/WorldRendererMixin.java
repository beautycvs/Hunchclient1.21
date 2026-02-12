package dev.hunchclient.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.hunchclient.render.MobGlow;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds custom mob glow colors support to WorldRenderer
 * Based on Skyblocker's implementation for 1.21.9+
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

    @Shadow
    @Final
    private LevelRenderState levelRenderState;


    /**
     * Mark that custom glow is used this frame
     * In 1.21.10 Mojang: extractVisibleEntities sets haveGlowingEntities on LevelRenderState
     */
    @Inject(
        method = "extractVisibleEntities",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/LevelRenderState;haveGlowingEntities:Z", opcode = Opcodes.PUTFIELD)
    )
    private void hunchclient$markIfCustomGlowUsedThisFrame(CallbackInfo ci, @Local EntityRenderState entityRenderState) {
        if (entityRenderState.getDataOrDefault(MobGlow.ENTITY_HAS_CUSTOM_GLOW, false)) {
            this.levelRenderState.setData(MobGlow.FRAME_USES_CUSTOM_GLOW, true);
        }
    }
}
