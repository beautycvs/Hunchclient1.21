package dev.hunchclient.module;

import net.minecraft.client.player.LocalPlayer;

/**
 * Public API for Animations functionality - called by mixins
 * This interface stays readable, implementation gets obfuscated
 */
public interface IAnimations {
    // Offset methods
    float getOffsetX();
    float getOffsetY();
    float getOffsetZ();

    // Swing animation methods
    float getSwingSpeed();
    float getSwingIntensity();
    float getSwingRotationScale();
    float getSwingTranslationScale();
    boolean shouldZeroSwing(LocalPlayer player);

    // Equipment methods
    float getEquipBobbingSpeed();
    boolean isNoEquipReset();

    // Rotation methods
    float getYaw();
    float getPitch();
    float getRoll();

    // Scale method
    float getScaleMultiplier();

    // Misc methods
    boolean isIgnoreHaste();
    boolean isEnabled();
}
