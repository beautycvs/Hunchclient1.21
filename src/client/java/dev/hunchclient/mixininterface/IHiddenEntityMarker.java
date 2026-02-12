package dev.hunchclient.mixininterface;

/**
 * Marker interface to track entities that should be hidden from rendering.
 * Applied to EntityRenderState via mixin.
 */
public interface IHiddenEntityMarker {
    boolean hunchclient$isHidden();
    void hunchclient$setHidden(boolean hidden);
}
