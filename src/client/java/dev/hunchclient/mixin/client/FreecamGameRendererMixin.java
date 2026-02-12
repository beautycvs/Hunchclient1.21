package dev.hunchclient.mixin.client;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.FreecamModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Freecam-related GameRenderer modifications.
 * - Disables block outlines when interactions are blocked
 * - Makes raycast come from player position when player control is enabled
 */
@Mixin(GameRenderer.class)
public class FreecamGameRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    /**
     * Disables block outlines when in freecam with interactions blocked.
     * This provides visual feedback that clicking won't do anything.
     */
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        FreecamModule freecam = ModuleManager.getInstance().getModule(FreecamModule.class);
        if (freecam != null && freecam.isFreecamEnabled() && !freecam.isPlayerControlEnabled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * When player control is enabled in freecam, make the raycast (pick) come from
     * the actual player position instead of the camera position.
     * This allows interacting with blocks near the player while viewing from freecam.
     */
    @ModifyVariable(method = "pick(F)V", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"))
    private Entity hunchclient$onPick(Entity entity) {
        FreecamModule freecam = ModuleManager.getInstance().getModule(FreecamModule.class);
        if (freecam != null && freecam.isFreecamEnabled() && freecam.isPlayerControlEnabled()) {
            // When player control is enabled, use player for raycasting
            return minecraft.player;
        }
        return entity;
    }
}
