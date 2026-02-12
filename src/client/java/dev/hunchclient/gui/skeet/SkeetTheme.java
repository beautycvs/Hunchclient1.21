package dev.hunchclient.gui.skeet;

import dev.hunchclient.module.impl.SkeetThemeModule;

/**
 * Skeet.cc inspired color scheme and styling constants
 * Clean, professional theme system for the GUI
 * Colors are now dynamically loaded from SkeetThemeModule
 */
public class SkeetTheme {

    // Default colors (used as fallback if module not loaded)
    private static final int DEFAULT_BG_PRIMARY = 0xE8171717;
    private static final int DEFAULT_BG_SECONDARY = 0xFF1F1F1F;
    private static final int DEFAULT_BG_HOVER = 0xFF252525;
    private static final int DEFAULT_BG_ACTIVE = 0xFF2D2D2D;
    private static final int DEFAULT_BG_FIELD = 0xFF1A1A1A;

    private static final int DEFAULT_BORDER_DEFAULT = 0xFF2A2A2A;
    private static final int DEFAULT_BORDER_LIGHT = 0xFF3A3A3A;
    private static final int DEFAULT_BORDER_ACCENT = 0xFF6699CC;

    private static final int DEFAULT_ACCENT_PRIMARY = 0xFF6699CC;
    private static final int DEFAULT_ACCENT_GLOW = 0x806699CC;
    private static final int DEFAULT_ACCENT_DIM = 0x406699CC;

    private static final int DEFAULT_TEXT_PRIMARY = 0xFFF0F0F0;
    private static final int DEFAULT_TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int DEFAULT_TEXT_DIM = 0xFF808080;
    private static final int DEFAULT_TEXT_DISABLED = 0xFF505050;

    private static final int DEFAULT_STATUS_SUCCESS = 0xFF5BFF8B;
    private static final int DEFAULT_STATUS_ERROR = 0xFFFF5555;
    private static final int DEFAULT_STATUS_WARNING = 0xFFFFAA00;
    private static final int DEFAULT_STATUS_INFO = 0xFF55AAFF;

    // Dynamic color getters - read from SkeetThemeModule
    public static int BG_PRIMARY() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBgPrimary() : DEFAULT_BG_PRIMARY;
    }

    public static int BG_SECONDARY() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBgSecondary() : DEFAULT_BG_SECONDARY;
    }

    public static int BG_HOVER() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBgHover() : DEFAULT_BG_HOVER;
    }

    public static int BG_ACTIVE() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBgActive() : DEFAULT_BG_ACTIVE;
    }

    public static int BG_FIELD() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBgField() : DEFAULT_BG_FIELD;
    }

    public static int BORDER_DEFAULT() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBorderDefault() : DEFAULT_BORDER_DEFAULT;
    }

    public static int BORDER_LIGHT() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBorderLight() : DEFAULT_BORDER_LIGHT;
    }

    public static int BORDER_ACCENT() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getBorderAccent() : DEFAULT_BORDER_ACCENT;
    }

    public static int ACCENT_PRIMARY() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getAccentPrimary() : DEFAULT_ACCENT_PRIMARY;
    }

    public static int ACCENT_GLOW() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getAccentGlow() : DEFAULT_ACCENT_GLOW;
    }

    public static int ACCENT_DIM() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getAccentDim() : DEFAULT_ACCENT_DIM;
    }

    public static int TEXT_PRIMARY() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getTextPrimary() : DEFAULT_TEXT_PRIMARY;
    }

    public static int TEXT_SECONDARY() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getTextSecondary() : DEFAULT_TEXT_SECONDARY;
    }

    public static int TEXT_DIM() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getTextDim() : DEFAULT_TEXT_DIM;
    }

    public static int TEXT_DISABLED() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getTextDisabled() : DEFAULT_TEXT_DISABLED;
    }

    public static int STATUS_SUCCESS() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getStatusSuccess() : DEFAULT_STATUS_SUCCESS;
    }

    public static int STATUS_ERROR() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getStatusError() : DEFAULT_STATUS_ERROR;
    }

    public static int STATUS_WARNING() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getStatusWarning() : DEFAULT_STATUS_WARNING;
    }

    public static int STATUS_INFO() {
        SkeetThemeModule module = SkeetThemeModule.getInstance();
        return module != null ? module.getStatusInfo() : DEFAULT_STATUS_INFO;
    }

    // Keep old constants for backward compatibility (marked as deprecated)
    /** @deprecated Use BG_PRIMARY() method instead */
    @Deprecated public static final int BG_PRIMARY = DEFAULT_BG_PRIMARY;
    /** @deprecated Use BG_SECONDARY() method instead */
    @Deprecated public static final int BG_SECONDARY = DEFAULT_BG_SECONDARY;
    /** @deprecated Use BG_HOVER() method instead */
    @Deprecated public static final int BG_HOVER = DEFAULT_BG_HOVER;
    /** @deprecated Use BG_ACTIVE() method instead */
    @Deprecated public static final int BG_ACTIVE = DEFAULT_BG_ACTIVE;
    /** @deprecated Use BG_FIELD() method instead */
    @Deprecated public static final int BG_FIELD = DEFAULT_BG_FIELD;

    /** @deprecated Use BORDER_DEFAULT() method instead */
    @Deprecated public static final int BORDER_DEFAULT = DEFAULT_BORDER_DEFAULT;
    /** @deprecated Use BORDER_LIGHT() method instead */
    @Deprecated public static final int BORDER_LIGHT = DEFAULT_BORDER_LIGHT;
    /** @deprecated Use BORDER_ACCENT() method instead */
    @Deprecated public static final int BORDER_ACCENT = DEFAULT_BORDER_ACCENT;

    /** @deprecated Use ACCENT_PRIMARY() method instead */
    @Deprecated public static final int ACCENT_PRIMARY = DEFAULT_ACCENT_PRIMARY;
    /** @deprecated Use ACCENT_GLOW() method instead */
    @Deprecated public static final int ACCENT_GLOW = DEFAULT_ACCENT_GLOW;
    /** @deprecated Use ACCENT_DIM() method instead */
    @Deprecated public static final int ACCENT_DIM = DEFAULT_ACCENT_DIM;

    /** @deprecated Use TEXT_PRIMARY() method instead */
    @Deprecated public static final int TEXT_PRIMARY = DEFAULT_TEXT_PRIMARY;
    /** @deprecated Use TEXT_SECONDARY() method instead */
    @Deprecated public static final int TEXT_SECONDARY = DEFAULT_TEXT_SECONDARY;
    /** @deprecated Use TEXT_DIM() method instead */
    @Deprecated public static final int TEXT_DIM = DEFAULT_TEXT_DIM;
    /** @deprecated Use TEXT_DISABLED() method instead */
    @Deprecated public static final int TEXT_DISABLED = DEFAULT_TEXT_DISABLED;

    /** @deprecated Use STATUS_SUCCESS() method instead */
    @Deprecated public static final int STATUS_SUCCESS = DEFAULT_STATUS_SUCCESS;
    /** @deprecated Use STATUS_ERROR() method instead */
    @Deprecated public static final int STATUS_ERROR = DEFAULT_STATUS_ERROR;
    /** @deprecated Use STATUS_WARNING() method instead */
    @Deprecated public static final int STATUS_WARNING = DEFAULT_STATUS_WARNING;
    /** @deprecated Use STATUS_INFO() method instead */
    @Deprecated public static final int STATUS_INFO = DEFAULT_STATUS_INFO;

    // Dimensions (these remain static as they're not customizable)
    public static final int WINDOW_MIN_WIDTH = 450;
    public static final int WINDOW_MIN_HEIGHT = 400;
    public static final int WINDOW_DEFAULT_WIDTH = 550;
    public static final int WINDOW_DEFAULT_HEIGHT = 450;

    public static final int TITLE_BAR_HEIGHT = 30;
    public static final int TAB_WIDTH = 120;
    public static final int TAB_HEIGHT = 30;
    public static final int CONTENT_PADDING = 10;

    public static final int RESIZE_HANDLE_SIZE = 15;
    public static final int SCROLLBAR_WIDTH = 4;

    // Border Thickness
    public static final int BORDER_THIN = 1;
    public static final int BORDER_MEDIUM = 2;
    public static final int BORDER_THICK = 3;

    // Spacing
    public static final int SPACING_XS = 2;
    public static final int SPACING_SM = 5;
    public static final int SPACING_MD = 10;
    public static final int SPACING_LG = 15;
    public static final int SPACING_XL = 20;

    // Animation Durations (seconds)
    public static final float ANIM_FAST = 0.15f;
    public static final float ANIM_NORMAL = 0.2f;
    public static final float ANIM_SLOW = 0.3f;

    // Shadow/Glow
    public static final int SHADOW_OFFSET = 2;
    public static final int GLOW_SIZE = 8;

    /**
     * Apply alpha channel to color
     */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Apply alpha percentage (0.0 - 1.0) to color
     */
    public static int withAlpha(int color, float alphaPercent) {
        int alpha = (int) (alphaPercent * 255);
        return withAlpha(color, alpha);
    }

    /**
     * Blend two colors
     */
    public static int blend(int color1, int color2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Get lighter version of color
     */
    public static int lighter(int color, float amount) {
        return blend(color, 0xFFFFFFFF, amount);
    }

    /**
     * Get darker version of color
     */
    public static int darker(int color, float amount) {
        return blend(color, 0xFF000000, amount);
    }
}
