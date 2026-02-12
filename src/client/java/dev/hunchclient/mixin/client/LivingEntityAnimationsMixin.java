package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IAnimations;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAnimationsMixin implements IAnimations.LivingEntityAccessor {

    @Shadow private boolean swinging;
    @Shadow private int swingTime;
    @Shadow protected float attackAnim;
    @Shadow protected float oAttackAnim;

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void hunchclient$modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
        IAnimations module = ModuleBridge.animations();
        if (module == null || !module.isEnabled()) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        int length = 6;

        if (!module.isIgnoreHaste()) {
            if (MobEffectUtil.hasDigSpeed(self)) {
                length -= 1 + MobEffectUtil.getDigSpeedAmplification(self);
            } else if (self.hasEffect(MobEffects.MINING_FATIGUE)) {
                MobEffectInstance fatigue = self.getEffect(MobEffects.MINING_FATIGUE);
                if (fatigue != null) {
                    length += (1 + fatigue.getAmplifier()) * 2;
                }
            }
        }

        length = Math.max((int) (length * Math.exp(-module.getSwingSpeed())), 1);
        cir.setReturnValue(length);
    }

    @Override
    public void hunchclient$setHandSwinging(boolean swinging) {
        this.swinging = swinging;
    }

    @Override
    public void hunchclient$setHandSwingTicks(int ticks) {
        this.swingTime = ticks;
    }

    @Override
    public void hunchclient$setHandSwingProgress(float progress) {
        this.attackAnim = progress;
    }

    @Override
    public void hunchclient$setLastHandSwingProgress(float progress) {
        this.oAttackAnim = progress;
    }
}
