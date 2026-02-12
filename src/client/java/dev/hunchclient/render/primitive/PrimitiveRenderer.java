package dev.hunchclient.render.primitive;

import dev.hunchclient.render.state.CameraRenderState;

/**
 * Interface for primitive renderers
 * 1:1 from Skyblocker
 */
public interface PrimitiveRenderer<T> {
    void submitPrimitives(T state, CameraRenderState cameraState);
}
