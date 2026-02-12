package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IBonzoStaffHelper;
import dev.hunchclient.bridge.module.IF7Sim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {

    /**
     * Prevent bow from entering "use" state in Terminator mode.
     * This stops the pull animation completely.
     */
    @Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
    private void hunchclient$preventBowUse(InteractionHand hand, CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        ItemStack stack = player.getItemInHand(hand);

        // Check if it's a bow and Terminator mode is active
        if (stack.getItem() instanceof BowItem) {
            IF7Sim f7sim = ModuleBridge.f7sim();
            if (f7sim != null && f7sim.isEnabled() && f7sim.isTerminatorMode()) {
                // Cancel the setCurrentHand - bow never enters "use" state
                ci.cancel();
            }
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void hunchclient$cancelVelocityForBonzoStaff(CallbackInfo ci) {
        IBonzoStaffHelper bsh = ModuleBridge.bonzoStaffHelper();
        if (bsh != null && bsh.shouldCancelVelocity()) {
            LocalPlayer player = (LocalPlayer) (Object) this;

            // Only cancel if on ground
            if (player.onGround()) {
                Vec3 currentVel = player.getDeltaMovement();
                player.setDeltaMovement(0, currentVel.y, 0);
            }
        }
    }

    /**
     * F7 Sim Insta-Shot: No slowdown when using bow
     * NOTE: In 1.21.10 the USING_ITEM_SPEED_FACTOR constant is no longer used as a bytecode constant
     * in LocalPlayer.aiStep(). The slowdown might be applied server-side or in a different location.
     * This mixin is now optional (require = 0) to prevent crashes.
     * TODO: Find new location for item-use slowdown if client-side modification is still needed.
     */
    @ModifyConstant(method = "aiStep", constant = @Constant(floatValue = 0.2F), require = 0)
    private float hunchclient$noBowSlowdown(float original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        IF7Sim f7sim = ModuleBridge.f7sim();

        if (f7sim != null && f7sim.isEnabled()) {
            // Check if holding bow
            if (player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem) {
                // Return 1.0F instead of 0.2F = no slowdown
                return 1.0F;
            }
        }
        return original;
    }
}
