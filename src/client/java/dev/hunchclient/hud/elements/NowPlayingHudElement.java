package dev.hunchclient.hud.elements;

import com.google.gson.JsonObject;
import dev.hunchclient.hud.BaseHudElement;
import dev.hunchclient.module.impl.misc.NowPlayingModule;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD Element for Now Playing display
 * Integrates with the unified HUD editor
 */
public class NowPlayingHudElement extends BaseHudElement {

    private boolean showProgressBar;
    private boolean showAlbum;
    private boolean showAlbumArt;
    private int backgroundColor;
    private int textColor;
    private int accentColor;

    public NowPlayingHudElement(float x, float y, int width, int height) {
        super("Now Playing", x, y, width, height);
        this.showProgressBar = true;
        this.showAlbum = true;
        this.showAlbumArt = true;
        this.backgroundColor = 0x80000000;
        this.textColor = 0xFFFFFFFF;
        this.accentColor = 0xFF1DB954;
    }

    @Override
    public void render(GuiGraphics context, int x, int y, int screenWidth, int screenHeight, boolean editMode) {
        if (!enabled) {
            return;
        }

        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        if (editMode) {
            // Show placeholder in edit mode
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            context.fill(x, y, x + width, y + height, backgroundColor);

            String text = "Now Playing";
            int textWidth = mc.font.width(text);
            context.drawString(mc.font, text, x + width / 2 - textWidth / 2, y + height / 2 - 4, textColor, true);
        } else {
            // Render actual Now Playing HUD using module's rendering logic
            module.renderAt(context, x, y, width, height, 0.0f);
        }
    }

    @Override
    public int getMinWidth() {
        return 200;
    }

    @Override
    public int getMinHeight() {
        return 60;
    }

    @Override
    public int getMaxWidth() {
        return 600;
    }

    @Override
    public int getMaxHeight() {
        return 200;
    }

    @Override
    public JsonObject saveToJson() {
        JsonObject json = super.saveToJson();
        json.addProperty("type", "now_playing");
        json.addProperty("showProgressBar", showProgressBar);
        json.addProperty("showAlbum", showAlbum);
        json.addProperty("showAlbumArt", showAlbumArt);
        json.addProperty("backgroundColor", backgroundColor);
        json.addProperty("textColor", textColor);
        json.addProperty("accentColor", accentColor);
        return json;
    }

    @Override
    public void loadFromJson(JsonObject json) {
        super.loadFromJson(json);
        if (json.has("showProgressBar")) this.showProgressBar = json.get("showProgressBar").getAsBoolean();
        if (json.has("showAlbum")) this.showAlbum = json.get("showAlbum").getAsBoolean();
        if (json.has("showAlbumArt")) this.showAlbumArt = json.get("showAlbumArt").getAsBoolean();
        if (json.has("backgroundColor")) this.backgroundColor = json.get("backgroundColor").getAsInt();
        if (json.has("textColor")) this.textColor = json.get("textColor").getAsInt();
        if (json.has("accentColor")) this.accentColor = json.get("accentColor").getAsInt();
    }

    // Getters and setters
    public boolean isShowProgressBar() {
        return showProgressBar;
    }

    public void setShowProgressBar(boolean showProgressBar) {
        this.showProgressBar = showProgressBar;
    }

    public boolean isShowAlbum() {
        return showAlbum;
    }

    public void setShowAlbum(boolean showAlbum) {
        this.showAlbum = showAlbum;
    }

    public boolean isShowAlbumArt() {
        return showAlbumArt;
    }

    public void setShowAlbumArt(boolean showAlbumArt) {
        this.showAlbumArt = showAlbumArt;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }
}
