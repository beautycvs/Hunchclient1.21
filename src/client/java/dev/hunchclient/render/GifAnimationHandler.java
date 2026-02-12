package dev.hunchclient.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles GIF animation decoding and playback
 */
public class GifAnimationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-GIF");
    private static final int MAX_FRAMES = 300; // Limit to prevent memory issues with huge GIFs

    private final List<NativeImage> frames = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>(); // Frame delays in milliseconds
    private int currentFrame = 0;
    private long lastFrameTime = 0;
    private boolean isLoaded = false;

    // Store GIF dimensions (constant across all frames)
    private int width = 0;
    private int height = 0;

    // PERFORMANCE: Track if frame changed since last check to avoid unnecessary GPU uploads
    // CRITICAL FIX: Initialize to true so first frame is uploaded to GPU
    private boolean frameChanged = true;

    /**
     * Load GIF from file
     */
    public static GifAnimationHandler loadFromFile(File file) {
        GifAnimationHandler handler = new GifAnimationHandler();
        try {
            handler.decodeGif(ImageIO.createImageInputStream(file));
            handler.isLoaded = handler.frames.size() > 0;
            if (handler.isLoaded) {
                LOGGER.info("Successfully loaded GIF with {} frames: {}", handler.frames.size(), file.getName());
            } else {
                LOGGER.error("GIF has no frames: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GIF from file: {}", file.getAbsolutePath(), e);
            handler.close();
        }
        return handler;
    }

    /**
     * Load GIF from input stream
     */
    public static GifAnimationHandler loadFromStream(InputStream stream) {
        GifAnimationHandler handler = new GifAnimationHandler();
        try {
            handler.decodeGif(ImageIO.createImageInputStream(stream));
            handler.isLoaded = handler.frames.size() > 0;
            if (!handler.isLoaded) {
                LOGGER.error("GIF has no frames from stream");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GIF from stream", e);
            handler.close();
        }
        return handler;
    }

    /**
     * Decode GIF frames and delays
     */
    private void decodeGif(ImageInputStream stream) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF readers found");
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(stream);

            int numFrames = reader.getNumImages(true);

        // SMART FRAME SKIPPING: Instead of cutting off at MAX_FRAMES,
        // skip every N-th frame to keep the full animation but at lower FPS
        int skipRate = 1;
        if (numFrames > MAX_FRAMES) {
            skipRate = (int) Math.ceil((double) numFrames / MAX_FRAMES);
            LOGGER.info("GIF has {} frames, using skip rate {} to optimize (will load ~{} frames)",
                       numFrames, skipRate, numFrames / skipRate);
        } else {
            LOGGER.info("Loading GIF with {} frames (no optimization needed)", numFrames);
        }

        // Get GIF dimensions from reader metadata (more reliable than first frame)
        width = reader.getWidth(0);
        height = reader.getHeight(0);
        LOGGER.info("GIF dimensions: {}x{}", width, height);

        // Create persistent canvas for accumulating frames (handles disposal methods correctly)
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();

        int loadedFrames = 0;
        BufferedImage previousFrame = null;

        for (int i = 0; i < numFrames; i++) {
            try {
                // Get frame metadata
                IIOMetadata metadata = reader.getImageMetadata(i);

                // Get disposal method
                String disposalMethod = getDisposalMethod(metadata);

                // Get frame position (x, y offset)
                int[] framePosition = getFramePosition(metadata);
                int frameX = framePosition[0];
                int frameY = framePosition[1];

                // Read frame (may be partial/optimized)
                BufferedImage frame = reader.read(i);

                // Draw frame onto canvas at correct position (handles partial frames correctly)
                g.drawImage(frame, frameX, frameY, null);

                // CRITICAL FIX: Always process ALL frames to maintain correct canvas state,
                // but only SAVE frames we want to keep (first, last, and every skipRate-th frame)
                boolean shouldKeepFrame = (i == 0 || i == numFrames - 1 || (skipRate <= 1) || (i % skipRate == 0));

                if (shouldKeepFrame) {
                    // Create a copy of the current canvas state for this frame
                    BufferedImage frameCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D gCopy = frameCopy.createGraphics();
                    gCopy.drawImage(canvas, 0, 0, null);
                    gCopy.dispose();

                    // Convert to NativeImage
                    NativeImage nativeImage = convertToNativeImage(frameCopy);
                    frames.add(nativeImage);

                    // Extract frame delay
                    int delay = extractDelay(reader.getImageMetadata(i));

                    // IMPORTANT: Adjust delay for skipped frames
                    // If we skip every 2nd frame, double the delay to keep same playback speed
                    if (skipRate > 1) {
                        delay *= skipRate;
                    }

                    delays.add(delay);
                    loadedFrames++;
                }

                // Handle disposal method for NEXT frame (ALWAYS, even for skipped frames)
                if ("restoreToBackgroundColor".equals(disposalMethod)) {
                    // Clear canvas to transparent for next frame
                    g.setComposite(java.awt.AlphaComposite.Clear);
                    g.fillRect(0, 0, width, height);
                    g.setComposite(java.awt.AlphaComposite.SrcOver);
                } else if ("restoreToPrevious".equals(disposalMethod)) {
                    // Restore to previous frame state
                    if (previousFrame != null) {
                        g.drawImage(previousFrame, 0, 0, null);
                    }
                }

                // Save current canvas state for potential restore (ALWAYS, even for skipped frames)
                if (previousFrame == null) {
                    previousFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                }
                java.awt.Graphics2D gPrev = previousFrame.createGraphics();
                gPrev.drawImage(canvas, 0, 0, null);
                gPrev.dispose();

            } catch (Exception e) {
                LOGGER.warn("Failed to load frame {} of {}, skipping", i, numFrames);
            }
        }

        // Clean up graphics
        g.dispose();

        LOGGER.info("Successfully loaded {} frames (original: {} frames, skip rate: {})",
                   loadedFrames, numFrames, skipRate);

        } finally {
            try {
                reader.dispose();
            } catch (Exception ignored) {
            }
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Convert BufferedImage to NativeImage (OPTIMIZED with bulk operations)
     */
    private NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        NativeImage nativeImage = new NativeImage(width, height, true);

        // OPTIMIZATION: Use bulk pixel transfer instead of per-pixel loops
        // getRGB with array is ~10x faster than individual getRGB calls
        int[] pixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);

        // PERFORMANCE FIX: Process in larger chunks to improve cache locality
        // Unroll loop to process 4 pixels at once (reduces loop overhead by 75%)
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width - 3; x += 4) {
                // Process 4 pixels at once
                int argb0 = pixels[rowStart + x];
                int argb1 = pixels[rowStart + x + 1];
                int argb2 = pixels[rowStart + x + 2];
                int argb3 = pixels[rowStart + x + 3];

                // Convert ARGB to ABGR by swapping R and B channels
                nativeImage.setPixelABGR(x, y, (argb0 & 0xFF00FF00) | ((argb0 & 0xFF) << 16) | ((argb0 >> 16) & 0xFF));
                nativeImage.setPixelABGR(x + 1, y, (argb1 & 0xFF00FF00) | ((argb1 & 0xFF) << 16) | ((argb1 >> 16) & 0xFF));
                nativeImage.setPixelABGR(x + 2, y, (argb2 & 0xFF00FF00) | ((argb2 & 0xFF) << 16) | ((argb2 >> 16) & 0xFF));
                nativeImage.setPixelABGR(x + 3, y, (argb3 & 0xFF00FF00) | ((argb3 & 0xFF) << 16) | ((argb3 >> 16) & 0xFF));
            }
            // Handle remaining pixels
            for (int x = (width / 4) * 4; x < width; x++) {
                int argb = pixels[rowStart + x];
                nativeImage.setPixelABGR(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
            }
        }

        return nativeImage;
    }

    /**
     * Extract frame delay from GIF metadata (in milliseconds)
     */
    private int extractDelay(IIOMetadata metadata) {
        try {
            String metaFormatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

            IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
            if (graphicsControlExtensionNode != null) {
                String delayTimeStr = graphicsControlExtensionNode.getAttribute("delayTime");
                int delayTime = Integer.parseInt(delayTimeStr);
                // GIF delay is in centiseconds (1/100 second), convert to milliseconds
                return delayTime * 10;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract frame delay, using default 100ms", e);
        }
        return 100; // Default 100ms
    }

    /**
     * Extract disposal method from GIF metadata
     * Returns: "none", "doNotDispose", "restoreToBackgroundColor", "restoreToPrevious"
     */
    private String getDisposalMethod(IIOMetadata metadata) {
        try {
            String metaFormatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

            IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
            if (graphicsControlExtensionNode != null) {
                String disposalMethodStr = graphicsControlExtensionNode.getAttribute("disposalMethod");

                // Convert numeric disposal method to string
                switch (disposalMethodStr) {
                    case "none": return "none";
                    case "doNotDispose": return "doNotDispose";
                    case "restoreToBackgroundColor": return "restoreToBackgroundColor";
                    case "restoreToPrevious": return "restoreToPrevious";
                    default: return "none";
                }
            }
        } catch (Exception e) {
            // Disposal method not critical, just use default
        }
        return "none"; // Default: do nothing
    }

    /**
     * Extract frame position (x, y offset) from GIF metadata
     * Returns: [x, y]
     */
    private int[] getFramePosition(IIOMetadata metadata) {
        try {
            String metaFormatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

            IIOMetadataNode imageDescriptor = getNode(root, "ImageDescriptor");
            if (imageDescriptor != null) {
                String imageLeftStr = imageDescriptor.getAttribute("imageLeftPosition");
                String imageTopStr = imageDescriptor.getAttribute("imageTopPosition");

                int x = Integer.parseInt(imageLeftStr);
                int y = Integer.parseInt(imageTopStr);

                return new int[]{x, y};
            }
        } catch (Exception e) {
            // Position not critical, use 0,0
        }
        return new int[]{0, 0}; // Default: top-left corner
    }

    /**
     * Find child node by name
     */
    private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        int length = rootNode.getLength();
        for (int i = 0; i < length; i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        return null;
    }

    /**
     * Update animation frame based on time and speed multiplier
     */
    public void update(float speedMultiplier) {
        if (!isLoaded || frames.isEmpty()) {
            return;
        }

        // Handle single-frame GIFs (static images)
        if (frames.size() == 1) {
            currentFrame = 0;
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Initialize timer on first update
        if (lastFrameTime == 0) {
            lastFrameTime = currentTime;
            return;
        }

        // Get delay for current frame (with safety check)
        int frameIndex = Math.max(0, Math.min(currentFrame, delays.size() - 1));
        int currentDelay = delays.get(frameIndex);

        // Avoid division by zero and ensure minimum delay
        float adjustedDelay = Math.max(10.0f, currentDelay / Math.max(0.1f, speedMultiplier));

        // Check if enough time has passed to advance frame
        long elapsed = currentTime - lastFrameTime;
        if (elapsed >= adjustedDelay) {
            // Advance to next frame
            currentFrame = (currentFrame + 1) % frames.size();

            // PERFORMANCE: Mark that frame changed for GPU upload optimization
            frameChanged = true;

            // CRITICAL FIX: Update lastFrameTime properly to avoid accumulating lag
            // If we're way behind, skip frames instead of accumulating delay
            if (elapsed >= adjustedDelay * 2) {
                // We're falling behind - reset timer to current time
                lastFrameTime = currentTime;
            } else {
                // Normal case - add the delay to maintain smooth timing
                lastFrameTime += (long) adjustedDelay;
            }
        }
    }

    /**
     * Check if frame changed since last check (for GPU upload optimization).
     * @return true if frame changed
     */
    public boolean hasFrameChanged() {
        return frameChanged;
    }

    /**
     * Mark frame as updated (reset the changed flag).
     * Call this after uploading to GPU to avoid redundant uploads.
     */
    public void markFrameUpdated() {
        frameChanged = false;
    }

    /**
     * Get current frame image
     */
    public NativeImage getCurrentFrame() {
        if (!isLoaded || frames.isEmpty()) {
            return null;
        }
        return frames.get(currentFrame);
    }

    /**
     * Get frame count
     */
    public int getFrameCount() {
        return frames.size();
    }

    /**
     * Get GIF width (constant across all frames)
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get GIF height (constant across all frames)
     */
    public int getHeight() {
        return height;
    }

    /**
     * Check if GIF is loaded
     */
    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Clean up resources
     */
    public void close() {
        for (NativeImage image : frames) {
            image.close();
        }
        frames.clear();
        delays.clear();
        isLoaded = false;
    }
}
