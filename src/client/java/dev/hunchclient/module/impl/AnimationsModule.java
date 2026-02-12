package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.util.TerminatorUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.google.gson.JsonObject;

/**
 * Viewmodel animation customisation module.
 *
 * Features:
 * <ul>
 *   <li>Custom translation, rotation, and scale for the held item</li>
 *   <li>Scaled/disabled swing animations</li>
 *   <li>Swing speed modifier with optional haste override</li>
 *   <li>Optional equip animation suppression</li>
 *   <li>Optional Terminator swing suppression</li>
 * </ul>
 */
public class AnimationsModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IAnimations {

    public enum SwingMode {
        VANILLA("Vanilla", "Default 1.8+ swing animation"),
        OLD_1_7("1.7", "Classic 1.7 - only rotation"),
        SCALED("Scaled", "Scale swing with size setting"),
        SMOOTH("Smooth", "Slower, smoother swing"),
        PUSH("Push", "Push item forward"),
        SLIDE("Slide", "Slide to the side"),
        STAB("Stab", "Quick stab motion"),
        EXHIBITION("Exhibition", "Exhibition client style"),
        WINDMILL("Windmill", "Spin like a windmill"),
        SPIN("Spin", "360 degree rotation"),
        SWING_DOWN("Swing Down", "Swing downward"),
        TAP("Tap", "Minimal tap animation"),
        ASTOLFO("Astolfo", "Astolfo client style"),
        STELLA("Stella", "Stella client style"),
        PUNCH("Punch", "Punch forward"),
        BLOCK_HIT("Block Hit", "W-tap style");

        private final String name;
        private final String description;

        SwingMode(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    private static AnimationsModule instance;

    // Transform settings (default values mirror classic animation feel)
    private float size = 0.0f;
    private SwingMode swingMode = SwingMode.SCALED;
    private float swingIntensity = 1.0f; // Control how far the swing goes (0.0 = minimal, 1.0 = normal, 2.0 = extreme)
    private float equipBobbingSpeed = 1.0f; // Control equip bobbing speed (0.0 = none, 1.0 = normal, 2.0 = faster)
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private float offsetZ = 0.0f;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private float roll = 0.0f;
    private float swingSpeed = 0.0f;
    private boolean ignoreHaste = false;
    private boolean noEquipReset = false;
    private boolean noSwing = false;
    private boolean noTermSwing = false;

    public AnimationsModule() {
        super("Animations", "Customise first-person item animations", Category.VISUALS, true);
        instance = this;
    }

    public static AnimationsModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        // No-op
    }

    @Override
    protected void onDisable() {
        // Nothing to clean up here
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }

        if (!noTermSwing) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isHoldingTerminator(player)) {
            return;
        }

        LivingEntityAccessor accessor = (LivingEntityAccessor) player;
        accessor.hunchclient$setHandSwinging(false);
        accessor.hunchclient$setHandSwingTicks(0);
        accessor.hunchclient$setHandSwingProgress(0.0f);
        accessor.hunchclient$setLastHandSwingProgress(0.0f);
    }

    private boolean isHoldingTerminator(LocalPlayer player) {
        return TerminatorUtil.isTerminator(player.getMainHandItem()) || TerminatorUtil.isTerminator(player.getOffhandItem());
    }

    public void resetSettings() {
        size = 0.0f;
        swingMode = SwingMode.SCALED;
        swingIntensity = 1.0f;
        equipBobbingSpeed = 1.0f;
        offsetX = 0.0f;
        offsetY = 0.0f;
        offsetZ = 0.0f;
        yaw = 0.0f;
        pitch = 0.0f;
        roll = 0.0f;
        swingSpeed = 0.0f;
        ignoreHaste = false;
        noEquipReset = false;
        noSwing = false;
        noTermSwing = false;
    }

    // ----- Computed helpers -----

    public float getScaleMultiplier() {
        return clamp((float) Math.exp(size), 0.05f, 5.0f);
    }

    public float getSwingRotationScale() {
        if (noSwing) {
            return 0.0f;
        }

        float baseScale = switch (swingMode) {
            case VANILLA -> 1.0f;
            case OLD_1_7 -> 1.0f;
            case SCALED -> clamp((float) Math.exp(size), 0.05f, 5.0f);
            case SMOOTH -> 0.8f;
            case PUSH -> 0.6f;
            case SLIDE -> 0.5f;
            case STAB -> 1.2f;
            case EXHIBITION -> 0.7f;
            case WINDMILL -> 2.0f;
            case SPIN -> 3.0f;
            case SWING_DOWN -> 1.0f;
            case TAP -> 0.3f;
            case ASTOLFO -> 0.9f;
            case STELLA -> 0.85f;
            case PUNCH -> 0.5f;
            case BLOCK_HIT -> 1.3f;
        };

        // Apply intensity multiplier
        return baseScale * swingIntensity;
    }

    public float getSwingTranslationScale() {
        if (noSwing) {
            return 0.0f;
        }

        float baseScale = switch (swingMode) {
            case VANILLA -> 1.0f;
            case OLD_1_7 -> 0.0f; // Classic 1.7 - no translation!
            case SCALED -> clamp((float) Math.exp(size), 0.05f, 5.0f);
            case SMOOTH -> 0.7f;
            case PUSH -> 1.5f;
            case SLIDE -> 0.8f;
            case STAB -> 0.4f;
            case EXHIBITION -> 0.5f;
            case WINDMILL -> 0.3f;
            case SPIN -> 0.2f;
            case SWING_DOWN -> 0.9f;
            case TAP -> 0.2f;
            case ASTOLFO -> 0.6f;
            case STELLA -> 0.75f;
            case PUNCH -> 1.8f;
            case BLOCK_HIT -> 1.1f;
        };

        // Apply intensity multiplier
        return baseScale * swingIntensity;
    }

    /**
     * Apply custom swing transformation for special modes
     */
    public void applyCustomSwingTransform(com.mojang.blaze3d.vertex.PoseStack matrices, float swingProgress, net.minecraft.world.entity.HumanoidArm arm) {
        if (noSwing || swingProgress == 0.0f) {
            return;
        }

        int direction = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        float smoothProgress = net.minecraft.util.Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float sqrtProgress = net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(swingProgress) * (float) Math.PI);

        // Apply intensity to all transformations
        float intensity = swingIntensity;

        switch (swingMode) {
            case PUSH -> {
                // Push forward during swing
                matrices.translate(0, 0, -smoothProgress * 0.3f * intensity);
            }
            case SLIDE -> {
                // Slide to the side
                matrices.translate(direction * smoothProgress * 0.3f * intensity, 0, 0);
            }
            case STAB -> {
                // Quick stab motion
                matrices.translate(0, 0, -sqrtProgress * 0.4f * intensity);
                matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(direction * smoothProgress * 10.0f * intensity));
            }
            case EXHIBITION -> {
                // Exhibition style - slight upward arc
                matrices.translate(0, -sqrtProgress * 0.1f * intensity, -smoothProgress * 0.15f * intensity);
            }
            case WINDMILL -> {
                // Windmill rotation
                matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(direction * swingProgress * 180.0f * intensity));
            }
            case SPIN -> {
                // Full 360 spin
                matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(direction * swingProgress * 360.0f * intensity));
            }
            case SWING_DOWN -> {
                // Swing downward
                matrices.translate(0, smoothProgress * 0.3f * intensity, 0);
                matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(smoothProgress * 30.0f * intensity));
            }
            case TAP -> {
                // Minimal tap
                matrices.translate(0, 0, -smoothProgress * 0.05f * intensity);
            }
            case ASTOLFO -> {
                // Astolfo style - smooth arc
                matrices.translate(direction * smoothProgress * 0.1f * intensity, -sqrtProgress * 0.15f * intensity, -smoothProgress * 0.1f * intensity);
                matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(direction * smoothProgress * 25.0f * intensity));
            }
            case STELLA -> {
                // Stella style - bouncy
                float bounce = net.minecraft.util.Mth.sin(swingProgress * (float) Math.PI * 2.0f) * 0.1f * intensity;
                matrices.translate(0, bounce, -smoothProgress * 0.1f * intensity);
            }
            case PUNCH -> {
                // Strong punch forward
                matrices.translate(0, sqrtProgress * 0.1f * intensity, -smoothProgress * 0.5f * intensity);
            }
            case BLOCK_HIT -> {
                // W-tap / block hit style
                float recoil = swingProgress < 0.5f ? swingProgress * 2.0f : (1.0f - swingProgress) * 2.0f;
                matrices.translate(0, 0, (recoil * 0.2f - smoothProgress * 0.1f) * intensity);
            }
            case VANILLA, OLD_1_7, SCALED, SMOOTH -> {
                // VANILLA, OLD_1_7, SCALED, SMOOTH - use default animations with scales
            }
        }
    }

    public boolean shouldZeroSwing(LocalPlayer player) {
        if (!isEnabled()) {
            return false;
        }
        if (noSwing) {
            return true;
        }
        return noTermSwing && player != null && isHoldingTerminator(player);
    }

    // ----- Getters / setters -----

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = clamp(size, -1.5f, 1.5f);
    }

    public SwingMode getSwingMode() {
        return swingMode;
    }

    public void setSwingMode(SwingMode mode) {
        this.swingMode = mode;
    }

    public float getSwingIntensity() {
        return swingIntensity;
    }

    public void setSwingIntensity(float intensity) {
        this.swingIntensity = clamp(intensity, 0.0f, 3.0f);
    }

    public float getEquipBobbingSpeed() {
        return equipBobbingSpeed;
    }

    public void setEquipBobbingSpeed(float speed) {
        this.equipBobbingSpeed = clamp(speed, 0.0f, 3.0f);
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = clamp(offsetX, -2.5f, 1.5f);
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = clamp(offsetY, -1.5f, 1.5f);
    }

    public float getOffsetZ() {
        return offsetZ;
    }

    public void setOffsetZ(float offsetZ) {
        this.offsetZ = clamp(offsetZ, -1.5f, 3.0f);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = clamp(yaw, -180.0f, 180.0f);
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = clamp(pitch, -180.0f, 180.0f);
    }

    public float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = clamp(roll, -180.0f, 180.0f);
    }

    public float getSwingSpeed() {
        return swingSpeed;
    }

    public void setSwingSpeed(float swingSpeed) {
        this.swingSpeed = clamp(swingSpeed, -2.0f, 1.0f);
    }

    public boolean isIgnoreHaste() {
        return ignoreHaste;
    }

    public void setIgnoreHaste(boolean ignoreHaste) {
        this.ignoreHaste = ignoreHaste;
    }

    public boolean isNoEquipReset() {
        return noEquipReset;
    }

    public void setNoEquipReset(boolean noEquipReset) {
        this.noEquipReset = noEquipReset;
    }

    public boolean isNoSwing() {
        return noSwing;
    }

    public void setNoSwing(boolean noSwing) {
        this.noSwing = noSwing;
    }

    public boolean isNoTermSwing() {
        return noTermSwing;
    }

    public void setNoTermSwing(boolean noTermSwing) {
        this.noTermSwing = noTermSwing;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        object.addProperty("size", this.size);
        object.addProperty("swingMode", this.swingMode.name());
        object.addProperty("swingIntensity", this.swingIntensity);
        object.addProperty("equipBobbingSpeed", this.equipBobbingSpeed);
        object.addProperty("offsetX", this.offsetX);
        object.addProperty("offsetY", this.offsetY);
        object.addProperty("offsetZ", this.offsetZ);
        object.addProperty("yaw", this.yaw);
        object.addProperty("pitch", this.pitch);
        object.addProperty("roll", this.roll);
        object.addProperty("swingSpeed", this.swingSpeed);
        object.addProperty("ignoreHaste", this.ignoreHaste);
        object.addProperty("noEquipReset", this.noEquipReset);
        object.addProperty("noSwing", this.noSwing);
        object.addProperty("noTermSwing", this.noTermSwing);
        return object;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("size")) setSize(data.get("size").getAsFloat());
        if (data.has("swingMode")) {
            try {
                setSwingMode(SwingMode.valueOf(data.get("swingMode").getAsString()));
            } catch (IllegalArgumentException e) {
                // Fallback to SCALED if invalid mode
                setSwingMode(SwingMode.SCALED);
            }
        }
        // Legacy support for old scaleSwing boolean
        if (data.has("scaleSwing") && !data.has("swingMode")) {
            setSwingMode(data.get("scaleSwing").getAsBoolean() ? SwingMode.SCALED : SwingMode.VANILLA);
        }
        if (data.has("swingIntensity")) setSwingIntensity(data.get("swingIntensity").getAsFloat());
        if (data.has("equipBobbingSpeed")) setEquipBobbingSpeed(data.get("equipBobbingSpeed").getAsFloat());
        if (data.has("offsetX")) setOffsetX(data.get("offsetX").getAsFloat());
        if (data.has("offsetY")) setOffsetY(data.get("offsetY").getAsFloat());
        if (data.has("offsetZ")) setOffsetZ(data.get("offsetZ").getAsFloat());
        if (data.has("yaw")) setYaw(data.get("yaw").getAsFloat());
        if (data.has("pitch")) setPitch(data.get("pitch").getAsFloat());
        if (data.has("roll")) setRoll(data.get("roll").getAsFloat());
        if (data.has("swingSpeed")) setSwingSpeed(data.get("swingSpeed").getAsFloat());
        if (data.has("ignoreHaste")) setIgnoreHaste(data.get("ignoreHaste").getAsBoolean());
        if (data.has("noEquipReset")) setNoEquipReset(data.get("noEquipReset").getAsBoolean());
        if (data.has("noSwing")) setNoSwing(data.get("noSwing").getAsBoolean());
        if (data.has("noTermSwing")) setNoTermSwing(data.get("noTermSwing").getAsBoolean());
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Scale Settings
        settings.add(new SliderSetting(
            "Size",
            "Item scale (exponential)",
            "size",
            -1.5f, 1.5f,
            () -> size,
            (val) -> setSize(val)
        ).withDecimals(2));

        // Build mode names from enum
        String[] modeNames = new String[SwingMode.values().length];
        for (int i = 0; i < SwingMode.values().length; i++) {
            modeNames[i] = SwingMode.values()[i].getName();
        }

        settings.add(new DropdownSetting(
            "Swing Mode",
            "Swing animation style",
            "swing_mode",
            modeNames,
            () -> swingMode.ordinal(),
            (index) -> {
                if (index >= 0 && index < SwingMode.values().length) {
                    setSwingMode(SwingMode.values()[index]);
                }
            }
        ));

        settings.add(new SliderSetting(
            "Swing Intensity",
            "How far the swing animation goes (0.0 = minimal, 1.0 = normal, 3.0 = extreme)",
            "swing_intensity",
            0.0f, 3.0f,
            () -> swingIntensity,
            (val) -> setSwingIntensity(val)
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Equip Bobbing Speed",
            "Speed of equip/swap bobbing animation (0.0 = none, 1.0 = normal, 3.0 = fast)",
            "equip_bobbing_speed",
            0.0f, 3.0f,
            () -> equipBobbingSpeed,
            (val) -> setEquipBobbingSpeed(val)
        ).withDecimals(2));

        // Position Offsets
        settings.add(new SliderSetting(
            "Offset X",
            "Horizontal offset",
            "offset_x",
            -2.5f, 1.5f,
            () -> offsetX,
            (val) -> setOffsetX(val)
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Offset Y",
            "Vertical offset",
            "offset_y",
            -1.5f, 1.5f,
            () -> offsetY,
            (val) -> setOffsetY(val)
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Offset Z",
            "Depth offset",
            "offset_z",
            -1.5f, 3.0f,
            () -> offsetZ,
            (val) -> setOffsetZ(val)
        ).withDecimals(2));

        // Rotation
        settings.add(new SliderSetting(
            "Yaw",
            "Yaw rotation (degrees)",
            "yaw",
            -180.0f, 180.0f,
            () -> yaw,
            (val) -> setYaw(val)
        ).withDecimals(1).withSuffix("°"));

        settings.add(new SliderSetting(
            "Pitch",
            "Pitch rotation (degrees)",
            "pitch",
            -180.0f, 180.0f,
            () -> pitch,
            (val) -> setPitch(val)
        ).withDecimals(1).withSuffix("°"));

        settings.add(new SliderSetting(
            "Roll",
            "Roll rotation (degrees)",
            "roll",
            -180.0f, 180.0f,
            () -> roll,
            (val) -> setRoll(val)
        ).withDecimals(1).withSuffix("°"));

        // Swing Settings
        settings.add(new SliderSetting(
            "Swing Speed",
            "Swing speed modifier",
            "swing_speed",
            -2.0f, 1.0f,
            () -> swingSpeed,
            (val) -> setSwingSpeed(val)
        ).withDecimals(2));

        settings.add(new CheckboxSetting(
            "Ignore Haste",
            "Ignore haste effect on swing speed",
            "ignore_haste",
            () -> ignoreHaste,
            (val) -> ignoreHaste = val
        ));

        settings.add(new CheckboxSetting(
            "No Equip Reset",
            "Disable equip animation",
            "no_equip_reset",
            () -> noEquipReset,
            (val) -> noEquipReset = val
        ));

        settings.add(new CheckboxSetting(
            "No Swing",
            "Disable all swing animations",
            "no_swing",
            () -> noSwing,
            (val) -> noSwing = val
        ));

        settings.add(new CheckboxSetting(
            "No Term Swing",
            "Disable Terminator swing animation",
            "no_term_swing",
            () -> noTermSwing,
            (val) -> noTermSwing = val
        ));

        return settings;
    }

    // ----- Utility -----

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Accessor hook for {@link net.minecraft.world.entity.LivingEntity} swing fields.
     */
    @SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
    public interface LivingEntityAccessor {
        void hunchclient$setHandSwinging(boolean swinging);
        void hunchclient$setHandSwingTicks(int ticks);
        void hunchclient$setHandSwingProgress(float progress);
        void hunchclient$setLastHandSwingProgress(float progress);
    }
}
