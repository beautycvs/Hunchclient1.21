package dev.hunchclient.module.impl.misc;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.DropdownSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;

import java.util.ArrayList;
import java.util.List;


public class RenderOptimizeModule extends Module implements ConfigurableModule, SettingsProvider {

    private static RenderOptimizeModule instance;

    // Original settings
    private boolean hideBlockBreakParticles = false;
    private boolean hideExplosionParticles = false;
    private boolean hideFallingDustParticles = false;
    private boolean hideFallingBlockEntities = false;
    private float sneakCameraAmount = 0.0f;


    private boolean removeFireOverlay = false;
    private boolean noHurtCamera = false;
    private boolean removeLightning = false;
    private boolean hideInventoryEffects = false;
    private boolean hidePotionOverlay = false;
    private boolean disableWaterOverlay = false;
    private boolean disableSuffocatingOverlay = false;
    private boolean disableVignette = false;
    private boolean disableVanillaArmorHud = false;
    private boolean disableFog = false;
    // Remove Armor: 0 = Disabled, 1 = Self Only, 2 = All Players
    private int removeArmorMode = 0;

    public RenderOptimizeModule() {
        super("RenderOptimize", "Hide block-break / explosion / falling block particles.", Category.VISUALS, true);
        instance = this;
    }

    public static RenderOptimizeModule getInstance() {
        return instance;
    }

    public static boolean shouldHideBlockBreakParticles() {
        return instance != null && instance.hideBlockBreakParticles;
    }

    public static boolean shouldHideExplosionParticles() {
        return instance != null && instance.hideExplosionParticles;
    }

    public static boolean shouldHideFallingDustParticles() {
        return instance != null && instance.hideFallingDustParticles;
    }

    public static boolean shouldHideFallingBlockEntities() {
        return instance != null && instance.hideFallingBlockEntities;
    }

    public static float getSneakCameraAmount() {
        return instance != null ? instance.sneakCameraAmount : 0.0f;
    }

    public static boolean shouldRemoveFireOverlay() {
        return instance != null && instance.isEnabled() && instance.removeFireOverlay;
    }

    public static boolean shouldDisableHurtCamera() {
        return instance != null && instance.isEnabled() && instance.noHurtCamera;
    }

    public static boolean shouldRemoveLightning() {
        return instance != null && instance.isEnabled() && instance.removeLightning;
    }

    public static boolean shouldHideInventoryEffects() {
        return instance != null && instance.isEnabled() && instance.hideInventoryEffects;
    }

    public static boolean shouldHidePotionOverlay() {
        return instance != null && instance.isEnabled() && instance.hidePotionOverlay;
    }

    public static boolean shouldDisableWaterOverlay() {
        return instance != null && instance.isEnabled() && instance.disableWaterOverlay;
    }

    public static boolean shouldDisableSuffocatingOverlay() {
        return instance != null && instance.isEnabled() && instance.disableSuffocatingOverlay;
    }

    public static boolean shouldDisableVignette() {
        return instance != null && instance.isEnabled() && instance.disableVignette;
    }

    public static boolean shouldDisableVanillaArmorHud() {
        return instance != null && instance.isEnabled() && instance.disableVanillaArmorHud;
    }

    public static boolean shouldDisableFog() {
        return instance != null && instance.isEnabled() && instance.disableFog;
    }

    /**
     * @return 0 = Disabled, 1 = Self Only, 2 = All Players
     */
    public static int getRemoveArmorMode() {
        return instance != null && instance.isEnabled() ? instance.removeArmorMode : 0;
    }

    public boolean isHideBlockBreakParticles() {
        return hideBlockBreakParticles;
    }

    public void setHideBlockBreakParticles(boolean hideBlockBreakParticles) {
        this.hideBlockBreakParticles = hideBlockBreakParticles;
    }

    public boolean isHideExplosionParticles() {
        return hideExplosionParticles;
    }

    public void setHideExplosionParticles(boolean hideExplosionParticles) {
        this.hideExplosionParticles = hideExplosionParticles;
    }

    public boolean isHideFallingDustParticles() {
        return hideFallingDustParticles;
    }

    public void setHideFallingDustParticles(boolean hideFallingDustParticles) {
        this.hideFallingDustParticles = hideFallingDustParticles;
    }

    public boolean isHideFallingBlockEntities() {
        return hideFallingBlockEntities;
    }

    public void setHideFallingBlockEntities(boolean hideFallingBlockEntities) {
        this.hideFallingBlockEntities = hideFallingBlockEntities;
    }

    public float getSneakCameraAmountValue() {
        return sneakCameraAmount;
    }

    public void setSneakCameraAmount(float sneakCameraAmount) {
        this.sneakCameraAmount = sneakCameraAmount;
    }

    // New getter/setter pairs for ported settings
    public boolean isRemoveFireOverlay() { return removeFireOverlay; }
    public void setRemoveFireOverlay(boolean v) { this.removeFireOverlay = v; }

    public boolean isNoHurtCamera() { return noHurtCamera; }
    public void setNoHurtCamera(boolean v) { this.noHurtCamera = v; }

    public boolean isRemoveLightning() { return removeLightning; }
    public void setRemoveLightning(boolean v) { this.removeLightning = v; }

    public boolean isHideInventoryEffects() { return hideInventoryEffects; }
    public void setHideInventoryEffects(boolean v) { this.hideInventoryEffects = v; }

    public boolean isHidePotionOverlay() { return hidePotionOverlay; }
    public void setHidePotionOverlay(boolean v) { this.hidePotionOverlay = v; }

    public boolean isDisableWaterOverlay() { return disableWaterOverlay; }
    public void setDisableWaterOverlay(boolean v) { this.disableWaterOverlay = v; }

    public boolean isDisableSuffocatingOverlay() { return disableSuffocatingOverlay; }
    public void setDisableSuffocatingOverlay(boolean v) { this.disableSuffocatingOverlay = v; }

    public boolean isDisableVignette() { return disableVignette; }
    public void setDisableVignette(boolean v) { this.disableVignette = v; }

    public boolean isDisableVanillaArmorHud() { return disableVanillaArmorHud; }
    public void setDisableVanillaArmorHud(boolean v) { this.disableVanillaArmorHud = v; }

    public boolean isDisableFog() { return disableFog; }
    public void setDisableFog(boolean v) { this.disableFog = v; }

    public int getRemoveArmorModeValue() { return removeArmorMode; }
    public void setRemoveArmorMode(int v) { this.removeArmorMode = v; }

    @Override
    protected void onEnable() {
        // No direct resources to manage.
    }

    @Override
    protected void onDisable() {
        // Nothing to clean up.
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("hideBlockBreakParticles", hideBlockBreakParticles);
        data.addProperty("hideExplosionParticles", hideExplosionParticles);
        data.addProperty("hideFallingDustParticles", hideFallingDustParticles);
        data.addProperty("hideFallingBlockEntities", hideFallingBlockEntities);
        data.addProperty("sneakCameraAmount", sneakCameraAmount);
        // New settings
        data.addProperty("removeFireOverlay", removeFireOverlay);
        data.addProperty("noHurtCamera", noHurtCamera);
        data.addProperty("removeLightning", removeLightning);
        data.addProperty("hideInventoryEffects", hideInventoryEffects);
        data.addProperty("hidePotionOverlay", hidePotionOverlay);
        data.addProperty("disableWaterOverlay", disableWaterOverlay);
        data.addProperty("disableSuffocatingOverlay", disableSuffocatingOverlay);
        data.addProperty("disableVignette", disableVignette);
        data.addProperty("disableVanillaArmorHud", disableVanillaArmorHud);
        data.addProperty("disableFog", disableFog);
        data.addProperty("removeArmorMode", removeArmorMode);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("hideBlockBreakParticles")) {
            hideBlockBreakParticles = data.get("hideBlockBreakParticles").getAsBoolean();
        }
        if (data.has("hideExplosionParticles")) {
            hideExplosionParticles = data.get("hideExplosionParticles").getAsBoolean();
        }
        if (data.has("hideFallingDustParticles")) {
            hideFallingDustParticles = data.get("hideFallingDustParticles").getAsBoolean();
        }
        if (data.has("hideFallingBlockEntities")) {
            hideFallingBlockEntities = data.get("hideFallingBlockEntities").getAsBoolean();
        }
        if (data.has("sneakCameraAmount")) {
            sneakCameraAmount = data.get("sneakCameraAmount").getAsFloat();
        }
        // New settings
        if (data.has("removeFireOverlay")) {
            removeFireOverlay = data.get("removeFireOverlay").getAsBoolean();
        }
        if (data.has("noHurtCamera")) {
            noHurtCamera = data.get("noHurtCamera").getAsBoolean();
        }
        if (data.has("removeLightning")) {
            removeLightning = data.get("removeLightning").getAsBoolean();
        }
        if (data.has("hideInventoryEffects")) {
            hideInventoryEffects = data.get("hideInventoryEffects").getAsBoolean();
        }
        if (data.has("hidePotionOverlay")) {
            hidePotionOverlay = data.get("hidePotionOverlay").getAsBoolean();
        }
        if (data.has("disableWaterOverlay")) {
            disableWaterOverlay = data.get("disableWaterOverlay").getAsBoolean();
        }
        if (data.has("disableSuffocatingOverlay")) {
            disableSuffocatingOverlay = data.get("disableSuffocatingOverlay").getAsBoolean();
        }
        if (data.has("disableVignette")) {
            disableVignette = data.get("disableVignette").getAsBoolean();
        }
        if (data.has("disableVanillaArmorHud")) {
            disableVanillaArmorHud = data.get("disableVanillaArmorHud").getAsBoolean();
        }
        if (data.has("disableFog")) {
            disableFog = data.get("disableFog").getAsBoolean();
        }
        if (data.has("removeArmorMode")) {
            removeArmorMode = data.get("removeArmorMode").getAsInt();
        }
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();
        settings.add(new CheckboxSetting(
            "Hide Block Break Particles",
            "Suppress vanilla block-breaking dust.",
            "renderopt_hide_block_break",
            () -> hideBlockBreakParticles,
            this::setHideBlockBreakParticles
        ));
        settings.add(new CheckboxSetting(
            "Hide Explosion Particles",
            "Prevent explosion dust and smoke particles from rendering.",
            "renderopt_hide_explosions",
            () -> hideExplosionParticles,
            this::setHideExplosionParticles
        ));
        settings.add(new CheckboxSetting(
            "Hide Falling Dust Particles",
            "Skip falling dust particles used by falling-block entities.",
            "renderopt_hide_falling_dust",
            () -> hideFallingDustParticles,
            this::setHideFallingDustParticles
        ));
        settings.add(new CheckboxSetting(
            "Hide Falling Block Entities",
            "Prevent sand/gravel/piston blocks from rendering while falling.",
            "renderopt_hide_falling_entities",
            () -> hideFallingBlockEntities,
            this::setHideFallingBlockEntities
        ));
        settings.add(new SliderSetting(
            "Sneak Camera Height",
            "Adjust camera height when sneaking (0% = disabled, 100% = 1.8.9 style full standing height)",
            "renderopt_sneak_camera",
            0.0f,
            1.0f,
            this::getSneakCameraAmountValue,
            this::setSneakCameraAmount
        ).asPercentage().withDecimals(0));

        settings.add(new CheckboxSetting(
            "Remove Fire Overlay",
            "Hide the fire overlay when you are on fire.",
            "renderopt_remove_fire",
            this::isRemoveFireOverlay,
            this::setRemoveFireOverlay
        ));
        settings.add(new CheckboxSetting(
            "No Hurt Camera",
            "Disable camera shake when taking damage.",
            "renderopt_no_hurt_cam",
            this::isNoHurtCamera,
            this::setNoHurtCamera
        ));
        settings.add(new CheckboxSetting(
            "Remove Lightning",
            "Hide lightning bolt rendering.",
            "renderopt_remove_lightning",
            this::isRemoveLightning,
            this::setRemoveLightning
        ));
        settings.add(new CheckboxSetting(
            "Hide Inventory Effects",
            "Hide potion effect icons in inventory screens.",
            "renderopt_hide_inv_effects",
            this::isHideInventoryEffects,
            this::setHideInventoryEffects
        ));
        settings.add(new CheckboxSetting(
            "Hide Potion Overlay",
            "Hide potion effect overlay on HUD.",
            "renderopt_hide_potion_overlay",
            this::isHidePotionOverlay,
            this::setHidePotionOverlay
        ));
        settings.add(new CheckboxSetting(
            "Disable Water Overlay",
            "Hide the water overlay when underwater.",
            "renderopt_disable_water",
            this::isDisableWaterOverlay,
            this::setDisableWaterOverlay
        ));
        settings.add(new CheckboxSetting(
            "Disable Suffocating Overlay",
            "Hide the block overlay when suffocating.",
            "renderopt_disable_suffocate",
            this::isDisableSuffocatingOverlay,
            this::setDisableSuffocatingOverlay
        ));
        settings.add(new CheckboxSetting(
            "Disable Vignette",
            "Hide the vignette effect around screen edges.",
            "renderopt_disable_vignette",
            this::isDisableVignette,
            this::setDisableVignette
        ));
        settings.add(new CheckboxSetting(
            "Disable Armor HUD",
            "Hide the vanilla armor bar on HUD.",
            "renderopt_disable_armor_hud",
            this::isDisableVanillaArmorHud,
            this::setDisableVanillaArmorHud
        ));
        settings.add(new CheckboxSetting(
            "Disable Fog",
            "Remove fog rendering for clearer vision.",
            "renderopt_disable_fog",
            this::isDisableFog,
            this::setDisableFog
        ));
        settings.add(new DropdownSetting(
            "Remove Armor",
            "Hide armor rendering on players.",
            "renderopt_remove_armor",
            new String[]{"Disabled", "Self Only", "All Players"},
            this::getRemoveArmorModeValue,
            this::setRemoveArmorMode
        ));
        return settings;
    }
}
