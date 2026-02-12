package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.render.GalaxyTextureReplacer;
import com.google.gson.JsonObject;
import java.awt.FileDialog;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;
import jnafilechooser.api.JnaFileChooser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Galaxy Viewmodel Overlay Module
 *
 * Replaces item textures in first-person (viewmodel) with an animated galaxy texture.
 * The galaxy reacts dynamically to camera movement (parallax effect).
 */
public class ViewmodelOverlayModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IViewmodelOverlay {

    private static ViewmodelOverlayModule instance;

    // Settings
    private float parallaxIntensity = 0.2f;
    private float animationSpeed = 1.0f;
    private float rotationSpeed = 0.0f;
    private float overlayOpacity = 1.0f; // 0.0 - 1.0 (0-100%)
    private boolean useWhitelist = false;
    private boolean useBlacklist = false;
    private final Set<String> whitelist = new HashSet<>();
    private final Set<String> blacklist = new HashSet<>();

    // Item Glow Settings
    private boolean itemGlowEnabled = false;
    private float glowRadius = 5.0f;
    private float glowIntensity = 1.0f;
    private int glowColor = 0xFFFFFFFF; // ARGB: White by default

    public ViewmodelOverlayModule() {
        super("Viewmodel Overlay", "Replace item textures with custom shader overlay (supports PNG, JPG, GIF)", Category.VISUALS, true); // Watchdog safe
        instance = this;
        System.out.println("[ViewmodelOverlayModule] Constructor called!");
    }

    public static ViewmodelOverlayModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        System.out.println("[ViewmodelOverlayModule] onEnable() called, initializing overlay system...");

        try {
            // Load default texture if none is loaded
            if (!dev.hunchclient.render.OverlayTextureManager.hasTexture()) {
                // Try to load default galaxy texture from resources
                boolean loaded = dev.hunchclient.render.OverlayTextureManager.loadFromResource("/assets/hunchclient/textures/galaxy.png");

                // If galaxy.png not found, use MAGENTA fallback to show system is working
                if (!loaded) {
                    System.out.println("[ViewmodelOverlayModule] Galaxy texture not found, using MAGENTA fallback");
                    dev.hunchclient.render.OverlayTextureManager.loadMagentaFallback();
                } else {
                    System.out.println("[ViewmodelOverlayModule] Galaxy texture loaded successfully");
                }
            }

            System.out.println("[ViewmodelOverlayModule] Initialization complete");
        } catch (Exception e) {
            System.err.println("[ViewmodelOverlayModule] ERROR during initialization:");
            e.printStackTrace();
        }
    }

    @Override
    protected void onDisable() {
        // Cleanup overlay textures
        dev.hunchclient.render.OverlayTextureManager.cleanup();

        // Cleanup item glow renderer
        if (itemGlowEnabled) {
            dev.hunchclient.render.ItemGlowRenderer.cleanup();
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }

        // Update GIF animation if loaded
        dev.hunchclient.render.OverlayTextureManager.tick(animationSpeed);
    }

    /**
     * Load a custom overlay texture from file
     * Supports PNG, JPG, and animated GIF
     */
    public boolean loadOverlayFromFile(java.io.File file) {
        return dev.hunchclient.render.OverlayTextureManager.loadFromFile(file);
    }

    /**
     * Load a custom overlay texture from resource path
     */
    public boolean loadOverlayFromResource(String resourcePath) {
        return dev.hunchclient.render.OverlayTextureManager.loadFromResource(resourcePath);
    }

    /**
     * Get the current overlay texture source (file path or resource)
     */
    public String getCurrentOverlaySource() {
        return dev.hunchclient.render.OverlayTextureManager.getCurrentSource();
    }

    /**
     * Check if the current overlay is animated (GIF)
     */
    public boolean isOverlayAnimated() {
        return dev.hunchclient.render.OverlayTextureManager.isAnimated();
    }

    /**
     * Check if the given item should have its texture replaced
     */
    public boolean shouldReplaceItem(Item item) {
        if (!isEnabled()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String itemName = itemId.getPath(); // e.g. "diamond_sword"

        // Whitelist has priority
        if (useWhitelist) {
            return whitelist.contains(itemName);
        }

        // Blacklist
        if (useBlacklist) {
            return !blacklist.contains(itemName);
        }

        // Default: replace all items
        return true;
    }

    /**
     * Check if a texture identifier should be replaced
     * This checks if the texture is for a first-person item
     */
    public boolean shouldReplaceTexture(ResourceLocation textureId) {
        if (!isEnabled()) {
            return false;
        }

        String path = textureId.getPath();

        // Only replace item textures
        // Minecraft item textures are in textures/item/
        if (!path.startsWith("textures/item/")) {
            return false;
        }

        // If using whitelist/blacklist, we need to extract the item name
        if (useWhitelist || useBlacklist) {
            // Extract item name from path: textures/item/diamond_sword.png -> diamond_sword
            String itemName = path
                .replace("textures/item/", "")
                .replace(".png", "");

            if (useWhitelist) {
                return whitelist.contains(itemName);
            }

            if (useBlacklist) {
                return !blacklist.contains(itemName);
            }
        }

        return true;
    }

    // ----- Getters -----

    public float getParallaxIntensity() {
        return parallaxIntensity;
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public float getRotationSpeed() {
        return rotationSpeed;
    }

    public float getOverlayOpacity() {
        return overlayOpacity;
    }

    // Item Glow Getters

    public boolean isItemGlowEnabled() {
        return itemGlowEnabled && isEnabled();
    }

    public float getGlowRadius() {
        return glowRadius;
    }

    public float getGlowIntensity() {
        return glowIntensity;
    }

    public int getGlowColor() {
        return glowColor;
    }

    public float[] getGlowColorRGB() {
        // Convert ARGB int to RGB float array
        float r = ((glowColor >> 16) & 0xFF) / 255.0f;
        float g = ((glowColor >> 8) & 0xFF) / 255.0f;
        float b = (glowColor & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    // ----- Setters -----

    public void setParallaxIntensity(float intensity) {
        this.parallaxIntensity = clamp(intensity, 0.0f, 1.0f);
    }

    public void setAnimationSpeed(float speed) {
        this.animationSpeed = clamp(speed, 0.0f, 5.0f);
    }

    public void setRotationSpeed(float speed) {
        this.rotationSpeed = clamp(speed, -2.0f, 2.0f);
    }

    public void setOverlayOpacity(float opacity) {
        this.overlayOpacity = clamp(opacity, 0.0f, 1.0f);
    }

    public void setUseWhitelist(boolean use) {
        this.useWhitelist = use;
        if (use) {
            this.useBlacklist = false; // Whitelist has priority
        }
    }

    public void setUseBlacklist(boolean use) {
        this.useBlacklist = use;
        if (use) {
            this.useWhitelist = false; // Can't use both
        }
    }

    public void addToWhitelist(String itemName) {
        whitelist.add(itemName.toLowerCase());
    }

    public void removeFromWhitelist(String itemName) {
        whitelist.remove(itemName.toLowerCase());
    }

    public void addToBlacklist(String itemName) {
        blacklist.add(itemName.toLowerCase());
    }

    public void removeFromBlacklist(String itemName) {
        blacklist.remove(itemName.toLowerCase());
    }

    public void clearWhitelist() {
        whitelist.clear();
    }

    public void clearBlacklist() {
        blacklist.clear();
    }

    // Item Glow Setters

    public void setItemGlowEnabled(boolean enabled) {
        this.itemGlowEnabled = enabled;
        if (enabled) {
            // Initialize ItemGlowRenderer if needed
            dev.hunchclient.render.ItemGlowRenderer.init();
        }
    }

    public void setGlowRadius(float radius) {
        this.glowRadius = clamp(radius, 1.0f, 20.0f);
        dev.hunchclient.render.ItemGlowRenderer.setGlowRadius(this.glowRadius);
    }

    public void setGlowIntensity(float intensity) {
        this.glowIntensity = clamp(intensity, 0.0f, 5.0f);
        dev.hunchclient.render.ItemGlowRenderer.setGlowIntensity(this.glowIntensity);
    }

    public void setGlowColor(int color) {
        this.glowColor = color;
        // Convert to RGB float and update renderer
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        dev.hunchclient.render.ItemGlowRenderer.setGlowColor(r, g, b);
    }

    // ----- Config Serialization -----

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        object.addProperty("parallaxIntensity", this.parallaxIntensity);
        object.addProperty("animationSpeed", this.animationSpeed);
        object.addProperty("rotationSpeed", this.rotationSpeed);
        object.addProperty("overlayOpacity", this.overlayOpacity);
        object.addProperty("useWhitelist", this.useWhitelist);
        object.addProperty("useBlacklist", this.useBlacklist);

        // Save lists as comma-separated strings
        object.addProperty("whitelist", String.join(",", whitelist));
        object.addProperty("blacklist", String.join(",", blacklist));

        // Save item glow settings
        object.addProperty("itemGlowEnabled", this.itemGlowEnabled);
        object.addProperty("glowRadius", this.glowRadius);
        object.addProperty("glowIntensity", this.glowIntensity);
        object.addProperty("glowColor", this.glowColor);

        return object;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        if (data.has("parallaxIntensity")) {
            setParallaxIntensity(data.get("parallaxIntensity").getAsFloat());
        }
        if (data.has("animationSpeed")) {
            setAnimationSpeed(data.get("animationSpeed").getAsFloat());
        }
        if (data.has("rotationSpeed")) {
            setRotationSpeed(data.get("rotationSpeed").getAsFloat());
        }
        if (data.has("overlayOpacity")) {
            setOverlayOpacity(data.get("overlayOpacity").getAsFloat());
        }
        if (data.has("useWhitelist")) {
            this.useWhitelist = data.get("useWhitelist").getAsBoolean();
        }
        if (data.has("useBlacklist")) {
            this.useBlacklist = data.get("useBlacklist").getAsBoolean();
        }

        // Load lists
        if (data.has("whitelist")) {
            String whitelistStr = data.get("whitelist").getAsString();
            if (!whitelistStr.isEmpty()) {
                for (String item : whitelistStr.split(",")) {
                    whitelist.add(item.trim().toLowerCase());
                }
            }
        }
        if (data.has("blacklist")) {
            String blacklistStr = data.get("blacklist").getAsString();
            if (!blacklistStr.isEmpty()) {
                for (String item : blacklistStr.split(",")) {
                    blacklist.add(item.trim().toLowerCase());
                }
            }
        }

        // Load item glow settings
        if (data.has("itemGlowEnabled")) {
            setItemGlowEnabled(data.get("itemGlowEnabled").getAsBoolean());
        }
        if (data.has("glowRadius")) {
            setGlowRadius(data.get("glowRadius").getAsFloat());
        }
        if (data.has("glowIntensity")) {
            setGlowIntensity(data.get("glowIntensity").getAsFloat());
        }
        if (data.has("glowColor")) {
            setGlowColor(data.get("glowColor").getAsInt());
        }
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Button to load custom texture
        settings.add(new ButtonSetting(
            "Load Custom Texture",
            "Open file dialog to load PNG, JPG, or GIF texture",
            "load_texture_button",
            this::promptOverlayFileSelection
        ));

        settings.add(new SliderSetting(
            "Parallax Intensity",
            "How much the galaxy moves with camera movement",
            "parallax_intensity",
            0.0f, 1.0f,
            () -> parallaxIntensity,
            (val) -> setParallaxIntensity(val)
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Animation Speed",
            "Speed of the galaxy animation",
            "animation_speed",
            0.0f, 5.0f,
            () -> animationSpeed,
            (val) -> setAnimationSpeed(val)
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Rotation Speed",
            "Rotation speed of the galaxy texture",
            "rotation_speed",
            -2.0f, 2.0f,
            () -> rotationSpeed,
            (val) -> setRotationSpeed(val)
        ).withDecimals(2));

        settings.add(new SliderSetting(
            "Overlay Opacity",
            "How strongly the overlay covers the item (0-100%)",
            "overlay_opacity",
            0.0f, 100.0f,
            () -> overlayOpacity * 100.0f,
            (val) -> setOverlayOpacity(val / 100.0f)
        ).withDecimals(0));

        settings.add(new CheckboxSetting(
            "Use Whitelist",
            "Only replace items in the whitelist",
            "use_whitelist",
            () -> useWhitelist,
            (val) -> setUseWhitelist(val)
        ));

        settings.add(new CheckboxSetting(
            "Use Blacklist",
            "Replace all items except those in blacklist",
            "use_blacklist",
            () -> useBlacklist,
            (val) -> setUseBlacklist(val)
        ));

        // Note: Whitelist/Blacklist editing would need a custom UI component
        // For now, users can edit the config file directly

        // Item Glow Settings
        settings.add(new CheckboxSetting(
            "Item Glow",
            "Enable glow effect on held items",
            "item_glow_enabled",
            () -> itemGlowEnabled,
            (val) -> setItemGlowEnabled(val)
        ));

        settings.add(new SliderSetting(
            "Glow Radius",
            "Radius of the item glow effect",
            "glow_radius",
            1.0f, 20.0f,
            () -> glowRadius,
            (val) -> setGlowRadius(val)
        ).withDecimals(1));

        settings.add(new SliderSetting(
            "Glow Intensity",
            "Intensity/brightness of the glow",
            "glow_intensity",
            0.0f, 5.0f,
            () -> glowIntensity,
            (val) -> setGlowIntensity(val)
        ).withDecimals(2));

        settings.add(new ColorPickerSetting(
            "Glow Color",
            "Color of the item glow",
            "glow_color",
            () -> glowColor,
            (color) -> setGlowColor(color)
        ));

        return settings;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Show a native file picker off-thread, then load the texture on the game thread.
     * Uses Linux-native dialogs (zenity/kdialog) first, then falls back to JNA/AWT/Swing.
     */
    private void promptOverlayFileSelection() {
        new Thread(() -> {
            try {
                // Force headless off before any AWT/JNA touch
                System.setProperty("java.awt.headless", "false");

                File chosen = null;

                // On Linux, try native dialogs first (they work better with GLFW)
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("linux")) {
                    chosen = showLinuxNativeDialog();
                }

                // Fallback chain: JNA -> AWT -> Swing
                if (chosen == null) {
                    chosen = showJnaFileChooser();
                }
                if (chosen == null) {
                    chosen = showAwtFileDialog();
                }
                if (chosen == null) {
                    chosen = showSwingFileChooser();
                }
                if (chosen == null) {
                    sendStatusMessage("§c[Overlay] Kein File ausgewählt.");
                    return; // user cancelled
                }

                final File fileToLoad = chosen;
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    boolean loaded = loadOverlayFromFile(fileToLoad);
                    sendStatusMessage(loaded
                        ? "§a[Overlay] Loaded " + fileToLoad.getName()
                        : "§c[Overlay] Failed to load " + fileToLoad.getName());
                });
            } catch (Exception e) {
                System.err.println("[ViewmodelOverlayModule] Error selecting overlay texture: " + e.getMessage());
                e.printStackTrace();
                sendStatusMessage("§c[Overlay] Fehler beim Öffnen des File Chooser: " + e.getMessage());
            }
        }, "HC-OverlayFileChooser").start();
    }

    /**
     * Use Linux native file dialogs (zenity or kdialog) which work better with GLFW
     */
    private File showLinuxNativeDialog() {
        // Try zenity first (GTK-based, most common on Linux)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "zenity", "--file-selection",
                "--title=Select Overlay Texture (PNG/JPG/GIF)",
                "--file-filter=Image files (png, jpg, gif) | *.png *.jpg *.jpeg *.gif"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String path = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && path != null && !path.isEmpty()) {
                File file = new File(path.trim());
                if (file.exists() && file.isFile()) {
                    return file;
                }
            }
        } catch (Exception e) {
            System.out.println("[ViewmodelOverlayModule] zenity not available, trying kdialog...");
        }

        // Try kdialog (KDE-based)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "kdialog", "--getopenfilename", System.getProperty("user.home"),
                "*.png *.jpg *.jpeg *.gif|Image files (PNG, JPG, GIF)"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String path = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && path != null && !path.isEmpty()) {
                File file = new File(path.trim());
                if (file.exists() && file.isFile()) {
                    return file;
                }
            }
        } catch (Exception e) {
            System.out.println("[ViewmodelOverlayModule] kdialog not available, falling back to Java dialogs...");
        }

        return null;
    }

    private void sendStatusMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(text), false);
            }
        });
    }

    private File showJnaFileChooser() {
        try {
            JnaFileChooser chooser = new JnaFileChooser();
            chooser.setTitle("Select Overlay Texture (PNG/JPG/GIF)");
            chooser.addFilter("Image files", "png", "jpg", "jpeg", "gif");
            chooser.setMode(JnaFileChooser.Mode.Files);
            chooser.setMultiSelectionEnabled(false);
            boolean approved = chooser.showOpenDialog(null);
            if (approved) {
                return chooser.getSelectedFile();
            }
        } catch (Throwable t) {
            System.err.println("[ViewmodelOverlayModule] JNA File Chooser failed: " + t.getMessage());
        }
        return null;
    }

    private File showAwtFileDialog() {
        try {
            FileDialog dialog = new FileDialog((Frame) null, "Select Overlay Texture (PNG/JPG/GIF)", FileDialog.LOAD);
            dialog.setFilenameFilter((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
            });
            dialog.setVisible(true);
            if (dialog.getFile() == null) {
                return null;
            }
            return new File(dialog.getDirectory(), dialog.getFile());
        } catch (Throwable t) {
            return null;
        }
    }

    private File showSwingFileChooser() {
        try {
            javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
            fileChooser.setDialogTitle("Select Overlay Texture (PNG, JPG, GIF)");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif"
            ));

            int result = fileChooser.showOpenDialog(null);
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                return fileChooser.getSelectedFile();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
