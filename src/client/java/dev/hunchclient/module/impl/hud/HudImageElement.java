package dev.hunchclient.module.impl.hud;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Represents a single HUD image element (PNG or GIF)
 */
public class HudImageElement {
    private final String id;
    private String source; // URL or file path
    private float x; // Position X (percentage 0-100)
    private float y; // Position Y (percentage 0-100)
    private int width; // Width in pixels
    private int height; // Height in pixels
    private boolean enabled;
    private float speedMultiplier; // For GIF animations (0.5 - 2.0)

    public HudImageElement(String source, float x, float y, int width, int height) {
        this.id = UUID.randomUUID().toString();
        this.source = source;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.enabled = true;
        this.speedMultiplier = 1.0f;
    }

    // Constructor from JSON
    public HudImageElement(JsonObject json) {
        this.id = json.has("id") ? json.get("id").getAsString() : UUID.randomUUID().toString();
        this.source = json.get("source").getAsString();
        this.x = json.get("x").getAsFloat();
        this.y = json.get("y").getAsFloat();
        this.width = json.get("width").getAsInt();
        this.height = json.get("height").getAsInt();
        this.enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : true;
        this.speedMultiplier = json.has("speedMultiplier") ? json.get("speedMultiplier").getAsFloat() : 1.0f;
    }

    // Serialize to JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("source", source);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("enabled", enabled);
        json.addProperty("speedMultiplier", speedMultiplier);
        return json;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    // Setters
    public void setSource(String source) {
        this.source = source;
    }

    public void setX(float x) {
        this.x = Math.max(0, Math.min(100, x));
    }

    public void setY(float y) {
        this.y = Math.max(0, Math.min(100, y));
    }

    public void setWidth(int width) {
        this.width = Math.max(10, Math.min(2048, width));
    }

    public void setHeight(int height) {
        this.height = Math.max(10, Math.min(2048, height));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        this.speedMultiplier = Math.max(0.1f, Math.min(5.0f, speedMultiplier));
    }

    // Helper to check if point is inside this element
    public boolean contains(int screenX, int screenY, int screenWidth, int screenHeight) {
        int pixelX = (int) (x * screenWidth / 100f);
        int pixelY = (int) (y * screenHeight / 100f);
        return screenX >= pixelX && screenX <= pixelX + width &&
               screenY >= pixelY && screenY <= pixelY + height;
    }

    // Get pixel coordinates
    public int getPixelX(int screenWidth) {
        return (int) (x * screenWidth / 100f);
    }

    public int getPixelY(int screenHeight) {
        return (int) (y * screenHeight / 100f);
    }
}
