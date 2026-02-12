package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.module.impl.hud.HudImageElement;
import dev.hunchclient.render.HudRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Custom Image HUD Module
 *
 * Displays custom PNG and GIF images on the HUD.
 * Supports multiple images, drag-and-drop positioning, and animated GIFs.
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only rendering
 * - No packets sent
 * - Visual only
 */
public class ImageHUDModule extends Module implements ConfigurableModule, SettingsProvider {

    private static ImageHUDModule INSTANCE;

    private final List<HudImageElement> images = new ArrayList<>();
    private final int maxImages = 10;

    // Flag to track if config was loaded (delayed loading to avoid GLFW crash)
    private boolean configLoaded = false;
    private JsonObject pendingConfig = null;

    // HUD elements for unified editor (one per image)
    private final List<dev.hunchclient.hud.elements.ImageHudElement> hudElements = new ArrayList<>();

    public ImageHUDModule() {
        // CRITICAL: Start disabled by default to prevent GLFW crashes
        // User can manually enable it after game is fully loaded
        super("HUD", "Display custom PNG/GIF images on your HUD", Category.VISUALS, false);
        INSTANCE = this;

        // Force disabled on construction to prevent startup crashes
        this.setEnabled(false);
    }

    public static ImageHUDModule getInstance() {
        return INSTANCE;
    }

    @Override
    protected void onEnable() {
        // Rendering happens via HudEditorManager now
        // DO NOT load textures here - GLFW may not be initialized yet
        // Textures are loaded lazily on first render

        // If config is pending and GLFW is ready, apply it now
        if (!configLoaded && pendingConfig != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                applyPendingConfig();
            }
        }

        // Register all images with HudEditorManager
        syncHudElements();
    }

    @Override
    protected void onDisable() {
        // Clear renderer cache when disabled
        // This is safe because it only clears caches, doesn't interact with GLFW
        try {
            HudRenderer.getInstance().clearCache();
        } catch (Exception e) {
            // Ignore errors during shutdown
        }

        // Unregister all HUD elements
        for (dev.hunchclient.hud.elements.ImageHudElement hudElement : hudElements) {
            dev.hunchclient.hud.HudEditorManager.getInstance().unregisterElement(hudElement);
        }
        hudElements.clear();
    }

    /**
     * Sync HudElements with current images list
     */
    private void syncHudElements() {
        // Unregister old elements
        for (dev.hunchclient.hud.elements.ImageHudElement hudElement : hudElements) {
            dev.hunchclient.hud.HudEditorManager.getInstance().unregisterElement(hudElement);
        }
        hudElements.clear();

        // Create and register new elements for each image
        for (HudImageElement image : images) {
            dev.hunchclient.hud.elements.ImageHudElement hudElement =
                dev.hunchclient.hud.elements.ImageHudElement.fromLegacy(image);
            hudElements.add(hudElement);
            dev.hunchclient.hud.HudEditorManager.getInstance().registerElement(hudElement);
        }
    }

    /**
     * Sync positions/sizes back from HUD editor to legacy elements
     * Call this when HUD editor makes changes
     */
    public void syncFromHudEditor() {
        for (dev.hunchclient.hud.elements.ImageHudElement hudElement : hudElements) {
            hudElement.syncBackToLegacy();
        }
    }

    /**
     * Get all HUD images
     * IMPORTANT: This method does NOT lazy-load config
     * Use getImagesWithConfigCheck() for rendering contexts
     */
    public List<HudImageElement> getImages() {
        return new ArrayList<>(images);
    }

    /**
     * Add a new image to the HUD
     */
    public boolean addImage(String source, float x, float y, int width, int height) {
        if (images.size() >= maxImages) {
            return false;
        }

        HudImageElement image = new HudImageElement(source, x, y, width, height);
        images.add(image);

        // If module is enabled, sync HUD elements immediately
        if (isEnabled()) {
            syncHudElements();
        }

        return true;
    }

    /**
     * Add a new image with default position and size
     */
    public boolean addImage(String source) {
        // Default: center screen, 100x100px
        return addImage(source, 50.0f, 50.0f, 100, 100);
    }

    /**
     * Remove an image by ID
     */
    public boolean removeImage(String id) {
        boolean removed = images.removeIf(img -> img.getId().equals(id));
        if (removed && isEnabled()) {
            syncHudElements();
        }
        return removed;
    }

    /**
     * Remove an image by index
     */
    public boolean removeImage(int index) {
        if (index >= 0 && index < images.size()) {
            images.remove(index);
            saveConfig();
            if (isEnabled()) {
                syncHudElements();
            }
            return true;
        }
        return false;
    }

    /**
     * Get image by ID
     */
    public HudImageElement getImage(String id) {
        return images.stream()
                .filter(img -> img.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Clear all images
     */
    public void clearImages() {
        images.clear();

        // CRITICAL: Only clear cache if GLFW is initialized
        // During startup/shutdown, GLFW might not be available
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                HudRenderer.getInstance().clearCache();
            }
        } catch (Exception e) {
            // GLFW not initialized or shutting down - safe to ignore
        }

        // Sync HUD elements
        if (isEnabled()) {
            syncHudElements();
        }
    }

    /**
     * Refresh an image from its source (useful for URL images)
     */
    public void refreshImage(String source) {
        if (source != null && !source.isEmpty()) {
            HudRenderer.getInstance().refreshFromUrl(source);
        }
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();

        // Save all images
        JsonArray imagesArray = new JsonArray();
        for (HudImageElement image : images) {
            imagesArray.add(image.toJson());
        }
        config.add("images", imagesArray);

        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        // CRITICAL FIX: Store config but don't apply it yet
        // Config will be applied on first render when GLFW is ready
        // This prevents GLFW crashes during startup
        this.pendingConfig = data;
    }

    /**
     * Actually load the config (called when GLFW is ready)
     */
    private void applyPendingConfig() {
        if (pendingConfig == null || configLoaded) {
            return;
        }

        // Mark as loaded to prevent duplicate loading
        configLoaded = true;

        // Clear existing images (data only, no texture operations)
        images.clear();

        // Load images from config (data only)
        if (pendingConfig.has("images")) {
            JsonArray imagesArray = pendingConfig.getAsJsonArray("images");
            for (int i = 0; i < imagesArray.size() && i < maxImages; i++) {
                try {
                    JsonObject imageJson = imagesArray.get(i).getAsJsonObject();
                    HudImageElement image = new HudImageElement(imageJson);
                    images.add(image);
                    // NOTE: Image textures are loaded lazily during first render
                } catch (Exception e) {
                    // Skip invalid image entries - don't crash the whole config loading
                }
            }
        }

        // Clear pending config
        pendingConfig = null;

        // Sync HUD elements after loading config
        if (isEnabled()) {
            syncHudElements();
        }
    }

    /**
     * Get all HUD images (lazily loads config if needed)
     */
    public List<HudImageElement> getImagesWithConfigCheck() {
        // Apply pending config if GLFW is ready
        if (!configLoaded && pendingConfig != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                applyPendingConfig();
            }
        }
        return new ArrayList<>(images);
    }

    /**
     * Get maximum number of images allowed
     */
    public int getMaxImages() {
        return maxImages;
    }

    /**
     * Get current number of images
     */
    public int getImageCount() {
        return images.size();
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Edit HUD Position Button (opens unified HUD editor)
        settings.add(new ButtonSetting(
            "Edit HUD Position",
            "Open unified HUD editor to position and resize images",
            "imagehud_edit_position",
            () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    mc.setScreen(new dev.hunchclient.hud.HudEditorScreen(mc.screen));
                });
            }
        ));

        // Add Image Button (opens add screen)
        settings.add(new ButtonSetting(
            "Add GIF/PNG",
            "Add a new image to HUD",
            "imagehud_add",
            () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    mc.setScreen(new dev.hunchclient.gui.AddImageScreen(mc.screen, this));
                });
            }
        ));

        // Refresh All Images Button
        settings.add(new ButtonSetting(
            "Refresh All",
            "Refresh all GIF/PNG images (fixes frozen animations)",
            "imagehud_refresh_all",
            () -> {
                Minecraft mc = Minecraft.getInstance();
                for (HudImageElement image : images) {
                    HudRenderer.getInstance().refreshImage(image.getSource());
                }
                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§b[HUD] §aAll images refreshed!"
                    ), false);
                }
            }
        ));

        // Clear All Images Button
        settings.add(new ButtonSetting(
            "Clear All Images",
            "Remove all HUD images",
            "imagehud_clear",
            () -> {
                clearImages();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§b[HUD] §aAll images cleared!"
                    ), false);
                }
            }
        ));

        return settings;
    }
}
