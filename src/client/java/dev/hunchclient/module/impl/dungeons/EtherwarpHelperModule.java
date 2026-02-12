package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ColorPickerSetting;
import dev.hunchclient.module.setting.DropdownSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.render.GlowESPRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

@Environment(value=EnvType.CLIENT)
public class EtherwarpHelperModule extends Module implements ConfigurableModule, SettingsProvider {
    private static final double BOX_EXPANSION = 0.00001;
    private static final float DEFAULT_CORNER_RATIO = 0.35f;

    private RenderMode renderMode = RenderMode.OUTLINE_FILLED;
    private float[] canWarpColor = new float[]{0.0f, 1.0f, 0.0f, 1.0f};
    private float[] cannotWarpColor = new float[]{1.0f, 0.0f, 0.0f, 1.0f};
    private float outlineWidth = 10.0f;
    private float fillOpacity = 0.35f;
    private float cornerRatio = 0.35f;
    private float glowBlur = 3.0f;
    private float glowSpread = 0.5f;
    private float glowIntensity = 1.0f;
    private float glowBoxSize = 10.0f;
    private boolean throughWalls = false; // Only render visible side by default
    private final Map<String, float[]> favoriteColors = new HashMap<>();

    public EtherwarpHelperModule() {
        super("EtherwarpHelper", "Shows etherwarp target block with ESP", Module.Category.DUNGEONS, true);
    }

    @Override
    protected void onEnable() {
        dev.hunchclient.render.WorldRenderExtractionCallback.EVENT.register(this::render);
    }

    @Override
    protected void onDisable() {
        // Cleanup if needed
    }

    /**
     * Render callback using PrimitiveCollector
     * Called during END_EXTRACTION phase - no camera transformation needed
     */
    private void render(PrimitiveCollector collector) {
        if (!this.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
            return;
        }

        String itemName = mc.player.getMainHandItem().getItem().toString().toLowerCase();
        if (!itemName.contains("shovel")) {
            return;
        }

        // Get etherwarp result - this always returns a position (either hit or max range)
        EtherwarpModule.EtherPos etherResult = EtherwarpModule.getEtherwarpResult();
        if (etherResult.pos == null) {
            return;
        }

        // Use green color if warpable, red if not
        boolean canWarp = etherResult.succeeded;
        float[] color = canWarp ? this.canWarpColor : this.cannotWarpColor;

        AABB box = new AABB(etherResult.pos).inflate(BOX_EXPANSION);

        // Use PrimitiveCollector to submit rendering primitives
        switch (this.renderMode) {
            case OUTLINE -> {
                collector.outlinedBox(box, color[0], color[1], color[2], color[3], this.outlineWidth, this.throughWalls);
            }
            case FILLED -> {
                float[] filledColor = this.withAlpha(color, this.fillOpacity);
                collector.filledBox(box, filledColor[0], filledColor[1], filledColor[2], filledColor[3], this.throughWalls);
            }
            case OUTLINE_FILLED -> {
                float[] filledColor = this.withAlpha(color, this.fillOpacity);
                collector.filledBox(box, filledColor[0], filledColor[1], filledColor[2], filledColor[3], this.throughWalls);
                collector.outlinedBox(box, color[0], color[1], color[2], color[3], this.outlineWidth, this.throughWalls);
            }
            case CORNERED_BOX -> {
                // TODO: Implement cornered box rendering with PrimitiveCollector
                // For now, fallback to outlined box
                collector.outlinedBox(box, color[0], color[1], color[2], color[3], this.outlineWidth, this.throughWalls);
            }
            case GLOW_ESP -> {
                // GPU-based post-processing glow with Gaussian blur and additive compositing
                PoseStack matrices = new PoseStack();
                GlowESPRenderer.queueGlowBox(matrices, box, color, this.glowIntensity, this.glowSpread);
            }
        }
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        object.addProperty("renderMode", this.renderMode.name());
        object.add("canWarpColor", toJsonArray(this.canWarpColor));
        object.add("cannotWarpColor", toJsonArray(this.cannotWarpColor));
        object.addProperty("outlineWidth", this.outlineWidth);
        object.addProperty("fillOpacity", this.fillOpacity);
        object.addProperty("cornerRatio", this.cornerRatio);
        object.addProperty("glowBlur", this.glowBlur);
        object.addProperty("glowSpread", this.glowSpread);
        object.addProperty("glowIntensity", this.glowIntensity);
        object.addProperty("glowBoxSize", this.glowBoxSize);
        object.addProperty("throughWalls", this.throughWalls);
        return object;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) {
            return;
        }

        if (data.has("renderMode")) {
            String mode = data.get("renderMode").getAsString();
            try {
                this.setRenderMode(RenderMode.valueOf(mode));
            } catch (IllegalArgumentException ignored) {
            }
        }

        float[] canColor = readColor(data, "canWarpColor", this.canWarpColor);
        this.setCanWarpColor(canColor[0], canColor[1], canColor[2], canColor[3]);

        float[] cannotColor = readColor(data, "cannotWarpColor", this.cannotWarpColor);
        this.setCannotWarpColor(cannotColor[0], cannotColor[1], cannotColor[2], cannotColor[3]);

        if (data.has("outlineWidth")) {
            this.setOutlineWidth(data.get("outlineWidth").getAsFloat());
        }
        if (data.has("fillOpacity")) {
            this.setFillOpacity(data.get("fillOpacity").getAsFloat());
        }
        if (data.has("cornerRatio")) {
            this.setCornerRatio(data.get("cornerRatio").getAsFloat());
        }
        if (data.has("glowBlur")) {
            this.glowBlur = data.get("glowBlur").getAsFloat();
        }
        if (data.has("glowSpread")) {
            this.glowSpread = data.get("glowSpread").getAsFloat();
        }
        if (data.has("glowIntensity")) {
            this.glowIntensity = data.get("glowIntensity").getAsFloat();
        }
        if (data.has("glowBoxSize")) {
            this.glowBoxSize = data.get("glowBoxSize").getAsFloat();
        }
        if (data.has("throughWalls")) {
            this.throughWalls = data.get("throughWalls").getAsBoolean();
        }
    }

    private static JsonArray toJsonArray(float[] values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    private static float[] readColor(JsonObject data, String key, float[] fallback) {
        if (data == null || !data.has(key) || !data.get(key).isJsonArray()) {
            return fallback.clone();
        }

        JsonArray array = data.getAsJsonArray(key);
        float[] result = fallback.clone();
        for (int i = 0; i < Math.min(array.size(), result.length); i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }

    private float[] withAlpha(float[] source, float alpha) {
        return new float[]{source[0], source[1], source[2], Math.max(0.0f, Math.min(1.0f, alpha))};
    }

    public RenderMode getRenderMode() {
        return this.renderMode;
    }

    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public float[] getCanWarpColor() {
        return this.canWarpColor.clone();
    }

    public void setCanWarpColor(float r, float g, float b, float a) {
        this.canWarpColor = new float[]{r, g, b, a};
    }

    public float[] getCannotWarpColor() {
        return this.cannotWarpColor.clone();
    }

    public void setCannotWarpColor(float r, float g, float b, float a) {
        this.cannotWarpColor = new float[]{r, g, b, a};
    }

    public void setOutlineWidth(float width) {
        this.outlineWidth = Math.max(0.5f, Math.min(10.0f, width));
    }

    public float getOutlineWidth() {
        return this.outlineWidth;
    }

    public void setFillOpacity(float opacity) {
        this.fillOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }

    public float getFillOpacity() {
        return this.fillOpacity;
    }

    public float getCornerRatio() {
        return this.cornerRatio;
    }

    public void setCornerRatio(float ratio) {
        this.cornerRatio = Math.max(0.05f, Math.min(0.9f, ratio));
    }

    public void resetCornerRatio() {
        this.cornerRatio = DEFAULT_CORNER_RATIO;
    }

    public void saveFavoriteColor(String name, float[] color) {
        this.favoriteColors.put(name, color.clone());
    }

    public float[] getFavoriteColor(String name) {
        float[] color = this.favoriteColors.get(name);
        return color != null ? color.clone() : null;
    }

    public Set<String> getFavoriteColorNames() {
        return this.favoriteColors.keySet();
    }

    public void removeFavoriteColor(String name) {
        this.favoriteColors.remove(name);
    }

    public void setColorToGreen() {
        this.setCanWarpColor(0.0f, 1.0f, 0.0f, 1.0f);
    }

    public void setColorToRed() {
        this.setCannotWarpColor(1.0f, 0.0f, 0.0f, 1.0f);
    }

    public void setColorToBlue() {
        this.setCanWarpColor(0.0f, 0.5f, 1.0f, 1.0f);
    }

    public void setColorToYellow() {
        this.setCanWarpColor(1.0f, 1.0f, 0.0f, 1.0f);
    }

    public void setColorToCyan() {
        this.setCanWarpColor(0.0f, 1.0f, 1.0f, 1.0f);
    }

    public void setColorToMagenta() {
        this.setCanWarpColor(1.0f, 0.0f, 1.0f, 1.0f);
    }

    private int floatArrayToARGB(float[] rgba) {
        int a = (int) (rgba[3] * 255.0f) & 0xFF;
        int r = (int) (rgba[0] * 255.0f) & 0xFF;
        int g = (int) (rgba[1] * 255.0f) & 0xFF;
        int b = (int) (rgba[2] * 255.0f) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void argbToFloatArray(int argb, float[] target) {
        target[3] = ((argb >> 24) & 0xFF) / 255.0f;
        target[0] = ((argb >> 16) & 0xFF) / 255.0f;
        target[1] = ((argb >> 8) & 0xFF) / 255.0f;
        target[2] = (argb & 0xFF) / 255.0f;
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new DropdownSetting(
            "Render Mode",
            "ESP rendering style",
            "etherwarp_render_mode",
            new String[]{"OUTLINE", "FILLED", "OUTLINE_FILLED", "CORNERED_BOX", "GLOW_ESP"},
            () -> this.renderMode.ordinal(),
            index -> this.renderMode = RenderMode.values()[index]
        ));

        settings.add(new CheckboxSetting(
            "Through Walls",
            "Render ESP through walls (visible through blocks)",
            "etherwarp_through_walls",
            () -> this.throughWalls,
            val -> this.throughWalls = val
        ));

        settings.add(new ColorPickerSetting(
            "Can Warp Color",
            "Color when block is warpable",
            "etherwarp_can_warp_color",
            () -> this.floatArrayToARGB(this.canWarpColor),
            color -> this.argbToFloatArray(color, this.canWarpColor)
        ));

        settings.add(new ColorPickerSetting(
            "Cannot Warp Color",
            "Color when block is not warpable",
            "etherwarp_cannot_warp_color",
            () -> this.floatArrayToARGB(this.cannotWarpColor),
            color -> this.argbToFloatArray(color, this.cannotWarpColor)
        ));

        settings.add(new SliderSetting(
            "Outline Width",
            "Thickness of outline",
            "etherwarp_outline_width",
            1.0f, 20.0f,
            () -> this.outlineWidth,
            val -> this.outlineWidth = val
        ).withDecimals(1).withSuffix("px"));

        settings.add(new SliderSetting(
            "Fill Opacity",
            "Opacity of filled area",
            "etherwarp_fill_opacity",
            0.0f, 1.0f,
            () -> this.fillOpacity,
            val -> this.fillOpacity = val
        ).withDecimals(2).asPercentage());

        settings.add(new SliderSetting(
            "Corner Ratio",
            "Size of corner indicators",
            "etherwarp_corner_ratio",
            0.1f, 0.9f,
            () -> this.cornerRatio,
            val -> this.cornerRatio = val
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Glow Blur",
            "Blur amount for glow effect",
            "etherwarp_glow_blur",
            5.0f, 50.0f,
            () -> this.glowBlur,
            val -> this.glowBlur = val
        ).withDecimals(1).withSuffix("px"));

        settings.add(new SliderSetting(
            "Glow Spread",
            "Spread amount for glow effect",
            "etherwarp_glow_spread",
            0.0f, 10.0f,
            () -> this.glowSpread,
            val -> this.glowSpread = val
        ).withDecimals(1).withSuffix("px"));

        settings.add(new SliderSetting(
            "Glow Intensity",
            "Intensity of glow effect",
            "etherwarp_glow_intensity",
            0.0f, 1.0f,
            () -> this.glowIntensity,
            val -> this.glowIntensity = val
        ).withDecimals(2).asPercentage());

        settings.add(new SliderSetting(
            "Glow Box Size",
            "Size of the 2D glow overlay",
            "etherwarp_glow_box_size",
            20.0f, 200.0f,
            () -> this.glowBoxSize,
            val -> this.glowBoxSize = val
        ).withDecimals(0).withSuffix("px"));

        return settings;
    }

    @Environment(value=EnvType.CLIENT)
    public enum RenderMode {
        OUTLINE,
        FILLED,
        OUTLINE_FILLED,
        CORNERED_BOX,
        GLOW_ESP
    }
}
