package dev.hunchclient.mixin.client;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.FreecamModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Comprehensive mixin to block ALL interactions while in Freecam mode.
 * This prevents any packets from being sent that could reveal the player's
 * actual position doesn't match the camera position.
 */
@Mixin(MultiPlayerGameMode.class)
public class FreecamInteractionMixin {

    /**
     * Block starting to destroy a block.
     */
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Block continuing to destroy a block.
     */
    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onContinueDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Block stopping block destruction.
     */
    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onStopDestroyBlock(CallbackInfo ci) {
        if (hunchclient$shouldBlockInteraction()) {
            ci.cancel();
        }
    }

    /**
     * Block attacking entities.
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onAttack(Player player, Entity target, CallbackInfo ci) {
        if (hunchclient$shouldBlockInteraction()) {
            ci.cancel();
        }
    }

    /**
     * Block interacting with entities.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onInteract(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * Block interacting with entities at specific location.
     */
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onInteractAt(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * Block using items on blocks (right-click).
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * Block using items (right-click in air).
     */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hunchclient$shouldBlockInteraction()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * Check if we should block interactions.
     * Returns true if freecam is enabled and player control is NOT enabled.
     */
    @Unique
    private static boolean hunchclient$shouldBlockInteraction() {
        FreecamModule freecam = ModuleManager.getInstance().getModule(FreecamModule.class);
        return freecam != null && freecam.isFreecamEnabled() && !freecam.isPlayerControlEnabled();
    }
}
