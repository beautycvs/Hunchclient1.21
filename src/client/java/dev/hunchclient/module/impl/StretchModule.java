package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Stretch Module - Emulates 4:3 aspect ratio (or custom)
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only rendering transformation
 * - No packets sent
 * - Visual only
 */
public class StretchModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IStretch {

    private float targetAspectRatio = 1.333f; // 4:3 default
    private boolean stretchEnabled = true;

    public StretchModule() {
        super("Stretch", "Stretches screen to custom aspect ratio (4:3 emulation)", Category.VISUALS, false);
    }

    @Override
    protected void onEnable() {
        // Enabled via rendering hooks
    }

    @Override
    protected void onDisable() {
        // Disabled via rendering hooks checking isEnabled()
    }

    public float calculateHorizontalScale() {
        if (!shouldApplyStretch()) {
            return 1.0f;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return 1.0f;
        }

        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();

        if (windowWidth <= 0 || windowHeight <= 0) {
            return 1.0f;
        }

        float currentAspect = (float) windowWidth / (float) windowHeight;
        float scaleX = currentAspect / targetAspectRatio;

        if (!Float.isFinite(scaleX) || scaleX <= 0.0f) {
            return 1.0f;
        }

        return scaleX;
    }

    // Config methods
    public float getTargetAspectRatio() {
        return targetAspectRatio;
    }

    public void setTargetAspectRatio(float ratio) {
        this.targetAspectRatio = Math.max(0.5f, Math.min(3.0f, ratio)); // Clamp between 0.5 and 3.0
    }

    public boolean isStretchEnabled() {
        return stretchEnabled && isEnabled();
    }

    public boolean shouldApplyStretch() {
        if (!isStretchEnabled()) {
            return false;
        }

        return Minecraft.getInstance() != null;
    }

    public void setStretchEnabled(boolean enabled) {
        this.stretchEnabled = enabled;
    }

    public void set4By3() {
        setTargetAspectRatio(1.333f);
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        object.addProperty("targetAspectRatio", this.targetAspectRatio);
        object.addProperty("stretchEnabled", this.stretchEnabled);
        return object;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("targetAspectRatio")) {
            setTargetAspectRatio(data.get("targetAspectRatio").getAsFloat());
        }
        if (data.has("stretchEnabled")) {
            this.stretchEnabled = data.get("stretchEnabled").getAsBoolean();
        }
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Active toggle
        settings.add(new CheckboxSetting(
            "Active",
            "Enable/disable stretch effect",
            "stretch_enabled",
            () -> stretchEnabled,
            (val) -> stretchEnabled = val
        ));

        // Aspect ratio slider
        settings.add(new SliderSetting(
            "Aspect Ratio",
            "Target aspect ratio (4:3 = 1.333)",
            "aspect_ratio",
            0.5f, 3.0f,
            () -> targetAspectRatio,
            (val) -> targetAspectRatio = val
        ).withDecimals(3));

        // Quick presets as dropdown
        settings.add(new DropdownSetting(
            "Presets",
            "Quick aspect ratio presets",
            "preset",
            new String[]{"4:3 (1.333)", "16:9 (1.778)", "16:10 (1.6)", "21:9 (2.333)", "Custom"},
            () -> {
                // Return index based on current ratio
                if (Math.abs(targetAspectRatio - 1.333f) < 0.01f) return 0;
                if (Math.abs(targetAspectRatio - 1.778f) < 0.01f) return 1;
                if (Math.abs(targetAspectRatio - 1.6f) < 0.01f) return 2;
                if (Math.abs(targetAspectRatio - 2.333f) < 0.01f) return 3;
                return 4; // Custom
            },
            (index) -> {
                switch (index) {
                    case 0 -> setTargetAspectRatio(1.333f);  // 4:3
                    case 1 -> setTargetAspectRatio(1.778f);  // 16:9
                    case 2 -> setTargetAspectRatio(1.6f);    // 16:10
                    case 3 -> setTargetAspectRatio(2.333f);  // 21:9
                    // case 4: Custom - don't change
                }
            }
        ));

        return settings;
    }
}
