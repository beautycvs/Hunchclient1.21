package dev.hunchclient.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * Manages loading and updating of overlay textures (PNG, GIF, etc.)
 * for the item overlay system.
 *
 * Supports:
 * - Static images (PNG, JPG)
 * - Animated GIFs with full animation support
 * - File paths, URLs, and resource loading
 */
public class OverlayTextureManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("OverlayTextureManager");
    private static final ResourceLocation DEFAULT_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/default_overlay.png");

    // Current texture state
    private static ResourceLocation currentTextureId;
    private static DynamicTexture currentTexture;
    private static GifAnimationHandler gifHandler;
    private static boolean isGif = false;
    private static String currentSource;

    // Pending load state (for deferred loading)
    private static String pendingResourcePath;
    private static File pendingFile;
    private static boolean needsLoad = false;

    /**
     * Load an overlay texture from a file path
     * Supports PNG, JPG, and GIF (with animation)
     */
    public static boolean loadFromFile(File file) {
        if (!file.exists() || !file.isFile()) {
            LOGGER.error("File does not exist: {}", file.getAbsolutePath());
            return false;
        }

        String fileName = file.getName().toLowerCase();
        String source = file.getAbsolutePath();

        // Check if it's a GIF
        if (fileName.endsWith(".gif")) {
            return loadGif(file, source);
        } else {
            return loadStaticImage(file, source);
        }
    }

    /**
     * Load an overlay texture from a resource path
     * If RenderSystem is not ready, defers loading until next tick
     */
    public static boolean loadFromResource(String resourcePath) {
        // Check if RenderSystem is initialized
        if (!isRenderSystemReady()) {
            LOGGER.info("Deferring texture load until RenderSystem is ready: {}", resourcePath);
            pendingResourcePath = resourcePath;
            pendingFile = null;
            needsLoad = true;
            return true; // Return true, will load later
        }

        try {
            InputStream stream = OverlayTextureManager.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                LOGGER.error("Resource not found: {}", resourcePath);
                return false;
            }

            if (resourcePath.toLowerCase().endsWith(".gif")) {
                return loadGifFromStream(stream, resourcePath);
            } else {
                return loadStaticImageFromStream(stream, resourcePath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load resource: {}", resourcePath, e);
            return false;
        }
    }

    /**
     * Create a magenta (debug) fallback texture
     * This is used when no custom texture is loaded to show the system is working
     */
    public static boolean loadMagentaFallback() {
        try {
            // Clean up previous texture
            cleanup();

            // Create a simple magenta texture (256x256)
            int size = 256;
            NativeImage image = new NativeImage(size, size, true);

            // Fill with bright magenta/pink color
            // RGB: (255, 0, 255) = Magenta
            // ABGR format: (A << 24) | (B << 16) | (G << 8) | R
            int magenta = (255 << 24) | (255 << 16) | (0 << 8) | 255;

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setPixelABGR(x, y, magenta);
                }
            }

            // Register texture
            currentTexture = new DynamicTexture(() -> "Magenta Fallback", image);
            currentTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/overlay_magenta_fallback");

            Minecraft.getInstance().getTextureManager().register(currentTextureId, currentTexture);

            isGif = false;
            currentSource = "magenta_fallback";

            LOGGER.info("Created magenta fallback overlay texture ({}x{}) - system ready!", size, size);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to create magenta fallback texture", e);
            return false;
        }
    }

    /**
     * Load a static image (PNG, JPG)
     */
    private static boolean loadStaticImage(File file, String source) {
        try {
            // Clean up previous texture
            cleanup();

            // Load image using ImageIO
            BufferedImage bufferedImage = ImageIO.read(file);
            if (bufferedImage == null) {
                LOGGER.error("Failed to read image: {}", file.getAbsolutePath());
                return false;
            }

            // Convert to NativeImage
            NativeImage nativeImage = convertToNativeImage(bufferedImage);

            // Register texture with Minecraft's texture manager
            currentTexture = new DynamicTexture(() -> "Overlay Texture", nativeImage);
            currentTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/overlay_" + System.currentTimeMillis());

            Minecraft.getInstance().getTextureManager().register(currentTextureId, currentTexture);

            isGif = false;
            currentSource = source;

            LOGGER.info("Loaded static image: {} ({}x{})", file.getName(), nativeImage.getWidth(), nativeImage.getHeight());
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load static image: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Load a static image from input stream
     */
    private static boolean loadStaticImageFromStream(InputStream stream, String source) {
        try {
            // Clean up previous texture
            cleanup();

            // Load image using ImageIO
            BufferedImage bufferedImage = ImageIO.read(stream);
            if (bufferedImage == null) {
                LOGGER.error("Failed to read image from stream: {}", source);
                return false;
            }

            // Convert to NativeImage
            NativeImage nativeImage = convertToNativeImage(bufferedImage);

            // Register texture
            currentTexture = new DynamicTexture(() -> "Overlay Texture", nativeImage);
            currentTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/overlay_" + System.currentTimeMillis());

            Minecraft.getInstance().getTextureManager().register(currentTextureId, currentTexture);

            isGif = false;
            currentSource = source;

            LOGGER.info("Loaded overlay texture: {} ({}x{})", source, nativeImage.getWidth(), nativeImage.getHeight());
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load static image from stream: {}", source, e);
            return false;
        }
    }

    /**
     * Load an animated GIF
     */
    private static boolean loadGif(File file, String source) {
        try {
            // Clean up previous texture
            cleanup();

            // Load GIF using GifAnimationHandler
            gifHandler = GifAnimationHandler.loadFromFile(file);

            if (!gifHandler.isLoaded()) {
                LOGGER.error("Failed to load GIF: {}", file.getAbsolutePath());
                return false;
            }

            // Get first frame
            NativeImage firstFrame = gifHandler.getCurrentFrame();
            if (firstFrame == null) {
                LOGGER.error("GIF has no frames: {}", file.getAbsolutePath());
                return false;
            }

            // Register texture with first frame
            currentTexture = new DynamicTexture(() -> "Overlay GIF", firstFrame);
            currentTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/overlay_gif_" + System.currentTimeMillis());

            Minecraft.getInstance().getTextureManager().register(currentTextureId, currentTexture);

            isGif = true;
            currentSource = source;

            LOGGER.info("Loaded GIF: {} ({} frames, {}x{})",
                file.getName(), gifHandler.getFrameCount(), gifHandler.getWidth(), gifHandler.getHeight());
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load GIF: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Load a GIF from input stream
     */
    private static boolean loadGifFromStream(InputStream stream, String source) {
        try {
            // Clean up previous texture
            cleanup();

            // Load GIF using GifAnimationHandler
            gifHandler = GifAnimationHandler.loadFromStream(stream);

            if (!gifHandler.isLoaded()) {
                LOGGER.error("Failed to load GIF from stream: {}", source);
                return false;
            }

            // Get first frame
            NativeImage firstFrame = gifHandler.getCurrentFrame();
            if (firstFrame == null) {
                LOGGER.error("GIF has no frames from stream: {}", source);
                return false;
            }

            // Register texture with first frame
            currentTexture = new DynamicTexture(() -> "Overlay GIF", firstFrame);
            currentTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/overlay_gif_" + System.currentTimeMillis());

            Minecraft.getInstance().getTextureManager().register(currentTextureId, currentTexture);

            isGif = true;
            currentSource = source;

            LOGGER.info("Loaded GIF from stream: {} ({} frames, {}x{})",
                source, gifHandler.getFrameCount(), gifHandler.getWidth(), gifHandler.getHeight());
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load GIF from stream: {}", source, e);
            return false;
        }
    }

    /**
     * Update GIF animation (call every tick)
     * Also handles deferred texture loading
     */
    public static void tick(float speedMultiplier) {
        // Handle deferred loading
        if (needsLoad && isRenderSystemReady()) {
            needsLoad = false;
            if (pendingResourcePath != null) {
                loadFromResource(pendingResourcePath);
                pendingResourcePath = null;
            } else if (pendingFile != null) {
                loadFromFile(pendingFile);
                pendingFile = null;
            }
        }

        // Update GIF animation
        if (!isGif || gifHandler == null) {
            return;
        }

        gifHandler.update(speedMultiplier);

        // Upload new frame to GPU if changed
        if (gifHandler.hasFrameChanged()) {
            NativeImage currentFrame = gifHandler.getCurrentFrame();
            if (currentFrame != null && currentTexture != null) {
                currentTexture.setPixels(currentFrame);
                currentTexture.upload();
                gifHandler.markFrameUpdated();
            }
        }
    }

    /**
     * Check if RenderSystem is ready for texture operations
     */
    private static boolean isRenderSystemReady() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.getTextureManager() != null && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the current texture identifier for binding
     */
    public static ResourceLocation getTextureId() {
        return currentTextureId != null ? currentTextureId : DEFAULT_TEXTURE_ID;
    }

    /**
     * Check if a texture is loaded
     */
    public static boolean hasTexture() {
        return currentTextureId != null && currentTexture != null;
    }

    /**
     * Get current source (file path, URL, etc.)
     */
    public static String getCurrentSource() {
        return currentSource;
    }

    /**
     * Check if current texture is an animated GIF
     */
    public static boolean isAnimated() {
        return isGif && gifHandler != null && gifHandler.getFrameCount() > 1;
    }

    /**
     * Bind the overlay texture to the specified texture unit using Minecraft's system
     * This is the correct way to bind textures in 1.21+
     * @param textureUnit The texture unit index (0, 1, 2, ...)
     * @return true if successful
     */
    public static boolean bindToUnit(int textureUnit) {
        if (currentTextureId == null) {
            return false;
        }
        try {
            net.minecraft.client.renderer.texture.AbstractTexture texture =
                Minecraft.getInstance().getTextureManager().getTexture(currentTextureId);
            if (texture != null && texture.getTextureView() != null) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(textureUnit, texture.getTextureView());
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to bind overlay texture", e);
            return false;
        }
    }

    /**
     * Get the raw OpenGL texture ID by binding and querying
     * Workaround for 1.21+ where direct ID access is abstracted
     */
    public static int getGlTextureId() {
        if (currentTextureId == null) {
            return -1;
        }
        try {
            net.minecraft.client.renderer.texture.AbstractTexture texture =
                Minecraft.getInstance().getTextureManager().getTexture(currentTextureId);
            if (texture != null && texture.getTextureView() != null) {
                // Bind to unit 0 first, then query the bound texture
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture.getTextureView());
                return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            }
            return -1;
        } catch (Exception e) {
            LOGGER.error("Failed to get GL texture ID", e);
            return -1;
        }
    }

    /**
     * Cleanup current texture resources
     */
    public static void cleanup() {
        if (currentTexture != null) {
            currentTexture.close();
            currentTexture = null;
        }

        if (currentTextureId != null && Minecraft.getInstance() != null) {
            Minecraft.getInstance().getTextureManager().release(currentTextureId);
            currentTextureId = null;
        }

        if (gifHandler != null) {
            gifHandler.close();
            gifHandler = null;
        }

        isGif = false;
        currentSource = null;
    }

    /**
     * Convert BufferedImage to NativeImage (ARGB -> ABGR)
     */
    private static NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        NativeImage nativeImage = new NativeImage(width, height, true);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = bufferedImage.getRGB(x, y);

                // Convert ARGB to ABGR
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelABGR(x, y, abgr);
            }
        }

        return nativeImage;
    }
}
