package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    /**
     * F7 Sim Insta-Shot: No bow draw animation
     * Make getItemUseTimeLeft return maxUseTime so animation progress is 0
     */
    @Inject(method = "getUseItemRemainingTicks", at = @At("RETURN"), cancellable = true)
    private void hunchclient$noBowAnimation(CallbackInfoReturnable<Integer> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (entity instanceof Player) {
            IF7Sim f7sim = ModuleBridge.f7sim();

            if (f7sim != null && f7sim.isEnabled()) {
                if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof BowItem) {
                    // Return maxUseTime so (maxUseTime - itemUseTimeLeft) = 0 = no animation
                    int maxUseTime = entity.getUseItem().getItem().getUseDuration(entity.getUseItem(), entity);
                    cir.setReturnValue(maxUseTime);
                }
            }
        }
    }
}
