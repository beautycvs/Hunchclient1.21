package dev.hunchclient.bridge.module;

public interface IViewmodelOverlay {
    boolean isEnabled();
    boolean isItemGlowEnabled();
    float getOverlayOpacity();
    float getParallaxIntensity();
}
