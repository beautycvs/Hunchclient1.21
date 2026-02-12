package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Module for customizing the Skeet GUI theme colors
 * Controls all color aspects of the SkeetScreen2 GUI
 */
public class SkeetThemeModule extends Module implements ConfigurableModule, SettingsProvider {

    private static SkeetThemeModule instance;

    // Background Colors (ARGB format)
    private int bgPrimary = 0xE8171717;        // Main dark background
    private int bgSecondary = 0xFF1F1F1F;      // Secondary background (tabs)
    private int bgHover = 0xFF252525;          // Hover state
    private int bgActive = 0xFF2D2D2D;         // Active/selected state
    private int bgField = 0xFF1A1A1A;          // Input fields

    // Border Colors
    private int borderDefault = 0xFF2A2A2A;    // Default border
    private int borderLight = 0xFF3A3A3A;      // Lighter border
    private int borderAccent = 0xFF6699CC;     // Accent border

    // Accent Colors
    private int accentPrimary = 0xFF6699CC;    // Blue accent (Skeet signature)
    private int accentGlow = 0x806699CC;       // Glowing accent (50% opacity)
    private int accentDim = 0x406699CC;        // Dimmed accent (25% opacity)

    // Text Colors
    private int textPrimary = 0xFFF0F0F0;      // Main text
    private int textSecondary = 0xFFAAAAAA;    // Secondary text
    private int textDim = 0xFF808080;          // Dimmed text
    private int textDisabled = 0xFF505050;     // Disabled text

    // Status Colors
    private int statusSuccess = 0xFF5BFF8B;    // Success/enabled
    private int statusError = 0xFFFF5555;      // Error/disabled
    private int statusWarning = 0xFFFFAA00;    // Warning
    private int statusInfo = 0xFF55AAFF;       // Info

    // Custom Font Settings
    private boolean useCustomFont = false;

    public SkeetThemeModule() {
        super("SkeetTheme", "Customize GUI Theme Colors", Category.MISC, false);
        instance = this;
    }

    public static SkeetThemeModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // Colors are always available, nothing to initialize
    }

    @Override
    public void onDisable() {
        // Nothing to do on disable - colors remain available
    }

    // ==================== Color Getters ====================

    public int getBgPrimary() { return bgPrimary; }
    public int getBgSecondary() { return bgSecondary; }
    public int getBgHover() { return bgHover; }
    public int getBgActive() { return bgActive; }
    public int getBgField() { return bgField; }

    public int getBorderDefault() { return borderDefault; }
    public int getBorderLight() { return borderLight; }
    public int getBorderAccent() { return borderAccent; }

    public int getAccentPrimary() { return accentPrimary; }
    public int getAccentGlow() { return accentGlow; }
    public int getAccentDim() { return accentDim; }

    public int getTextPrimary() { return textPrimary; }
    public int getTextSecondary() { return textSecondary; }
    public int getTextDim() { return textDim; }
    public int getTextDisabled() { return textDisabled; }

    public int getStatusSuccess() { return statusSuccess; }
    public int getStatusError() { return statusError; }
    public int getStatusWarning() { return statusWarning; }
    public int getStatusInfo() { return statusInfo; }

    public boolean useCustomFont() { return useCustomFont; }
    public void setUseCustomFont(boolean use) { this.useCustomFont = use; }

    // ==================== Color Setters ====================

    public void setBgPrimary(int color) { this.bgPrimary = color; }
    public void setBgSecondary(int color) { this.bgSecondary = color; }
    public void setBgHover(int color) { this.bgHover = color; }
    public void setBgActive(int color) { this.bgActive = color; }
    public void setBgField(int color) { this.bgField = color; }

    public void setBorderDefault(int color) { this.borderDefault = color; }
    public void setBorderLight(int color) { this.borderLight = color; }
    public void setBorderAccent(int color) { this.borderAccent = color; }

    public void setAccentPrimary(int color) { this.accentPrimary = color; }
    public void setAccentGlow(int color) { this.accentGlow = color; }
    public void setAccentDim(int color) { this.accentDim = color; }

    public void setTextPrimary(int color) { this.textPrimary = color; }
    public void setTextSecondary(int color) { this.textSecondary = color; }
    public void setTextDim(int color) { this.textDim = color; }
    public void setTextDisabled(int color) { this.textDisabled = color; }

    public void setStatusSuccess(int color) { this.statusSuccess = color; }
    public void setStatusError(int color) { this.statusError = color; }
    public void setStatusWarning(int color) { this.statusWarning = color; }
    public void setStatusInfo(int color) { this.statusInfo = color; }

    // ==================== SettingsProvider Implementation ====================

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Background Colors Section
        settings.add(new ColorPickerSetting("Primary Background", "Main window background", "theme_bg_primary",
            () -> bgPrimary, this::setBgPrimary));
        settings.add(new ColorPickerSetting("Secondary Background", "Tab bar background", "theme_bg_secondary",
            () -> bgSecondary, this::setBgSecondary));
        settings.add(new ColorPickerSetting("Hover Background", "Hover state color", "theme_bg_hover",
            () -> bgHover, this::setBgHover));
        settings.add(new ColorPickerSetting("Active Background", "Selected/active state", "theme_bg_active",
            () -> bgActive, this::setBgActive));
        settings.add(new ColorPickerSetting("Field Background", "Input field background", "theme_bg_field",
            () -> bgField, this::setBgField));

        // Border Colors Section
        settings.add(new ColorPickerSetting("Default Border", "Standard border color", "theme_border_default",
            () -> borderDefault, this::setBorderDefault));
        settings.add(new ColorPickerSetting("Light Border", "Light border accent", "theme_border_light",
            () -> borderLight, this::setBorderLight));
        settings.add(new ColorPickerSetting("Accent Border", "Highlighted borders", "theme_border_accent",
            () -> borderAccent, this::setBorderAccent));

        // Accent Colors Section
        settings.add(new ColorPickerSetting("Primary Accent", "Main accent color", "theme_accent_primary",
            () -> accentPrimary, this::setAccentPrimary));
        settings.add(new ColorPickerSetting("Glow Accent", "Glowing effects", "theme_accent_glow",
            () -> accentGlow, this::setAccentGlow));
        settings.add(new ColorPickerSetting("Dim Accent", "Dimmed accents", "theme_accent_dim",
            () -> accentDim, this::setAccentDim));

        // Text Colors Section
        settings.add(new ColorPickerSetting("Primary Text", "Main text color", "theme_text_primary",
            () -> textPrimary, this::setTextPrimary));
        settings.add(new ColorPickerSetting("Secondary Text", "Less important text", "theme_text_secondary",
            () -> textSecondary, this::setTextSecondary));
        settings.add(new ColorPickerSetting("Dim Text", "Dimmed text", "theme_text_dim",
            () -> textDim, this::setTextDim));
        settings.add(new ColorPickerSetting("Disabled Text", "Disabled elements", "theme_text_disabled",
            () -> textDisabled, this::setTextDisabled));

        // Status Colors Section
        settings.add(new ColorPickerSetting("Success Color", "Success/enabled state", "theme_status_success",
            () -> statusSuccess, this::setStatusSuccess));
        settings.add(new ColorPickerSetting("Error Color", "Error/failed state", "theme_status_error",
            () -> statusError, this::setStatusError));
        settings.add(new ColorPickerSetting("Warning Color", "Warning messages", "theme_status_warning",
            () -> statusWarning, this::setStatusWarning));
        settings.add(new ColorPickerSetting("Info Color", "Info messages", "theme_status_info",
            () -> statusInfo, this::setStatusInfo));

        // Custom Font Toggle
        settings.add(new CheckboxSetting("Use Custom Font", "Use font from CustomFont module", "theme_use_custom_font",
            () -> useCustomFont, this::setUseCustomFont));

        return settings;
    }

    // ==================== ConfigurableModule Implementation ====================

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enabled", isEnabled());

        // Background colors
        config.addProperty("bgPrimary", bgPrimary);
        config.addProperty("bgSecondary", bgSecondary);
        config.addProperty("bgHover", bgHover);
        config.addProperty("bgActive", bgActive);
        config.addProperty("bgField", bgField);

        // Border colors
        config.addProperty("borderDefault", borderDefault);
        config.addProperty("borderLight", borderLight);
        config.addProperty("borderAccent", borderAccent);

        // Accent colors
        config.addProperty("accentPrimary", accentPrimary);
        config.addProperty("accentGlow", accentGlow);
        config.addProperty("accentDim", accentDim);

        // Text colors
        config.addProperty("textPrimary", textPrimary);
        config.addProperty("textSecondary", textSecondary);
        config.addProperty("textDim", textDim);
        config.addProperty("textDisabled", textDisabled);

        // Status colors
        config.addProperty("statusSuccess", statusSuccess);
        config.addProperty("statusError", statusError);
        config.addProperty("statusWarning", statusWarning);
        config.addProperty("statusInfo", statusInfo);

        // Custom font
        config.addProperty("useCustomFont", useCustomFont);

        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("enabled")) setEnabled(data.get("enabled").getAsBoolean());

        // Background colors
        if (data.has("bgPrimary")) bgPrimary = data.get("bgPrimary").getAsInt();
        if (data.has("bgSecondary")) bgSecondary = data.get("bgSecondary").getAsInt();
        if (data.has("bgHover")) bgHover = data.get("bgHover").getAsInt();
        if (data.has("bgActive")) bgActive = data.get("bgActive").getAsInt();
        if (data.has("bgField")) bgField = data.get("bgField").getAsInt();

        // Border colors
        if (data.has("borderDefault")) borderDefault = data.get("borderDefault").getAsInt();
        if (data.has("borderLight")) borderLight = data.get("borderLight").getAsInt();
        if (data.has("borderAccent")) borderAccent = data.get("borderAccent").getAsInt();

        // Accent colors
        if (data.has("accentPrimary")) accentPrimary = data.get("accentPrimary").getAsInt();
        if (data.has("accentGlow")) accentGlow = data.get("accentGlow").getAsInt();
        if (data.has("accentDim")) accentDim = data.get("accentDim").getAsInt();

        // Text colors
        if (data.has("textPrimary")) textPrimary = data.get("textPrimary").getAsInt();
        if (data.has("textSecondary")) textSecondary = data.get("textSecondary").getAsInt();
        if (data.has("textDim")) textDim = data.get("textDim").getAsInt();
        if (data.has("textDisabled")) textDisabled = data.get("textDisabled").getAsInt();

        // Status colors
        if (data.has("statusSuccess")) statusSuccess = data.get("statusSuccess").getAsInt();
        if (data.has("statusError")) statusError = data.get("statusError").getAsInt();
        if (data.has("statusWarning")) statusWarning = data.get("statusWarning").getAsInt();
        if (data.has("statusInfo")) statusInfo = data.get("statusInfo").getAsInt();

        // Custom font
        if (data.has("useCustomFont")) useCustomFont = data.get("useCustomFont").getAsBoolean();
    }

    /**
     * Get the font to use for SkeetTheme
     * Returns custom font if enabled, otherwise NVGRenderer default
     */
    public dev.hunchclient.render.Font getFont() {
        if (useCustomFont) {
            CustomFontModule customFont = CustomFontModule.getInstance();
            if (customFont != null && customFont.isEnabled()) {
                return customFont.getSelectedFont();
            }
        }
        return dev.hunchclient.render.NVGRenderer.defaultFont;
    }
}
