/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package dev.hunchclient.module.impl;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.render.CustomShaderManager;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/* @Environment(value=EnvType.CLIENT)
public class GlowShaderSettingsModule
extends Module
implements ConfigurableModule,
SettingsProvider {
    public GlowShaderSettingsModule() {
        super("GlowShaderSettings", "Customize entity glow ESP appearance", Module.Category.VISUALS, false);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        CustomShaderManager.setGlowRadius(3.0f);
        CustomShaderManager.setFinalAlphaValue(0.1f);
        CustomShaderManager.setGlowStrength(1.0f);
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("glowRadius", (Number)Float.valueOf(CustomShaderManager.getGlowRadius()));
        config.addProperty("finalAlphaValue", (Number)Float.valueOf(CustomShaderManager.getFinalAlphaValue()));
        config.addProperty("glowStrength", (Number)Float.valueOf(CustomShaderManager.getGlowStrength()));
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) {
            return;
        }
        if (data.has("glowRadius")) {
            CustomShaderManager.setGlowRadius(data.get("glowRadius").getAsFloat());
        }
        if (data.has("finalAlphaValue")) {
            CustomShaderManager.setFinalAlphaValue(data.get("finalAlphaValue").getAsFloat());
        }
        if (data.has("glowStrength")) {
            CustomShaderManager.setGlowStrength(data.get("glowStrength").getAsFloat());
        }
    }

    @Override
    public List<ModuleSetting> getSettings() {
        ArrayList<ModuleSetting> settings = new ArrayList<ModuleSetting>();
        settings.add(new SliderSetting("Glow Radius", "How wide the glow extends from entities (higher = wider)", "glow_radius", 1.0f, 10.0f, CustomShaderManager::getGlowRadius, val -> CustomShaderManager.setGlowRadius(val.floatValue())).withDecimals(1).withSuffix(" px"));
        settings.add(new SliderSetting("Entity Fill Alpha", "Transparency of the filled entity area (0 = invisible, 1 = opaque)", "final_alpha_value", 0.0f, 1.0f, CustomShaderManager::getFinalAlphaValue, val -> CustomShaderManager.setFinalAlphaValue(val.floatValue())).withDecimals(2));
        settings.add(new SliderSetting("Glow Strength", "Overall glow intensity multiplier", "glow_strength", 0.0f, 3.0f, CustomShaderManager::getGlowStrength, val -> CustomShaderManager.setGlowStrength(val.floatValue())).withDecimals(2));
        return settings;
    }
}

 */