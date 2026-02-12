package dev.hunchclient.module.impl.misc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple async album art loader for Now Playing HUD
 * Loads thumbnail images from file paths provided by SMTC
 */
public class AlbumArtLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-AlbumArt");

    private static AlbumArtLoader INSTANCE;

    // Current loaded image
    private ResourceLocation currentTexture = null;
    private volatile String currentPath = null; // Make volatile for thread-safe reads
    private volatile String loadingPath = null; // Track what we're currently loading
    private volatile long lastModifiedTime = 0; // Track file modification time to avoid reloading same file
    private int width = 0;
    private int height = 0;
    private final Object loadLock = new Object(); // Lock for load operations
    private final AtomicLong loadSequence = new AtomicLong(0);
    private volatile long activeLoadId = 0;

    private AlbumArtLoader() {}

    public static AlbumArtLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AlbumArtLoader();
        }
        return INSTANCE;
    }

    /**
     * Load album art from file path asynchronously
     * @param filePath Path to thumbnail file
     */
    public void loadAlbumArt(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            LOGGER.debug("File path is null or empty, clearing album art");
            clearAlbumArt();
            return;
        }

        long loadId;
        // Skip if already loaded or loading this exact path (thread-safe check)
        synchronized (loadLock) {
            // Check if same path is already loaded
            if (filePath.equals(currentPath)) {
                // Check if file was modified since we last loaded it
                try {
                    Path path = Paths.get(filePath);
                    if (Files.exists(path)) {
                        long currentModified = Files.getLastModifiedTime(path).toMillis();
                        if (currentModified <= lastModifiedTime) {
                            // File hasn't changed, skip reload
                            return;
                        }
                        // File was modified, continue to reload
                    } else {
                        // File no longer exists, keep current texture
                        return;
                    }
                } catch (Exception e) {
                    // On error, don't reload
                    return;
                }
            }

            if (filePath.equals(loadingPath)) {
                // Already loading this path
                return;
            }

            // Mark this path as loading
            loadingPath = filePath;
            loadId = loadSequence.incrementAndGet();
            activeLoadId = loadId;
            LOGGER.info("Starting async load of album art from: {}", filePath);
        }

        final long requestId = loadId;
        CompletableFuture.runAsync(() -> {
            try {
                Path path = Paths.get(filePath);
                LOGGER.info("Attempting to load album art from absolute path: {}", path.toAbsolutePath());

                if (!Files.exists(path)) {
                    LOGGER.warn("Album art file not found: {}", path.toAbsolutePath());
                    markLoadCompleteIfCurrent(requestId, filePath, 0);
                    return;
                }

                // Check file modification time
                long modifiedTime = Files.getLastModifiedTime(path).toMillis();

                // Check if file is readable and has content
                long fileSize = Files.size(path);
                LOGGER.info("Album art file exists: size={} bytes, readable={}", fileSize, Files.isReadable(path));

                if (!Files.isReadable(path) || fileSize == 0) {
                    LOGGER.warn("Album art file not readable or empty: {}", filePath);
                    markLoadCompleteIfCurrent(requestId, filePath, modifiedTime);
                    return;
                }

                // Load image with retry logic (file might still be writing)
                NativeImage image = null;
                int retries = 5; // Increased from 3
                Exception lastException = null;

                for (int attempt = 0; attempt < retries && image == null; attempt++) {
                    // Wait before retry (but not on first attempt)
                    if (attempt > 0) {
                        LOGGER.info("Retry {} of {} to read album art...", attempt + 1, retries);
                        try {
                            Thread.sleep(300); // Increased from 150ms - file needs more time to be written
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        LOGGER.info("Reading NativeImage from file...");
                    }

                    try {
                        // Load entire file into memory and close stream immediately
                        // This prevents file locking issues with SmtcReader
                        byte[] imageData;
                        try (InputStream stream = new FileInputStream(path.toFile())) {
                            imageData = stream.readAllBytes();
                        }

                        // Debug: Log file size and first bytes
                        LOGGER.info("Read {} bytes from file", imageData.length);

                        // Detect file format
                        boolean isPNG = false;
                        boolean isJPEG = false;

                        if (imageData.length >= 16) {
                            StringBuilder hexDump = new StringBuilder("First 16 bytes (hex): ");
                            for (int i = 0; i < 16 && i < imageData.length; i++) {
                                hexDump.append(String.format("%02X ", imageData[i] & 0xFF));
                            }
                            LOGGER.info(hexDump.toString());

                            // Check for PNG signature: 89 50 4E 47 0D 0A 1A 0A
                            isPNG = imageData.length >= 8 &&
                                (imageData[0] & 0xFF) == 0x89 &&
                                (imageData[1] & 0xFF) == 0x50 &&
                                (imageData[2] & 0xFF) == 0x4E &&
                                (imageData[3] & 0xFF) == 0x47 &&
                                (imageData[4] & 0xFF) == 0x0D &&
                                (imageData[5] & 0xFF) == 0x0A &&
                                (imageData[6] & 0xFF) == 0x1A &&
                                (imageData[7] & 0xFF) == 0x0A;

                            // Check for JPEG signature: FF D8 FF
                            isJPEG = imageData.length >= 3 &&
                                (imageData[0] & 0xFF) == 0xFF &&
                                (imageData[1] & 0xFF) == 0xD8 &&
                                (imageData[2] & 0xFF) == 0xFF;

                            LOGGER.info("File format detection: PNG={}, JPEG={}", isPNG, isJPEG);

                            if (!isPNG && !isJPEG) {
                                LOGGER.warn("File does not appear to be PNG or JPEG format!");
                            }
                        } else {
                            LOGGER.warn("File is too small: {} bytes", imageData.length);
                        }

                        // Now decode from memory
                        try (java.io.ByteArrayInputStream memStream = new java.io.ByteArrayInputStream(imageData)) {
                            // Try to load as PNG first (native support)
                            if (isPNG) {
                                image = NativeImage.read(memStream);
                                LOGGER.info("NativeImage loaded successfully (PNG)! Size: {}x{}", image.getWidth(), image.getHeight());
                            }
                            // For JPEG and other formats, convert via BufferedImage
                            else if (isJPEG) {
                                LOGGER.info("Loading JPEG image via ImageIO...");
                                BufferedImage bufferedImage = ImageIO.read(memStream);
                                if (bufferedImage != null) {
                                    image = convertBufferedImageToNativeImage(bufferedImage);
                                    LOGGER.info("NativeImage converted successfully (JPEG)! Size: {}x{}", image.getWidth(), image.getHeight());
                                } else {
                                    throw new RuntimeException("ImageIO returned null for JPEG");
                                }
                            }
                            // Try generic ImageIO for unknown formats
                            else {
                                LOGGER.info("Trying generic ImageIO for unknown format...");
                                BufferedImage bufferedImage = ImageIO.read(memStream);
                                if (bufferedImage != null) {
                                    image = convertBufferedImageToNativeImage(bufferedImage);
                                    LOGGER.info("NativeImage converted successfully (generic)! Size: {}x{}", image.getWidth(), image.getHeight());
                                } else {
                                    throw new RuntimeException("ImageIO could not read image");
                                }
                            }
                        }
                    } catch (Exception e) {
                        lastException = e;
                        LOGGER.warn("Failed attempt {} of {}: {}", attempt + 1, retries, e.getMessage());
                        if (e.getMessage() != null && e.getMessage().contains("Bad PNG signature")) {
                            LOGGER.error("PNG signature error - file may be corrupted or in wrong format");
                        }
                        // Continue to next iteration if not last attempt
                    }
                }

                if (image != null) {
                    // Upload texture on main thread
                    String loadedPath = filePath; // Capture for lambda
                    long capturedModTime = modifiedTime; // Capture modification time
                    NativeImage finalImage = image; // For lambda
                    Minecraft.getInstance().execute(() -> {
                        try {
                            if (!shouldApplyLoad(requestId, loadedPath)) {
                                try {
                                    finalImage.close();
                                } catch (Exception ignored) {}
                                return;
                            }
                            uploadTexture(finalImage, loadedPath, capturedModTime, requestId);
                        } catch (Exception e) {
                            LOGGER.error("Failed to upload album art texture: {}", e.getMessage(), e);
                            try {
                                finalImage.close();
                            } catch (Exception ignored) {}
                            clearLoadingIfCurrent(requestId, loadedPath);
                        }
                    });
                } else {
                    // Failed after retries - file might be corrupted or still being written
                    LOGGER.warn("Failed to read album art image after {} retries: {}",
                        retries, lastException != null ? lastException.getMessage() : "unknown");
                    markLoadCompleteIfCurrent(requestId, filePath, modifiedTime);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load album art: {}", e.getMessage(), e);
                clearLoadingIfCurrent(requestId, filePath);
            }
        });
    }

    /**
     * Upload texture to GPU (must be called on main thread)
     */
    private void uploadTexture(NativeImage image, String loadedPath, long modifiedTime, long loadId) {
        Minecraft mc = Minecraft.getInstance();

        synchronized (loadLock) {
            if (!isLoadIdCurrent(loadId, loadedPath)) {
                try {
                    image.close();
                } catch (Exception ignored) {}
                return;
            }

            // Clean up old texture
            if (currentTexture != null) {
                try {
                    mc.getTextureManager().release(currentTexture);
                } catch (Exception e) {
                    LOGGER.warn("Failed to destroy old album art texture: {}", e.getMessage());
                }
                currentTexture = null;
            }

            // Create new texture
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("hunchclient", "albumart_nowplaying");
            DynamicTexture texture = new DynamicTexture(() -> "AlbumArt", image);
            mc.getTextureManager().register(id, texture);

            currentTexture = id;
            width = image.getWidth();
            height = image.getHeight();

            // Update current path, modification time, and clear loading state (thread-safe)
            currentPath = loadedPath;
            lastModifiedTime = modifiedTime;
            loadingPath = null;
        }

        LOGGER.info("Loaded album art: {}x{} from {} (modified: {})", width, height, loadedPath, modifiedTime);
    }

    /**
     * Clear current album art
     */
    public void clearAlbumArt() {
        synchronized (loadLock) {
            activeLoadId = loadSequence.incrementAndGet();
            if (currentTexture != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        synchronized (loadLock) {
                            if (currentTexture != null) {
                                mc.getTextureManager().release(currentTexture);
                                currentTexture = null;
                            }
                            currentPath = null;
                            loadingPath = null;
                            lastModifiedTime = 0;
                            width = 0;
                            height = 0;
                        }
                    });
                }
            } else {
                // No texture to destroy, just clear the paths
                currentPath = null;
                loadingPath = null;
                lastModifiedTime = 0;
            }
        }
    }

    /**
     * Check if album art is loaded and ready
     */
    public boolean hasAlbumArt() {
        return currentTexture != null && width > 0 && height > 0;
    }

    /**
     * Get current texture identifier
     */
    public ResourceLocation getTexture() {
        return currentTexture;
    }

    /**
     * Get image width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get image height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Render album art at position with size using DrawContext
     */
    public void render(GuiGraphics context, int x, int y, int size) {
        if (!hasAlbumArt()) {
            return;
        }

        // Calculate render size maintaining aspect ratio
        int renderWidth = size;
        int renderHeight = size;

        if (width > height) {
            renderHeight = (size * height) / width;
        } else if (height > width) {
            renderWidth = (size * width) / height;
        }

        // Center in the box
        int renderX = x + (size - renderWidth) / 2;
        int renderY = y + (size - renderHeight) / 2;

        // Render using DrawContext (1.21 API)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            currentTexture,
            renderX, renderY,        // Screen position
            0.0f, 0.0f,             // UV start
            renderWidth, renderHeight, // Render size
            width, height,           // Region size
            width, height            // Total texture size
        );
    }

    /**
     * Convert a BufferedImage to NativeImage (for JPEG support)
     */
    private NativeImage convertBufferedImageToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        // Create a new NativeImage with RGBA format
        NativeImage nativeImage = new NativeImage(width, height, false);

        // Copy pixels from BufferedImage to NativeImage
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = bufferedImage.getRGB(x, y);

                // Extract ARGB components
                int alpha = (argb >> 24) & 0xFF;
                int red = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int blue = argb & 0xFF;

                // NativeImage.setColor uses ABGR format
                // Convert ARGB to ABGR
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;

                nativeImage.setPixelABGR(x, y, abgr);
            }
        }

        return nativeImage;
    }

    /**
     * Cleanup on shutdown
     */
    public void shutdown() {
        clearAlbumArt();
    }

    private boolean shouldApplyLoad(long loadId, String path) {
        synchronized (loadLock) {
            return isLoadIdCurrent(loadId, path);
        }
    }

    private void markLoadCompleteIfCurrent(long loadId, String path, long modifiedTime) {
        synchronized (loadLock) {
            if (!isLoadIdCurrent(loadId, path)) {
                return;
            }
            currentPath = path;
            lastModifiedTime = modifiedTime;
            loadingPath = null;
        }
    }

    private void clearLoadingIfCurrent(long loadId, String path) {
        synchronized (loadLock) {
            if (!isLoadIdCurrent(loadId, path)) {
                return;
            }
            loadingPath = null;
        }
    }

    private boolean isLoadIdCurrent(long loadId, String path) {
        return activeLoadId == loadId && path != null && path.equals(loadingPath);
    }
}
