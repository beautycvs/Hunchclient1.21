package dev.hunchclient.module.impl.misc;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.util.FFmpegManager;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Replay Buffer Module
 *
 * Uses native OpenGL frame capture to record Minecraft gameplay.
 * Frames are buffered in memory and encoded with FFmpeg on save.
 *
 * Features:
 * - Native capture: Records ONLY Minecraft (no desktop/other windows)
 * - Works in all fullscreen modes (windowed, borderless, exclusive)
 * - Configurable buffer length (10s - 5min)
 * - Quality settings (FPS, bitrate, codec)
 * - Hardware encoding support (NVENC, AMF, QSV)
 * - Auto-save on ban detection
 * - Manual save via F9 keybind
 *
 * Requirements:
 * - FFmpeg must be installed (can auto-download)
 */
public class ReplayBufferModule extends Module implements ConfigurableModule, SettingsProvider {

    private static ReplayBufferModule INSTANCE;

    // FFmpeg Manager
    private final FFmpegManager ffmpeg = FFmpegManager.getInstance();

    // Keybind for saving replay
    private KeyMapping saveReplayKey;
    private boolean keyRegistered = false;

    // Settings
    private String ffmpegPath = ""; // Custom FFmpeg path (empty = use PATH)
    private int bufferLength = 30; // seconds
    private int fps = 60; // Default 60 FPS for smooth replays
    private int videoBitrate = 8000; // kbps
    private int presetIndex = 0; // 0=ultrafast, 1=superfast, 2=veryfast, 3=faster, 4=fast, 5=medium
    private int codecIndex = 0; // 0=libx264, 1=h264_nvenc, 2=h264_amf, 3=h264_qsv
    private boolean autoSaveOnBan = true;
    private boolean showNotifications = true;
    private String outputPath = "";
    private int resolutionScalePercent = 50; // 50% resolution by default for performance
    private boolean useTargetFileSize = false; // Target file size mode (like 8mb.video)
    private int targetFileSizeMB = 8; // Default 8MB (Discord limit)
    private boolean captureCursor = false; // Draw software cursor on recorded frames

    // Preset and codec options
    private static final String[] PRESETS = {"ultrafast", "superfast", "veryfast", "faster", "fast", "medium"};
    private static final String[] CODECS = {"libx264 (CPU)", "h264_nvenc (NVIDIA)", "h264_amf (AMD)", "h264_qsv (Intel)"};
    private static final String[] CODEC_VALUES = {"libx264", "h264_nvenc", "h264_amf", "h264_qsv"};

    // Ban detection state
    private boolean lastBanState = false;

    // FFmpeg download state
    private volatile boolean isDownloading = false;
    private volatile String downloadStatus = "";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
    private static final String FFMPEG_DOWNLOAD_URL_WINDOWS = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    private static final String FFMPEG_DOWNLOAD_URL_LINUX = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz";
    private static final String FFMPEG_EXECUTABLE = IS_WINDOWS ? "ffmpeg.exe" : "ffmpeg";

    public ReplayBufferModule() {
        super("ReplayBuffer", "Native gameplay recording - save last X seconds (F9)", Category.MISC, RiskLevel.SAFE);
        INSTANCE = this;

        // Register keybind
        registerKeybind();

        // Setup FFmpeg callbacks
        ffmpeg.setOnSaveComplete(() -> {
            if (showNotifications) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("\u00A7a\u00A7l[Replay] \u00A77Clip saved successfully!"),
                            false
                        );
                    }
                });
            }
        });

        ffmpeg.setOnError(error -> {
            if (showNotifications) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("\u00A7c\u00A7l[Replay] \u00A77Error: " + error),
                            false
                        );
                    }
                });
            }
        });
    }

    public static ReplayBufferModule getInstance() {
        return INSTANCE;
    }

    private void registerKeybind() {
        if (!keyRegistered) {
            saveReplayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hunchclient.save_replay",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F9, // F9 to save replay
                dev.hunchclient.HunchModClient.KEYBIND_CATEGORY
            ));
            keyRegistered = true;
        }
    }

    @Override
    protected void onEnable() {
        // Check FFmpeg availability
        if (!ffmpeg.isFFmpegAvailable()) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("\u00A7c\u00A7l[Replay] \u00A77FFmpeg not found! Please install FFmpeg and add it to PATH."),
                        false
                    );
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("\u00A77Download: \u00A7bhttps://ffmpeg.org/download.html"),
                        false
                    );
                }
            });
            this.setEnabled(false);
            return;
        }

        // Apply settings to FFmpeg
        applySettings();

        // Start recording
        boolean started = ffmpeg.startRecording();
        if (started) {
            if (showNotifications) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("\u00A7a\u00A7l[Replay] \u00A77Buffer started! Press \u00A7eF9\u00A77 to save clip."),
                            false
                        );
                    }
                });
            }
        } else {
            this.setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        ffmpeg.stopRecording();

        if (showNotifications) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("\u00A7c\u00A7l[Replay] \u00A77Buffer stopped."),
                        false
                    );
                }
            });
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        // Check for save keybind
        if (saveReplayKey != null && saveReplayKey.consumeClick()) {
            saveReplay("manual");
        }

        // Check for ban (auto-save)
        if (autoSaveOnBan) {
            checkBanState();
        }
    }

    /**
     * Apply current settings to FFmpeg manager
     */
    private void applySettings() {
        // Set FFmpeg path if custom path is specified
        if (!ffmpegPath.isEmpty()) {
            ffmpeg.setFFmpegPath(ffmpegPath);
        } else {
            ffmpeg.setFFmpegPath("ffmpeg"); // Default to PATH
        }

        ffmpeg.setBufferLengthSeconds(bufferLength);
        ffmpeg.setFps(fps);
        ffmpeg.setVideoBitrate(videoBitrate);
        ffmpeg.setPreset(PRESETS[presetIndex]);
        ffmpeg.setVideoCodec(CODEC_VALUES[codecIndex]);
        ffmpeg.setResolutionScale(resolutionScalePercent / 100.0f);

        // Target file size settings
        ffmpeg.setUseTargetFileSize(useTargetFileSize);
        if (useTargetFileSize) {
            ffmpeg.setTargetFileSizeMB(targetFileSizeMB);
        }

        // Cursor capture
        ffmpeg.setCaptureCursor(captureCursor);

        if (!outputPath.isEmpty()) {
            ffmpeg.setOutputDirectory(outputPath);
        }
    }

    /**
     * Save the current replay buffer
     */
    public void saveReplay(String suffix) {
        if (!isEnabled() || !ffmpeg.isRecording()) {
            if (showNotifications) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("\u00A7c\u00A7l[Replay] \u00A77Buffer not active!"),
                            false
                        );
                    }
                });
            }
            return;
        }

        if (ffmpeg.isProcessing()) {
            if (showNotifications) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("\u00A7e\u00A7l[Replay] \u00A77Already saving, please wait..."),
                            false
                        );
                    }
                });
            }
            return;
        }

        if (showNotifications) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("\u00A7e\u00A7l[Replay] \u00A77Saving clip..."),
                        false
                    );
                }
            });
        }

        ffmpeg.saveReplay(suffix);
    }

    /**
     * Check for ban state changes (Hypixel ban detection)
     */
    private void checkBanState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check if disconnected with ban message
        // This is a simplified check - you may want to hook into disconnect packets
        boolean currentlyBanned = isBanDetected();

        if (currentlyBanned && !lastBanState) {
            // Just got banned - save replay
            HunchClient.LOGGER.info("[Replay] Ban detected! Auto-saving replay...");
            saveReplay("ban");
        }

        lastBanState = currentlyBanned;
    }

    /**
     * Check if player was banned (simplified detection)
     * Override this method or hook into packet events for better detection
     */
    private boolean isBanDetected() {
        Minecraft mc = Minecraft.getInstance();

        // Check if we're on disconnect screen
        if (mc.screen != null) {
            String screenName = mc.screen.getClass().getSimpleName();
            if (screenName.contains("Disconnect")) {
                // Could check disconnect reason here
                return true;
            }
        }

        return false;
    }

    /**
     * Manually trigger ban save (can be called from other modules)
     */
    public void triggerBanSave() {
        if (isEnabled() && autoSaveOnBan) {
            saveReplay("ban_detected");
        }
    }

    /**
     * Check if FFmpeg is available in system PATH
     */
    private boolean isFFmpegInPath() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "ffmpeg");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Download and install FFmpeg automatically
     */
    private void downloadAndInstallFFmpeg() {
        if (isDownloading) {
            sendNotification("§e§l[Replay] §7Download already in progress...");
            return;
        }

        new Thread(() -> {
            isDownloading = true;
            try {
                // Target directory: config/hunchclient/ffmpeg/
                Path ffmpegDir = Paths.get("config", "hunchclient", "ffmpeg");
                Path binDir = ffmpegDir.resolve("bin");
                Path ffmpegExe = binDir.resolve(FFMPEG_EXECUTABLE);

                // On Linux, first check if ffmpeg is already in PATH (installed via package manager)
                if (IS_LINUX && isFFmpegInPath()) {
                    sendNotification("§a§l[Replay] §7FFmpeg found in PATH!");
                    ffmpegPath = "ffmpeg";
                    applySettings();
                    isDownloading = false;
                    return;
                }

                // Check if already installed locally
                if (Files.exists(ffmpegExe)) {
                    sendNotification("§a§l[Replay] §7FFmpeg already installed!");
                    ffmpegPath = ffmpegExe.toAbsolutePath().toString();
                    applySettings();
                    isDownloading = false;
                    return;
                }

                // Create directories
                Files.createDirectories(ffmpegDir);

                sendNotification("§e§l[Replay] §7Downloading FFmpeg...");
                downloadStatus = "Downloading...";

                // Download archive to temp file
                String tempFileName = IS_WINDOWS ? "ffmpeg-download.zip" : "ffmpeg-download.tar.xz";
                Path tempArchive = Paths.get(System.getProperty("java.io.tmpdir"), tempFileName);

                String downloadUrl = IS_WINDOWS ? FFMPEG_DOWNLOAD_URL_WINDOWS : FFMPEG_DOWNLOAD_URL_LINUX;
                URL url = URI.create(downloadUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "HunchClient");
                connection.setInstanceFollowRedirects(true);

                // Handle GitHub redirects
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    String newUrl = connection.getHeaderField("Location");
                    connection = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                    connection.setRequestProperty("User-Agent", "HunchClient");
                }

                long fileSize = connection.getContentLengthLong();

                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempArchive.toFile())) {

                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int bytesRead;
                    int lastPercent = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;

                        if (fileSize > 0) {
                            int percent = (int) ((downloaded * 100) / fileSize);
                            if (percent != lastPercent && percent % 10 == 0) {
                                downloadStatus = "Downloading: " + percent + "%";
                                lastPercent = percent;
                            }
                        }
                    }
                }

                sendNotification("§e§l[Replay] §7Extracting FFmpeg...");
                downloadStatus = "Extracting...";

                if (IS_WINDOWS) {
                    // Extract ZIP - find the bin folder with ffmpeg.exe
                    try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(tempArchive.toFile()))) {
                        ZipEntry entry;
                        while ((entry = zipIn.getNextEntry()) != null) {
                            String entryName = entry.getName();

                            // We only need files from the bin folder (ffmpeg.exe, ffprobe.exe, ffplay.exe)
                            if (entryName.contains("/bin/") && !entry.isDirectory()) {
                                String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                                Path outPath = binDir.resolve(fileName);

                                Files.createDirectories(outPath.getParent());

                                try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                                    byte[] buffer = new byte[8192];
                                    int len;
                                    while ((len = zipIn.read(buffer)) > 0) {
                                        fos.write(buffer, 0, len);
                                    }
                                }

                                HunchClient.LOGGER.info("[Replay] Extracted: " + fileName);
                            }
                            zipIn.closeEntry();
                        }
                    }
                } else {
                    // Linux: Extract tar.xz using system tar command
                    Files.createDirectories(binDir);
                    ProcessBuilder extractPb = new ProcessBuilder(
                        "tar", "-xf", tempArchive.toAbsolutePath().toString(),
                        "--strip-components=2", // Skip the top-level and bin directories
                        "--wildcards", "*/bin/ffmpeg", "*/bin/ffprobe",
                        "-C", binDir.toAbsolutePath().toString()
                    );
                    Process extractProcess = extractPb.start();
                    int extractExit = extractProcess.waitFor();

                    if (extractExit != 0) {
                        // Try alternative extraction method
                        ProcessBuilder altPb = new ProcessBuilder(
                            "sh", "-c",
                            "tar -xf " + tempArchive.toAbsolutePath() + " -C " + ffmpegDir.toAbsolutePath() +
                            " && find " + ffmpegDir.toAbsolutePath() + " -name 'ffmpeg' -type f -exec mv {} " + binDir.toAbsolutePath() + "/ \\;" +
                            " && find " + ffmpegDir.toAbsolutePath() + " -name 'ffprobe' -type f -exec mv {} " + binDir.toAbsolutePath() + "/ \\;"
                        );
                        altPb.start().waitFor();
                    }

                    // Make executable
                    Path ffmpegBin = binDir.resolve("ffmpeg");
                    Path ffprobeBin = binDir.resolve("ffprobe");
                    if (Files.exists(ffmpegBin)) {
                        ffmpegBin.toFile().setExecutable(true);
                    }
                    if (Files.exists(ffprobeBin)) {
                        ffprobeBin.toFile().setExecutable(true);
                    }

                    HunchClient.LOGGER.info("[Replay] Extracted FFmpeg for Linux");
                }

                // Delete temp archive
                Files.deleteIfExists(tempArchive);

                // Verify installation
                if (Files.exists(ffmpegExe)) {
                    // Set the path automatically
                    ffmpegPath = ffmpegExe.toAbsolutePath().toString();
                    applySettings();

                    sendNotification("§a§l[Replay] §7FFmpeg installed successfully!");
                    sendNotification("§7Path: §b" + ffmpegPath);
                    downloadStatus = "Installed!";
                } else {
                    sendNotification("§c§l[Replay] §7Installation failed - " + FFMPEG_EXECUTABLE + " not found");
                    downloadStatus = "Failed";
                }

            } catch (Exception e) {
                HunchClient.LOGGER.error("[Replay] FFmpeg download failed: " + e.getMessage(), e);
                sendNotification("§c§l[Replay] §7Download failed: " + e.getMessage());
                downloadStatus = "Error: " + e.getMessage();
            } finally {
                isDownloading = false;
            }
        }, "FFmpeg-Downloader").start();
    }

    /**
     * Send a notification to the player
     */
    private void sendNotification(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
            } else {
                HunchClient.LOGGER.info(message.replaceAll("§.", ""));
            }
        });
    }

    // Settings Provider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Apply path first to check status
        applySettings();

        // FFmpeg status
        boolean ffmpegAvailable = ffmpeg.isFFmpegAvailable();

        // Download/Install FFmpeg button
        String buttonText = isDownloading ? "Downloading..." : (ffmpegAvailable ? "FFmpeg Ready" : "Install FFmpeg");
        String buttonDesc = isDownloading ? downloadStatus : (ffmpegAvailable ? "FFmpeg is installed and ready" : "Click to auto-download and install FFmpeg");
        settings.add(new ButtonSetting(
            buttonText,
            buttonDesc,
            "replay_ffmpeg_download",
            () -> {
                if (!isDownloading) {
                    downloadAndInstallFFmpeg();
                }
            }
        ));

        // FFmpeg path textbox
        settings.add(new TextBoxSetting(
            "FFmpeg Path",
            IS_WINDOWS ? "Full path to ffmpeg.exe (e.g. C:\\ffmpeg\\bin\\ffmpeg.exe)" : "Full path to ffmpeg (or leave empty to use PATH)",
            "replay_ffmpeg_path",
            () -> ffmpegPath,
            (val) -> {
                ffmpegPath = val;
                applySettings();
            }
        ));

        // Buffer length
        settings.add(new SliderSetting(
            "Buffer Length",
            "How many seconds to keep in buffer (10-300)",
            "replay_buffer_length",
            10, 300,
            () -> (float) bufferLength,
            (val) -> {
                bufferLength = val.intValue();
                if (isEnabled()) {
                    // Restart recording with new settings
                    ffmpeg.stopRecording();
                    applySettings();
                    ffmpeg.startRecording();
                }
            }
        ).withDecimals(0).withSuffix("s"));

        // FPS
        settings.add(new SliderSetting(
            "FPS",
            "Recording framerate (15-60)",
            "replay_fps",
            15, 60,
            () -> (float) fps,
            (val) -> {
                fps = val.intValue();
            }
        ).withDecimals(0));

        // Video Bitrate
        settings.add(new SliderSetting(
            "Video Bitrate",
            "Video quality in kbps (1000-50000)",
            "replay_video_bitrate",
            1000, 50000,
            () -> (float) videoBitrate,
            (val) -> {
                videoBitrate = val.intValue();
            }
        ).withDecimals(0).withSuffix(" kbps"));

        // Target File Size (like 8mb.video)
        settings.add(new CheckboxSetting(
            "Limit File Size",
            "Compress output to target size (like 8mb.video)",
            "replay_limit_file_size",
            () -> useTargetFileSize,
            (val) -> {
                useTargetFileSize = val;
                applySettings();
            }
        ));

        settings.add(new SliderSetting(
            "Target Size (MB)",
            "Maximum file size in MB (re-encodes on save)",
            "replay_target_size_mb",
            8, 500,
            () -> (float) targetFileSizeMB,
            (val) -> {
                targetFileSizeMB = val.intValue();
                applySettings();
            }
        ).withDecimals(0).withSuffix(" MB"));

        // Resolution Scale (for performance)
        settings.add(new SliderSetting(
            "Resolution Scale",
            "Capture at lower resolution for better FPS (25-100%)",
            "replay_resolution_scale",
            25, 100,
            () -> (float) resolutionScalePercent,
            (val) -> {
                resolutionScalePercent = val.intValue();
                applySettings();
            }
        ).withDecimals(0).withSuffix("%"));

        // Encoder Preset
        settings.add(new DropdownSetting(
            "Encoder Preset",
            "Encoding speed vs quality tradeoff",
            "replay_preset",
            PRESETS,
            () -> presetIndex,
            (val) -> presetIndex = val
        ));

        // Video Codec
        settings.add(new DropdownSetting(
            "Video Codec",
            "Hardware encoding for better performance",
            "replay_codec",
            CODECS,
            () -> codecIndex,
            (val) -> codecIndex = val
        ));

        // Auto-save on ban
        settings.add(new CheckboxSetting(
            "Auto-Save on Ban",
            "Automatically save replay when banned",
            "replay_auto_save_ban",
            () -> autoSaveOnBan,
            (val) -> autoSaveOnBan = val
        ));

        // Show notifications
        settings.add(new CheckboxSetting(
            "Show Notifications",
            "Show chat messages for replay events",
            "replay_notifications",
            () -> showNotifications,
            (val) -> showNotifications = val
        ));

        // Capture cursor
        settings.add(new CheckboxSetting(
            "Capture Cursor",
            "Draw software cursor on recorded frames (visible in replay)",
            "replay_capture_cursor",
            () -> captureCursor,
            (val) -> {
                captureCursor = val;
                applySettings();
            }
        ));

        // Manual save button
        settings.add(new ButtonSetting(
            "Save Clip Now",
            "Manually save the current buffer",
            "replay_save_now",
            () -> saveReplay("manual")
        ));

        // Open output folder
        settings.add(new ButtonSetting(
            "Open Output Folder",
            "Open the folder containing saved replays",
            "replay_open_folder",
            () -> {
                try {
                    String dir = ffmpeg.getOutputDirectory();
                    // Create folder if it doesn't exist
                    java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dir));
                    // Use explorer on Windows for reliable folder opening
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"explorer", dir});
                    } else {
                        java.awt.Desktop.getDesktop().open(new java.io.File(dir));
                    }
                } catch (Exception e) {
                    HunchClient.LOGGER.error("Failed to open folder: " + e.getMessage());
                    sendNotification("§c§l[Replay] §7Failed to open folder: " + e.getMessage());
                }
            }
        ));

        return settings;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("ffmpegPath", ffmpegPath);
        config.addProperty("bufferLength", bufferLength);
        config.addProperty("fps", fps);
        config.addProperty("videoBitrate", videoBitrate);
        config.addProperty("presetIndex", presetIndex);
        config.addProperty("codecIndex", codecIndex);
        config.addProperty("autoSaveOnBan", autoSaveOnBan);
        config.addProperty("showNotifications", showNotifications);
        config.addProperty("outputPath", outputPath);
        config.addProperty("resolutionScalePercent", resolutionScalePercent);
        config.addProperty("useTargetFileSize", useTargetFileSize);
        config.addProperty("targetFileSizeMB", targetFileSizeMB);
        config.addProperty("captureCursor", captureCursor);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        if (data.has("ffmpegPath")) ffmpegPath = data.get("ffmpegPath").getAsString();
        if (data.has("bufferLength")) bufferLength = data.get("bufferLength").getAsInt();
        if (data.has("fps")) fps = data.get("fps").getAsInt();
        if (data.has("videoBitrate")) videoBitrate = data.get("videoBitrate").getAsInt();
        if (data.has("presetIndex")) presetIndex = data.get("presetIndex").getAsInt();
        if (data.has("codecIndex")) codecIndex = data.get("codecIndex").getAsInt();
        if (data.has("autoSaveOnBan")) autoSaveOnBan = data.get("autoSaveOnBan").getAsBoolean();
        if (data.has("showNotifications")) showNotifications = data.get("showNotifications").getAsBoolean();
        if (data.has("outputPath")) outputPath = data.get("outputPath").getAsString();
        if (data.has("resolutionScalePercent")) resolutionScalePercent = data.get("resolutionScalePercent").getAsInt();
        if (data.has("useTargetFileSize")) useTargetFileSize = data.get("useTargetFileSize").getAsBoolean();
        if (data.has("targetFileSizeMB")) targetFileSizeMB = data.get("targetFileSizeMB").getAsInt();
        if (data.has("captureCursor")) captureCursor = data.get("captureCursor").getAsBoolean();

        // Apply loaded settings
        applySettings();
    }

    // Getters for external access
    public boolean isRecording() {
        return ffmpeg.isRecording();
    }

    public int getBufferLength() {
        return bufferLength;
    }
}
