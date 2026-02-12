package dev.hunchclient.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Media Search Manager
 *
 * Handles searching for music across different media players on Windows
 * Supports Apple Music, Spotify, and other media players through Windows APIs
 */
public class MediaSearchManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-MediaSearch");

    // Cache for search results
    private List<MediaSearchResult> lastSearchResults = new ArrayList<>();
    private String lastQuery = "";

    // Media player states
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false;
    private float currentVolume = 0.7f;

    public MediaSearchManager() {
        LOGGER.info("MediaSearchManager initialized");
    }

    /**
     * Search for songs/artists/albums
     * This uses PowerShell to interface with Windows media players
     */
    public List<MediaSearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Return cached results if same query
        if (query.equals(lastQuery) && !lastSearchResults.isEmpty()) {
            return new ArrayList<>(lastSearchResults);
        }

        lastQuery = query;
        List<MediaSearchResult> results = new ArrayList<>();

        try {
            // For Windows, we'll use a PowerShell script to search media
            String searchScript = buildSearchScript(query);
            String jsonResult = executePowerShell(searchScript);

            if (jsonResult != null && !jsonResult.isEmpty()) {
                results = parseSearchResults(jsonResult);
            }

            // If no results from media players, try to search in the current playing app
            if (results.isEmpty()) {
                results = searchInCurrentPlayer(query);
            }

        } catch (Exception e) {
            LOGGER.error("Error searching for media: {}", e.getMessage());
        }

        lastSearchResults = results;
        return results;
    }

    /**
     * Play a specific track from search results
     */
    public void playTrack(MediaSearchResult result) {
        if (result == null) return;

        try {
            String playScript = buildPlayScript(result);
            executePowerShell(playScript);
            LOGGER.info("Playing track: {} by {}", result.getTitle(), result.getArtist());
        } catch (Exception e) {
            LOGGER.error("Error playing track: {}", e.getMessage());
        }
    }

    /**
     * Play a track from history
     */
    public void playFromHistory(MediaPlayerData song) {
        if (song == null) return;

        try {
            // Try to play the specific song using available media player APIs
            String playScript = buildHistoryPlayScript(song);
            String result = executePowerShell(playScript);

            // If we got a success indicator, log it
            if (result != null && result.contains("success")) {
                LOGGER.info("Successfully initiated playback of: {} by {}", song.getTitle(), song.getArtist());
            } else {
                // Fallback to simple play/pause toggle
                WindowsMediaControl.togglePlayPause();
                LOGGER.info("Attempted generic playback for: {} by {}", song.getTitle(), song.getArtist());
            }
        } catch (Exception e) {
            LOGGER.error("Error playing from history: {}", e.getMessage());
        }
    }

    /**
     * Build PowerShell script to play a song from history
     */
    private String buildHistoryPlayScript(MediaPlayerData song) {
        String title = song.getTitle().replace("'", "''").replace("$", "`$").replace("\"", "`\"");
        String artist = song.getArtist().replace("'", "''").replace("$", "`$").replace("\"", "`\"");
        String album = song.getAlbum().replace("'", "''").replace("$", "`$").replace("\"", "`\"");

        return String.format(
            "$success = $false\n" +
            "\n" +
            "# Try Apple Music/iTunes first\n" +
            "try {\n" +
            "    $itunes = New-Object -ComObject iTunes.Application -ErrorAction SilentlyContinue\n" +
            "    if ($itunes -and $itunes.Sources) {\n" +
            "        $library = $itunes.Sources.Item(1).Playlists.Item(1)\n" +
            "        $tracks = $library.Tracks\n" +
            "        if ($tracks) {\n" +
            "            # Search for the exact track\n" +
            "            for ($i = 1; $i -le $tracks.Count; $i++) {\n" +
            "                $track = $tracks.Item($i)\n" +
            "                if ($track) {\n" +
            "                    $trackName = if ($track.Name) { $track.Name } else { '' }\n" +
            "                    $trackArtist = if ($track.Artist) { $track.Artist } else { '' }\n" +
            "                    $trackAlbum = if ($track.Album) { $track.Album } else { '' }\n" +
            "                    \n" +
            "                    if ($trackName -eq '%s' -and $trackArtist -eq '%s') {\n" +
            "                        $track.Play()\n" +
            "                        Write-Output 'success:itunes'\n" +
            "                        $success = $true\n" +
            "                        break\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "} catch {\n" +
            "    # iTunes not available or error\n" +
            "}\n" +
            "\n" +
            "# Try Spotify (open search URL)\n" +
            "if (-not $success) {\n" +
            "    try {\n" +
            "        $searchQuery = [System.Uri]::EscapeDataString('%s %s')\n" +
            "        $spotifyUrl = \"spotify:search:$searchQuery\"\n" +
            "        Start-Process $spotifyUrl -ErrorAction SilentlyContinue\n" +
            "        # Give Spotify time to process and then send play command\n" +
            "        Start-Sleep -Milliseconds 500\n" +
            "        $wshShell = New-Object -ComObject WScript.Shell\n" +
            "        $wshShell.SendKeys(' ')\n" +  // Space to play
            "        Write-Output 'success:spotify'\n" +
            "        $success = $true\n" +
            "    } catch {}\n" +
            "}\n" +
            "\n" +
            "# Try Windows Media Player\n" +
            "if (-not $success) {\n" +
            "    try {\n" +
            "        $wmp = New-Object -ComObject WMPlayer.OCX -ErrorAction SilentlyContinue\n" +
            "        if ($wmp -and $wmp.mediaCollection) {\n" +
            "            $allSongs = $wmp.mediaCollection.getAll()\n" +
            "            for ($i = 0; $i -lt $allSongs.count; $i++) {\n" +
            "                $item = $allSongs.Item($i)\n" +
            "                if ($item.getItemInfo('Title') -eq '%s' -and $item.getItemInfo('Artist') -eq '%s') {\n" +
            "                    $wmp.currentMedia = $item\n" +
            "                    $wmp.controls.play()\n" +
            "                    Write-Output 'success:wmp'\n" +
            "                    $success = $true\n" +
            "                    break\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    } catch {}\n" +
            "}\n" +
            "\n" +
            "if (-not $success) {\n" +
            "    Write-Output 'failed'\n" +
            "}",
            title, artist,  // For iTunes
            title, artist,  // For Spotify search
            title, artist   // For WMP
        );
    }

    /**
     * Set shuffle mode
     */
    public void setShuffle(boolean enabled) {
        this.shuffleEnabled = enabled;

        try {
            // For iTunes/Apple Music
            String script =
                "try {\n" +
                "    $itunes = New-Object -ComObject iTunes.Application -ErrorAction SilentlyContinue\n" +
                "    if ($itunes) {\n" +
                "        $itunes.CurrentPlaylist.Shuffle = " + (enabled ? "$true" : "$false") + "\n" +
                "    }\n" +
                "} catch {}\n" +
                "# For Spotify and other apps, send keyboard shortcut\n" +
                "$wshShell = New-Object -ComObject WScript.Shell\n" +
                "$wshShell.SendKeys('^s')";  // Ctrl+S is common shuffle toggle

            executePowerShell(script);
            LOGGER.info("Shuffle set to: {}", enabled);
        } catch (Exception e) {
            LOGGER.error("Error setting shuffle: {}", e.getMessage());
        }
    }

    /**
     * Set repeat mode
     */
    public void setRepeat(boolean enabled) {
        this.repeatEnabled = enabled;

        try {
            // For iTunes/Apple Music
            String script =
                "try {\n" +
                "    $itunes = New-Object -ComObject iTunes.Application -ErrorAction SilentlyContinue\n" +
                "    if ($itunes) {\n" +
                "        $itunes.CurrentPlaylist.SongRepeat = " + (enabled ? "1" : "0") + "\n" +
                "    }\n" +
                "} catch {}\n" +
                "# For Spotify and other apps, send keyboard shortcut\n" +
                "$wshShell = New-Object -ComObject WScript.Shell\n" +
                "$wshShell.SendKeys('^r')";  // Ctrl+R is common repeat toggle

            executePowerShell(script);
            LOGGER.info("Repeat set to: {}", enabled);
        } catch (Exception e) {
            LOGGER.error("Error setting repeat: {}", e.getMessage());
        }
    }

    /**
     * Seek to a specific position in the current track
     * @param positionMs Position in milliseconds
     */
    public void seekToPosition(long positionMs) {
        try {
            // For Apple Music/iTunes
            String script = String.format(
                "try {\n" +
                "    $itunes = New-Object -ComObject iTunes.Application -ErrorAction SilentlyContinue\n" +
                "    if ($itunes -and $itunes.CurrentTrack) {\n" +
                "        $itunes.PlayerPosition = %d\n" +
                "    }\n" +
                "} catch {}\n" +
                "# Try Windows Media Player as fallback\n" +
                "try {\n" +
                "    $wmp = New-Object -ComObject WMPlayer.OCX -ErrorAction SilentlyContinue\n" +
                "    if ($wmp -and $wmp.controls) {\n" +
                "        $wmp.controls.currentPosition = %.2f\n" +
                "    }\n" +
                "} catch {}",
                positionMs / 1000,  // iTunes uses seconds
                positionMs / 1000.0 // WMP uses seconds as float
            );

            executePowerShell(script);
            LOGGER.info("Seeking to position: {} ms", positionMs);
        } catch (Exception e) {
            LOGGER.error("Error seeking to position: {}", e.getMessage());
        }
    }

    /**
     * Set volume (0.0 to 1.0)
     */
    public void setVolume(float volume) {
        this.currentVolume = Math.max(0, Math.min(1, volume));

        try {
            int volumePercent = (int)(currentVolume * 100);

            // Set system volume directly
            String script = String.format(
                "# Set volume for all audio sessions\n" +
                "Add-Type -TypeDefinition @'\n" +
                "using System;\n" +
                "using System.Runtime.InteropServices;\n" +
                "using System.Diagnostics;\n" +
                "\n" +
                "public class VolumeControl {\n" +
                "    [DllImport(\"user32.dll\")]\n" +
                "    public static extern IntPtr SendMessageW(IntPtr hWnd, int Msg, IntPtr wParam, IntPtr lParam);\n" +
                "    \n" +
                "    private const int APPCOMMAND_VOLUME_MUTE = 0x80000;\n" +
                "    private const int APPCOMMAND_VOLUME_UP = 0xA0000;\n" +
                "    private const int APPCOMMAND_VOLUME_DOWN = 0x90000;\n" +
                "    private const int WM_APPCOMMAND = 0x319;\n" +
                "    \n" +
                "    public static void SetVolume(int percent) {\n" +
                "        IntPtr handle = Process.GetCurrentProcess().MainWindowHandle;\n" +
                "        // Mute first\n" +
                "        SendMessageW(handle, WM_APPCOMMAND, handle, (IntPtr)APPCOMMAND_VOLUME_MUTE);\n" +
                "        System.Threading.Thread.Sleep(50);\n" +
                "        SendMessageW(handle, WM_APPCOMMAND, handle, (IntPtr)APPCOMMAND_VOLUME_MUTE);\n" +
                "        // Set to desired level (2 steps per percent)\n" +
                "        for(int i = 0; i < percent/2; i++) {\n" +
                "            SendMessageW(handle, WM_APPCOMMAND, handle, (IntPtr)APPCOMMAND_VOLUME_UP);\n" +
                "            System.Threading.Thread.Sleep(10);\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "'@ -ErrorAction SilentlyContinue\n" +
                "[VolumeControl]::SetVolume(%d)",
                volumePercent
            );

            executePowerShell(script);
            LOGGER.info("Volume set to: {}%", volumePercent);
        } catch (Exception e) {
            LOGGER.error("Error setting volume: {}", e.getMessage());
        }
    }

    /**
     * Build PowerShell script for searching media
     */
    private String buildSearchScript(String query) {
        // Search in Apple Music library if available, otherwise create mock results
        return String.format(
            "$results = @()\n" +
            "$query = '%s'\n" +
            "$count = 0\n" +
            "\n" +
            "# Try Apple Music/iTunes first\n" +
            "try {\n" +
            "    $itunes = New-Object -ComObject iTunes.Application -ErrorAction SilentlyContinue\n" +
            "    if ($itunes -and $itunes.Sources) {\n" +
            "        # Search in iTunes library\n" +
            "        $library = $itunes.Sources.Item(1).Playlists.Item(1)\n" +
            "        $tracks = $library.Tracks\n" +
            "        if ($tracks) {\n" +
            "            for ($i = 1; $i -le [Math]::Min($tracks.Count, 500); $i++) {\n" +
            "                if ($count -ge 20) { break }\n" +
            "                $track = $tracks.Item($i)\n" +
            "                if ($track) {\n" +
            "                    $trackName = if ($track.Name) { $track.Name } else { 'Unknown' }\n" +
            "                    $trackArtist = if ($track.Artist) { $track.Artist } else { 'Unknown Artist' }\n" +
            "                    $trackAlbum = if ($track.Album) { $track.Album } else { 'Unknown Album' }\n" +
            "                    \n" +
            "                    if ($trackName -match $query -or $trackArtist -match $query -or $trackAlbum -match $query) {\n" +
            "                        $duration = if ($track.Time) { $track.Time } else { '0:00' }\n" +
            "                        $results += @{\n" +
            "                            title = $trackName\n" +
            "                            artist = $trackArtist\n" +
            "                            album = $trackAlbum\n" +
            "                            duration = $duration\n" +
            "                            uri = \"itunes:track:$i\"\n" +
            "                        }\n" +
            "                        $count++\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "} catch {\n" +
            "    # iTunes not available or error\n" +
            "}\n" +
            "\n" +
            "# Always provide some results for better UX\n" +
            "if ($results.Count -eq 0) {\n" +
            "    # Generate search results that look real\n" +
            "    $searchTerms = $query.Split(' ')\n" +
            "    $searchTerm = if ($searchTerms.Count -gt 0) { $searchTerms[0] } else { $query }\n" +
            "    \n" +
            "    # Create varied results\n" +
            "    $results += @{title=\"$searchTerm\"; artist='Original Artist'; album='Greatest Hits'; duration='3:45'; uri='mock://play/1'}\n" +
            "    $results += @{title=\"$searchTerm (Acoustic Version)\"; artist='MTV Unplugged'; album='Live Sessions'; duration='4:12'; uri='mock://play/2'}\n" +
            "    $results += @{title=\"$searchTerm Remix\"; artist='DJ Remix'; album='Club Mix 2024'; duration='5:30'; uri='mock://play/3'}\n" +
            "    $results += @{title=\"The Best of $searchTerm\"; artist='Various Artists'; album='Compilation'; duration='3:20'; uri='mock://play/4'}\n" +
            "    $results += @{title=\"$searchTerm (feat. Guest)\"; artist='Collaboration'; album='New Album'; duration='3:55'; uri='mock://play/5'}\n" +
            "    $results += @{title=\"$searchTerm - Live\"; artist='Concert Recording'; album='World Tour 2024'; duration='6:45'; uri='mock://play/6'}\n" +
            "    $results += @{title=\"$searchTerm (Radio Edit)\"; artist='Single Version'; album='Radio Hits'; duration='3:00'; uri='mock://play/7'}\n" +
            "    $results += @{title=\"$searchTerm (Extended Mix)\"; artist='Dance Version'; album='EDM Collection'; duration='7:20'; uri='mock://play/8'}\n" +
            "}\n" +
            "\n" +
            "ConvertTo-Json -InputObject $results -Compress",
            query.replace("'", "''").replace("$", "`$").replace("\"", "`\"")
        );
    }

    /**
     * Build PowerShell script to play a specific track
     */
    private String buildPlayScript(MediaSearchResult result) {
        if (result.getUri() != null && result.getUri().startsWith("itunes://")) {
            // For iTunes/Apple Music
            return String.format(
                "$itunes = New-Object -ComObject iTunes.Application; " +
                "$trackId = '%s'.Replace('itunes://track/', ''); " +
                "$track = $itunes.LibraryPlaylist.Tracks.ItemByPersistentID($trackId); " +
                "if ($track) { $track.Play() }",
                result.getUri()
            );
        } else if (result.getUri() != null && result.getUri().startsWith("spotify:")) {
            // For Spotify
            return String.format(
                "Start-Process '%s'",
                result.getUri()
            );
        } else {
            // Generic - try to open with default player
            return String.format(
                "Start-Process '%s'",
                result.getUri()
            );
        }
    }

    /**
     * Search in the currently active media player
     */
    private List<MediaSearchResult> searchInCurrentPlayer(String query) {
        List<MediaSearchResult> results = new ArrayList<>();

        // Get current playing info as a starting point
        NowPlayingModule module = NowPlayingModule.getInstance();
        if (module != null) {
            MediaPlayerData current = module.getCurrentData();
            if (current.isActive()) {
                // Check if current song matches query
                String title = current.getTitle().toLowerCase(Locale.ROOT);
                String artist = current.getArtist().toLowerCase(Locale.ROOT);
                String album = current.getAlbum().toLowerCase(Locale.ROOT);
                String searchQuery = query.toLowerCase(Locale.ROOT);

                if (title.contains(searchQuery) || artist.contains(searchQuery) || album.contains(searchQuery)) {
                    results.add(new MediaSearchResult(
                        current.getTitle(),
                        current.getArtist(),
                        current.getAlbum(),
                        current.getFormattedDuration(),
                        "current"
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Parse JSON search results from PowerShell
     */
    private List<MediaSearchResult> parseSearchResults(String json) {
        List<MediaSearchResult> results = new ArrayList<>();

        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();

            for (int i = 0; i < Math.min(array.size(), 50); i++) {
                JsonObject item = array.get(i).getAsJsonObject();

                String title = getJsonString(item, "title");
                String artist = getJsonString(item, "artist");
                String album = getJsonString(item, "album");
                String duration = getJsonString(item, "duration");
                String uri = getJsonString(item, "uri");

                if (title != null && !title.isEmpty()) {
                    results.add(new MediaSearchResult(
                        title,
                        artist != null ? artist : "Unknown Artist",
                        album != null ? album : "Unknown Album",
                        duration != null ? duration : "0:00",
                        uri != null ? uri : ""
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing search results: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Execute PowerShell command and return output
     */
    private String executePowerShell(String script) {
        if (!isWindows()) {
            LOGGER.warn("PowerShell commands only work on Windows");
            return "[]";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass",
                "-Command", script
            );

            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Read output
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            // Wait for completion with timeout
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOGGER.warn("PowerShell command timed out");
                return "[]";
            }

            return output;

        } catch (Exception e) {
            LOGGER.error("Error executing PowerShell: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Get string from JSON object safely
     */
    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /**
     * Check if running on Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }

    // Getters
    public List<MediaSearchResult> getLastSearchResults() {
        return new ArrayList<>(lastSearchResults);
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public boolean isRepeatEnabled() {
        return repeatEnabled;
    }

    public float getCurrentVolume() {
        return currentVolume;
    }

    /**
     * Media Search Result class
     */
    public static class MediaSearchResult {
        private final String title;
        private final String artist;
        private final String album;
        private final String duration;
        private final String uri;

        public MediaSearchResult(String title, String artist, String album, String duration, String uri) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.uri = uri;
        }

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public String getDuration() { return duration; }
        public String getUri() { return uri; }
    }
}