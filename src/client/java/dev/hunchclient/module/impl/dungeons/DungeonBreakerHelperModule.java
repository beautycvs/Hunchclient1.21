package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dungeon Breaker Helper Module
 *
 * Renders ESP for blocks when holding dungeon breaker
 * - Green color if durability > 0 (can break)
 * - Red color if durability = 0 (cannot break)
 *
 * WATCHDOG SAFE: YES (client-side rendering only)
 */
public class DungeonBreakerHelperModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double BOX_EXPANSION = 0.0025D;

    private static final Set<String> DUNGEON_BREAKER_IDS = Set.of(
        "DUNGEON_STONE_PICKAXE",
        "DUNGEON_IRON_PICKAXE",
        "DUNGEON_GOLD_PICKAXE",
        "DUNGEON_DIAMOND_PICKAXE"
    );

    // Render settings
    private RenderMode renderMode = RenderMode.OUTLINE_FILLED;

    // Colors (with alpha)
    private float[] canBreakColor = {0.0f, 1.0f, 0.0f, 1.0f}; // Green (has durability)
    private float[] cannotBreakColor = {1.0f, 0.0f, 0.0f, 1.0f}; // Red (no durability)

    private float outlineWidth = 2.0f;
    private float fillOpacity = 0.3f;
    private float cornerRatio = 0.25f;

    public DungeonBreakerHelperModule() {
        super("DungeonBreakerHelper", "ESP for dungeon breaker with durability check", Category.DUNGEONS, true);
    }

    @Override
    protected void onEnable() {
        dev.hunchclient.render.WorldRenderExtractionCallback.EVENT.register(this::onRenderWorld);
    }

    @Override
    protected void onDisable() {
        // Event is automatically unregistered when module disabled
    }

    private void onRenderWorld(PrimitiveCollector collector) {
        if (!isEnabled()) return;
        if (MC.player == null || MC.level == null) return;
        if (!isHoldingDungeonBreaker()) return;

        BlockPos targetBlock = getLookingAtBlock();
        if (targetBlock == null) return;

        BlockState state = MC.level.getBlockState(targetBlock);
        if (state.isAir()) return;

        // Check durability
        int durability = getDungeonBreakerDurability();
        boolean canBreak = durability > 0;

        // Select color based on durability
        float[] color = canBreak ? canBreakColor : cannotBreakColor;

        // Render the block ESP
        renderBlockESP(collector, targetBlock, color);
    }

    private void renderBlockESP(PrimitiveCollector collector, BlockPos pos, float[] color) {
        AABB box = new AABB(pos).inflate(BOX_EXPANSION);
        float[] rgba = {color[0], color[1], color[2], 1.0f}; // Add alpha channel

        switch (renderMode) {
            case OUTLINE -> collector.submitOutlinedBox(box, rgba, outlineWidth, true);
            case FILLED -> collector.submitFilledBox(box, rgba, fillOpacity, true);
            case OUTLINE_FILLED -> {
                collector.submitFilledBox(box, rgba, fillOpacity, true);
                collector.submitOutlinedBox(box, rgba, outlineWidth, true);
            }
            case CORNER -> {
                // Corner mode not directly supported by PrimitiveCollector, fall back to outline
                collector.submitOutlinedBox(box, rgba, outlineWidth, true);
            }
        }
    }

    private void drawCorner(VertexConsumer vertex, PoseStack.Pose entry, double x, double y, double z, double offsetX, double offsetZ, float r, float g, float b, float a) {
        emitLine(vertex, entry, x, y, z, x + offsetX, y, z, r, g, b, a);
        emitLine(vertex, entry, x, y, z, x, y, z + offsetZ, r, g, b, a);
    }

    private void drawVertical(VertexConsumer vertex, PoseStack.Pose entry, double x, double z, double minY, double maxY, double offset, float r, float g, float b, float a) {
        emitLine(vertex, entry, x, minY, z, x, minY + offset, z, r, g, b, a);
        emitLine(vertex, entry, x, maxY, z, x, maxY - offset, z, r, g, b, a);
    }

    private void emitLine(VertexConsumer vertex, PoseStack.Pose entry, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        Vec3 normalVec = new Vec3(x2 - x1, y2 - y1, z2 - z1);
        if (normalVec.lengthSqr() == 0.0) {
            normalVec = new Vec3(0.0, 1.0, 0.0);
        } else {
            normalVec = normalVec.normalize();
        }

        emitVertex(vertex, entry, x1, y1, z1, r, g, b, a, normalVec);
        emitVertex(vertex, entry, x2, y2, z2, r, g, b, a, normalVec);
    }

    private void emitVertex(VertexConsumer vertex, PoseStack.Pose entry, double x, double y, double z, float r, float g, float b, float a, Vec3 normal) {
        vertex.addVertex(entry.pose(), (float) x, (float) y, (float) z)
            .setColor(r, g, b, a)
            .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private float[] withAlpha(float[] source, float alpha) {
        return new float[]{source[0], source[1], source[2], Math.max(0.0f, Math.min(1.0f, alpha))};
    }

    private boolean isHoldingDungeonBreaker() {
        if (MC.player == null) return false;

        ItemStack mainHand = MC.player.getMainHandItem();
        ItemStack offHand = MC.player.getOffhandItem();

        return isDungeonBreakerStack(mainHand) || isDungeonBreakerStack(offHand);
    }

    private boolean isDungeonBreakerStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        try {
            // Check item type (must be pickaxe)
            String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
            if (!itemName.contains("pickaxe")) return false;

            // Check NBT for Skyblock ID
            CompoundTag attributes = getExtraAttributes(stack);
            if (attributes != null && attributes.contains("id")) {
                String id = attributes.getString("id").orElse("");
                if (DUNGEON_BREAKER_IDS.contains(id)) {
                    return true;
                }
            }

            // Fallback: Check display name for "Dungeonbreaker" or "Dungeon Breaker"
            String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            return displayName.contains("dungeonbreaker") ||
                   (displayName.contains("dungeon") && displayName.contains("breaker"));
        } catch (Exception e) {
            return false;
        }
    }

    private int getDungeonBreakerDurability() {
        if (MC.player == null) return 0;

        ItemStack stack = MC.player.getMainHandItem();
        if (!isDungeonBreakerStack(stack)) {
            stack = MC.player.getOffhandItem();
            if (!isDungeonBreakerStack(stack)) return 0;
        }

        try {
            CompoundTag attributes = getExtraAttributes(stack);
            if (attributes != null) {
                // Check for durability in NBT
                if (attributes.contains("drill_fuel")) {
                    return attributes.getInt("drill_fuel").orElse(0);
                }
                if (attributes.contains("durability")) {
                    return attributes.getInt("durability").orElse(0);
                }
            }

            // Fallback: Use vanilla durability
            return stack.getMaxDamage() - stack.getDamageValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private BlockPos getLookingAtBlock() {
        if (MC.player == null || MC.level == null) return null;

        double range = 5.0; // Dungeon breaker range
        Vec3 eyePos = MC.player.getEyePosition();
        Vec3 lookVec = MC.player.getViewVector(1.0f);
        Vec3 traceEnd = eyePos.add(lookVec.scale(range));

        BlockHitResult result = MC.level.clip(new ClipContext(
            eyePos,
            traceEnd,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            MC.player
        ));

        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = result.getBlockPos();
            BlockState state = MC.level.getBlockState(pos);
            if (!state.isAir()) {
                return pos;
            }
        }
        return null;
    }

    private CompoundTag getExtraAttributes(ItemStack stack) {
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                if (nbt != null) {
                    if (nbt.contains("ExtraAttributes")) {
                        return nbt.getCompound("ExtraAttributes").orElse(null);
                    }
                    if (nbt.contains("extra_attributes")) {
                        return nbt.getCompound("extra_attributes").orElse(null);
                    }
                    if (nbt.contains("id")) {
                        return nbt;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Getters and Setters
    public RenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(RenderMode mode) {
        this.renderMode = mode;
    }

    public float[] getCanBreakColor() {
        return canBreakColor.clone();
    }

    public void setCanBreakColor(float r, float g, float b, float a) {
        this.canBreakColor = new float[]{r, g, b, a};
    }

    public float[] getCannotBreakColor() {
        return cannotBreakColor.clone();
    }

    public void setCannotBreakColor(float r, float g, float b, float a) {
        this.cannotBreakColor = new float[]{r, g, b, a};
    }

    public float getOutlineWidth() {
        return outlineWidth;
    }

    public void setOutlineWidth(float width) {
        this.outlineWidth = Math.max(0.5f, Math.min(10.0f, width));
    }

    public float getFillOpacity() {
        return fillOpacity;
    }

    public void setFillOpacity(float opacity) {
        this.fillOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }

    public float getCornerRatio() {
        return cornerRatio;
    }

    public void setCornerRatio(float ratio) {
        this.cornerRatio = Math.max(0.05f, Math.min(0.9f, ratio));
    }

    public void resetCornerRatio() {
        setCornerRatio(0.25f);
    }

    // Helper: Convert float[RGBA] to ARGB int
    private int floatArrayToARGB(float[] rgba) {
        int a = (int)(rgba[3] * 255) & 0xFF;
        int r = (int)(rgba[0] * 255) & 0xFF;
        int g = (int)(rgba[1] * 255) & 0xFF;
        int b = (int)(rgba[2] * 255) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Helper: Convert ARGB int to float[RGBA]
    private void argbToFloatArray(int argb, float[] target) {
        target[3] = ((argb >> 24) & 0xFF) / 255.0f;
        target[0] = ((argb >> 16) & 0xFF) / 255.0f;
        target[1] = ((argb >> 8) & 0xFF) / 255.0f;
        target[2] = (argb & 0xFF) / 255.0f;
    }

    // ConfigurableModule implementation
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("renderMode", renderMode.name());
        config.add("canBreakColor", toJsonArray(canBreakColor));
        config.add("cannotBreakColor", toJsonArray(cannotBreakColor));
        config.addProperty("outlineWidth", outlineWidth);
        config.addProperty("fillOpacity", fillOpacity);
        config.addProperty("cornerRatio", cornerRatio);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("renderMode")) {
            try {
                renderMode = RenderMode.valueOf(data.get("renderMode").getAsString());
            } catch (IllegalArgumentException ignored) {}
        }
        if (data.has("canBreakColor")) {
            float[] color = readColorFromJson(data.getAsJsonArray("canBreakColor"));
            if (color != null) canBreakColor = color;
        }
        if (data.has("cannotBreakColor")) {
            float[] color = readColorFromJson(data.getAsJsonArray("cannotBreakColor"));
            if (color != null) cannotBreakColor = color;
        }
        if (data.has("outlineWidth")) setOutlineWidth(data.get("outlineWidth").getAsFloat());
        if (data.has("fillOpacity")) setFillOpacity(data.get("fillOpacity").getAsFloat());
        if (data.has("cornerRatio")) setCornerRatio(data.get("cornerRatio").getAsFloat());
    }

    private JsonArray toJsonArray(float[] values) {
        JsonArray array = new JsonArray();
        for (float v : values) array.add(v);
        return array;
    }

    private float[] readColorFromJson(JsonArray array) {
        if (array == null || array.size() != 4) return null;
        float[] color = new float[4];
        for (int i = 0; i < 4; i++) {
            color[i] = array.get(i).getAsFloat();
        }
        return color;
    }

    // SettingsProvider implementation
    @Override
    public java.util.List<ModuleSetting> getSettings() {
        java.util.List<ModuleSetting> settings = new java.util.ArrayList<>();

        settings.add(new DropdownSetting(
            "Render Mode",
            "ESP rendering style",
            "dungeonbreaker_render_mode",
            new String[]{"OUTLINE", "FILLED", "OUTLINE_FILLED", "CORNER"},
            () -> renderMode.ordinal(),
            index -> renderMode = RenderMode.values()[index]
        ));

        settings.add(new ColorPickerSetting(
            "Can Break Color",
            "Color when breaker has durability",
            "dungeonbreaker_can_break_color",
            () -> floatArrayToARGB(canBreakColor),
            color -> argbToFloatArray(color, canBreakColor)
        ));

        settings.add(new ColorPickerSetting(
            "Cannot Break Color",
            "Color when breaker has no durability",
            "dungeonbreaker_cannot_break_color",
            () -> floatArrayToARGB(cannotBreakColor),
            color -> argbToFloatArray(color, cannotBreakColor)
        ));

        settings.add(new SliderSetting(
            "Outline Width",
            "Thickness of outline",
            "dungeonbreaker_outline_width",
            0.5f, 10.0f,
            () -> outlineWidth,
            val -> outlineWidth = val
        ).withDecimals(1).withSuffix("px"));

        settings.add(new SliderSetting(
            "Fill Opacity",
            "Opacity of filled area",
            "dungeonbreaker_fill_opacity",
            0.0f, 1.0f,
            () -> fillOpacity,
            val -> fillOpacity = val
        ).withDecimals(2).asPercentage());

        settings.add(new SliderSetting(
            "Corner Ratio",
            "Size of corner indicators",
            "dungeonbreaker_corner_ratio",
            0.05f, 0.9f,
            () -> cornerRatio,
            val -> cornerRatio = val
        ).withDecimals(2));

        return settings;
    }

    public enum RenderMode {
        OUTLINE,
        FILLED,
        OUTLINE_FILLED,
        CORNER
    }
}
