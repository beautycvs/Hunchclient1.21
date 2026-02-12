package dev.hunchclient.hud;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Abstract base implementation of HudElement
 * Handles common positioning, sizing, and serialization logic
 */
public abstract class BaseHudElement implements HudElement {

    protected final String id;
    protected String displayName;
    protected float x;
    protected float y;
    protected int width;
    protected int height;
    protected HudAnchor anchor;
    protected boolean enabled;
    protected boolean locked;
    protected int zOrder;

    public BaseHudElement(String displayName, float x, float y, int width, int height) {
        this.id = UUID.randomUUID().toString();
        this.displayName = displayName;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.anchor = HudAnchor.TOP_LEFT;
        this.enabled = true;
        this.locked = false;
        this.zOrder = 0;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public void setX(float x) {
        this.x = x;
    }

    @Override
    public void setY(float y) {
        this.y = y;
    }

    @Override
    public void setSize(int width, int height) {
        this.width = Math.max(getMinWidth(), width);
        this.height = Math.max(getMinHeight(), height);

        if (getMaxWidth() > 0) {
            this.width = Math.min(getMaxWidth(), this.width);
        }
        if (getMaxHeight() > 0) {
            this.height = Math.min(getMaxHeight(), this.height);
        }
    }

    @Override
    public HudAnchor getAnchor() {
        return anchor;
    }

    @Override
    public void setAnchor(HudAnchor anchor) {
        this.anchor = anchor;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public int getZOrder() {
        return zOrder;
    }

    @Override
    public void setZOrder(int zOrder) {
        this.zOrder = zOrder;
    }

    @Override
    public JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("displayName", displayName);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("anchor", anchor.name());
        json.addProperty("enabled", enabled);
        json.addProperty("locked", locked);
        json.addProperty("zOrder", zOrder);
        return json;
    }

    @Override
    public void loadFromJson(JsonObject json) {
        if (json.has("x")) this.x = json.get("x").getAsFloat();
        if (json.has("y")) this.y = json.get("y").getAsFloat();
        if (json.has("width")) this.width = json.get("width").getAsInt();
        if (json.has("height")) this.height = json.get("height").getAsInt();
        if (json.has("anchor")) {
            try {
                this.anchor = HudAnchor.valueOf(json.get("anchor").getAsString());
            } catch (IllegalArgumentException e) {
                this.anchor = HudAnchor.TOP_LEFT;
            }
        }
        if (json.has("enabled")) this.enabled = json.get("enabled").getAsBoolean();
        if (json.has("locked")) this.locked = json.get("locked").getAsBoolean();
        if (json.has("zOrder")) this.zOrder = json.get("zOrder").getAsInt();
    }

    /**
     * Calculate absolute X position based on anchor and screen size
     */
    protected int calculateAbsoluteX(int screenWidth) {
        return anchor.calculateX(x, screenWidth, width);
    }

    /**
     * Calculate absolute Y position based on anchor and screen size
     */
    protected int calculateAbsoluteY(int screenHeight) {
        return anchor.calculateY(y, screenHeight, height);
    }
}
