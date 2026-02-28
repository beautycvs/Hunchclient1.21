package dev.hunchclient.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Central configuration for all GUI settings
 * Manages window state, theme, and other preferences
 */
public class GuiSettings {

    private static GuiSettings instance;

    // Window state
    private int windowX = 0;
    private int windowY = 0;
    private int windowWidth = 450;
    private int windowHeight = 400;
    private float guiScale = 1.1f;
    private int selectedTab = 0;

    // Theme settings
    private String selectedTheme = "dark";
    private boolean customColorsEnabled = false;
    private String selectedFont = "default";
    private float fontSize = 0.7f;

    // Module card states (which modules are expanded)
    private Map<String, Boolean> expandedModules = new HashMap<>();

    // Theme color definitions
    private final Map<String, ThemeColors> themes = new HashMap<>();

    // Current theme colors (dynamically updated based on selectedTheme)
    private int bgColor = 0xFF1a1a1a;
    private int bgSecondaryColor = 0xFF252525;
    private int bgHoverColor = 0xFF303030;
    private int bgActiveColor = 0xFF353535;
    private int bgFieldColor = 0xFF202020;

    private int accentColor = 0xFF00ff88;
    private int accentSecondaryColor = 0xFF00cc66;
    private int accentGlowColor = 0x8800ff88;

    private int textPrimaryColor = 0xFFe0e0e0;
    private int textSecondaryColor = 0xFFa0a0a0;
    private int textDimColor = 0xFF606060;
    private int textDisabledColor = 0xFF404040;

    private int borderDefaultColor = 0xFF404040;
    private int borderAccentColor = 0xFF00ff88;
    private int borderActiveColor = 0xFF606060;

    private int statusSuccessColor = 0xFF00ff88;
    private int statusErrorColor = 0xFFff4444;
    private int statusWarningColor = 0xFFffaa00;
    private int statusInfoColor = 0xFF4488ff;

    // Singleton
    private GuiSettings() {
        initializeThemes();
        applyTheme(selectedTheme);
    }

    public static GuiSettings getInstance() {
        if (instance == null) {
            instance = new GuiSettings();
        }
        return instance;
    }

    /**
     * Initialize all available themes
     */
    private void initializeThemes() {
        // Dark Theme (Default)
        themes.put("dark", new ThemeColors(
            0xFF1a1a1a, 0xFF252525, 0xFF303030, 0xFF353535, 0xFF202020,  // BG colors
            0xFF00ff88, 0xFF00cc66, 0x8800ff88,                           // Accent colors
            0xFFe0e0e0, 0xFFa0a0a0, 0xFF606060, 0xFF404040,              // Text colors
            0xFF404040, 0xFF00ff88, 0xFF606060,                          // Border colors
            0xFF00ff88, 0xFFff4444, 0xFFffaa00, 0xFF4488ff               // Status colors
        ));

        // Light Theme
        themes.put("light", new ThemeColors(
            0xFFf5f5f5, 0xFFe8e8e8, 0xFFdcdcdc, 0xFFd0d0d0, 0xFFfafafa,
            0xFF00aa55, 0xFF008844, 0x8800aa55,
            0xFF202020, 0xFF505050, 0xFF808080, 0xFFa0a0a0,
            0xFFc0c0c0, 0xFF00aa55, 0xFFa0a0a0,
            0xFF00aa55, 0xFFcc0000, 0xFFff8800, 0xFF0066cc
        ));

        // Purple Theme
        themes.put("purple", new ThemeColors(
            0xFF1a0f1f, 0xFF251a2a, 0xFF302035, 0xFF3a2540, 0xFF201525,
            0xFFaa66ff, 0xFF8844dd, 0x88aa66ff,
            0xFFe0d0ff, 0xFFa090c0, 0xFF605070, 0xFF403050,
            0xFF604080, 0xFFaa66ff, 0xFF705090,
            0xFFaa66ff, 0xFFff4466, 0xFFffaa44, 0xFF66aaff
        ));

        // Blue Theme
        themes.put("blue", new ThemeColors(
            0xFF0f1a1f, 0xFF1a252a, 0xFF203035, 0xFF253a40, 0xFF152025,
            0xFF4488ff, 0xFF3366dd, 0x884488ff,
            0xFFd0e0ff, 0xFF90a0c0, 0xFF506070, 0xFF304050,
            0xFF405060, 0xFF4488ff, 0xFF506070,
            0xFF4488ff, 0xFFff4444, 0xFFffaa00, 0xFF44ff88
        ));

        // Red Theme
        themes.put("red", new ThemeColors(
            0xFF1f0f0f, 0xFF2a1a1a, 0xFF352020, 0xFF402525, 0xFF251515,
            0xFFff4444, 0xFFdd2222, 0x88ff4444,
            0xFFffd0d0, 0xFFc09090, 0xFF705050, 0xFF503030,
            0xFF604040, 0xFFff4444, 0xFF705050,
            0xFF44ff44, 0xFFff4444, 0xFFffaa00, 0xFF4488ff
        ));

        // Green Theme
        themes.put("green", new ThemeColors(
            0xFF0f1f0f, 0xFF1a2a1a, 0xFF203520, 0xFF254025, 0xFF152515,
            0xFF44ff44, 0xFF22dd22, 0x8844ff44,
            0xFFd0ffd0, 0xFF90c090, 0xFF507050, 0xFF305030,
            0xFF406040, 0xFF44ff44, 0xFF507050,
            0xFF44ff44, 0xFFff4444, 0xFFffaa00, 0xFF4488ff
        ));

        // Sunset Theme
        themes.put("sunset", new ThemeColors(
            0xFF1f1a0f, 0xFF2a251a, 0xFF353020, 0xFF403a25, 0xFF252015,
            0xFFff8844, 0xFFdd6622, 0x88ff8844,
            0xFFffe0d0, 0xFFc0a090, 0xFF706050, 0xFF504030,
            0xFF605040, 0xFFff8844, 0xFF706050,
            0xFF88ff44, 0xFFff4444, 0xFFffaa00, 0xFF4488ff
        ));

        // Midnight Theme
        themes.put("midnight", new ThemeColors(
            0xFF0a0a14, 0xFF0f0f1f, 0xFF14142a, 0xFF1a1a35, 0xFF08081a,
            0xFF6644ff, 0xFF4422dd, 0x886644ff,
            0xFFd0d0ff, 0xFF9090c0, 0xFF505070, 0xFF303050,
            0xFF403060, 0xFF6644ff, 0xFF505070,
            0xFF44ff88, 0xFFff4444, 0xFFffaa00, 0xFF4488ff
        ));

        // Neon Theme
        themes.put("neon", new ThemeColors(
            0xFF0a0a0a, 0xFF0f0f0f, 0xFF141414, 0xFF1a1a1a, 0xFF080808,
            0xFFff00ff, 0xFFdd00dd, 0x88ff00ff,
            0xFFffffff, 0xFFffaaff, 0xFFff66ff, 0xFFcc44cc,
            0xFFff00ff, 0xFFff00ff, 0xFFff00ff,
            0xFF00ff00, 0xFFff0000, 0xFFffff00, 0xFF00ffff
        ));

        // Ocean Theme
        themes.put("ocean", new ThemeColors(
            0xFF0a141f, 0xFF0f1f2a, 0xFF142a35, 0xFF1a3540, 0xFF081a25,
            0xFF00aaff, 0xFF0088dd, 0x8800aaff,
            0xFFd0e8ff, 0xFF90b0d0, 0xFF507090, 0xFF305070,
            0xFF406080, 0xFF00aaff, 0xFF507090,
            0xFF00ff88, 0xFFff4444, 0xFFffaa00, 0xFF88ff44
        ));
    }

    /**
     * Apply a theme by name
     */
    public void applyTheme(String themeName) {
        ThemeColors colors = themes.get(themeName);
        if (colors != null) {
            bgColor = colors.bgPrimary;
            bgSecondaryColor = colors.bgSecondary;
            bgHoverColor = colors.bgHover;
            bgActiveColor = colors.bgActive;
            bgFieldColor = colors.bgField;

            accentColor = colors.accentPrimary;
            accentSecondaryColor = colors.accentSecondary;
            accentGlowColor = colors.accentGlow;

            textPrimaryColor = colors.textPrimary;
            textSecondaryColor = colors.textSecondary;
            textDimColor = colors.textDim;
            textDisabledColor = colors.textDisabled;

            borderDefaultColor = colors.borderDefault;
            borderAccentColor = colors.borderAccent;
            borderActiveColor = colors.borderActive;

            statusSuccessColor = colors.statusSuccess;
            statusErrorColor = colors.statusError;
            statusWarningColor = colors.statusWarning;
            statusInfoColor = colors.statusInfo;
        }
    }

    // Getters and setters for GUI settings
    public int getWindowX() { return windowX; }
    public void setWindowX(int windowX) { this.windowX = windowX; }

    public int getWindowY() { return windowY; }
    public void setWindowY(int windowY) { this.windowY = windowY; }

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }

    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }

    public float getGuiScale() { return guiScale; }
    public void setGuiScale(float guiScale) { this.guiScale = guiScale; }

    public int getSelectedTab() { return selectedTab; }
    public void setSelectedTab(int selectedTab) { this.selectedTab = selectedTab; }

    public String getSelectedTheme() { return selectedTheme; }
    public void setSelectedTheme(String theme) {
        this.selectedTheme = theme;
        applyTheme(theme);
    }

    public boolean isCustomColorsEnabled() { return customColorsEnabled; }
    public void setCustomColorsEnabled(boolean enabled) { this.customColorsEnabled = enabled; }

    public String getSelectedFont() { return selectedFont; }
    public void setSelectedFont(String font) { this.selectedFont = font; }

    public float getFontSize() { return fontSize; }
    public void setFontSize(float size) { this.fontSize = size; }

    public Map<String, Boolean> getExpandedModules() { return expandedModules; }

    // Color getters
    public int getBgColor() { return bgColor; }
    public int getBgSecondaryColor() { return bgSecondaryColor; }
    public int getBgHoverColor() { return bgHoverColor; }
    public int getBgActiveColor() { return bgActiveColor; }
    public int getBgFieldColor() { return bgFieldColor; }

    public int getAccentColor() { return accentColor; }
    public int getAccentSecondaryColor() { return accentSecondaryColor; }
    public int getAccentGlowColor() { return accentGlowColor; }

    public int getTextPrimaryColor() { return textPrimaryColor; }
    public int getTextSecondaryColor() { return textSecondaryColor; }
    public int getTextDimColor() { return textDimColor; }
    public int getTextDisabledColor() { return textDisabledColor; }

    public int getBorderDefaultColor() { return borderDefaultColor; }
    public int getBorderAccentColor() { return borderAccentColor; }
    public int getBorderActiveColor() { return borderActiveColor; }

    public int getStatusSuccessColor() { return statusSuccessColor; }
    public int getStatusErrorColor() { return statusErrorColor; }
    public int getStatusWarningColor() { return statusWarningColor; }
    public int getStatusInfoColor() { return statusInfoColor; }

    /**
     * Theme color container
     */
    private static class ThemeColors {
        final int bgPrimary, bgSecondary, bgHover, bgActive, bgField;
        final int accentPrimary, accentSecondary, accentGlow;
        final int textPrimary, textSecondary, textDim, textDisabled;
        final int borderDefault, borderAccent, borderActive;
        final int statusSuccess, statusError, statusWarning, statusInfo;

        ThemeColors(int bgPrimary, int bgSecondary, int bgHover, int bgActive, int bgField,
                   int accentPrimary, int accentSecondary, int accentGlow,
                   int textPrimary, int textSecondary, int textDim, int textDisabled,
                   int borderDefault, int borderAccent, int borderActive,
                   int statusSuccess, int statusError, int statusWarning, int statusInfo) {
            this.bgPrimary = bgPrimary;
            this.bgSecondary = bgSecondary;
            this.bgHover = bgHover;
            this.bgActive = bgActive;
            this.bgField = bgField;
            this.accentPrimary = accentPrimary;
            this.accentSecondary = accentSecondary;
            this.accentGlow = accentGlow;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textDim = textDim;
            this.textDisabled = textDisabled;
            this.borderDefault = borderDefault;
            this.borderAccent = borderAccent;
            this.borderActive = borderActive;
            this.statusSuccess = statusSuccess;
            this.statusError = statusError;
            this.statusWarning = statusWarning;
            this.statusInfo = statusInfo;
        }
    }
}
