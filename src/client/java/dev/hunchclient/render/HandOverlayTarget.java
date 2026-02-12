package dev.hunchclient.render;

/**
 * Stub for hand overlay target.
 * DISABLED: MC 1.21+ render-device system is incompatible with FBO switching during active RenderPass.
 * The old beginWrite()/clearColor()/clear() API no longer exists.
 */
public final class HandOverlayTarget {

    private HandOverlayTarget() {}

    public static boolean beginHandPass() {
        // Disabled - would crash with "Close the existing render pass"
        return false;
    }

    public static void endHandPass() {
        // No-op
    }

    public static int getHandColorGlId() {
        return -1;
    }
}
