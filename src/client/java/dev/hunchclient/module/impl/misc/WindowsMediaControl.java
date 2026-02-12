package dev.hunchclient.module.impl.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.client.Minecraft;

/**
 * Windows Media Control helper that bridges against the external SmtcReader helper process.
 *
 * SmtcReader is a small WinRT bridge that continuously writes the currently playing media
 * information (title / artist / album / album art) to JSON files. We download, extract
 * and supervise the tool on demand, then translate the JSON payload into {@link MediaPlayerData}.
 *
 * The legacy PowerShell-based implementation relied on WinRT metadata being present locally
 * which is not guaranteed on modern Windows installs. This helper avoids that requirement.
 */
public final class WindowsMediaControl {

    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-Media");

    /**
     * JNA Interface for Windows User32.dll
     */
    public interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        /**
         * Synthesizes a keystroke
         * @param bVk Virtual key code
         * @param bScan Hardware scan code
         * @param dwFlags Flags (KEYEVENTF_EXTENDEDKEY, KEYEVENTF_KEYUP)
         * @param dwExtraInfo Extra info
         */
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    // Windows Virtual Key Codes for media keys
    // These are not available in older Java versions, so we define them manually
    private static final int VK_MEDIA_NEXT_TRACK = 0xB0;      // 176
    private static final int VK_MEDIA_PREVIOUS_TRACK = 0xB1;  // 177
    private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;      // 179
    private static final int VK_VOLUME_MUTE = 0xAD;           // 173
    private static final int VK_VOLUME_DOWN = 0xAE;           // 174
    private static final int VK_VOLUME_UP = 0xAF;             // 175

    private static float currentVolume = 0.5f; // Track current volume level (0.0 to 1.0)

    private WindowsMediaControl() {
    }

    /**
     * Asynchronously fetch current media information from the SMTC bridge (Windows) or MPRIS (Linux).
     */
    public static CompletableFuture<MediaPlayerData> getMediaInfoAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String osName = System.getProperty("os.name");
            if (isWindows()) {
                try {
                    return SmtcReaderBridge.getInstance().readCurrentData();
                } catch (Exception e) {
                    LOGGER.error("Failed to read SMTC data: {}", e.getMessage());
                    return createInactiveData();
                }
            } else if (isLinux()) {
                try {
                    return LinuxMprisBridge.getInstance().readCurrentData();
                } catch (Exception e) {
                    LOGGER.warn("[Linux MPRIS] Failed to read data: {}", e.getMessage());
                    return createInactiveData();
                }
            } else {
                LOGGER.warn("[Media] Unknown OS: {} - no media support", osName);
                return createInactiveData();
            }
        });
    }

    /**
     * Toggle play/pause using a synthetic media key.
     */
    public static void togglePlayPause() {
        LOGGER.info("togglePlayPause() called");
        if (isWindows()) {
            sendMediaKey(VK_MEDIA_PLAY_PAUSE);
        } else if (isLinux()) {
            LinuxMprisBridge.getInstance().sendCommand("play-pause");
        }
    }

    /**
     * Skip to the next track using a synthetic media key.
     */
    public static void nextTrack() {
        LOGGER.info("nextTrack() called");
        if (isWindows()) {
            sendMediaKey(VK_MEDIA_NEXT_TRACK);
        } else if (isLinux()) {
            LinuxMprisBridge.getInstance().sendCommand("next");
        }
    }

    /**
     * Skip to the previous track using a synthetic media key.
     */
    public static void previousTrack() {
        LOGGER.info("previousTrack() called");
        if (isWindows()) {
            sendMediaKey(VK_MEDIA_PREVIOUS_TRACK);
        } else if (isLinux()) {
            LinuxMprisBridge.getInstance().sendCommand("previous");
        }
    }

    /**
     * Set system volume to a specific level (0.0 to 1.0).
     * This uses volume up/down keys to reach the target level.
     */
    public static void setVolume(float targetVolume) {
        if (!isWindows()) {
            return;
        }

        targetVolume = Math.max(0, Math.min(1, targetVolume));

        // Calculate volume difference
        float volumeDiff = targetVolume - currentVolume;

        // Each volume key press changes volume by ~2% (50 steps to go from 0 to 100)
        int steps = Math.round(Math.abs(volumeDiff) * 50);

        if (steps == 0) {
            return; // No change needed
        }

        int keyToSend = volumeDiff > 0 ? VK_VOLUME_UP : VK_VOLUME_DOWN;

        // Send volume keys asynchronously
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < steps; i++) {
                sendMediaKeySync(keyToSend);
                try {
                    Thread.sleep(10); // Small delay between key presses
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        currentVolume = targetVolume;
        LOGGER.debug("Volume changed to: {}%", (int)(targetVolume * 100));
    }

    /**
     * Shutdown background helpers and clean up temporary assets.
     */
    public static void shutdown() {
        if (isWindows()) {
            SmtcReaderBridge.getInstance().shutdown();
        }
    }

    /**
     * Restart the SMTC Reader process.
     * Called on world load to ensure fresh connection to media sessions.
     */
    public static void restart() {
        if (isWindows()) {
            LOGGER.info("Restarting SMTC Reader process due to world load");
            SmtcReaderBridge.getInstance().restart();
        }
    }

    // Windows keybd_event flags
    private static final int KEYEVENTF_EXTENDEDKEY = 0x0001;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    private static void sendMediaKey(int keyCode) {
        LOGGER.info("sendMediaKey({}) called", keyCode);

        if (!isWindows()) {
            LOGGER.warn("Not on Windows, skipping media key");
            return;
        }

        // Use JNA to call Windows API keybd_event directly
        // This is much faster and more reliable than PowerShell
        CompletableFuture.runAsync(() -> {
            sendMediaKeySync(keyCode);
        });
    }

    private static void sendMediaKeySync(int keyCode) {
        try {
            User32 user32 = User32.INSTANCE;

            // Key down (press)
            user32.keybd_event(
                (byte) keyCode,         // Virtual key code
                (byte) 0,               // Hardware scan code (0 = system default)
                KEYEVENTF_EXTENDEDKEY,  // Flags (extended key)
                0                       // Extra info
            );

            // Small delay between key down and key up
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Key up (release)
            user32.keybd_event(
                (byte) keyCode,                            // Virtual key code
                (byte) 0,                                  // Hardware scan code
                KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP,  // Flags (extended key + key up)
                0                                          // Extra info
            );

            LOGGER.debug("Media key {} sent successfully via JNA", keyCode);
        } catch (Exception e) {
            LOGGER.error("Failed to send media key {} via JNA: {}", keyCode, e.getMessage(), e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        boolean result = os.contains("linux") || os.contains("nix") || os.contains("nux");
        // Log once on first check
        if (!osCheckLogged) {
            osCheckLogged = true;
            LOGGER.info("[Media] OS detected: '{}', isLinux={}, isWindows={}, isFlatpak={}",
                System.getProperty("os.name"), result, isWindows(), isFlatpak());
        }
        return result;
    }

    private static volatile boolean osCheckLogged = false;

    /**
     * Check if running inside a Flatpak sandbox
     */
    private static boolean isFlatpak() {
        // Check for Flatpak environment variable or /.flatpak-info file
        String flatpakId = System.getenv("FLATPAK_ID");
        if (flatpakId != null && !flatpakId.isEmpty()) {
            return true;
        }
        return java.nio.file.Files.exists(java.nio.file.Paths.get("/.flatpak-info"));
    }

    private static MediaPlayerData createInactiveData() {
        MediaPlayerData data = new MediaPlayerData();
        data.setActive(false);
        data.setTitle("No media playing");
        data.setArtist("");
        data.setStatus(MediaPlayerData.PlaybackStatus.STOPPED);
        data.setHasTimeline(false);
        return data;
    }

    /**
     * Helper responsible for downloading, extracting, starting and reading data from SmtcReader.
     */
    private static final class SmtcReaderBridge {

        private static final String DOWNLOAD_URL = "https://34.7.234.242/helper/SmtcReader.zip";
        private static final SmtcReaderBridge INSTANCE = new SmtcReaderBridge();

        private final Object lock = new Object();

        private final Path helperRoot;
        private final Path binDir;
        private final Path workDir;
        private final Path executable;
        private final Path songDataFile;
        private final Path albumArtFile;

        private Process process;
        private long lastDataTimestamp = -1L;
        private MediaPlayerData cachedData = createInactiveData();
        private long lastRestartTime = 0;

        private SmtcReaderBridge() {
            Path runDir;
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                runDir = client.gameDirectory.toPath();
            } else {
                runDir = Paths.get(System.getProperty("user.dir"));
            }

            helperRoot = runDir.resolve("hunchclient").resolve("helper").resolve("SmtcReader");
            binDir = helperRoot.resolve("bin");
            workDir = helperRoot.resolve("workspace");
            executable = binDir.resolve("SmtcReader.exe");
            songDataFile = workDir.resolve("data").resolve("song_data.json");
            albumArtFile = workDir.resolve("assets").resolve("album_art.png");
        }

        static SmtcReaderBridge getInstance() {
            return INSTANCE;
        }

        MediaPlayerData readCurrentData() throws IOException {
            ensureProcessRunning();

            if (!Files.exists(songDataFile)) {
                return cachedData;
            }

            long modified = Files.getLastModifiedTime(songDataFile).toMillis();
            if (modified == lastDataTimestamp && cachedData != null) {
                return cachedData;
            }

            String json = Files.readString(songDataFile, StandardCharsets.UTF_8);
            MediaPlayerData data = parseSongData(json);
            cachedData = data;
            lastDataTimestamp = modified;
            return data;
        }

        void shutdown() {
            synchronized (lock) {
                killProcess();
                deleteSilently(songDataFile);
                deleteSilently(albumArtFile);
            }
        }

        void restart() {
            synchronized (lock) {
                // Prevent rapid restarts (minimum 3 seconds between restarts)
                long now = System.currentTimeMillis();
                if (now - lastRestartTime < 3000) {
                    LOGGER.debug("SMTC Reader restart skipped, too soon after last restart");
                    return;
                }
                lastRestartTime = now;

                // Kill existing process
                killProcess();

                // Clear cached data
                lastDataTimestamp = -1L;
                cachedData = createInactiveData();

                // Clean up old data files
                deleteSilently(songDataFile);
                deleteSilently(albumArtFile);

                // Process will be restarted on next read
                LOGGER.info("SMTC Reader process terminated, will restart on next media check");
            }
        }

        private void killProcess() {
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
                process = null;
            }
        }

        private void ensureProcessRunning() throws IOException {
            synchronized (lock) {
                Files.createDirectories(helperRoot);
                Files.createDirectories(workDir);

                if (!Files.exists(executable)) {
                    downloadAndExtract();
                }

                if (process == null || !process.isAlive()) {
                    startProcess();
                }
            }
        }

        private void downloadAndExtract() throws IOException {
            Files.createDirectories(binDir);
            Path zipPath = helperRoot.resolve("SmtcReader.zip");
            download(zipPath);

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path target = binDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                            zis.transferTo(out);
                        }
                    }
                }
            }

            deleteSilently(zipPath);
        }

        private void download(Path destination) throws IOException {
            LOGGER.info("Downloading SmtcReader helper...");

            HttpURLConnection connection = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "HunchClient/NowPlaying");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("Failed to download SmtcReader: HTTP " + status);
            }

            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                in.transferTo(out);
            } finally {
                connection.disconnect();
            }
        }

        private void startProcess() throws IOException {
            ProcessBuilder builder = new ProcessBuilder(executable.toString(), workDir.toAbsolutePath().toString());
            builder.directory(binDir.toFile());
            builder.redirectErrorStream(true);

            Process proc = builder.start();
            drain(proc.getInputStream());
            process = proc;
        }

        private void drain(InputStream input) {
            Thread thread = new Thread(() -> {
                try (InputStream in = input) {
                    in.transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                }
            }, "HunchClient-SmtcReader-IO");

            thread.setDaemon(true);
            thread.start();
        }

        private MediaPlayerData parseSongData(String json) {
            try {
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                String title = getString(obj, "title");
                String artist = getString(obj, "artist");
                String album = getString(obj, "album");
                String artPath = getString(obj, "albumArtPath");

                boolean hasTitle = title != null && !title.isBlank();

                MediaPlayerData data = new MediaPlayerData();
                data.setTitle(title != null ? title : "");
                data.setArtist(artist != null ? artist : "");
                data.setAlbum(album != null ? album : "");
                data.setStatus(hasTitle ? MediaPlayerData.PlaybackStatus.PLAYING : MediaPlayerData.PlaybackStatus.STOPPED);
                data.setActive(hasTitle);
                data.setHasTimeline(false);

                // Handle album art path
                if (artPath != null && !artPath.isBlank()) {
                    LOGGER.debug("Received album art path from SMTC: {}", artPath);

                    Path albumArtPath = Paths.get(artPath);
                    if (!albumArtPath.isAbsolute()) {
                        albumArtPath = workDir.resolve(artPath);
                        LOGGER.debug("Resolved relative path to: {}", albumArtPath);
                    }

                    // Check if file exists and is readable
                    if (Files.exists(albumArtPath)) {
                        if (Files.isReadable(albumArtPath)) {
                            long fileSize = Files.size(albumArtPath);
                            LOGGER.info("Album art file found: {} (size: {} bytes)", albumArtPath, fileSize);

                            if (fileSize > 0) {
                                data.setThumbnailUrl(albumArtPath.toAbsolutePath().toString());
                            } else {
                                LOGGER.warn("Album art file is empty: {}", albumArtPath);
                                data.setThumbnailUrl(null);
                            }
                        } else {
                            LOGGER.warn("Album art file exists but is not readable: {}", albumArtPath);
                            data.setThumbnailUrl(null);
                        }
                    } else {
                        LOGGER.warn("Album art file does not exist: {}", albumArtPath);

                        // Try the default path as fallback
                        Path defaultPath = albumArtFile; // workDir/assets/album_art.png
                        if (Files.exists(defaultPath) && Files.isReadable(defaultPath) && Files.size(defaultPath) > 0) {
                            LOGGER.info("Using default album art path: {}", defaultPath);
                            data.setThumbnailUrl(defaultPath.toAbsolutePath().toString());
                        } else {
                            LOGGER.debug("Default album art path also not available: {}", defaultPath);
                            data.setThumbnailUrl(null);
                        }
                    }
                } else {
                    LOGGER.debug("No album art path in SMTC data");

                    // Check if default album art file exists
                    if (Files.exists(albumArtFile) && Files.isReadable(albumArtFile) && Files.size(albumArtFile) > 0) {
                        LOGGER.info("Using default album art file: {}", albumArtFile);
                        data.setThumbnailUrl(albumArtFile.toAbsolutePath().toString());
                    } else {
                        data.setThumbnailUrl(null);
                    }
                }

                data.updateTimestamp();
                return data;
            } catch (Exception e) {
                LOGGER.error("Failed to parse SmtcReader JSON: {}", e.getMessage());
                return createInactiveData();
            }
        }

        private String getString(JsonObject obj, String key) {
            return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : null;
        }

        private void deleteSilently(Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Linux MPRIS bridge using playerctl CLI tool.
     * playerctl is a command-line utility for controlling media players via MPRIS D-Bus interface.
     */
    private static final class LinuxMprisBridge {

        private static final LinuxMprisBridge INSTANCE = new LinuxMprisBridge();
        private static volatile boolean playerctlAvailable = true;
        private static volatile boolean checkedAvailability = false;

        private MediaPlayerData cachedData = createInactiveData();
        private long lastFetchTime = 0;
        private static final long CACHE_DURATION_MS = 2000; // Cache for 2 seconds to avoid spamming playerctl
        private String lastLoggedTitle = null; // Track last title to avoid log spam

        static LinuxMprisBridge getInstance() {
            return INSTANCE;
        }

        MediaPlayerData readCurrentData() {
            // Check cache
            long now = System.currentTimeMillis();
            if (now - lastFetchTime < CACHE_DURATION_MS && cachedData != null) {
                return cachedData;
            }

            // Check if playerctl is available
            if (!checkedAvailability) {
                checkedAvailability = true;
                playerctlAvailable = isPlayerctlAvailable();
                if (playerctlAvailable) {
                    LOGGER.info("[Linux MPRIS] playerctl found and available!");
                } else {
                    LOGGER.warn("[Linux MPRIS] playerctl not found. Install it for media integration: sudo dnf install playerctl");
                }
            }

            if (!playerctlAvailable) {
                return createInactiveData();
            }

            try {
                // Get player status
                String status = runCommand("playerctl", "status");

                if (status == null || status.isBlank() || status.contains("No players found")) {
                    if (lastLoggedTitle != null) {
                        LOGGER.info("[Linux MPRIS] No active players found");
                        lastLoggedTitle = null;
                    }
                    cachedData = createInactiveData();
                    lastFetchTime = now;
                    return cachedData;
                }

                // Get metadata
                String title = runCommand("playerctl", "metadata", "title");
                String artist = runCommand("playerctl", "metadata", "artist");
                String album = runCommand("playerctl", "metadata", "album");
                String artUrl = runCommand("playerctl", "metadata", "mpris:artUrl");

                // Only log when title changes
                if (title != null && !title.equals(lastLoggedTitle)) {
                    LOGGER.info("[Linux MPRIS] Now playing: '{}' by '{}'", title, artist);
                    lastLoggedTitle = title;
                }

                MediaPlayerData data = new MediaPlayerData();
                data.setTitle(title != null ? title.trim() : "");
                data.setArtist(artist != null ? artist.trim() : "");
                data.setAlbum(album != null ? album.trim() : "");

                // Parse status
                status = status.trim().toLowerCase();
                if (status.equals("playing")) {
                    data.setStatus(MediaPlayerData.PlaybackStatus.PLAYING);
                    data.setActive(true);
                    LOGGER.debug("[Linux MPRIS] Status: PLAYING, active=true");
                } else if (status.equals("paused")) {
                    data.setStatus(MediaPlayerData.PlaybackStatus.PAUSED);
                    data.setActive(true);
                    LOGGER.debug("[Linux MPRIS] Status: PAUSED, active=true");
                } else {
                    data.setStatus(MediaPlayerData.PlaybackStatus.STOPPED);
                    data.setActive(false);
                    LOGGER.debug("[Linux MPRIS] Status: STOPPED (unknown: {}), active=false", status);
                }

                // Handle album art URL
                if (artUrl != null && !artUrl.isBlank()) {
                    artUrl = artUrl.trim();
                    // Convert file:// URLs to path
                    if (artUrl.startsWith("file://")) {
                        artUrl = artUrl.substring(7);
                    }
                    data.setThumbnailUrl(artUrl);
                }

                data.setHasTimeline(false);
                data.updateTimestamp();

                cachedData = data;
                lastFetchTime = now;
                return data;

            } catch (Exception e) {
                LOGGER.debug("Error reading MPRIS data: {}", e.getMessage());
                return createInactiveData();
            }
        }

        private boolean isPlayerctlAvailable() {
            boolean flatpak = isFlatpak();
            LOGGER.info("[Linux MPRIS] Checking playerctl availability (Flatpak: {})", flatpak);

            // Try direct execution first (most reliable)
            try {
                ProcessBuilder pb;
                if (flatpak) {
                    // Use flatpak-spawn to run playerctl on host system
                    pb = new ProcessBuilder("flatpak-spawn", "--host", "playerctl", "--version");
                    LOGGER.info("[Linux MPRIS] Using flatpak-spawn to access host playerctl");
                } else {
                    pb = new ProcessBuilder("playerctl", "--version");
                }
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean finished = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    LOGGER.info("[Linux MPRIS] playerctl found via direct execution (Flatpak: {})", flatpak);
                    return true;
                }
            } catch (Exception e) {
                LOGGER.info("[Linux MPRIS] Direct playerctl check failed: {}", e.getMessage());
            }

            // Fallback: Try shell methods (only if not in Flatpak)
            if (!flatpak) {
                String[] checkCommands = {
                    "which playerctl",
                    "command -v playerctl"
                };

                for (String cmd : checkCommands) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        int exitCode = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) ? p.exitValue() : -1;
                        if (exitCode == 0) {
                            LOGGER.info("[Linux MPRIS] playerctl found via: {}", cmd);
                            return true;
                        }
                    } catch (Exception e) {
                        LOGGER.info("[Linux MPRIS] Check failed for '{}': {}", cmd, e.getMessage());
                    }
                }
            }

            LOGGER.warn("[Linux MPRIS] playerctl NOT found! Install with: sudo dnf install playerctl");
            return false;
        }

        private String runCommand(String... command) {
            try {
                String[] actualCommand;
                if (isFlatpak()) {
                    // Prefix with flatpak-spawn --host to run on host system
                    actualCommand = new String[command.length + 2];
                    actualCommand[0] = "flatpak-spawn";
                    actualCommand[1] = "--host";
                    System.arraycopy(command, 0, actualCommand, 2, command.length);
                } else {
                    actualCommand = command;
                }

                ProcessBuilder pb = new ProcessBuilder(actualCommand);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                String output;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    output = reader.readLine();
                }

                p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                return output;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Send media control command via playerctl
         */
        void sendCommand(String action) {
            if (!playerctlAvailable) return;

            try {
                ProcessBuilder pb;
                if (isFlatpak()) {
                    pb = new ProcessBuilder("flatpak-spawn", "--host", "playerctl", action);
                } else {
                    pb = new ProcessBuilder("playerctl", action);
                }
                pb.start();
            } catch (Exception e) {
                LOGGER.debug("Failed to send playerctl command: {}", e.getMessage());
            }
        }
    }
}
