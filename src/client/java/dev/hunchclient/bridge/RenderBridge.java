package dev.hunchclient.bridge;

import dev.hunchclient.render.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;

public final class RenderBridge {

    private RenderBridge() {}

    // === MobGlow constants (DataComponentType references) ===

    public static final int NO_GLOW = MobGlow.NO_GLOW;

    public static Object entityHasCustomGlow() {
        return MobGlow.ENTITY_HAS_CUSTOM_GLOW;
    }

    public static Object frameUsesCustomGlow() {
        return MobGlow.FRAME_USES_CUSTOM_GLOW;
    }

    // === MobGlow ===

    public static boolean hasOrComputeMobGlow(Entity entity) {
        return MobGlow.hasOrComputeMobGlow(entity);
    }

    public static int getMobGlowOrDefault(Entity entity, int defaultColor) {
        return MobGlow.getMobGlowOrDefault(entity, defaultColor);
    }

    // === CustomFontRenderQueue ===

    public static void startFontCollecting() {
        CustomFontRenderQueue.startCollecting();
    }

    public static boolean isFontCollecting() {
        return CustomFontRenderQueue.isCollecting();
    }

    public static void queueFontText(String text, float x, float y, float size,
                                      int color, boolean shadow, Object font, Object scissor) {
        CustomFontRenderQueue.queueText(text, x, y, size, color, shadow,
                (Font) font, (CustomFontRenderQueue.ScissorBox) scissor);
    }

    public static void queueStyledText(Component text, float x, float y, float size,
                                        int defaultColor, boolean shadow, Object font, Object scissor) {
        CustomFontRenderQueue.queueStyledText(text, x, y, size, defaultColor, shadow,
                (Font) font, (CustomFontRenderQueue.ScissorBox) scissor);
    }

    public static void queueStyledText(FormattedCharSequence text, float x, float y, float size,
                                        int defaultColor, boolean shadow, Object font, Object scissor) {
        CustomFontRenderQueue.queueStyledText(text, x, y, size, defaultColor, shadow,
                (Font) font, (CustomFontRenderQueue.ScissorBox) scissor);
    }

    public static void renderAllFonts(int framebufferWidth, int framebufferHeight) {
        CustomFontRenderQueue.renderAll(framebufferWidth, framebufferHeight);
    }

    public static void clearFontQueue() {
        CustomFontRenderQueue.clear();
    }

    // === ScissorStateTracker ===

    public static void pushScissor(int x1, int y1, int x2, int y2) {
        ScissorStateTracker.pushScissor(x1, y1, x2, y2);
    }

    public static void popScissor() {
        ScissorStateTracker.popScissor();
    }

    public static int[] getScissorBounds() {
        return ScissorStateTracker.getCurrentBounds();
    }

    public static boolean isScissorActive() {
        return ScissorStateTracker.isActive();
    }

    // === ViewmodelOverlayRenderer ===

    @SuppressWarnings("deprecation")
    public static boolean isOverlayCapturing() {
        return ViewmodelOverlayRenderer.isCapturing();
    }

    public static void captureWorldSnapshot() {
        ViewmodelOverlayRenderer.captureWorldSnapshot();
    }

    public static void applyOverlayPostProcess(float opacity, float parallax) {
        ViewmodelOverlayRenderer.applyOverlayPostProcess(opacity, parallax);
    }

    public static boolean hasWorldSnapshot() {
        return ViewmodelOverlayRenderer.hasWorldSnapshot();
    }

    // === OverlayTextureManager ===

    public static boolean hasOverlayTexture() {
        return OverlayTextureManager.hasTexture();
    }

    public static ResourceLocation getOverlayTextureId() {
        return OverlayTextureManager.getTextureId();
    }

    // === DarkModeRenderer ===

    public static void renderDarkModeOverlay() {
        DarkModeRenderer.renderDarkModeOverlay();
    }

    // === FirstPersonRenderContext ===

    public static void setFirstPerson(boolean firstPerson) {
        FirstPersonRenderContext.setFirstPerson(firstPerson);
    }

    public static boolean isFirstPerson() {
        return FirstPersonRenderContext.isFirstPerson();
    }

    public static void setDisplayContext(ItemDisplayContext context) {
        FirstPersonRenderContext.setDisplayContext(context);
    }

    // === ItemGlowRenderer ===

    public static void beginItemGlowCapture() {
        ItemGlowRenderer.beginCapture();
    }

    public static void endItemGlowCapture() {
        ItemGlowRenderer.endCaptureAndRender();
    }

    public static boolean isItemGlowCapturing() {
        return ItemGlowRenderer.isCapturing();
    }

    // === ScissorBox helpers (for DrawContextMixin) ===

    /** Capture the current GL scissor state. Returns Object (ScissorBox). */
    public static Object captureScissorBox() {
        return CustomFontRenderQueue.ScissorBox.capture();
    }

    /** Create a ScissorBox with given parameters. Returns Object (ScissorBox). */
    public static Object createScissorBox(int x, int y, int width, int height, boolean enabled) {
        return new CustomFontRenderQueue.ScissorBox(x, y, width, height, enabled);
    }

    /** Check if a ScissorBox (Object) has scissor enabled. */
    public static boolean isScissorBoxEnabled(Object scissorBox) {
        return ((CustomFontRenderQueue.ScissorBox) scissorBox).enabled;
    }

    // === CustomShaderManager ===

    public static void loadShaders(Object resourceProvider) {
        CustomShaderManager.loadShaders((net.minecraft.server.packs.resources.ResourceProvider) resourceProvider);
    }

    // === FirstPersonRenderContext (reset) ===

    public static void resetFirstPerson() {
        FirstPersonRenderContext.reset();
    }

    // === GUI scissor bounds (SkeetScreen2 bridge) ===

    public static int[] getGuiScissorBounds() {
        return dev.hunchclient.gui.SkeetScreen2.getGuiScissorBounds();
    }

    // === GalaxyRenderState ===

    public static float getGalaxyCameraYaw() {
        return GalaxyRenderState.getCameraYaw();
    }

    public static float getGalaxyCameraPitch() {
        return GalaxyRenderState.getCameraPitch();
    }
}
