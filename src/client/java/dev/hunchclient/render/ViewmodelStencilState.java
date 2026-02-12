package dev.hunchclient.render;

/**
 * Tracks stencil buffer state for viewmodel overlay rendering.
 * The stencil is written during ItemFeatureRenderer.render() for first-person items,
 * then used by ViewmodelOverlayRenderer to apply the overlay.
 */
public class ViewmodelStencilState {

    private static boolean stencilCaptured = false;

    /**
     * Set whether stencil capture is complete this frame
     */
    public static void setStencilCaptured(boolean captured) {
        stencilCaptured = captured;
    }

    /**
     * Check if stencil was captured this frame
     */
    public static boolean isStencilCaptured() {
        return stencilCaptured;
    }

    /**
     * Reset stencil state for next frame
     */
    public static void reset() {
        stencilCaptured = false;
    }
}
