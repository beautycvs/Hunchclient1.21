package dev.hunchclient.util;

import dev.hunchclient.HunchClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL32;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * FFmpeg Manager for Replay Buffer functionality
 *
 * Uses native OpenGL frame capture and streams directly to FFmpeg.
 * FFmpeg handles the buffering via segment muxer - NO Java heap storage.
 * This captures ONLY Minecraft's rendering, works in any fullscreen mode.
 */
public class FFmpegManager {

    private static FFmpegManager INSTANCE;

    // FFmpeg process for continuous recording
    private Process ffmpegProcess;
    private OutputStream ffmpegStdin;
    private volatile boolean isRecording = false;
    private volatile boolean isProcessing = false;

    // Configuration
    private String ffmpegPath = "ffmpeg";
    private String outputDirectory;
    private String tempDirectory;
    private int bufferLengthSeconds = 30;
    private int segmentDuration = 5; // seconds per segment
    private int fps = 60; // Default 60 FPS for smooth replays
    private int videoBitrate = 8000; // kbps
    private String videoCodec = "libx264";
    private String preset = "ultrafast";
    private float resolutionScale = 0.5f; // Capture at 50% resolution by default for performance

    // Target file size feature (like 8mb.video)
    private int targetFileSizeMB = 0; // 0 = disabled, target output size in MB
    private boolean useTargetFileSize = false;

    // Cursor capture feature
    private boolean captureCursor = false;

    // Frame capture state
    private int currentWidth = 0;
    private int currentHeight = 0;
    private long lastFrameTime = 0;
    private long frameInterval; // nanoseconds between frames

    // Async frame writing
    private ExecutorService frameWriter;
    private final BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>(3);

    // PBO for async GPU readback (TRIPLE buffered with fence sync)
    private static final int PBO_COUNT = 3;
    private int[] pboIds = new int[PBO_COUNT];
    private long[] fenceIds = new long[PBO_COUNT]; // GL sync objects
    private int pboWriteIndex = 0; // Where we write new frames
    private int pboReadIndex = 0;  // Where we read completed frames
    private boolean pbosInitialized = false;
    private int pboWidth = 0;
    private int pboHeight = 0;
    private int captureWidth = 0;  // Actual capture resolution (scaled)
    private int captureHeight = 0;
    private int frameCount = 0; // Track frames for PBO warmup

    // Reusable byte array pool to avoid allocations
    private final BlockingQueue<byte[]> byteArrayPool = new LinkedBlockingQueue<>(6);

    // Performance tracking for dynamic frame skipping
    private long lastCaptureTime = 0;
    private int consecutiveSlowFrames = 0;
    private static final int MAX_SLOW_FRAMES = 3; // Skip after 3 slow frames

    // Callbacks
    private Runnable onSaveComplete;
    private java.util.function.Consumer<String> onError;

    private FFmpegManager() {
        // Set default directories - use config/hunchclient for easier access
        String gameDir = System.getProperty("user.dir"); // Minecraft game directory
        outputDirectory = gameDir + File.separator + "config" + File.separator + "hunchclient" + File.separator + "clips";
        tempDirectory = gameDir + File.separator + "config" + File.separator + "hunchclient" + File.separator + "replay_temp";

        // Create directories
        try {
            Files.createDirectories(Paths.get(outputDirectory));
            Files.createDirectories(Paths.get(tempDirectory));
        } catch (IOException e) {
            HunchClient.LOGGER.error("Failed to create replay directories: " + e.getMessage());
        }

        updateFrameInterval();
    }

    public static FFmpegManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FFmpegManager();
        }
        return INSTANCE;
    }

    private void updateFrameInterval() {
        frameInterval = 1_000_000_000L / fps;
    }

    /**
     * Check if FFmpeg is available
     */
    public boolean isFFmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get FFmpeg version string
     */
    public String getFFmpegVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.startsWith("ffmpeg version")) {
                    return line;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }
        return "Not found";
    }

    /**
     * Start the replay buffer recording
     */
    public synchronized boolean startRecording() {
        if (isRecording) {
            HunchClient.LOGGER.warn("Replay buffer is already recording");
            return false;
        }

        if (!isFFmpegAvailable()) {
            String error = "FFmpeg not found! Please install FFmpeg.";
            HunchClient.LOGGER.error(error);
            if (onError != null) onError.accept(error);
            return false;
        }

        // Clean temp directory
        cleanTempDirectory();

        // We'll start FFmpeg when we get the first frame (need dimensions)
        updateFrameInterval();
        lastFrameTime = 0;
        currentWidth = 0;
        currentHeight = 0;

        isRecording = true;
        HunchClient.LOGGER.info("Replay buffer started (native capture, {} FPS, {} seconds buffer)", fps, bufferLengthSeconds);
        return true;
    }

    /**
     * Start FFmpeg process with given dimensions.
     * Captures at full resolution, FFmpeg scales down for encoding performance.
     */
    private synchronized boolean startFFmpegProcess(int width, int height) {
        if (ffmpegProcess != null) {
            return true; // Already running
        }

        // Capture at FULL resolution - glReadPixels must read the whole framebuffer
        captureWidth = width;
        captureHeight = height;

        // Calculate output dimensions (scaled down for encoding performance)
        int outputWidth = Math.max(320, (int)(width * resolutionScale));
        int outputHeight = Math.max(240, (int)(height * resolutionScale));
        // Ensure dimensions are even (required for most codecs)
        outputWidth = outputWidth & ~1;
        outputHeight = outputHeight & ~1;

        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-y");

            // Input: raw video from pipe at FULL resolution (BGRA is faster on most GPUs)
            command.add("-f");
            command.add("rawvideo");
            command.add("-pix_fmt");
            command.add("bgra");
            command.add("-s");
            command.add(width + "x" + height); // Full resolution input
            command.add("-r");
            command.add(String.valueOf(fps));
            command.add("-i");
            command.add("pipe:0");

            // Video codec
            command.add("-c:v");
            command.add(videoCodec);

            if (videoCodec.equals("libx264")) {
                command.add("-preset");
                command.add(preset);
                command.add("-tune");
                command.add("zerolatency");
                command.add("-threads");
                command.add("0"); // Auto-detect threads
            } else if (videoCodec.equals("h264_nvenc")) {
                // NVENC parameters - compatible with all NVENC versions
                command.add("-preset");
                command.add("p2"); // p1-p7, p2=fast
                command.add("-tune");
                command.add("ll"); // Low latency
                command.add("-rc");
                command.add("vbr"); // Variable bitrate works better with segment muxer
                command.add("-rc-lookahead");
                command.add("0"); // No lookahead for low latency
                command.add("-b_ref_mode");
                command.add("0"); // No B-frame references
            } else if (videoCodec.equals("h264_amf")) {
                command.add("-quality");
                command.add("speed");
                command.add("-rc");
                command.add("cbr");
            } else if (videoCodec.equals("h264_qsv")) {
                command.add("-preset");
                command.add("veryfast");
                command.add("-look_ahead");
                command.add("0");
            }

            command.add("-pix_fmt");
            command.add("yuv420p");
            command.add("-b:v");
            command.add(videoBitrate + "k");
            command.add("-maxrate");
            command.add((videoBitrate * 2) + "k");
            command.add("-bufsize");
            command.add((videoBitrate * 2) + "k");
            command.add("-g");
            command.add(String.valueOf(fps * 2)); // Keyframe every 2 seconds

            // Video filters: flip vertically and scale down for encoding performance
            StringBuilder vfBuilder = new StringBuilder();
            vfBuilder.append("vflip");
            if (resolutionScale < 1.0f) {
                // Scale DOWN for faster encoding, then scale back up on playback
                // Output is at reduced resolution but captures full screen
                vfBuilder.append(",scale=").append(outputWidth).append(":").append(outputHeight);
                vfBuilder.append(":flags=fast_bilinear"); // Fastest scaling algorithm
            }
            command.add("-vf");
            command.add(vfBuilder.toString());

            // Force keyframes at segment boundaries for clean cuts
            command.add("-force_key_frames");
            command.add("expr:gte(t,n_forced*" + segmentDuration + ")");

            // MP4 flags for segmentation (CRITICAL for hardware encoders!)
            command.add("-movflags");
            command.add("frag_keyframe+empty_moov+default_base_moof");

            // Segment muxer for rolling buffer
            command.add("-f");
            command.add("segment");
            command.add("-segment_time");
            command.add(String.valueOf(segmentDuration));
            command.add("-segment_format");
            command.add("mp4");
            command.add("-segment_wrap");
            command.add(String.valueOf((bufferLengthSeconds / segmentDuration) + 2));
            command.add("-reset_timestamps");
            command.add("1");
            command.add("-strftime");
            command.add("0");

            // Output pattern
            command.add(tempDirectory + File.separator + "segment_%03d.mp4");

            HunchClient.LOGGER.info("Starting FFmpeg: {}", String.join(" ", command));
            HunchClient.LOGGER.info("Capture: {}x{} -> Encode: {}x{} ({}% scale)",
                width, height, outputWidth, outputHeight, (int)(resolutionScale * 100));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(new File(tempDirectory));

            ffmpegProcess = pb.start();
            ffmpegStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 2 * 1024 * 1024);

            // Read FFmpeg output in background
            CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    boolean hasError = false;
                    StringBuilder errorLog = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        // Log errors and important info
                        if (line.contains("Error") || line.contains("error")) {
                            HunchClient.LOGGER.error("[FFmpeg] {}", line);
                            hasError = true;
                            errorLog.append(line).append("\n");
                        } else if (line.contains("Warning") || line.contains("warning")) {
                            HunchClient.LOGGER.warn("[FFmpeg] {}", line);
                        } else if (line.contains("Opening") || line.contains("Output") || line.contains("Stream")) {
                            HunchClient.LOGGER.info("[FFmpeg] {}", line);
                        }
                    }

                    // If there were errors, notify user
                    if (hasError && onError != null) {
                        String errorMsg = "FFmpeg encoding error - check logs for details";
                        if (errorLog.toString().contains("not found") || errorLog.toString().contains("Unknown encoder")) {
                            errorMsg = "Selected codec not available - try a different codec";
                        }
                        onError.accept(errorMsg);
                    }
                } catch (IOException e) {
                    // Process ended
                }
            });

            // Start frame writer thread
            frameWriter = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ReplayBuffer-FrameWriter");
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY); // High priority for smooth recording
                return t;
            });

            frameWriter.submit(() -> {
                while (isRecording && !Thread.currentThread().isInterrupted()) {
                    try {
                        byte[] frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (frame != null && ffmpegStdin != null) {
                            ffmpegStdin.write(frame);
                            // Return to pool after writing
                            byteArrayPool.offer(frame);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        HunchClient.LOGGER.error("Error writing frame to FFmpeg: {}", e.getMessage());
                        break;
                    }
                }
            });

            currentWidth = width;
            currentHeight = height;

            HunchClient.LOGGER.info("FFmpeg process started - full screen capture at {}x{}", width, height);
            return true;

        } catch (Exception e) {
            HunchClient.LOGGER.error("Failed to start FFmpeg: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initialize PBOs for async GPU readback (triple-buffered)
     */
    private void initializePBOs(int width, int height) {
        if (pbosInitialized && pboWidth == width && pboHeight == height) {
            return;
        }

        // Cleanup old PBOs
        cleanupPBOs();

        int bufferSize = width * height * 4; // BGRA (4 bytes per pixel)

        for (int i = 0; i < PBO_COUNT; i++) {
            pboIds[i] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[i]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, bufferSize, GL15.GL_STREAM_READ);
            fenceIds[i] = 0; // No fence initially
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

        pboWidth = width;
        pboHeight = height;
        pboWriteIndex = 0;
        pboReadIndex = 0;
        pbosInitialized = true;
        frameCount = 0;
        consecutiveSlowFrames = 0;

        HunchClient.LOGGER.info("Initialized {} PBOs for {}x{} BGRA capture", PBO_COUNT, width, height);
    }

    /**
     * Cleanup PBOs and fence objects
     */
    private void cleanupPBOs() {
        if (pbosInitialized) {
            try {
                // Delete fence objects
                for (int i = 0; i < PBO_COUNT; i++) {
                    if (fenceIds[i] != 0) {
                        GL32.glDeleteSync(fenceIds[i]);
                        fenceIds[i] = 0;
                    }
                }
                // Delete PBOs
                GL15.glDeleteBuffers(pboIds);
                for (int i = 0; i < PBO_COUNT; i++) {
                    pboIds[i] = 0;
                }
            } catch (Exception e) {
                // Ignore - context might be gone
            }
            pbosInitialized = false;
        }
    }

    /**
     * Capture a frame from OpenGL framebuffer using async PBO readback with fence sync.
     * Uses triple-buffering and non-blocking fence checks for maximum performance.
     * Called from GameRendererMixin after each frame render.
     */
    public void captureFrame(int width, int height) {
        if (!isRecording) return;

        // Frame rate limiting
        long currentTime = System.nanoTime();
        if (lastFrameTime != 0 && (currentTime - lastFrameTime) < frameInterval) {
            return;
        }

        // Dynamic frame skipping - if we're falling behind, skip frames
        if (consecutiveSlowFrames >= MAX_SLOW_FRAMES) {
            consecutiveSlowFrames = 0; // Reset and skip this frame
            return;
        }

        lastFrameTime = currentTime;

        // Clear any previous OpenGL errors to avoid false positives
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // Drain error queue
        }

        // Start FFmpeg if not started yet or resolution changed
        if (ffmpegProcess == null || width != currentWidth || height != currentHeight) {
            stopFFmpegProcess();
            cleanupPBOs();
            if (!startFFmpegProcess(width, height)) {
                return;
            }
        }

        // Use FULL resolution for capture (FFmpeg scales down for encoding)
        int capWidth = width;
        int capHeight = height;

        // Initialize PBOs if needed (at full resolution)
        if (!pbosInitialized || pboWidth != capWidth || pboHeight != capHeight) {
            initializePBOs(capWidth, capHeight);
        }

        int bufferSize = capWidth * capHeight * 4; // BGRA

        // === STEP 1: Try to read from the oldest PBO (if fence is signaled) ===
        if (frameCount >= PBO_COUNT) {
            int readIdx = pboReadIndex;
            long fence = fenceIds[readIdx];

            if (fence != 0) {
                // Check fence with small timeout to avoid race conditions
                // 1ms is enough to avoid busy-waiting but prevents premature access
                int syncStatus = GL32.glClientWaitSync(fence, 0, 1_000_000); // 1ms in nanoseconds

                if (syncStatus == GL32.GL_ALREADY_SIGNALED || syncStatus == GL32.GL_CONDITION_SATISFIED) {
                    // PBO is ready - read it without blocking!
                    GL32.glDeleteSync(fence);
                    fenceIds[readIdx] = 0;

                    try {
                        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[readIdx]);

                        // Check for errors before mapping
                        int error = GL11.glGetError();
                        if (error != GL11.GL_NO_ERROR) {
                            HunchClient.LOGGER.warn("OpenGL error before mapping PBO: 0x{}", Integer.toHexString(error));
                            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                            pboReadIndex = (pboReadIndex + 1) % PBO_COUNT;
                            return;
                        }

                        ByteBuffer mappedBuffer = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);

                        if (mappedBuffer != null) {
                            // Get from pool or create new array
                            byte[] frameData = byteArrayPool.poll();
                            if (frameData == null || frameData.length != bufferSize) {
                                frameData = new byte[bufferSize];
                            }

                            // Direct bulk copy
                            mappedBuffer.rewind();
                            mappedBuffer.get(frameData);

                            // Draw cursor overlay if enabled
                            if (captureCursor) {
                                drawCursorOnFrame(frameData, capWidth, capHeight);
                            }

                            boolean unmapSuccess = GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                            if (!unmapSuccess) {
                                HunchClient.LOGGER.warn("Failed to unmap PBO buffer");
                            }

                            // Queue frame for FFmpeg (drop if full - better than blocking)
                            if (!frameQueue.offer(frameData)) {
                                byteArrayPool.offer(frameData); // Return to pool
                            }
                        } else {
                            HunchClient.LOGGER.warn("Failed to map PBO buffer");
                        }
                    } catch (Exception e) {
                        HunchClient.LOGGER.error("Error reading from PBO: {}", e.getMessage());
                    } finally {
                        // Always unbind
                        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                    }

                    pboReadIndex = (pboReadIndex + 1) % PBO_COUNT;
                    consecutiveSlowFrames = 0;
                } else if (syncStatus == GL32.GL_TIMEOUT_EXPIRED || syncStatus == GL32.GL_WAIT_FAILED) {
                    // Not ready yet - skip reading this frame (don't block!)
                    consecutiveSlowFrames++;
                }
            }
        }

        // === STEP 2: Start async read into the next PBO ===
        int writeIdx = pboWriteIndex;

        try {
            // Clean up old fence if exists
            if (fenceIds[writeIdx] != 0) {
                GL32.glDeleteSync(fenceIds[writeIdx]);
                fenceIds[writeIdx] = 0;
            }

            // Initiate async GPU read (non-blocking with PBO)
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[writeIdx]);

            // Check for errors after bind
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                HunchClient.LOGGER.warn("OpenGL error after binding PBO for write: 0x{}", Integer.toHexString(error));
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                return;
            }

            // Start async read from framebuffer into PBO
            GL11.glReadPixels(0, 0, capWidth, capHeight, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0);

            // Check for errors after readPixels
            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                HunchClient.LOGGER.warn("OpenGL error after glReadPixels: 0x{}", Integer.toHexString(error));
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                return;
            }

            // Create fence to check when this read is complete
            fenceIds[writeIdx] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

            // Advance write index
            pboWriteIndex = (pboWriteIndex + 1) % PBO_COUNT;
            frameCount++;

        } catch (Exception e) {
            HunchClient.LOGGER.error("Error initiating PBO read: {}", e.getMessage());
        } finally {
            // Always unbind
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        }

        // Track capture time for performance monitoring
        lastCaptureTime = System.nanoTime() - currentTime;
    }

    /**
     * Draw a cursor overlay on the frame data (BGRA format).
     * Draws a simple triangular cursor pointer at the current mouse position.
     * Note: OpenGL captures upside-down, FFmpeg flips with vflip, so we invert Y here.
     */
    private void drawCursorOnFrame(byte[] frameData, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null) return;

        // Get mouse position (screen coordinates)
        int mouseX = (int) mc.mouseHandler.xpos();
        int mouseY = (int) mc.mouseHandler.ypos();

        // Invert Y because OpenGL captures upside-down (FFmpeg will flip it back)
        mouseY = height - mouseY;

        // Cursor size (scales with resolution)
        int cursorSize = Math.max(12, Math.min(24, width / 80));

        // Draw cursor shape: triangular pointer with black outline
        // Cursor points: tip at (0,0), then (0, size), then (size*0.7, size*0.7)
        drawCursorTriangle(frameData, width, height, mouseX, mouseY, cursorSize);
    }

    /**
     * Draw a triangular cursor with outline at the specified position.
     * Uses BGRA format (Blue, Green, Red, Alpha).
     */
    private void drawCursorTriangle(byte[] frameData, int width, int height, int tipX, int tipY, int size) {
        // Cursor colors (BGRA)
        byte[] white = {(byte) 255, (byte) 255, (byte) 255, (byte) 255}; // White fill
        byte[] black = {0, 0, 0, (byte) 255}; // Black outline

        // Draw filled triangle (white)
        for (int y = 0; y < size; y++) {
            // Triangle width at this row (narrows towards tip)
            int rowWidth = (int) ((size - y) * 0.6);

            for (int x = 0; x <= rowWidth; x++) {
                int px = tipX + x;
                int py = tipY + y;

                // Draw outline (black border)
                boolean isOutline = (y == 0) || (x == 0) || (y == size - 1) ||
                                   (x >= rowWidth - 1) || (y >= size - 2 && x >= rowWidth - 2);

                if (isOutline) {
                    setPixel(frameData, width, height, px, py, black);
                } else {
                    setPixel(frameData, width, height, px, py, white);
                }
            }
        }

        // Draw diagonal edge with outline
        for (int i = 0; i < size; i++) {
            int px = tipX + (int)(i * 0.6);
            int py = tipY + i;
            setPixel(frameData, width, height, px, py, black);
            setPixel(frameData, width, height, px + 1, py, black);
        }
    }

    /**
     * Set a pixel in BGRA frame data with bounds checking.
     */
    private void setPixel(byte[] frameData, int width, int height, int x, int y, byte[] bgra) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;

        int index = (y * width + x) * 4;
        if (index < 0 || index + 3 >= frameData.length) return;

        frameData[index] = bgra[0];     // Blue
        frameData[index + 1] = bgra[1]; // Green
        frameData[index + 2] = bgra[2]; // Red
        frameData[index + 3] = bgra[3]; // Alpha
    }

    /**
     * Stop the FFmpeg process
     */
    private synchronized void stopFFmpegProcess() {
        if (frameWriter != null) {
            frameWriter.shutdownNow();
            frameWriter = null;
        }

        frameQueue.clear();

        if (ffmpegStdin != null) {
            try {
                ffmpegStdin.flush();
                ffmpegStdin.close();
            } catch (IOException e) {
                // Ignore
            }
            ffmpegStdin = null;
        }

        if (ffmpegProcess != null) {
            try {
                ffmpegProcess.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
            if (ffmpegProcess.isAlive()) {
                ffmpegProcess.destroyForcibly();
            }
            ffmpegProcess = null;
        }
    }

    /**
     * Stop the replay buffer recording
     */
    public synchronized void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;
        stopFFmpegProcess();
        cleanupPBOs();
        cleanTempDirectory();

        HunchClient.LOGGER.info("Replay buffer stopped");
    }

    /**
     * Save the current replay buffer to a video file
     */
    public synchronized void saveReplay() {
        saveReplay(null);
    }

    /**
     * Save the current replay buffer with custom filename suffix
     */
    public synchronized void saveReplay(String suffix) {
        if (isProcessing) {
            HunchClient.LOGGER.warn("Already processing a save request");
            return;
        }

        isProcessing = true;

        CompletableFuture.runAsync(() -> {
            try {
                // Flush current FFmpeg output
                if (ffmpegStdin != null) {
                    try {
                        ffmpegStdin.flush();
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // Wait a bit for segments to finalize
                Thread.sleep(500);

                // Generate output filename
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                String timestamp = sdf.format(new Date());
                String baseName = "Replay_" + timestamp;
                if (suffix != null && !suffix.isEmpty()) {
                    baseName = baseName + "_" + suffix;
                }
                final String filename = baseName + ".mp4";
                String outputPath = outputDirectory + File.separator + filename;

                // Get list of segment files
                List<Path> segments = getSegmentFiles();
                if (segments.isEmpty()) {
                    HunchClient.LOGGER.warn("No segments found in temp directory: {}", tempDirectory);

                    // Check if any files exist at all
                    try {
                        long fileCount = Files.list(Paths.get(tempDirectory)).count();
                        HunchClient.LOGGER.warn("Total files in temp directory: {}", fileCount);
                    } catch (Exception e) {
                        HunchClient.LOGGER.error("Could not list temp directory: {}", e.getMessage());
                    }

                    if (onError != null) onError.accept("No replay data available - check if recording is working");
                    return;
                }

                HunchClient.LOGGER.info("Saving {} segments to: {}", segments.size(), outputPath);

                // Create concat file
                Path concatFile = Paths.get(tempDirectory, "concat.txt");
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(concatFile))) {
                    for (Path segment : segments) {
                        writer.println("file '" + segment.toAbsolutePath().toString().replace("\\", "/") + "'");
                    }
                }

                int exitCode;

                if (useTargetFileSize && targetFileSizeMB > 0) {
                    // Target file size mode: Concat to temp, then re-encode with calculated bitrate
                    exitCode = saveWithTargetFileSize(concatFile, outputPath);
                } else {
                    // Standard mode: Just concat segments (fast, no re-encoding)
                    List<String> concatCommand = new ArrayList<>();
                    concatCommand.add(ffmpegPath);
                    concatCommand.add("-y");
                    concatCommand.add("-f");
                    concatCommand.add("concat");
                    concatCommand.add("-safe");
                    concatCommand.add("0");
                    concatCommand.add("-i");
                    concatCommand.add(concatFile.toString());
                    concatCommand.add("-c");
                    concatCommand.add("copy");
                    concatCommand.add(outputPath);

                    ProcessBuilder pb = new ProcessBuilder(concatCommand);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    // Read output
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("Error") || line.contains("error")) {
                                HunchClient.LOGGER.error("[FFmpeg Concat] {}", line);
                            }
                        }
                    }

                    exitCode = process.waitFor();
                }

                if (exitCode == 0) {
                    HunchClient.LOGGER.info("Replay saved successfully: {}", outputPath);

                    // Notify in-game with clickable message
                    final File outputFile = new File(outputPath);
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            MutableComponent message = Component.literal("§a§l[Replay] §r§aSaved! ")
                                .append(Component.literal("§b§n" + filename)
                                    .withStyle(style -> style
                                        .withClickEvent(new ClickEvent.OpenFile(outputFile))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to open file")))));

                            Minecraft.getInstance().player.displayClientMessage(message, false);
                        }
                    });

                    if (onSaveComplete != null) onSaveComplete.run();
                } else {
                    String error = "FFmpeg concat failed with exit code: " + exitCode;
                    HunchClient.LOGGER.error(error);
                    if (onError != null) onError.accept(error);
                }

                // Cleanup concat file
                Files.deleteIfExists(concatFile);

            } catch (Exception e) {
                String error = "Failed to save replay: " + e.getMessage();
                HunchClient.LOGGER.error(error, e);
                if (onError != null) onError.accept(error);
            } finally {
                isProcessing = false;
            }
        });
    }

    /**
     * Save with target file size - re-encodes to fit within specified MB limit
     * Like 8mb.video - calculates optimal bitrate based on video duration
     */
    private int saveWithTargetFileSize(Path concatFile, String outputPath) throws Exception {
        Path tempConcatOutput = Paths.get(tempDirectory, "temp_concat.mp4");

        // Step 1: Concat segments to temp file (fast, no re-encoding)
        HunchClient.LOGGER.info("[Replay] Step 1/3: Concatenating segments...");
        List<String> concatCommand = new ArrayList<>();
        concatCommand.add(ffmpegPath);
        concatCommand.add("-y");
        concatCommand.add("-f");
        concatCommand.add("concat");
        concatCommand.add("-safe");
        concatCommand.add("0");
        concatCommand.add("-i");
        concatCommand.add(concatFile.toString());
        concatCommand.add("-c");
        concatCommand.add("copy");
        concatCommand.add(tempConcatOutput.toString());

        ProcessBuilder concatPb = new ProcessBuilder(concatCommand);
        concatPb.redirectErrorStream(true);
        Process concatProcess = concatPb.start();
        drainProcessOutput(concatProcess);
        int concatExit = concatProcess.waitFor();

        if (concatExit != 0) {
            HunchClient.LOGGER.error("[Replay] Concat failed with exit code: {}", concatExit);
            return concatExit;
        }

        // Step 2: Get video duration using ffprobe
        HunchClient.LOGGER.info("[Replay] Step 2/3: Analyzing video duration...");
        double duration = getVideoDuration(tempConcatOutput);
        if (duration <= 0) {
            HunchClient.LOGGER.error("[Replay] Could not determine video duration");
            Files.deleteIfExists(tempConcatOutput);
            return -1;
        }

        // Calculate optimal bitrate: (targetSizeKB * 8) / durationSeconds
        // Use 95% of target to leave room for container overhead
        int targetSizeKB = (int) (targetFileSizeMB * 1024 * 0.95);
        int calculatedBitrate = (int) ((targetSizeKB * 8.0) / duration);

        // Clamp bitrate to reasonable range
        calculatedBitrate = Math.max(500, Math.min(50000, calculatedBitrate));

        HunchClient.LOGGER.info("[Replay] Target: {}MB, Duration: {:.1f}s, Calculated bitrate: {}kbps",
            targetFileSizeMB, duration, calculatedBitrate);

        // Step 3: Re-encode with calculated bitrate
        HunchClient.LOGGER.info("[Replay] Step 3/3: Re-encoding to target size...");
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(ffmpegPath);
        encodeCommand.add("-y");
        encodeCommand.add("-i");
        encodeCommand.add(tempConcatOutput.toString());
        encodeCommand.add("-c:v");
        encodeCommand.add("libx264"); // Use CPU encoder for consistent quality
        encodeCommand.add("-preset");
        encodeCommand.add("medium"); // Better quality than ultrafast for final output
        encodeCommand.add("-b:v");
        encodeCommand.add(calculatedBitrate + "k");
        encodeCommand.add("-maxrate");
        encodeCommand.add((int)(calculatedBitrate * 1.5) + "k");
        encodeCommand.add("-bufsize");
        encodeCommand.add((calculatedBitrate * 2) + "k");
        encodeCommand.add("-pix_fmt");
        encodeCommand.add("yuv420p");
        encodeCommand.add("-movflags");
        encodeCommand.add("+faststart"); // Web-friendly: metadata at start
        encodeCommand.add(outputPath);

        ProcessBuilder encodePb = new ProcessBuilder(encodeCommand);
        encodePb.redirectErrorStream(true);
        Process encodeProcess = encodePb.start();
        drainProcessOutput(encodeProcess);
        int encodeExit = encodeProcess.waitFor();

        // Cleanup temp file
        Files.deleteIfExists(tempConcatOutput);

        if (encodeExit == 0) {
            // Log actual file size
            File outputFile = new File(outputPath);
            if (outputFile.exists()) {
                long actualSizeKB = outputFile.length() / 1024;
                long actualSizeMB = actualSizeKB / 1024;
                HunchClient.LOGGER.info("[Replay] Final size: {}MB (target: {}MB)",
                    actualSizeMB, targetFileSizeMB);
            }
        }

        return encodeExit;
    }

    /**
     * Get video duration in seconds using ffprobe
     */
    private double getVideoDuration(Path videoFile) {
        try {
            // Try ffprobe first
            String ffprobePath = ffmpegPath.replace("ffmpeg", "ffprobe");
            List<String> command = new ArrayList<>();
            command.add(ffprobePath);
            command.add("-v");
            command.add("error");
            command.add("-show_entries");
            command.add("format=duration");
            command.add("-of");
            command.add("csv=p=0");
            command.add(videoFile.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }
            process.waitFor();

            if (output != null && !output.isEmpty()) {
                return Double.parseDouble(output.trim());
            }
        } catch (Exception e) {
            HunchClient.LOGGER.warn("[Replay] ffprobe failed, using segment count estimate: {}", e.getMessage());
        }

        // Fallback: estimate from buffer length setting
        return bufferLengthSeconds;
    }

    /**
     * Drain process output to prevent blocking
     */
    private void drainProcessOutput(Process process) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Error") || line.contains("error")) {
                        HunchClient.LOGGER.error("[FFmpeg] {}", line);
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        });
    }

    /**
     * Get sorted list of segment files (oldest first)
     */
    private List<Path> getSegmentFiles() {
        List<Path> segments = new ArrayList<>();

        try {
            Files.list(Paths.get(tempDirectory))
                .filter(p -> p.getFileName().toString().startsWith("segment_") && p.getFileName().toString().endsWith(".mp4"))
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .forEach(segments::add);
        } catch (IOException e) {
            HunchClient.LOGGER.error("Error listing segment files: {}", e.getMessage());
        }

        // Only keep segments within buffer length
        int maxSegments = bufferLengthSeconds / segmentDuration;
        if (segments.size() > maxSegments) {
            segments = segments.subList(segments.size() - maxSegments, segments.size());
        }

        return segments;
    }

    /**
     * Clean the temp directory
     */
    private void cleanTempDirectory() {
        try {
            Files.list(Paths.get(tempDirectory))
                .filter(p -> p.getFileName().toString().endsWith(".mp4") || p.getFileName().toString().equals("concat.txt"))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Shutdown and cleanup all resources
     */
    public void shutdown() {
        stopRecording();
        cleanupPBOs();
    }

    // Getters and Setters

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public String getFFmpegPath() {
        return ffmpegPath;
    }

    public void setFFmpegPath(String path) {
        this.ffmpegPath = path;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String dir) {
        this.outputDirectory = dir;
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            HunchClient.LOGGER.error("Failed to create output directory: " + e.getMessage());
        }
    }

    public int getBufferLengthSeconds() {
        return bufferLengthSeconds;
    }

    public void setBufferLengthSeconds(int seconds) {
        this.bufferLengthSeconds = Math.max(10, Math.min(300, seconds));
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = Math.max(15, Math.min(60, fps));
        updateFrameInterval();
    }

    public int getVideoBitrate() {
        return videoBitrate;
    }

    public void setVideoBitrate(int kbps) {
        this.videoBitrate = Math.max(1000, Math.min(50000, kbps));
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(String codec) {
        this.videoCodec = codec;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public float getResolutionScale() {
        return resolutionScale;
    }

    public void setResolutionScale(float scale) {
        this.resolutionScale = Math.max(0.25f, Math.min(1.0f, scale));
    }

    public int getTargetFileSizeMB() {
        return targetFileSizeMB;
    }

    public void setTargetFileSizeMB(int sizeMB) {
        this.targetFileSizeMB = Math.max(0, Math.min(500, sizeMB));
        this.useTargetFileSize = this.targetFileSizeMB > 0;
    }

    public boolean isUseTargetFileSize() {
        return useTargetFileSize;
    }

    public void setUseTargetFileSize(boolean use) {
        this.useTargetFileSize = use;
    }

    public boolean isCaptureCursor() {
        return captureCursor;
    }

    public void setCaptureCursor(boolean capture) {
        this.captureCursor = capture;
    }

    // Legacy compatibility methods
    public int getAudioBitrate() { return 128; }
    public void setAudioBitrate(int kbps) { }
    public String getCaptureMethod() { return "native"; }
    public void setCaptureMethod(String method) { }
    public boolean isCaptureAudio() { return false; }
    public void setCaptureAudio(boolean capture) { }
    public String getAudioDevice() { return ""; }
    public void setAudioDevice(String device) { }

    public void setOnSaveComplete(Runnable callback) {
        this.onSaveComplete = callback;
    }

    public void setOnError(java.util.function.Consumer<String> callback) {
        this.onError = callback;
    }
}
