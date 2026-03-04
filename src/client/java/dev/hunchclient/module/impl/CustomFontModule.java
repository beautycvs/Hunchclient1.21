package dev.hunchclient.module.impl;

import dev.hunchclient.render.FontConfig;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.render.Font;
import dev.hunchclient.render.FontManager;
import dev.hunchclient.render.NVGRenderer;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Font Module
 * - Allows users to select custom fonts for Terminal GUI and SkeetTheme
 * - Optional: Replace all Minecraft fonts globally
 * - Fonts loaded from config/hunchclient/fonts/
 */
public class CustomFontModule extends Module implements ConfigurableModule, SettingsProvider {

    private static CustomFontModule instance;

    // Settings
    private int selectedFontIndex = 0;
    private boolean replaceMinecraftFont = false;
    private float fontSize = 1.0f; // 1.0 = 100% size
    private float descriptionScale = 0.5f; // 0.5 = 50% size

    public CustomFontModule() {
        super("CustomFont", "Custom fonts for GUI", Category.VISUALS, false);
        instance = this;
    }

    public static CustomFontModule getInstance() {
        if (instance == null) {
            instance = new CustomFontModule();
        }
        return instance;
    }

    @Override
    protected void onEnable() {
        // Apply selected font
        applyFont();
    }

    @Override
    protected void onDisable() {
        // Fonts remain available even when disabled
    }

    /**
     * Get the selected custom font
     */
    public Font getSelectedFont() {
        List<String> fontNames = FontManager.getAvailableFontNames();
        if (fontNames.isEmpty()) {
            return FontManager.getDefaultFont();
        }

        // Ensure index is valid
        if (selectedFontIndex < 0 || selectedFontIndex >= fontNames.size()) {
            selectedFontIndex = 0;
        }

        String fontName = fontNames.get(selectedFontIndex);
        Font font = FontManager.getFont(fontName);

        return font != null ? font : FontManager.getDefaultFont();
    }

    /**
     * Get the selected font name
     */
    public String getSelectedFontName() {
        List<String> fontNames = FontManager.getAvailableFontNames();
        if (fontNames.isEmpty()) {
            return "No fonts available";
        }

        if (selectedFontIndex < 0 || selectedFontIndex >= fontNames.size()) {
            selectedFontIndex = 0;
        }

        return fontNames.get(selectedFontIndex);
    }

    /**
     * Should Minecraft font be replaced globally
     */
    public boolean shouldReplaceMinecraftFont() {
        return isEnabled() && replaceMinecraftFont;
    }

    /**
     * Get font size multiplier (1.0 = 100%)
     */
    public float getFontSize() {
        return fontSize;
    }
    public float getDescriptionScale() {
        return descriptionScale;
    }
    
    /**
     * Apply the selected font
     */
    private void applyFont() {
        Font selectedFont = getSelectedFont();
        if (selectedFont != null) {
            FontConfig.setSelectedFont(selectedFont.name);
            NVGRenderer.clearFontCache();
            System.out.println("[CustomFont] Applied font: " + selectedFont.name);
        }
    }

    // ==================== Settings ====================

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        try {
            // Font Selection Dropdown
            List<String> fontNames = FontManager.getAvailableFontNames();
            if (fontNames == null || fontNames.isEmpty()) {
                fontNames = List.of("No fonts available");
            }
            String[] fontOptions = fontNames.toArray(new String[0]);

            settings.add(new DropdownSetting(
                "Font",
                "Select custom font",
                "font_selection",
                fontOptions,
                () -> selectedFontIndex,
                index -> {
                    selectedFontIndex = index;
                    applyFont();
                }
            ));

            // Replace Minecraft Font Toggle
            settings.add(new CheckboxSetting(
                "Replace Minecraft Font",
                "Use custom font globally in Minecraft",
                "replace_mc_font",
                () -> replaceMinecraftFont,
                value -> {
                    replaceMinecraftFont = value;
                    applyFont();
                }
            ));

            // Font Size Slider (stored as 0.5-2.0, displayed as 50%-200%)
            settings.add(new SliderSetting(
                "Font Size",
                "Adjust font size (100% = default)",
                "font_size",
                50f,   // Min: 50%
                200f,  // Max: 200%
                () -> fontSize * 100f, // Convert to percentage for display
                value -> {
                    fontSize = value / 100f; // Convert back to multiplier
                    NVGRenderer.clearFontCache(); // Clear cache when size changes
                    dev.hunchclient.gui.GuiSettings.getInstance().setFontSize(fontSize); // Sync to GuiSettings so the GUI actually uses this size
                }
            ).withDecimals(0).withSuffix("%"));
                
            settings.add(new SliderSetting(
                "Description Scale",
                "Scale of module description text",
                "description_scale",
                10f,
                100f,
                () -> descriptionScale * 100f,
                value -> descriptionScale = value / 100f
            ).withDecimals(0).withSuffix("%"));
            
            // Reload Fonts Button
            settings.add(new ButtonSetting(
                "Reload Fonts",
                "Reload all fonts from config/hunchclient/fonts/",
                "reload_fonts",
                () -> {
                    FontManager.reloadFonts();
                    applyFont();
                    System.out.println("[CustomFont] Fonts reloaded!");
                }
            ));

            // Info button for fonts directory
            String fontsDir = FontManager.getFontsDirectory().toAbsolutePath().toString();
            settings.add(new ButtonSetting(
                "Open Fonts Folder",
                "Click to open: " + fontsDir,
                "open_fonts_folder",
                () -> {
                    try {
                        java.awt.Desktop.getDesktop().open(FontManager.getFontsDirectory().toFile());
                    } catch (Exception e) {
                        System.err.println("[CustomFont] Failed to open fonts folder: " + e.getMessage());
                    }
                }
            ));
        } catch (Exception e) {
            System.err.println("[CustomFontModule] Error creating settings: " + e.getMessage());
            e.printStackTrace();
        }

        return settings;
    }

    // ==================== Config ====================

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enabled", isEnabled());
        config.addProperty("selectedFontIndex", selectedFontIndex);
        config.addProperty("replaceMinecraftFont", replaceMinecraftFont);
        config.addProperty("fontSize", fontSize);
        config.addProperty("descriptionScale", descriptionScale);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("enabled")) {
            setEnabled(data.get("enabled").getAsBoolean());
        }
        if (data.has("selectedFontIndex")) {
            selectedFontIndex = data.get("selectedFontIndex").getAsInt();
        }
        if (data.has("replaceMinecraftFont")) {
            replaceMinecraftFont = data.get("replaceMinecraftFont").getAsBoolean();
        }
        if (data.has("fontSize")) {
            fontSize = data.get("fontSize").getAsFloat();
            dev.hunchclient.gui.GuiSettings.getInstance().setFontSize(fontSize); // Sync saved font size to GuiSettings on startup
        }
        if (data.has("descriptionScale")) {
            descriptionScale = data.get("descriptionScale").getAsFloat();
        }
        // Apply font after loading config
        if (isEnabled()) {
            applyFont();
        }
    }
}
