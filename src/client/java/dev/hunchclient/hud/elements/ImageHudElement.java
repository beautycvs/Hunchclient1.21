package dev.hunchclient.hud.elements;

import com.google.gson.JsonObject;
import dev.hunchclient.hud.BaseHudElement;
import dev.hunchclient.module.impl.hud.HudImageElement;
import dev.hunchclient.render.HudRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD Element adapter for image/GIF display
 * Wraps HudImageElement for use with the unified HUD editor
 */
public class ImageHudElement extends BaseHudElement {

    private String source;
    private float speedMultiplier;

    public ImageHudElement(String source, float x, float y, int width, int height) {
        super("Image: " + getShortName(source), x, y, width, height);
        this.source = source;
        this.speedMultiplier = 1.0f;
    }

    private HudImageElement legacyElement; // Reference to original element

    /**
     * Create from old HudImageElement format
     */
    public static ImageHudElement fromLegacy(HudImageElement legacy) {
        ImageHudElement element = new ImageHudElement(
            legacy.getSource(),
            legacy.getX(),
            legacy.getY(),
            legacy.getWidth(),
            legacy.getHeight()
        );
        element.setEnabled(legacy.isEnabled());
        element.speedMultiplier = legacy.getSpeedMultiplier();
        element.legacyElement = legacy; // Keep reference for syncing back
        return element;
    }

    /**
     * Sync position/size changes back to the legacy element
     * Call this when positions change in the HUD editor
     */
    public void syncBackToLegacy() {
        if (legacyElement != null) {
            legacyElement.setX(this.x);
            legacyElement.setY(this.y);
            legacyElement.setWidth(this.width);
            legacyElement.setHeight(this.height);
            legacyElement.setEnabled(this.enabled);
            legacyElement.setSpeedMultiplier(this.speedMultiplier);
        }
    }

    @Override
    public void render(GuiGraphics context, int x, int y, int screenWidth, int screenHeight, boolean editMode) {
        if (!enabled) {
            return;
        }

        // Create temporary HudImageElement for rendering
        HudImageElement tempElement = new HudImageElement(source, this.x, this.y, width, height);
        tempElement.setEnabled(enabled);
        tempElement.setSpeedMultiplier(speedMultiplier);

        // Use existing HudRenderer
        java.util.List<HudImageElement> list = java.util.Collections.singletonList(tempElement);
        HudRenderer.getInstance().renderAllImages(context, list);
    }

    @Override
    public int getMinWidth() {
        return 10;
    }

    @Override
    public int getMinHeight() {
        return 10;
    }

    @Override
    public JsonObject saveToJson() {
        JsonObject json = super.saveToJson();
        json.addProperty("type", "image");
        json.addProperty("source", source);
        json.addProperty("speedMultiplier", speedMultiplier);
        return json;
    }

    @Override
    public void loadFromJson(JsonObject json) {
        super.loadFromJson(json);
        if (json.has("source")) this.source = json.get("source").getAsString();
        if (json.has("speedMultiplier")) this.speedMultiplier = json.get("speedMultiplier").getAsFloat();
        this.displayName = "Image: " + getShortName(source);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
        this.displayName = "Image: " + getShortName(source);
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        this.speedMultiplier = Math.max(0.1f, Math.min(5.0f, speedMultiplier));
    }

    /**
     * Get short name for display (filename or last part of URL)
     */
    private static String getShortName(String source) {
        if (source == null || source.isEmpty()) {
            return "Unknown";
        }

        // Extract filename from path or URL
        int lastSlash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < source.length() - 1) {
            return source.substring(lastSlash + 1);
        }

        // If too long, truncate
        if (source.length() > 20) {
            return source.substring(0, 17) + "...";
        }

        return source;
    }
}
