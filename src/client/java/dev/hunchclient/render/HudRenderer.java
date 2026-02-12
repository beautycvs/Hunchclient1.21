package dev.hunchclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hunchclient.module.impl.hud.HudImageElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Renderer for custom HUD images (PNG and GIF)
 */
public class HudRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-HUD");
    private static final HudRenderer INSTANCE = new HudRenderer();

    private static final String CACHE_DIR = "config/hunchclient/hud_cache";
    private static final String IMAGES_DIR = "config/hunchclient/hud_images";
    private static final int MAX_IMAGE_SIZE = 2048;

    // LRU Cache limits to prevent memory leaks
    private static final int MAX_TEXTURE_CACHE_SIZE = 50;
    private static final int MAX_GIF_CACHE_SIZE = 10;

    // Cache: source -> texture identifier (LRU with auto-eviction)
    private final Map<String, ResourceLocation> textureCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > MAX_TEXTURE_CACHE_SIZE) {
                // Cleanup texture before removal
                try {
                    Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                } catch (Exception ignored) {}
                textureDimensions.remove(eldest.getKey());
                return true;
            }
            return false;
        }
    };
    // Texture dimensions: source -> {width, height}
    private final Map<String, int[]> textureDimensions = new HashMap<>();
    // GIF animations: source -> handler (LRU with auto-eviction)
    private final Map<String, GifAnimationHandler> gifCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GifAnimationHandler> eldest) {
            if (size() > MAX_GIF_CACHE_SIZE) {
                // Cleanup GIF resources before removal
                try {
                    eldest.getValue().close();
                } catch (Exception ignored) {}
                String key = eldest.getKey();
                DynamicTexture tex = gifTextures.remove(key);
                if (tex != null) {
                    try { tex.close(); } catch (Exception ignored) {}
                }
                return true;
            }
            return false;
        }
    };
    // GIF textures: source -> NativeImageBackedTexture
    private final Map<String, DynamicTexture> gifTextures = new HashMap<>();
    // Download futures: source -> future
    private final Map<String, CompletableFuture<Void>> downloadFutures = new HashMap<>();
    // Download progress: source -> progress (0.0 to 1.0)
    private final Map<String, Float> downloadProgress = new HashMap<>();
    // Loading status: source -> is currently loading GIF frames
    private final Map<String, Boolean> gifLoadingStatus = new HashMap<>();

    // Reusable buffer for active sources (avoids allocation every frame)
    private final Set<String> activeSourcesBuffer = new HashSet<>();

    private HudRenderer() {
        // Ensure directories exist
        try {
            Files.createDirectories(Paths.get(CACHE_DIR));
            Files.createDirectories(Paths.get(IMAGES_DIR));
        } catch (IOException e) {
            LOGGER.error("Failed to create HUD directories", e);
        }
    }

    public static HudRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Render all HUD images
     */
    public void renderAllImages(GuiGraphics context, List<HudImageElement> images) {
        if (images == null || images.isEmpty()) {
            if (!textureCache.isEmpty() || !gifCache.isEmpty() || !gifTextures.isEmpty()) {
                clearCache();
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Reuse buffer instead of allocating new HashSet every frame
        activeSourcesBuffer.clear();
        for (HudImageElement image : images) {
            if (!image.isEnabled()) {
                continue;
            }

            String source = image.getSource();
            if (source != null && !source.isEmpty()) {
                activeSourcesBuffer.add(source);
            }

            // CRITICAL: Wrap each image render in try-catch to prevent one broken image from crashing everything
            try {
                renderImage(context, image, screenWidth, screenHeight);
            } catch (Exception e) {
                // Log error but continue rendering other images
                LOGGER.error("Failed to render HUD image: {} - Error: {}", image.getSource(), e.getMessage());
                // Mark image as disabled to prevent repeated errors
                image.setEnabled(false);
            }
        }

        pruneCaches(activeSourcesBuffer);
    }

    /**
     * Render a single image
     */
    private void renderImage(GuiGraphics context, HudImageElement image, int screenWidth, int screenHeight) {
        try {
            String source = image.getSource();
            if (source == null || source.isEmpty()) {
                return;
            }

            // Validate source before rendering
            if (!isValidSource(source)) {
                LOGGER.warn("Invalid image source: {}", source);
                image.setEnabled(false);
                return;
            }

            // Check if it's a GIF
            if (source.toLowerCase().endsWith(".gif")) {
                renderGif(context, image, screenWidth, screenHeight);
            } else {
                renderStatic(context, image, screenWidth, screenHeight);
            }
        } catch (Exception e) {
            LOGGER.error("Error rendering image {}: {}", image.getSource(), e.getMessage());
            throw e; // Re-throw to be caught by renderAllImages
        }
    }

    /**
     * Validate image source (URL or local file)
     */
    private boolean isValidSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return false;
        }

        // Check if it's a URL
        if (source.startsWith("http://") || source.startsWith("https://")) {
            // Basic URL validation
            try {
                java.net.URI.create(source).toURL();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // Check if it's a local file reference
        // Allow any filename (will be checked when loading)
        return !source.contains(".."); // Prevent directory traversal
    }

    /**
     * Render static image (PNG, JPG, etc.)
     */
    private void renderStatic(GuiGraphics context, HudImageElement image, int screenWidth, int screenHeight) {
        ResourceLocation textureId = getOrLoadTexture(image.getSource());
        if (textureId == null) {
            // Show loading placeholder
            int x = image.getPixelX(screenWidth);
            int y = image.getPixelY(screenHeight);
            int width = image.getWidth();
            int height = image.getHeight();
            context.fill(x, y, x + width, y + height, 0x40808080); // Semi-transparent gray placeholder
            return;
        }

        // Get texture dimensions
        int[] dimensions = textureDimensions.get(image.getSource());
        if (dimensions == null) {
            LOGGER.warn("No dimensions found for texture: {}", image.getSource());
            return;
        }

        int textureWidth = dimensions[0];
        int textureHeight = dimensions[1];

        int x = image.getPixelX(screenWidth);
        int y = image.getPixelY(screenHeight);
        int width = image.getWidth();
        int height = image.getHeight();

        // Render the texture
        // drawTexture(pipeline, sprite, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x, y,           // Screen position
            0.0f, 0.0f,     // UV start (top-left of texture)
            width, height,  // Render size on screen
            textureWidth, textureHeight,  // Region size in texture (use full texture)
            textureWidth, textureHeight   // Total texture dimensions
        );
    }

    /**
     * Render animated GIF
     * FIXED: Now with comprehensive error handling to prevent GLFW crashes
     */
    private void renderGif(GuiGraphics context, HudImageElement image, int screenWidth, int screenHeight) {
        try {
            // Validate Minecraft instance and render context
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) {
                LOGGER.warn("Minecraft client not ready for GIF rendering");
                return;
            }
            String source = image.getSource();
            GifAnimationHandler handler = getOrLoadGif(source);
            if (handler == null || !handler.isLoaded()) {
                // Show loading progress bar
                int x = image.getPixelX(screenWidth);
                int y = image.getPixelY(screenHeight);
                int width = image.getWidth();
                int height = image.getHeight();

                // Check if we're currently downloading
                float progress = downloadProgress.getOrDefault(source, 0.0f);
                Boolean isLoadingFrames = gifLoadingStatus.getOrDefault(source, false);

                // Draw background
                context.fill(x, y, x + width, y + height, 0x80000000); // Semi-transparent black

                if (isLoadingFrames) {
                    // Loading frames - show indeterminate progress
                    int barWidth = width - 20;
                    int barHeight = 20;
                    int barX = x + 10;
                    int barY = y + height / 2 - 10;

                    // Draw progress bar background
                    context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

                    // Animated loading bar (indeterminate)
                    long time = System.currentTimeMillis();
                    int animOffset = (int) ((time / 10) % (barWidth + 40)) - 20;
                    int loadingBarWidth = 60;
                    context.fill(
                        Math.max(barX, barX + animOffset),
                        barY + 2,
                        Math.min(barX + barWidth, barX + animOffset + loadingBarWidth),
                        barY + barHeight - 2,
                        0xFF00AAFF // Light blue
                    );
                } else if (progress > 0) {
                    // Downloading - show determinate progress
                    int barWidth = width - 20;
                    int barHeight = 20;
                    int barX = x + 10;
                    int barY = y + height / 2 - 10;

                    // Draw progress bar background
                    context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

                    // Draw progress bar fill
                    int fillWidth = (int) (barWidth * progress);
                    context.fill(barX + 2, barY + 2, barX + 2 + fillWidth, barY + barHeight - 2, 0xFF00FF00);
                } else {
                    // Not yet started - show placeholder
                    context.fill(x, y, x + width, y + height, 0x40808080);
                }
                return;
            }

            // Update animation
            handler.update(image.getSpeedMultiplier());

            // Get current frame image
            NativeImage frameImage = handler.getCurrentFrame();
            if (frameImage == null) {
                return;
            }

            // IMPORTANT: Use handler's stored dimensions (constant across all frames)
            // NOT the individual frame dimensions (which can vary in optimized GIFs)
            int textureWidth = handler.getWidth();
            int textureHeight = handler.getHeight();

            if (textureWidth == 0 || textureHeight == 0) {
                LOGGER.warn("GIF has invalid dimensions: {}x{}", textureWidth, textureHeight);
                return;
            }

            // Get or create texture for this GIF
            String sourceHash = getMD5Hash(source);
            ResourceLocation frameId = ResourceLocation.fromNamespaceAndPath("hunchclient", "gif_frame/" + sourceHash);

            DynamicTexture texture = gifTextures.get(source);

            if (texture == null) {
                // CRITICAL: Force destroy any old texture that might be lingering
                // This prevents frozen/duplicate textures from previous loads
                try {
                    mc.getTextureManager().release(frameId);
                    LOGGER.info("Force destroyed old texture: {}", frameId);
                } catch (Exception ignored) {
                    // Texture doesn't exist, that's fine
                }

                // Create new texture for this GIF
                // Copy the frame data to avoid issues with the handler managing the NativeImage
                NativeImage frameCopy = new NativeImage(textureWidth, textureHeight, true);
                frameCopy.copyFrom(frameImage);
                texture = new DynamicTexture(() -> "GIF " + source, frameCopy);

                // Register texture with unique identifier
                mc.getTextureManager().register(frameId, texture);

                // CRITICAL FIX: Upload first frame to GPU immediately
                // This ensures the GIF is visible from the start
                texture.upload();
                LOGGER.info("Uploaded initial GIF frame to GPU");

                // Cache the texture and identifier
                gifTextures.put(source, texture);
                textureCache.put(source, frameId);

                LOGGER.info("Created new GIF texture: {} ({}x{})", source, textureWidth, textureHeight);
            } else {
                // PERFORMANCE OPTIMIZATION: Only copy and upload if frame changed
                // This prevents millions of pixel copies and GPU uploads per second!
                if (handler.hasFrameChanged()) {
                    NativeImage existingImage = texture.getPixels();
                    if (existingImage != null) {
                        // All frames are now guaranteed to have full GIF dimensions (handled in GifAnimationHandler)
                        existingImage.copyFrom(frameImage);
                        texture.upload(); // Upload to GPU
                        handler.markFrameUpdated(); // Mark as uploaded to avoid redundant copies
                    }
                }
            }

            int x = image.getPixelX(screenWidth);
            int y = image.getPixelY(screenHeight);
            int width = image.getWidth();
            int height = image.getHeight();

            // Render the GIF frame
            context.blit(
                RenderPipelines.GUI_TEXTURED,
                frameId,
                x, y,           // Screen position
                0.0f, 0.0f,     // UV start (top-left of texture)
                width, height,  // Render size on screen
                textureWidth, textureHeight,  // Region size in texture (use full texture)
                textureWidth, textureHeight   // Total texture dimensions
            );
        } catch (Exception e) {
            // Catch all exceptions to prevent crashes
            LOGGER.error("Error rendering GIF: {}", image.getSource(), e);
            // Show error placeholder
            try {
                int x = image.getPixelX(screenWidth);
                int y = image.getPixelY(screenHeight);
                int width = image.getWidth();
                int height = image.getHeight();
                context.fill(x, y, x + width, y + height, 0x40FF0000); // Red placeholder for errors
            } catch (Exception ignored) {
                // Even placeholder failed, just give up
            }
        }
    }

    /**
     * Get or load static texture
     */
    private ResourceLocation getOrLoadTexture(String source) {
        // Check cache
        if (textureCache.containsKey(source)) {
            return textureCache.get(source);
        }

        // Check if it's a URL
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return loadFromUrl(source);
        } else {
            return loadFromFile(source);
        }
    }

    /**
     * Get or load GIF animation
     */
    private GifAnimationHandler getOrLoadGif(String source) {
        try {
            // Check cache
            GifAnimationHandler cachedHandler = gifCache.get(source);
            if (cachedHandler != null && cachedHandler.isLoaded()) {
                // Clear loading status since it's already loaded
                gifLoadingStatus.remove(source);
                return cachedHandler;
            }

            // Remove failed handler from cache
            if (cachedHandler != null) {
                try {
                    cachedHandler.close();
                } catch (Exception e) {
                    LOGGER.warn("Error closing old GIF handler: {}", e.getMessage());
                }
                gifCache.remove(source);
                gifTextures.remove(source);
                textureCache.remove(source);
            }

            // Load GIF
            GifAnimationHandler handler = null;
            if (source.startsWith("http://") || source.startsWith("https://")) {
                File cachedFile = getCachedFile(source);
                if (cachedFile.exists()) {
                    LOGGER.info("Loading GIF from cached URL: {}", source);

                    // Mark as loading frames
                    gifLoadingStatus.put(source, true);

                    try {
                        handler = GifAnimationHandler.loadFromFile(cachedFile);

                        // If cached file is corrupted, delete and re-download
                        if (handler == null || !handler.isLoaded()) {
                            LOGGER.warn("Cached GIF is corrupted, deleting: {}", cachedFile.getAbsolutePath());
                            cachedFile.delete();
                            gifLoadingStatus.remove(source);
                            downloadFromUrl(source);
                            return null;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to load cached GIF, deleting: {}", e.getMessage());
                        cachedFile.delete();
                        gifLoadingStatus.remove(source);
                        downloadFromUrl(source);
                        return null;
                    }
                } else {
                    // Download asynchronously
                    LOGGER.info("Downloading GIF from URL: {}", source);
                    downloadFromUrl(source);
                    return null;
                }
            } else {
                File file = new File(IMAGES_DIR, source);
                if (file.exists()) {
                    LOGGER.info("Loading GIF from local file: {}", source);

                    // Mark as loading frames
                    gifLoadingStatus.put(source, true);

                    try {
                        handler = GifAnimationHandler.loadFromFile(file);

                        if (handler == null || !handler.isLoaded()) {
                            LOGGER.error("Failed to load GIF file (corrupted?): {}", source);
                            gifLoadingStatus.remove(source);
                            return null;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error loading GIF file: {}", e.getMessage());
                        gifLoadingStatus.remove(source);
                        return null;
                    }
                } else {
                    LOGGER.warn("GIF file not found: {}", source);
                    return null;
                }
            }

            if (handler != null && handler.isLoaded()) {
                gifCache.put(source, handler);
                gifLoadingStatus.remove(source); // Loading complete
                LOGGER.info("Cached GIF successfully: {}", source);
            } else {
                gifLoadingStatus.remove(source); // Loading failed
            }
            return handler;
        } catch (Exception e) {
            LOGGER.error("Critical error in getOrLoadGif for {}: {}", source, e.getMessage());
            return null;
        }
    }

    /**
     * Load texture from URL (cached)
     */
    private ResourceLocation loadFromUrl(String url) {
        File cachedFile = getCachedFile(url);

        // If cached, load from file
        if (cachedFile.exists()) {
            return loadTextureFromFile(cachedFile, url);
        }

        // Download asynchronously
        downloadFromUrl(url);
        return null;
    }

    /**
     * Download image from URL asynchronously
     */
    private void downloadFromUrl(String url) {
        // Check if already downloading
        if (downloadFutures.containsKey(url)) {
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                File cachedFile = getCachedFile(url);
                downloadFile(url, cachedFile);
                LOGGER.info("Downloaded HUD image from: {}", url);
            } catch (Exception e) {
                LOGGER.error("Failed to download HUD image from: {}", url, e);
            } finally {
                downloadFutures.remove(url);
            }
        });

        downloadFutures.put(url, future);
    }

    /**
     * Download file from URL with progress tracking
     */
    private void downloadFile(String urlString, File outputFile) throws IOException {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "HunchClient/1.0");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        // Get file size for progress tracking
        long fileSize = connection.getContentLengthLong();
        long totalBytesRead = 0;

        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Update progress (if file size is known)
                if (fileSize > 0) {
                    float progress = (float) totalBytesRead / fileSize;
                    downloadProgress.put(urlString, progress);
                }
            }

            // Download complete
            downloadProgress.put(urlString, 1.0f);
        } finally {
            // Clear progress after a delay
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                    downloadProgress.remove(urlString);
                } catch (InterruptedException ignored) {}
            });
        }
    }

    /**
     * Load texture from local file
     */
    private ResourceLocation loadFromFile(String path) {
        File file = new File(IMAGES_DIR, path);
        if (!file.exists()) {
            LOGGER.warn("HUD image file not found: {}", path);
            return null;
        }

        return loadTextureFromFile(file, path);
    }

    /**
     * Load texture from file and register
     */
    private ResourceLocation loadTextureFromFile(File file, String source) {
        try {
            BufferedImage bufferedImage = ImageIO.read(file);
            if (bufferedImage == null) {
                LOGGER.error("Failed to read image: {}", file.getAbsolutePath());
                return null;
            }

            // Check size limits
            if (bufferedImage.getWidth() > MAX_IMAGE_SIZE || bufferedImage.getHeight() > MAX_IMAGE_SIZE) {
                LOGGER.error("Image too large (max {}x{}): {}", MAX_IMAGE_SIZE, MAX_IMAGE_SIZE, file.getAbsolutePath());
                return null;
            }

            // Store dimensions
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            textureDimensions.put(source, new int[]{width, height});

            // Convert to NativeImage
            NativeImage nativeImage = convertToNativeImage(bufferedImage);

            // Create texture
            String hash = getMD5Hash(source);
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "hud/" + hash);
            DynamicTexture texture = new DynamicTexture(() -> "HUD " + source, nativeImage);

            // Register texture
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().register(textureId, texture);

            // Cache
            textureCache.put(source, textureId);
            LOGGER.info("Loaded HUD texture: {} ({}x{})", source, width, height);
            return textureId;

        } catch (Exception e) {
            LOGGER.error("Failed to load texture from file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Convert BufferedImage to NativeImage (OPTIMIZED with bulk pixel transfer)
     */
    private NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        NativeImage nativeImage = new NativeImage(width, height, true);

        // OPTIMIZATION: Use bulk pixel transfer instead of per-pixel loops
        // getRGB with array is ~10x faster than individual getRGB calls
        int[] pixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);

        // Process pixels in bulk (still need ARGB -> ABGR conversion)
        // Single loop is faster than nested loops (better cache locality)
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[index++];

                // Convert ARGB to ABGR (NativeImage format)
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

    /**
     * Get cached file path for URL
     */
    private File getCachedFile(String url) {
        String hash = getMD5Hash(url);
        String extension = getFileExtension(url);
        return new File(CACHE_DIR, hash + extension);
    }

    /**
     * Get MD5 hash of string
     */
    private String getMD5Hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * Get file extension from URL or path
     */
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastDot > lastSlash) {
            return path.substring(lastDot);
        }
        return ".png"; // Default
    }

    /**
     * Refresh image from source (force reload)
     */
    public void refreshImage(String source) {
        LOGGER.info("=== REFRESHING IMAGE: {} ===", source);

        // CRITICAL FIX: Don't call refreshFromUrl since it would duplicate cleanup
        // Just do cleanup here and trigger re-download
        if (source.startsWith("http://") || source.startsWith("https://")) {
            refreshFromUrl(source);
        } else {
            // For local files, just clear cache (file doesn't need re-download)
            clearImageCache(source);
        }

        LOGGER.info("=== REFRESH COMPLETE FOR: {} ===", source);
    }

    /**
     * Clear cache for a specific image
     */
    private void clearImageCache(String source) {
        Minecraft mc = Minecraft.getInstance();

        // Cancel any pending downloads first
        CompletableFuture<Void> pendingDownload = downloadFutures.remove(source);
        if (pendingDownload != null && !pendingDownload.isDone()) {
            pendingDownload.cancel(true);
            LOGGER.info("Cancelled pending download for: {}", source);
        }

        // Close and remove GIF handler (stops animation)
        GifAnimationHandler oldHandler = gifCache.remove(source);
        if (oldHandler != null) {
            oldHandler.close();
            LOGGER.info("Closed old GIF handler");
        }

        // Close texture object
        DynamicTexture oldTexture = gifTextures.remove(source);
        if (oldTexture != null) {
            oldTexture.close();
            LOGGER.info("Closed old GIF texture object");
        }

        // CRITICAL - Destroy texture from TextureManager
        ResourceLocation oldId = textureCache.remove(source);
        if (oldId != null) {
            mc.getTextureManager().release(oldId);
            LOGGER.info("Destroyed texture from TextureManager: {}", oldId);
        }

        // Clear dimension cache
        textureDimensions.remove(source);

        // Clear progress indicators
        downloadProgress.remove(source);
        gifLoadingStatus.remove(source);
    }

    /**
     * Refresh image from URL (force re-download)
     */
    public void refreshFromUrl(String url) {
        LOGGER.info("Refreshing from URL: {}", url);

        // Clear all caches for this URL
        clearImageCache(url);

        // Delete cached file to force re-download
        File cachedFile = getCachedFile(url);
        if (cachedFile.exists()) {
            boolean deleted = cachedFile.delete();
            LOGGER.info("Deleted cached file: {} (success: {})", cachedFile.getName(), deleted);
        }

        // Trigger new download
        if (url.toLowerCase().endsWith(".gif")) {
            getOrLoadGif(url);
        } else {
            getOrLoadTexture(url);
        }
    }

    /**
     * Clear all caches
     */
    public void clearCache() {
        LOGGER.info("Clearing all HUD image caches");

        // Destroy all textures from TextureManager
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation id : textureCache.values()) {
            mc.getTextureManager().release(id);
        }

        textureCache.clear();
        textureDimensions.clear();

        for (GifAnimationHandler handler : gifCache.values()) {
            handler.close();
        }
        gifCache.clear();

        for (DynamicTexture texture : gifTextures.values()) {
            texture.close();
        }
        gifTextures.clear();

        LOGGER.info("All caches cleared");
    }

    private void pruneCaches(Set<String> activeSources) {
        if (activeSources == null) {
            return;
        }

        for (String source : new HashSet<>(textureCache.keySet())) {
            if (!activeSources.contains(source)) {
                clearImageCache(source);
            }
        }

        for (String source : new HashSet<>(gifCache.keySet())) {
            if (!activeSources.contains(source)) {
                clearImageCache(source);
            }
        }
    }
}
