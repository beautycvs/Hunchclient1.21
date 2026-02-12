package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.render.CustomRenderLayers;
import dev.hunchclient.render.RawGLBlockOutlineBlur;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Module for configurable block outlines with CUSTOM Gaussian blur.
 * Uses raw OpenGL - NO MINECRAFT WRAPPERS!
 */
public class BlockOutlineModule extends Module implements ConfigurableModule, SettingsProvider {

    private static BlockOutlineModule instance;
    private final RawGLBlockOutlineBlur renderer;

    // Configuration
    private float blurRadius = 4.0f;
    private float threshold = 0.3f;
    private float[] outlineColor = {1.0f, 1.0f, 1.0f, 1.0f}; // White

    public BlockOutlineModule() {
        super("BlockOutline", "Custom Gaussian Blur (Raw OpenGL)", Category.VISUALS, true);
        instance = this;
        renderer = new RawGLBlockOutlineBlur();
    }

    public static BlockOutlineModule getInstance() {
        return instance;
    }

    public RawGLBlockOutlineBlur getRenderer() {
        return renderer;
    }

    // Configuration getters/setters
    public float getBlurRadius() {
        return blurRadius;
    }

    public void setBlurRadius(float radius) {
        this.blurRadius = Math.max(1.0f, Math.min(16.0f, radius));
        renderer.setBlurRadius(this.blurRadius);
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = Math.max(0.0f, Math.min(1.0f, threshold));
        renderer.setThreshold(this.threshold);
    }

    public float[] getOutlineColor() {
        return outlineColor;
    }

    public void setOutlineColor(float r, float g, float b, float a) {
        this.outlineColor[0] = r;
        this.outlineColor[1] = g;
        this.outlineColor[2] = b;
        this.outlineColor[3] = a;
        renderer.setOutlineColor(r, g, b, a);
    }

    @Override
    protected void onEnable() {
        renderer.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        renderer.setEnabled(false);
    }

    /**
     * Custom block outline renderer with multi-pass "fake blur" effect.
     * This is called from WorldRendererMixin instead of vanilla rendering.
     * NO FRAMEBUFFERS - just renders the outline multiple times with offset & transparency!
     */
    public void renderCustomBlockOutline(
        Camera camera,
        MultiBufferSource.BufferSource vertexConsumers,
        PoseStack matrices,
        boolean translucent
    ) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hitResult = mc.hitResult;

        if (!(hitResult instanceof BlockHitResult)) {
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState blockState = mc.level.getBlockState(blockPos);

        if (blockState.isAir() || !mc.level.getWorldBorder().isWithinBounds(blockPos)) {
            return;
        }

        // Check if this block should be rendered in this pass (translucent vs opaque)
        boolean isTranslucent = ItemBlockRenderTypes.getChunkRenderType(blockState).sortOnUpload();
        if (isTranslucent != translucent) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        VoxelShape shape = blockState.getShape(mc.level, blockPos, CollisionContext.of(mc.player));

        if (shape.isEmpty()) {
            return;
        }

        // Use custom RenderLayer with thick line width!
        // This works because lineWidth is part of the RenderLayer definition, not a GL state change
        RenderType thickLayer = CustomRenderLayers.getThickBlockOutline(blurRadius);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(thickLayer);

        // Use custom outline color
        int mainColor = ARGB.colorFromFloat(
            outlineColor[3],
            outlineColor[0],
            outlineColor[1],
            outlineColor[2]
        );

        ShapeRenderer.renderShape(
            matrices,
            vertexConsumer,
            shape,
            (double) blockPos.getX() - cameraPos.x,
            (double) blockPos.getY() - cameraPos.y,
            (double) blockPos.getZ() - cameraPos.z,
            mainColor
        );

        // Flush
        vertexConsumers.endLastBatch();
    }

    // ==================== SettingsProvider Implementation ====================

    @Override
    public java.util.List<ModuleSetting> getSettings() {
        java.util.List<ModuleSetting> settings = new java.util.ArrayList<>();

        // Blur Radius Slider
        settings.add(new SliderSetting(
            "Blur Radius",
            "Outline blur/thickness (1-16)",
            "blockoutline_blur",
            1.0f, 16.0f,
            () -> blurRadius,
            val -> setBlurRadius(val)
        ));

        // Threshold Slider
        settings.add(new SliderSetting(
            "Threshold",
            "Blur threshold (0-1)",
            "blockoutline_threshold",
            0.0f, 1.0f,
            () -> threshold,
            val -> setThreshold(val)
        ));

        // Outline Color Picker
        settings.add(new ColorPickerSetting(
            "Outline Color",
            "Block outline color (RGBA)",
            "blockoutline_color",
            () -> {
                // Convert float[] to ARGB int
                int a = (int) (outlineColor[3] * 255);
                int r = (int) (outlineColor[0] * 255);
                int g = (int) (outlineColor[1] * 255);
                int b = (int) (outlineColor[2] * 255);
                return (a << 24) | (r << 16) | (g << 8) | b;
            },
            color -> {
                // Convert ARGB int to float[]
                float a = ((color >> 24) & 0xFF) / 255f;
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                setOutlineColor(r, g, b, a);
            }
        ));

        return settings;
    }

    // ==================== ConfigurableModule Implementation ====================

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enabled", isEnabled());
        config.addProperty("blurRadius", blurRadius);
        config.addProperty("threshold", threshold);
        config.addProperty("colorR", outlineColor[0]);
        config.addProperty("colorG", outlineColor[1]);
        config.addProperty("colorB", outlineColor[2]);
        config.addProperty("colorA", outlineColor[3]);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("enabled")) setEnabled(data.get("enabled").getAsBoolean());
        if (data.has("blurRadius")) setBlurRadius(data.get("blurRadius").getAsFloat());
        if (data.has("threshold")) setThreshold(data.get("threshold").getAsFloat());
        if (data.has("colorR") && data.has("colorG") && data.has("colorB") && data.has("colorA")) {
            setOutlineColor(
                data.get("colorR").getAsFloat(),
                data.get("colorG").getAsFloat(),
                data.get("colorB").getAsFloat(),
                data.get("colorA").getAsFloat()
            );
        }
    }
}
