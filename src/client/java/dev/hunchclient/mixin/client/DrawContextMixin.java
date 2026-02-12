package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import dev.hunchclient.module.impl.CustomFontModule;
import dev.hunchclient.render.CustomFontRenderQueue;
import dev.hunchclient.render.Font;
import dev.hunchclient.render.NVGRenderer;
import dev.hunchclient.render.ScissorStateTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DrawContext Mixin for NameProtect - 1.21.8 CORRECT SIGNATURES
 * Hooks ALL GUI text rendering via DrawContext (used by all modern GUIs)
 * ONLY modifies during rendering - does NOT change actual data
 */
@Mixin(value = GuiGraphics.class, priority = 1000)
public class DrawContextMixin {

    // ===== PERFORMANCE OPTIMIZATION: Cache all reflection fields (lazy init to avoid flow obfuscation issues) =====
    private static java.lang.reflect.Field MATRICES_FIELD = null;
    private static java.lang.reflect.Field FONT_HEIGHT_FIELD = null;
    private static boolean REFLECTION_INITIALIZED = false;

    // SKIDFUSCATOR-SAFE: No try-catch in static block (flow obfuscation breaks exception handlers)
    // Instead we use lazy initialization on first use
    private static void ensureReflectionInitialized() {
        if (REFLECTION_INITIALIZED) return;
        REFLECTION_INITIALIZED = true;

        // Try multiple field names for matrices (obfuscation-safe)
        MATRICES_FIELD = findFieldSafe(GuiGraphics.class, "matrices", "field_52805", "Matrix3x2fStack");
        FONT_HEIGHT_FIELD = findFieldSafe(net.minecraft.client.gui.Font.class, "fontHeight", "field_1774", null);
    }

    // SKIDFUSCATOR-SAFE: Simple method without nested try-catch blocks
    private static java.lang.reflect.Field findFieldSafe(Class<?> clazz, String name1, String name2, String typeName) {
        java.lang.reflect.Field field = tryGetField(clazz, name1);
        if (field != null) return field;

        field = tryGetField(clazz, name2);
        if (field != null) return field;

        if (typeName != null) {
            field = findFieldByTypeName(clazz, typeName);
            if (field != null) {
                System.out.println("[DrawContextMixin] Found field by type: " + field.getName() + " (" + typeName + ")");
                return field;
            }
        }

        System.err.println("[DrawContextMixin] WARNING: Could not find field " + name1 + "/" + name2);
        return null;
    }

    // SKIDFUSCATOR-SAFE: Single try-catch per method (flow obfuscation can handle this)
    private static java.lang.reflect.Field tryGetField(Class<?> clazz, String name) {
        if (name == null) return null;
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }

    // SKIDFUSCATOR-SAFE: No try-catch, just iteration
    private static java.lang.reflect.Field findFieldByTypeName(Class<?> clazz, String typeName) {
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            if (field.getType().getName().contains(typeName)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private INameProtect hunchclient$getModule() {
        return ModuleBridge.nameProtect();
    }

    // ===== HOOK drawText() METHODS =====

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String hunchclient$sanitizeDrawTextString(String text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeString(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private FormattedCharSequence hunchclient$sanitizeDrawTextOrderedText(FormattedCharSequence text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeOrderedText(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component hunchclient$sanitizeDrawTextText(Component text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeText(text);
        }
        return text;
    }

    // ===== HOOK drawTextWithShadow() METHODS =====

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String hunchclient$sanitizeDrawTextWithShadowString(String text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeString(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private FormattedCharSequence hunchclient$sanitizeDrawTextWithShadowOrderedText(FormattedCharSequence text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeOrderedText(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component hunchclient$sanitizeDrawTextWithShadowText(Component text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeText(text);
        }
        return text;
    }

    // ===== HOOK drawCenteredTextWithShadow() METHODS =====

    @ModifyVariable(
        method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String hunchclient$sanitizeDrawCenteredTextWithShadowString(String text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeString(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component hunchclient$sanitizeDrawCenteredTextWithShadowText(Component text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeText(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private FormattedCharSequence hunchclient$sanitizeDrawCenteredTextWithShadowOrderedText(FormattedCharSequence text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeOrderedText(text);
        }
        return text;
    }

    // ===== HOOK drawStringWithBackdrop() METHOD (USED BY SCOREBOARD) =====

    @ModifyVariable(
        method = "drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component hunchclient$sanitizeDrawTextWithBackgroundText(Component text) {
        INameProtect module = hunchclient$getModule();
        if (module != null && text != null) {
            return module.sanitizeText(text);
        }
        return text;
    }

    /**
     * Intercept drawStringWithBackdrop(Component) to use custom font - USED BY SCOREBOARD
     */
    @Inject(
        method = "drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void customFont$drawStringWithBackdrop(net.minecraft.client.gui.Font textRenderer, Component text, int x, int y, int backgroundColor, int textColor,
                                                   CallbackInfo ci) {
        // SKIDFUSCATOR-SAFE: Initialize reflection fields lazily (not in static block)
        ensureReflectionInitialized();

        CustomFontModule fontModule = CustomFontModule.getInstance();
        // EARLY EXIT: Skip processing if module is disabled or font not ready
        if (fontModule == null || !fontModule.isEnabled() || !fontModule.shouldReplaceMinecraftFont()) {
            return;
        }

        Font customFont = fontModule.getSelectedFont();
        if (customFont == null || text == null) {
            return;
        }

        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

            // Get GUI scale factor to convert from scaled to framebuffer coordinates
            double scaleFactor = mc.getWindow().getGuiScale();

            // Get DrawContext to access matrix stack
            GuiGraphics context = (GuiGraphics) (Object) this;

            // Extract and apply full matrix transformation from DrawContext's 2D matrix stack
            float transformedX, transformedY;
            float matrixScale = 1.0f;

            if (MATRICES_FIELD != null) {
                try {
                    org.joml.Matrix3x2fStack matrices = (org.joml.Matrix3x2fStack) MATRICES_FIELD.get(context);

                    float adjustedX = x + 2;
                    float adjustedY = y;

                    transformedX = matrices.m00() * adjustedX + matrices.m10() * adjustedY + matrices.m20();
                    transformedY = matrices.m01() * adjustedX + matrices.m11() * adjustedY + matrices.m21();

                    matrixScale = (float) Math.sqrt(matrices.m00() * matrices.m00() + matrices.m01() * matrices.m01());
                } catch (Exception e) {
                    transformedX = x + 2;
                    transformedY = y;
                    matrixScale = 1.0f;
                }
            } else {
                transformedX = x + 2;
                transformedY = y;
                matrixScale = 1.0f;
            }

            // Convert transformed coordinates to framebuffer scale
            float nvgX = (float) (transformedX * scaleFactor);
            float nvgY = (float) (transformedY * scaleFactor);

            // Get text renderer size
            float textRendererSize = 9f;
            if (FONT_HEIGHT_FIELD != null) {
                try {
                    textRendererSize = (float) FONT_HEIGHT_FIELD.getInt(textRenderer);
                } catch (Exception ignored) {
                }
            }

            float fontSize = fontModule.getFontSize();
            float nvgSize = (float) (textRendererSize * matrixScale * scaleFactor * fontSize);

            // Get scissor from OpenGL state
            CustomFontRenderQueue.ScissorBox scissor = CustomFontRenderQueue.ScissorBox.capture();

            if (!scissor.enabled) {
                // First check ScissorStateTracker (captures all GuiGraphics.enableScissor calls)
                int[] trackedBounds = ScissorStateTracker.getCurrentBounds();
                if (trackedBounds != null && trackedBounds.length == 4) {
                    // trackedBounds is [x1, y1, x2, y2] in screen coords
                    int scX = trackedBounds[0];
                    int scY = trackedBounds[1];
                    int scWidth = trackedBounds[2] - trackedBounds[0];
                    int scHeight = trackedBounds[3] - trackedBounds[1];

                    int fbX = (int) (scX * scaleFactor);
                    int fbY = (int) (scY * scaleFactor);
                    int fbWidth = (int) (scWidth * scaleFactor);
                    int fbHeight = (int) (scHeight * scaleFactor);

                    int framebufferHeight = mc.getWindow().getHeight();
                    int glY = framebufferHeight - fbY - fbHeight;

                    scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                } else {
                    // Fallback to SkeetScreen2 bounds (legacy support)
                    int[] guiBounds = dev.hunchclient.gui.SkeetScreen2.getGuiScissorBounds();
                    if (guiBounds != null && guiBounds.length == 4) {
                        int fbX = (int) (guiBounds[0] * scaleFactor);
                        int fbY = (int) (guiBounds[1] * scaleFactor);
                        int fbWidth = (int) (guiBounds[2] * scaleFactor);
                        int fbHeight = (int) (guiBounds[3] * scaleFactor);

                        int framebufferHeight = mc.getWindow().getHeight();
                        int glY = framebufferHeight - fbY - fbHeight;

                        scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                    }
                }
            }

            // Queue STYLED text - use textColor as default color
            CustomFontRenderQueue.queueStyledText(text, nvgX, nvgY, nvgSize, textColor, true, customFont, scissor);

            ci.cancel();
        } catch (Exception e) {
            System.err.println("[CustomFont] Error queueing scoreboard text: " + e.getMessage());
        }
    }

    // ===== HOOK renderTooltip() METHODS (TOOLTIPS) =====
    // NOTE: renderTooltip(Component, int, int) and renderTooltip(Font, Component, int, int)
    // do not exist in Mojang mappings 1.21.10 GuiGraphics - tooltip sanitization
    // is handled via ItemStackTooltipMixin.getTooltip() instead

    // ===== CUSTOM FONT RENDERING =====

    /**
     * Intercept drawText(String) to use custom font
     */
    @Inject(
        method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void customFont$drawTextString(net.minecraft.client.gui.Font textRenderer, String text, int x, int y, int color, boolean shadow,
                                          CallbackInfo ci) {
        // SKIDFUSCATOR-SAFE: Initialize reflection fields lazily (not in static block)
        ensureReflectionInitialized();

        CustomFontModule fontModule = CustomFontModule.getInstance();
        // EARLY EXIT: Skip processing if module is disabled or font not ready
        if (fontModule == null || !fontModule.isEnabled() || !fontModule.shouldReplaceMinecraftFont()) {
            return;
        }

        Font customFont = fontModule.getSelectedFont();
        if (customFont == null || text == null || text.isEmpty()) {
            return;
        }

        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

            // Get GUI scale factor to convert from scaled to framebuffer coordinates
            double scaleFactor = mc.getWindow().getGuiScale();

            // OPTIMIZED: Use cached reflection field (initialized once at class load)
            float textRendererSize = 9f; // Default
            if (FONT_HEIGHT_FIELD != null) {
                try {
                    textRendererSize = (float) FONT_HEIGHT_FIELD.getInt(textRenderer);
                } catch (Exception ignored) {
                    // Use default
                }
            }

            // Get DrawContext to access matrix stack
            GuiGraphics context = (GuiGraphics) (Object) this;

            // Extract and apply full matrix transformation from DrawContext's 2D matrix stack
            float transformedX, transformedY;
            float matrixScale = 1.0f;

            if (MATRICES_FIELD != null) {
                try {
                    // OPTIMIZED: Use pre-cached field instead of getDeclaredField()
                    org.joml.Matrix3x2fStack matrices = (org.joml.Matrix3x2fStack) MATRICES_FIELD.get(context);

                    // Matrix3x2f format:
                    // [ m00 m10 m20 ]  <- X scale, rotation, X translation
                    // [ m01 m11 m21 ]  <- rotation, Y scale, Y translation

                    // Apply FULL matrix transformation to coordinates (includes rotation, scale, translation)
                    // Adjust x by +2 pixels BEFORE transformation for better alignment
                    float adjustedX = x + 2;
                    float adjustedY = y;

                    transformedX = matrices.m00() * adjustedX + matrices.m10() * adjustedY + matrices.m20();
                    transformedY = matrices.m01() * adjustedX + matrices.m11() * adjustedY + matrices.m21();

                    // Get scale component for font size calculation
                    // Use sqrt(m00^2 + m01^2) to get actual scale (works with rotation)
                    matrixScale = (float) Math.sqrt(matrices.m00() * matrices.m00() + matrices.m01() * matrices.m01());
                } catch (Exception e) {
                    // Fallback to identity transform
                    transformedX = x + 2;
                    transformedY = y;
                    matrixScale = 1.0f;
                }
            } else {
                // No cached field, use identity transform
                transformedX = x + 2;
                transformedY = y;
                matrixScale = 1.0f;
            }

            // Convert transformed coordinates to framebuffer scale
            float nvgX = (float) (transformedX * scaleFactor);
            float nvgY = (float) (transformedY * scaleFactor);

            // Combine TextRenderer size with matrix scale and user's font size setting
            // textRendererSize = base size from TextRenderer (tooltips may have different size)
            // matrixScale = transformation scale from matrix (rotation, scaling)
            // scaleFactor = GUI scale (1.0, 2.0, etc.)
            // fontSize = user's font size multiplier (0.5 - 2.0)
            // NOTE: fontSize only affects SIZE, NOT position (position stays fixed)
            float fontSize = fontModule.getFontSize();
            float nvgSize = (float) (textRendererSize * matrixScale * scaleFactor * fontSize);

            // Get scissor from OpenGL state (captures the actual active scissor at render time)
            CustomFontRenderQueue.ScissorBox scissor = CustomFontRenderQueue.ScissorBox.capture();

            // FALLBACK: If no GL scissor is active, use ScissorStateTracker or SkeetScreen2 GUI bounds
            if (!scissor.enabled) {
                // First check ScissorStateTracker (captures all GuiGraphics.enableScissor calls)
                int[] trackedBounds = ScissorStateTracker.getCurrentBounds();
                if (trackedBounds != null && trackedBounds.length == 4) {
                    // trackedBounds is [x1, y1, x2, y2] in screen coords
                    int scX = trackedBounds[0];
                    int scY = trackedBounds[1];
                    int scWidth = trackedBounds[2] - trackedBounds[0];
                    int scHeight = trackedBounds[3] - trackedBounds[1];

                    int fbX = (int) (scX * scaleFactor);
                    int fbY = (int) (scY * scaleFactor);
                    int fbWidth = (int) (scWidth * scaleFactor);
                    int fbHeight = (int) (scHeight * scaleFactor);

                    int framebufferHeight = mc.getWindow().getHeight();
                    int glY = framebufferHeight - fbY - fbHeight;

                    scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                } else {
                    // Fallback to SkeetScreen2 bounds (legacy support)
                    int[] guiBounds = dev.hunchclient.gui.SkeetScreen2.getGuiScissorBounds();
                    if (guiBounds != null && guiBounds.length == 4) {
                        int fbX = (int) (guiBounds[0] * scaleFactor);
                        int fbY = (int) (guiBounds[1] * scaleFactor);
                        int fbWidth = (int) (guiBounds[2] * scaleFactor);
                        int fbHeight = (int) (guiBounds[3] * scaleFactor);

                        int framebufferHeight = mc.getWindow().getHeight();
                        int glY = framebufferHeight - fbY - fbHeight;

                        scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                    }
                }
            }

            // Queue text for rendering at the end of the frame (always on top)
            CustomFontRenderQueue.queueText(text, nvgX, nvgY, nvgSize, color, shadow, customFont, scissor);

            ci.cancel();
        } catch (Exception e) {
            // Fallback to vanilla if error
            // System.err.println("[CustomFont] Error queueing text: " + e.getMessage());
        }
    }

    /**
     * Intercept drawText(Text) to use custom font
     */
    @Inject(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void customFont$drawTextText(net.minecraft.client.gui.Font textRenderer, Component text, int x, int y, int color, boolean shadow,
                                        CallbackInfo ci) {
        // SKIDFUSCATOR-SAFE: Initialize reflection fields lazily (not in static block)
        ensureReflectionInitialized();

        CustomFontModule fontModule = CustomFontModule.getInstance();
        // EARLY EXIT: Skip processing if module is disabled or font not ready
        if (fontModule == null || !fontModule.isEnabled() || !fontModule.shouldReplaceMinecraftFont()) {
            return;
        }

        Font customFont = fontModule.getSelectedFont();
        if (customFont == null || text == null) {
            return;
        }

        try {
            // Don't convert to String - that strips color information!
            // Instead extract styled segments directly from Text object

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

            // Get GUI scale factor to convert from scaled to framebuffer coordinates
            double scaleFactor = mc.getWindow().getGuiScale();

            // Get DrawContext to access matrix stack
            GuiGraphics context = (GuiGraphics) (Object) this;

            // Extract and apply full matrix transformation from DrawContext's 2D matrix stack
            float transformedX, transformedY;
            float matrixScale = 1.0f;

            if (MATRICES_FIELD != null) {
                try {
                    // OPTIMIZED: Use pre-cached field instead of getDeclaredField()
                    org.joml.Matrix3x2fStack matrices = (org.joml.Matrix3x2fStack) MATRICES_FIELD.get(context);

                    // Matrix3x2f format:
                    // [ m00 m10 m20 ]  <- X scale, rotation, X translation
                    // [ m01 m11 m21 ]  <- rotation, Y scale, Y translation

                    // Apply FULL matrix transformation to coordinates (includes rotation, scale, translation)
                    // Adjust x by +2 pixels BEFORE transformation for better alignment
                    float adjustedX = x + 2;
                    float adjustedY = y;

                    transformedX = matrices.m00() * adjustedX + matrices.m10() * adjustedY + matrices.m20();
                    transformedY = matrices.m01() * adjustedX + matrices.m11() * adjustedY + matrices.m21();

                    // Get scale component for font size calculation
                    // Use sqrt(m00^2 + m01^2) to get actual scale (works with rotation)
                    matrixScale = (float) Math.sqrt(matrices.m00() * matrices.m00() + matrices.m01() * matrices.m01());
                } catch (Exception e) {
                    // Fallback to identity transform
                    transformedX = x + 2;
                    transformedY = y;
                    matrixScale = 1.0f;
                }
            } else {
                // No cached field, use identity transform
                transformedX = x + 2;
                transformedY = y;
                matrixScale = 1.0f;
            }

            // Convert transformed coordinates to framebuffer scale
            float nvgX = (float) (transformedX * scaleFactor);
            float nvgY = (float) (transformedY * scaleFactor);

            // OPTIMIZED: Use cached reflection field
            float textRendererSize = 9f;
            if (FONT_HEIGHT_FIELD != null) {
                try {
                    textRendererSize = (float) FONT_HEIGHT_FIELD.getInt(textRenderer);
                } catch (Exception ignored) {
                    // Use default
                }
            }

            // Combine TextRenderer size with matrix scale and user's font size setting
            // textRendererSize = base size from TextRenderer (tooltips may have different size)
            // matrixScale = transformation scale from matrix (rotation, scaling)
            // scaleFactor = GUI scale (1.0, 2.0, etc.)
            // fontSize = user's font size multiplier (0.5 - 2.0)
            // NOTE: fontSize only affects SIZE, NOT position (position stays fixed)
            float fontSize = fontModule.getFontSize();
            float nvgSize = (float) (textRendererSize * matrixScale * scaleFactor * fontSize);

            // Get scissor from OpenGL state (captures the actual active scissor at render time)
            CustomFontRenderQueue.ScissorBox scissor = CustomFontRenderQueue.ScissorBox.capture();

            // FALLBACK: If no GL scissor is active, use ScissorStateTracker or SkeetScreen2 GUI bounds
            if (!scissor.enabled) {
                // First check ScissorStateTracker (captures all GuiGraphics.enableScissor calls)
                int[] trackedBounds = ScissorStateTracker.getCurrentBounds();
                if (trackedBounds != null && trackedBounds.length == 4) {
                    // trackedBounds is [x1, y1, x2, y2] in screen coords
                    int scX = trackedBounds[0];
                    int scY = trackedBounds[1];
                    int scWidth = trackedBounds[2] - trackedBounds[0];
                    int scHeight = trackedBounds[3] - trackedBounds[1];

                    int fbX = (int) (scX * scaleFactor);
                    int fbY = (int) (scY * scaleFactor);
                    int fbWidth = (int) (scWidth * scaleFactor);
                    int fbHeight = (int) (scHeight * scaleFactor);

                    int framebufferHeight = mc.getWindow().getHeight();
                    int glY = framebufferHeight - fbY - fbHeight;

                    scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                } else {
                    // Fallback to SkeetScreen2 bounds (legacy support)
                    int[] guiBounds = dev.hunchclient.gui.SkeetScreen2.getGuiScissorBounds();
                    if (guiBounds != null && guiBounds.length == 4) {
                        int fbX = (int) (guiBounds[0] * scaleFactor);
                        int fbY = (int) (guiBounds[1] * scaleFactor);
                        int fbWidth = (int) (guiBounds[2] * scaleFactor);
                        int fbHeight = (int) (guiBounds[3] * scaleFactor);

                        int framebufferHeight = mc.getWindow().getHeight();
                        int glY = framebufferHeight - fbY - fbHeight;

                        scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                    }
                }
            }

            // Queue STYLED text - this extracts colors from Text's Style objects!
            CustomFontRenderQueue.queueStyledText(text, nvgX, nvgY, nvgSize, color, shadow, customFont, scissor);

            ci.cancel();
        } catch (Exception e) {
            System.err.println("[CustomFont] Error queueing styled text: " + e.getMessage());
        }
    }

    /**
     * Intercept drawText(OrderedText) to use custom font
     */
    @Inject(
        method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void customFont$drawTextOrderedText(net.minecraft.client.gui.Font textRenderer, FormattedCharSequence text, int x, int y, int color, boolean shadow,
                                                CallbackInfo ci) {
        // SKIDFUSCATOR-SAFE: Initialize reflection fields lazily (not in static block)
        ensureReflectionInitialized();

        CustomFontModule fontModule = CustomFontModule.getInstance();
        // EARLY EXIT: Skip processing if module is disabled or font not ready
        if (fontModule == null || !fontModule.isEnabled() || !fontModule.shouldReplaceMinecraftFont()) {
            return;
        }

        Font customFont = fontModule.getSelectedFont();
        if (customFont == null || text == null) {
            return;
        }

        try {
            // Don't convert to String - that strips color information!
            // Instead extract styled segments directly from OrderedText

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

            // Get GUI scale factor to convert from scaled to framebuffer coordinates
            double scaleFactor = mc.getWindow().getGuiScale();

            // Get DrawContext to access matrix stack
            GuiGraphics context = (GuiGraphics) (Object) this;

            // Extract and apply full matrix transformation from DrawContext's 2D matrix stack
            float transformedX, transformedY;
            float matrixScale = 1.0f;

            if (MATRICES_FIELD != null) {
                try {
                    // OPTIMIZED: Use pre-cached field instead of getDeclaredField()
                    org.joml.Matrix3x2fStack matrices = (org.joml.Matrix3x2fStack) MATRICES_FIELD.get(context);

                    // Matrix3x2f format:
                    // [ m00 m10 m20 ]  <- X scale, rotation, X translation
                    // [ m01 m11 m21 ]  <- rotation, Y scale, Y translation

                    // Apply FULL matrix transformation to coordinates (includes rotation, scale, translation)
                    // Adjust x by +2 pixels BEFORE transformation for better alignment
                    float adjustedX = x + 2;
                    float adjustedY = y;

                    transformedX = matrices.m00() * adjustedX + matrices.m10() * adjustedY + matrices.m20();
                    transformedY = matrices.m01() * adjustedX + matrices.m11() * adjustedY + matrices.m21();

                    // Get scale component for font size calculation
                    // Use sqrt(m00^2 + m01^2) to get actual scale (works with rotation)
                    matrixScale = (float) Math.sqrt(matrices.m00() * matrices.m00() + matrices.m01() * matrices.m01());
                } catch (Exception e) {
                    // Fallback to identity transform
                    transformedX = x + 2;
                    transformedY = y;
                    matrixScale = 1.0f;
                }
            } else {
                // No cached field, use identity transform
                transformedX = x + 2;
                transformedY = y;
                matrixScale = 1.0f;
            }

            // Convert transformed coordinates to framebuffer scale
            float nvgX = (float) (transformedX * scaleFactor);
            float nvgY = (float) (transformedY * scaleFactor);

            // OPTIMIZED: Use cached reflection field
            float textRendererSize = 9f;
            if (FONT_HEIGHT_FIELD != null) {
                try {
                    textRendererSize = (float) FONT_HEIGHT_FIELD.getInt(textRenderer);
                } catch (Exception ignored) {
                    // Use default
                }
            }

            // Combine TextRenderer size with matrix scale and user's font size setting
            // textRendererSize = base size from TextRenderer (tooltips may have different size)
            // matrixScale = transformation scale from matrix (rotation, scaling)
            // scaleFactor = GUI scale (1.0, 2.0, etc.)
            // fontSize = user's font size multiplier (0.5 - 2.0)
            // NOTE: fontSize only affects SIZE, NOT position (position stays fixed)
            float fontSize = fontModule.getFontSize();
            float nvgSize = (float) (textRendererSize * matrixScale * scaleFactor * fontSize);

            // Get scissor from OpenGL state (captures the actual active scissor at render time)
            CustomFontRenderQueue.ScissorBox scissor = CustomFontRenderQueue.ScissorBox.capture();

            // FALLBACK: If no GL scissor is active, use ScissorStateTracker or SkeetScreen2 GUI bounds
            if (!scissor.enabled) {
                // First check ScissorStateTracker (captures all GuiGraphics.enableScissor calls)
                int[] trackedBounds = ScissorStateTracker.getCurrentBounds();
                if (trackedBounds != null && trackedBounds.length == 4) {
                    // trackedBounds is [x1, y1, x2, y2] in screen coords
                    int scX = trackedBounds[0];
                    int scY = trackedBounds[1];
                    int scWidth = trackedBounds[2] - trackedBounds[0];
                    int scHeight = trackedBounds[3] - trackedBounds[1];

                    int fbX = (int) (scX * scaleFactor);
                    int fbY = (int) (scY * scaleFactor);
                    int fbWidth = (int) (scWidth * scaleFactor);
                    int fbHeight = (int) (scHeight * scaleFactor);

                    int framebufferHeight = mc.getWindow().getHeight();
                    int glY = framebufferHeight - fbY - fbHeight;

                    scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                } else {
                    // Fallback to SkeetScreen2 bounds (legacy support)
                    int[] guiBounds = dev.hunchclient.gui.SkeetScreen2.getGuiScissorBounds();
                    if (guiBounds != null && guiBounds.length == 4) {
                        int fbX = (int) (guiBounds[0] * scaleFactor);
                        int fbY = (int) (guiBounds[1] * scaleFactor);
                        int fbWidth = (int) (guiBounds[2] * scaleFactor);
                        int fbHeight = (int) (guiBounds[3] * scaleFactor);

                        int framebufferHeight = mc.getWindow().getHeight();
                        int glY = framebufferHeight - fbY - fbHeight;

                        scissor = new CustomFontRenderQueue.ScissorBox(fbX, glY, fbWidth, fbHeight, true);
                    }
                }
            }

            // Queue STYLED text - this extracts colors from OrderedText's Style objects!
            CustomFontRenderQueue.queueStyledText(text, nvgX, nvgY, nvgSize, color, shadow, customFont, scissor);

            ci.cancel();
        } catch (Exception e) {
            System.err.println("[CustomFont] Error queueing styled text: " + e.getMessage());
        }
    }
}
