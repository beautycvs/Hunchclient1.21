package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.HunchClient;
import dev.hunchclient.accessor.PlayerEntityRenderStateAccessor;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.PlayerSizeSpinModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to apply player size scaling and spin transformations
 * WATCHDOG SAFE: Client-side only visual transformation
 */
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {

    static {
        HunchClient.LOGGER.info("PlayerEntityRendererMixin CLASS LOADED!");
    }

    // Capture the actual player name BEFORE NameProtect modifies it
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("HEAD"))
    private void hunchclient$storeActualName(Avatar avatar, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        // Avatar extends LivingEntity but Player has getGameProfile() - all client players are AbstractClientPlayer
        if (avatar instanceof Player player) {
            String actualName = player.getGameProfile().name();
            ((PlayerEntityRenderStateAccessor)(Object)state).hunchclient$setActualPlayerName(actualName);
        }
    }

    // Inject into scale() method to apply transformations
    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
    private void hunchclient$applyPlayerScale(AvatarRenderState state, PoseStack matrices, CallbackInfo ci) {
        PlayerSizeSpinModule module = ModuleCache.get(PlayerSizeSpinModule.class);
        if (module == null) {
            return;
        }

        // Get actual player name from accessor (captured before NameProtect)
        String playerName = ((PlayerEntityRenderStateAccessor)(Object)state).hunchclient$getActualPlayerName();
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        // Check if this is the local player
        boolean isSelf = Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.getGameProfile().name().equals(playerName);

        // Get model
        PlayerSizeSpinModule.PlayerModel model = module.getPlayerModel(playerName, isSelf);
        if (model == null) {
            return;
        }

        // Apply spin
        if (model.spin) {
            long time = System.currentTimeMillis();
            float angle = (time % 10000L) / 10000.0f * 360.0f * model.spinSpeed;
            if (model.invertSpin) {
                angle = -angle;
            }
            matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        }

        // Apply upside down
        if (model.upsideDown) {
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
            matrices.translate(0.0, 1.8, 0.0);
        }

        // Apply custom scale
        matrices.scale(model.scaleX, model.scaleY, model.scaleZ);
    }
}
