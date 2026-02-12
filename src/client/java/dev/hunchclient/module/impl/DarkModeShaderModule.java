package dev.hunchclient.module.impl;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.ColorPickerSetting;
import dev.hunchclient.module.setting.DropdownSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.render.DarkModeRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Dark Mode Shader Module
 *
 * Applies a customizable color tint overlay to the world using Minecraft's native post-processing
 * Works WITH fullbright - visual tint only, doesn't affect lighting
 * Works in BOTH first-person and third-person (F5) views
 *
 * WATCHDOG SAFE: YES - Client-side rendering only
 */
public class DarkModeShaderModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IDarkModeShader {

    private static DarkModeShaderModule instance;

    // Settings
    private int tintColor = 0xFF3020C0; // Default: Dark purple-blue (ARGB)
    private float intensity = 0.6f; // 0-1
    private int blendMode = 0; // 0=Multiply, 1=Overlay, 2=Additive, 3=Screen
    private float vignetteStrength = 0.3f; // 0-1
    private float saturation = 1.0f; // 0-2
    private float contrast = 1.1f; // 0-2
    private float chromaticAberration = 0.002f; // 0-0.01 (much more subtle)
    private float brightness = 1.5f; // 0.1-5.0 (compensates for tint darkening)

    public DarkModeShaderModule() {
        super("DarkModeShader", "Color tint overlay (works with Fullbright)", Category.VISUALS, true);
        instance = this;
    }

    public static DarkModeShaderModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        // Renderer will be lazy-initialized on first render
        updateRendererSettings();
    }

    @Override
    protected void onDisable() {
        // Renderer stays initialized but won't be called
    }

    private void updateRendererSettings() {
        // Extract RGB components
        float r = ((tintColor >> 16) & 0xFF) / 255f;
        float g = ((tintColor >> 8) & 0xFF) / 255f;
        float b = (tintColor & 0xFF) / 255f;

        DarkModeRenderer.setTintColor(r, g, b);
        DarkModeRenderer.setIntensity(intensity);
        DarkModeRenderer.setBlendMode(blendMode);
        DarkModeRenderer.setVignetteStrength(vignetteStrength);
        DarkModeRenderer.setSaturation(saturation);
        DarkModeRenderer.setContrast(contrast);
        DarkModeRenderer.setChromaticAberration(chromaticAberration);
        DarkModeRenderer.setBrightness(brightness);
    }

    // Getters/Setters for settings
    public int getTintColor() {
        return tintColor;
    }

    public void setTintColor(int color) {
        this.tintColor = color;
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = Math.max(0.0f, Math.min(1.0f, intensity));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public int getBlendMode() {
        return blendMode;
    }

    public void setBlendMode(int mode) {
        this.blendMode = Math.max(0, Math.min(3, mode));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public String getBlendModeName() {
        return switch (blendMode) {
            case 0 -> "Multiply";
            case 1 -> "Overlay";
            case 2 -> "Additive";
            case 3 -> "Screen";
            default -> "Unknown";
        };
    }

    public float getVignetteStrength() {
        return vignetteStrength;
    }

    public void setVignetteStrength(float strength) {
        this.vignetteStrength = Math.max(0.0f, Math.min(1.0f, strength));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float sat) {
        this.saturation = Math.max(0.0f, Math.min(2.0f, sat));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float con) {
        this.contrast = Math.max(0.0f, Math.min(2.0f, con));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public float getChromaticAberration() {
        return chromaticAberration;
    }

    public void setChromaticAberration(float amount) {
        this.chromaticAberration = Math.max(0.0f, Math.min(0.01f, amount));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float bright) {
        this.brightness = Math.max(0.1f, Math.min(5.0f, bright));
        if (isEnabled()) {
            updateRendererSettings();
        }
    }

    // ==================== SettingsProvider Implementation ====================

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Tint Color Picker
        settings.add(new ColorPickerSetting(
            "Tint Color",
            "Overlay color (RGBA)",
            "darkmode_tint_color",
            this::getTintColor,
            this::setTintColor
        ));

        // Intensity Slider
        settings.add(new SliderSetting(
            "Intensity",
            "How strong the tint effect is (0-100%)",
            "darkmode_intensity",
            0.0f, 1.0f,
            this::getIntensity,
            this::setIntensity
        ).asPercentage());

        // Blend Mode Dropdown
        settings.add(new DropdownSetting(
            "Blend Mode",
            "How the tint is blended with the world",
            "darkmode_blend_mode",
            new String[]{"Multiply", "Overlay", "Additive", "Screen"},
            this::getBlendMode,
            this::setBlendMode
        ));

        // Vignette Strength
        settings.add(new SliderSetting(
            "Vignette",
            "Darkens screen edges (0-100%)",
            "darkmode_vignette",
            0.0f, 1.0f,
            this::getVignetteStrength,
            this::setVignetteStrength
        ).asPercentage());

        // Saturation
        settings.add(new SliderSetting(
            "Saturation",
            "Color saturation (0-200%, 100%=normal)",
            "darkmode_saturation",
            0.0f, 2.0f,
            this::getSaturation,
            this::setSaturation
        ).asPercentage());

        // Contrast
        settings.add(new SliderSetting(
            "Contrast",
            "Image contrast (0-200%, 100%=normal)",
            "darkmode_contrast",
            0.0f, 2.0f,
            this::getContrast,
            this::setContrast
        ).asPercentage());

        // Chromatic Aberration
        settings.add(new SliderSetting(
            "Chromatic Aberration",
            "RGB color shift at edges (0-1%, subtle effect)",
            "darkmode_chromatic",
            0.0f, 0.01f,
            this::getChromaticAberration,
            this::setChromaticAberration
        ).asPercentage());

        // Brightness
        settings.add(new SliderSetting(
            "Brightness",
            "Brightness multiplier (10-500%, compensates for tint darkening)",
            "darkmode_brightness",
            0.1f, 5.0f,
            this::getBrightness,
            this::setBrightness
        ).asPercentage());

        return settings;
    }

    // ==================== ConfigurableModule Implementation ====================

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enabled", isEnabled());
        config.addProperty("tintColor", tintColor);
        config.addProperty("intensity", intensity);
        config.addProperty("blendMode", blendMode);
        config.addProperty("vignetteStrength", vignetteStrength);
        config.addProperty("saturation", saturation);
        config.addProperty("contrast", contrast);
        config.addProperty("chromaticAberration", chromaticAberration);
        config.addProperty("brightness", brightness);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("enabled")) setEnabled(data.get("enabled").getAsBoolean());
        if (data.has("tintColor")) setTintColor(data.get("tintColor").getAsInt());
        if (data.has("intensity")) setIntensity(data.get("intensity").getAsFloat());
        if (data.has("blendMode")) setBlendMode(data.get("blendMode").getAsInt());
        if (data.has("vignetteStrength")) setVignetteStrength(data.get("vignetteStrength").getAsFloat());
        if (data.has("saturation")) setSaturation(data.get("saturation").getAsFloat());
        if (data.has("contrast")) setContrast(data.get("contrast").getAsFloat());
        if (data.has("chromaticAberration")) setChromaticAberration(data.get("chromaticAberration").getAsFloat());
        if (data.has("brightness")) setBrightness(data.get("brightness").getAsFloat());
    }
}
