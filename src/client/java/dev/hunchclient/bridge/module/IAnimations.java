package dev.hunchclient.bridge.module;

import net.minecraft.client.player.LocalPlayer;

public interface IAnimations {
    float getOffsetX();
    float getOffsetY();
    float getOffsetZ();
    float getSwingSpeed();
    float getSwingIntensity();
    float getSwingRotationScale();
    float getSwingTranslationScale();
    boolean shouldZeroSwing(LocalPlayer player);
    float getEquipBobbingSpeed();
    boolean isNoEquipReset();
    float getYaw();
    float getPitch();
    float getRoll();
    float getScaleMultiplier();
    boolean isIgnoreHaste();
    boolean isEnabled();

    interface LivingEntityAccessor {
        void hunchclient$setHandSwinging(boolean swinging);
        void hunchclient$setHandSwingTicks(int ticks);
        void hunchclient$setHandSwingProgress(float progress);
        void hunchclient$setLastHandSwingProgress(float progress);
    }
}
