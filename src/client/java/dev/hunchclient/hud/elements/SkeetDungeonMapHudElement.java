package dev.hunchclient.hud.elements;

import com.google.gson.JsonObject;
import dev.hunchclient.hud.BaseHudElement;
import dev.hunchclient.hud.HudAnchor;
import dev.hunchclient.module.impl.dungeons.devmap.DevMapState;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.FloorType;
import dev.hunchclient.module.impl.dungeons.map.SkeetDungeonMapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD Element for the Skeet-styled dungeon map
 * Integrates with the unified HUD editor
 */
public class SkeetDungeonMapHudElement extends BaseHudElement {

    private static SkeetDungeonMapHudElement INSTANCE;

    private final SkeetDungeonMapRenderer renderer;
    private float scale;

    public SkeetDungeonMapHudElement(float x, float y, float scale) {
        super("Skeet Dungeon Map", x, y, (int)(128 * scale), (int)(128 * scale));
        this.scale = scale;
        this.anchor = HudAnchor.TOP_LEFT;
        this.renderer = new SkeetDungeonMapRenderer();
        INSTANCE = this;
    }

    public static SkeetDungeonMapHudElement getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(GuiGraphics context, int x, int y, int screenWidth, int screenHeight, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();

        if (editMode) {
            // Show placeholder in edit mode
            context.fill(x, y, x + width, y + height, 0x80000000);

            // Draw Skeet-style border preview
            context.fill(x, y, x + width, y + 1, 0xFFFF0000); // Red top
            context.fill(x, y + height - 1, x + width, y + height, 0xFF0000FF); // Blue bottom
            context.fill(x, y, x + 1, y + height, 0xFFFF00FF); // Magenta left
            context.fill(x + width - 1, y, x + width, y + height, 0xFF00FF00); // Green right

            String text1 = "Skeet Dungeon Map";
            String text2 = "(RGB Glow)";
            int text1Width = mc.font.width(text1);
            int text2Width = mc.font.width(text2);

            context.drawString(mc.font, text1, x + width / 2 - text1Width / 2, y + height / 2 - 4, 0xFFFFFFFF, true);
            context.drawString(mc.font, text2, x + width / 2 - text2Width / 2, y + height / 2 + 6, 0xFFAAAAAA, true);
            return;
        }

        // Tick state (handles floor persistence)
        DevMapState.getInstance().tick();

        // Only render if we have a valid floor
        if (DevMapState.getInstance().getFloor() == FloorType.NONE) {
            return;
        }

        // Don't render in boss fight
        if (DevMapState.getInstance().isInBoss()) {
            return;
        }

        // Render actual map
        renderer.render(context, x, y, width, height, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    @Override
    public void setSize(int width, int height) {
        // Maintain aspect ratio (square map)
        int size = Math.min(width, height);
        super.setSize(size, size);
        this.scale = size / 128.0f;
    }

    @Override
    public int getMinWidth() {
        return 64; // 0.5x scale
    }

    @Override
    public int getMinHeight() {
        return 64;
    }

    @Override
    public int getMaxWidth() {
        return 384; // 3x scale
    }

    @Override
    public int getMaxHeight() {
        return 384;
    }

    @Override
    public JsonObject saveToJson() {
        JsonObject json = super.saveToJson();
        json.addProperty("type", "skeet_dungeon_map");
        json.addProperty("scale", scale);
        return json;
    }

    @Override
    public void loadFromJson(JsonObject json) {
        super.loadFromJson(json);
        if (json.has("scale")) {
            this.scale = json.get("scale").getAsFloat();
            this.width = (int)(128 * scale);
            this.height = (int)(128 * scale);
        }
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.5f, Math.min(3.0f, scale));
        this.width = (int)(128 * this.scale);
        this.height = (int)(128 * this.scale);
    }

    public SkeetDungeonMapRenderer getRenderer() {
        return renderer;
    }
}
