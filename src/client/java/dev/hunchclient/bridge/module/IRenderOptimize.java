package dev.hunchclient.bridge.module;

public interface IRenderOptimize {
    boolean isEnabled();
    int getRemoveArmorMode();
    float getSneakCameraAmount();
    boolean shouldHideBlockBreakParticles();
    boolean shouldDisableHurtCamera();
    boolean shouldRemoveLightning();
    boolean shouldHideFallingBlockEntities();
    boolean shouldDisableVignette();
    boolean shouldDisableVanillaArmorHud();
    boolean shouldHidePotionOverlay();
    boolean shouldRemoveFireOverlay();
    boolean shouldDisableWaterOverlay();
    boolean shouldDisableSuffocatingOverlay();
    boolean shouldHideInventoryEffects();
    boolean shouldHideExplosionParticles();
    boolean shouldHideFallingDustParticles();
    boolean shouldDisableFog();
}
