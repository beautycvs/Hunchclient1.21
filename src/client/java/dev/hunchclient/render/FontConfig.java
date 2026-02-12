package dev.hunchclient.render;

import com.google.gson.JsonObject;
import dev.hunchclient.util.ConfigManagerUtil;

import java.io.IOException;

/**
 * Config for font selection
 * Saves the selected font name to config
 */
public class FontConfig {
    private static final String KEY_SELECTED_FONT = "selectedFont";
    private static final String KEY_TERMINAL_FONT = "terminalFont";
    private static final String KEY_GLOBAL_FONT = "globalFont";

    private static String selectedFont = null;
    private static String terminalFont = null;
    private static boolean useGlobalFont = false;

    static {
        // Ensure FontManager is initialized first
        try {
            FontManager.ensureLoaded();
        } catch (Exception e) {
            System.err.println("[FontConfig] Error initializing FontManager: " + e.getMessage());
        }
        load();
    }

    /**
     * Load font config from file
     */
    public static void load() {
        try {
            JsonObject config = ConfigManagerUtil.loadOrCreateConfig();

            if (config.has("fonts")) {
                JsonObject fonts = config.getAsJsonObject("fonts");

                if (fonts.has(KEY_SELECTED_FONT)) {
                    selectedFont = fonts.get(KEY_SELECTED_FONT).getAsString();
                }
                if (fonts.has(KEY_TERMINAL_FONT)) {
                    terminalFont = fonts.get(KEY_TERMINAL_FONT).getAsString();
                }
                if (fonts.has(KEY_GLOBAL_FONT)) {
                    useGlobalFont = fonts.get(KEY_GLOBAL_FONT).getAsBoolean();
                }

                System.out.println("[FontConfig] Loaded - Selected: " + selectedFont +
                                   ", Terminal: " + terminalFont +
                                   ", Global: " + useGlobalFont);
            }
        } catch (IOException e) {
            System.err.println("[FontConfig] Failed to load config: " + e.getMessage());
        }
    }

    /**
     * Save font config to file
     */
    public static void save() {
        try {
            JsonObject config = ConfigManagerUtil.loadOrCreateConfig();

            JsonObject fonts = new JsonObject();
            if (selectedFont != null) {
                fonts.addProperty(KEY_SELECTED_FONT, selectedFont);
            }
            if (terminalFont != null) {
                fonts.addProperty(KEY_TERMINAL_FONT, terminalFont);
            }
            fonts.addProperty(KEY_GLOBAL_FONT, useGlobalFont);

            config.add("fonts", fonts);

            ConfigManagerUtil.saveConfig(config);
            System.out.println("[FontConfig] Saved font config");
        } catch (IOException e) {
            System.err.println("[FontConfig] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Get the currently selected font
     */
    public static Font getSelectedFont() {
        if (selectedFont != null && FontManager.hasFont(selectedFont)) {
            Font font = FontManager.getFont(selectedFont);
            if (font != null) return font;
        }

        // Fallback to default font
        Font defaultFont = FontManager.getDefaultFont();
        if (defaultFont != null) {
            return defaultFont;
        }

        // Last resort - get first available font
        java.util.List<String> available = FontManager.getAvailableFontNames();
        if (available != null && !available.isEmpty()) {
            return FontManager.getFont(available.get(0));
        }

        System.err.println("[FontConfig] CRITICAL: No fonts available!");
        return null;
    }

    /**
     * Get the terminal font (if set, otherwise uses selected font)
     */
    public static Font getTerminalFont() {
        if (terminalFont != null && FontManager.hasFont(terminalFont)) {
            return FontManager.getFont(terminalFont);
        }
        return getSelectedFont();
    }

    /**
     * Set the selected font
     */
    public static void setSelectedFont(String fontName) {
        if (FontManager.hasFont(fontName)) {
            selectedFont = fontName;
            save();
            System.out.println("[FontConfig] Selected font changed to: " + fontName);
        } else {
            System.err.println("[FontConfig] Font not found: " + fontName);
        }
    }

    /**
     * Set the terminal font
     */
    public static void setTerminalFont(String fontName) {
        if (FontManager.hasFont(fontName)) {
            terminalFont = fontName;
            save();
            System.out.println("[FontConfig] Terminal font changed to: " + fontName);
        } else {
            System.err.println("[FontConfig] Font not found: " + fontName);
        }
    }

    /**
     * Get selected font name
     */
    public static String getSelectedFontName() {
        return selectedFont;
    }

    /**
     * Get terminal font name
     */
    public static String getTerminalFontName() {
        return terminalFont;
    }

    /**
     * Should use global font for Minecraft
     */
    public static boolean useGlobalFont() {
        return useGlobalFont;
    }

    /**
     * Set whether to use global font
     */
    public static void setUseGlobalFont(boolean use) {
        useGlobalFont = use;
        save();
    }
}
