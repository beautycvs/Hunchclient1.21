package dev.hunchclient.module.impl.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Media Statistics Manager
 *
 * Tracks listening statistics for media playback including:
 * - Play count per song
 * - Total listening time per song/artist
 * - Most played songs/artists
 * - Listening history with timestamps
 * - Daily/Weekly/Monthly statistics
 */
public class MediaStatisticsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-MediaStats");
    private static MediaStatisticsManager INSTANCE;

    // Statistics storage
    private final Map<String, SongStatistics> songStats = new ConcurrentHashMap<>();
    private final Map<String, ArtistStatistics> artistStats = new ConcurrentHashMap<>();
    private final List<PlaySession> playSessions = Collections.synchronizedList(new ArrayList<>());

    // Current tracking
    private PlaySession currentSession = null;
    private long lastUpdateTime = 0;
    private final long UPDATE_INTERVAL_MS = 5000; // Update every 5 seconds

    // File storage
    private static final String STATS_FILE = "config/hunchclient/media_statistics.json";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
        .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
        .setPrettyPrinting()
        .create();

    private MediaStatisticsManager() {
        loadStatistics();
        LOGGER.info("MediaStatisticsManager initialized");
    }

    public static MediaStatisticsManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MediaStatisticsManager();
        }
        return INSTANCE;
    }

    /**
     * Track a song play/update
     */
    public void trackSong(MediaPlayerData data) {
        if (data == null || !data.isActive()) {
            endCurrentSession();
            return;
        }

        String songKey = createSongKey(data);
        String artistKey = data.getArtist();

        // Check if this is a new song
        if (currentSession == null || !currentSession.songKey.equals(songKey)) {
            endCurrentSession();
            startNewSession(data);
        } else {
            // Update current session
            updateCurrentSession(data);
        }

        // Update statistics periodically
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            updateStatistics(songKey, artistKey);
            lastUpdateTime = now;
        }
    }

    private void startNewSession(MediaPlayerData data) {
        currentSession = new PlaySession();
        currentSession.songKey = createSongKey(data);
        currentSession.title = data.getTitle();
        currentSession.artist = data.getArtist();
        currentSession.album = data.getAlbum();
        currentSession.startTime = System.currentTimeMillis();
        currentSession.lastUpdateTime = System.currentTimeMillis();

        // Increment play count
        SongStatistics songStat = songStats.computeIfAbsent(currentSession.songKey, k -> new SongStatistics(data));
        songStat.playCount++;
        songStat.lastPlayed = LocalDateTime.now();

        ArtistStatistics artistStat = artistStats.computeIfAbsent(data.getArtist(), k -> new ArtistStatistics(data.getArtist()));
        artistStat.playCount++;

        playSessions.add(currentSession);

        // Keep only last 500 sessions
        if (playSessions.size() > 500) {
            playSessions.remove(0);
        }

        LOGGER.info("Started tracking: {} by {}", data.getTitle(), data.getArtist());
    }

    private void updateCurrentSession(MediaPlayerData data) {
        if (currentSession != null) {
            currentSession.lastUpdateTime = System.currentTimeMillis();

            // Update duration if available
            if (data.hasTimeline()) {
                currentSession.durationMs = data.getDurationMs();
                currentSession.progress = data.getProgress();
            }
        }
    }

    private void endCurrentSession() {
        if (currentSession != null) {
            currentSession.endTime = System.currentTimeMillis();
            long listenTime = currentSession.endTime - currentSession.startTime;

            // Update total listen time
            String songKey = currentSession.songKey;
            String artist = currentSession.artist;

            SongStatistics songStat = songStats.get(songKey);
            if (songStat != null) {
                songStat.totalListenTimeMs += listenTime;
            }

            ArtistStatistics artistStat = artistStats.get(artist);
            if (artistStat != null) {
                artistStat.totalListenTimeMs += listenTime;
            }

            saveStatistics();
            LOGGER.info("Ended session for: {} (listened for {}ms)", currentSession.title, listenTime);
            currentSession = null;
        }
    }

    private void updateStatistics(String songKey, String artistKey) {
        if (currentSession != null) {
            long listenTime = System.currentTimeMillis() - currentSession.lastUpdateTime;

            SongStatistics songStat = songStats.get(songKey);
            if (songStat != null && listenTime > 0 && listenTime < UPDATE_INTERVAL_MS * 2) {
                songStat.totalListenTimeMs += listenTime;
            }

            ArtistStatistics artistStat = artistStats.get(artistKey);
            if (artistStat != null && listenTime > 0 && listenTime < UPDATE_INTERVAL_MS * 2) {
                artistStat.totalListenTimeMs += listenTime;
            }

            currentSession.lastUpdateTime = System.currentTimeMillis();
        }
    }

    private String createSongKey(MediaPlayerData data) {
        return data.getTitle() + "|" + data.getArtist();
    }

    /**
     * Get top played songs
     */
    public List<SongStatistics> getTopSongs(int limit) {
        return songStats.values().stream()
            .sorted((a, b) -> Integer.compare(b.playCount, a.playCount))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get top played artists
     */
    public List<ArtistStatistics> getTopArtists(int limit) {
        return artistStats.values().stream()
            .sorted((a, b) -> Integer.compare(b.playCount, a.playCount))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get most listened songs by total time
     */
    public List<SongStatistics> getMostListenedSongs(int limit) {
        return songStats.values().stream()
            .sorted((a, b) -> Long.compare(b.totalListenTimeMs, a.totalListenTimeMs))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get most listened artists by total time
     */
    public List<ArtistStatistics> getMostListenedArtists(int limit) {
        return artistStats.values().stream()
            .sorted((a, b) -> Long.compare(b.totalListenTimeMs, a.totalListenTimeMs))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get recent play sessions
     */
    public List<PlaySession> getRecentSessions(int limit) {
        List<PlaySession> recent = new ArrayList<>(playSessions);
        Collections.reverse(recent);
        return recent.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Get total statistics
     */
    public TotalStatistics getTotalStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.totalSongs = songStats.size();
        total.totalArtists = artistStats.size();
        total.totalPlayCount = songStats.values().stream().mapToInt(s -> s.playCount).sum();
        total.totalListenTimeMs = songStats.values().stream().mapToLong(s -> s.totalListenTimeMs).sum();
        total.totalSessions = playSessions.size();

        // Calculate average session length
        if (!playSessions.isEmpty()) {
            long totalSessionTime = playSessions.stream()
                .filter(s -> s.endTime > 0)
                .mapToLong(s -> s.endTime - s.startTime)
                .sum();
            long completedSessions = playSessions.stream()
                .filter(s -> s.endTime > 0)
                .count();
            if (completedSessions > 0) {
                total.averageSessionLengthMs = totalSessionTime / completedSessions;
            }
        }

        return total;
    }

    /**
     * Get statistics for today
     */
    public DailyStatistics getTodayStatistics() {
        LocalDate today = LocalDate.now();
        return getDayStatistics(today);
    }

    /**
     * Get statistics for a specific day
     */
    public DailyStatistics getDayStatistics(LocalDate date) {
        DailyStatistics daily = new DailyStatistics();
        daily.date = date;

        long dayStart = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
        long dayEnd = date.plusDays(1).atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;

        List<PlaySession> daySessions = playSessions.stream()
            .filter(s -> s.startTime >= dayStart && s.startTime < dayEnd)
            .collect(Collectors.toList());

        daily.sessionCount = daySessions.size();
        daily.totalListenTimeMs = daySessions.stream()
            .mapToLong(s -> {
                long end = s.endTime > 0 ? s.endTime : System.currentTimeMillis();
                return Math.min(end, dayEnd) - Math.max(s.startTime, dayStart);
            })
            .sum();

        // Get unique songs and artists for the day
        Set<String> uniqueSongs = new HashSet<>();
        Set<String> uniqueArtists = new HashSet<>();
        for (PlaySession session : daySessions) {
            uniqueSongs.add(session.songKey);
            uniqueArtists.add(session.artist);
        }
        daily.uniqueSongs = uniqueSongs.size();
        daily.uniqueArtists = uniqueArtists.size();

        return daily;
    }

    /**
     * Format time duration for display
     */
    public static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Save statistics to file
     */
    private void saveStatistics() {
        try {
            File file = new File(STATS_FILE);
            file.getParentFile().mkdirs();

            JsonObject root = new JsonObject();
            root.add("songStats", gson.toJsonTree(songStats));
            root.add("artistStats", gson.toJsonTree(artistStats));
            root.add("playSessions", gson.toJsonTree(playSessions));

            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(root, writer);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to save statistics: {}", e.getMessage());
        }
    }

    /**
     * Load statistics from file
     */
    private void loadStatistics() {
        File file = new File(STATS_FILE);
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("songStats")) {
                Type songStatsType = new TypeToken<Map<String, SongStatistics>>(){}.getType();
                Map<String, SongStatistics> loaded = gson.fromJson(root.get("songStats"), songStatsType);
                if (loaded != null) {
                    songStats.putAll(loaded);
                }
            }

            if (root.has("artistStats")) {
                Type artistStatsType = new TypeToken<Map<String, ArtistStatistics>>(){}.getType();
                Map<String, ArtistStatistics> loaded = gson.fromJson(root.get("artistStats"), artistStatsType);
                if (loaded != null) {
                    artistStats.putAll(loaded);
                }
            }

            if (root.has("playSessions")) {
                Type sessionType = new TypeToken<List<PlaySession>>(){}.getType();
                List<PlaySession> loaded = gson.fromJson(root.get("playSessions"), sessionType);
                if (loaded != null) {
                    playSessions.addAll(loaded);
                }
            }

            LOGGER.info("Loaded statistics: {} songs, {} artists, {} sessions",
                       songStats.size(), artistStats.size(), playSessions.size());

        } catch (Exception e) {
            LOGGER.error("Failed to load statistics: {}", e.getMessage());
        }
    }

    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(DATE_TIME_FORMATTER));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }

            if (json.isJsonPrimitive()) {
                return LocalDateTime.parse(json.getAsString(), DATE_TIME_FORMATTER);
            }

            if (json.isJsonObject()) {
                JsonObject object = json.getAsJsonObject();
                if (object.has("date") && object.has("time")) {
                    LocalDate date = LegacyJavaTimeParser.parseDate(object.getAsJsonObject("date"));
                    LocalTime time = LegacyJavaTimeParser.parseTime(object.getAsJsonObject("time"));
                    return LocalDateTime.of(date, time);
                }
            }

            throw new JsonParseException("Unable to deserialize LocalDateTime: " + json);
        }
    }

    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(DATE_FORMATTER));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }

            if (json.isJsonPrimitive()) {
                return LocalDate.parse(json.getAsString(), DATE_FORMATTER);
            }

            if (json.isJsonObject()) {
                return LegacyJavaTimeParser.parseDate(json.getAsJsonObject());
            }

            throw new JsonParseException("Unable to deserialize LocalDate: " + json);
        }
    }

    private static final class LegacyJavaTimeParser {
        private static LocalDate parseDate(JsonObject dateObj) {
            if (dateObj == null) {
                throw new JsonParseException("Legacy LocalDate representation missing date object");
            }

            int year = getRequiredInt(dateObj, "year");
            int month = extractMonth(dateObj);
            int day = getRequiredInt(dateObj, "dayOfMonth", "day");

            return LocalDate.of(year, month, day);
        }

        private static LocalTime parseTime(JsonObject timeObj) {
            if (timeObj == null) {
                throw new JsonParseException("Legacy LocalTime representation missing time object");
            }

            int hour = getRequiredInt(timeObj, "hour");
            int minute = getOptionalInt(timeObj, 0, "minute");
            int second = getOptionalInt(timeObj, 0, "second");
            int nano = getOptionalInt(timeObj, 0, "nano");

            return LocalTime.of(hour, minute, second, nano);
        }

        private static int extractMonth(JsonObject obj) {
            Integer direct = getOptionalIntNullable(obj, "monthValue", "month", "monthOfYear");
            if (direct != null) {
                return direct;
            }

            if (obj.has("month")) {
                JsonElement monthElement = obj.get("month");
                if (monthElement.isJsonPrimitive() && monthElement.getAsJsonPrimitive().isString()) {
                    String monthName = monthElement.getAsString();
                    try {
                        return Month.valueOf(monthName.toUpperCase(Locale.ROOT)).getValue();
                    } catch (IllegalArgumentException ex) {
                        throw new JsonParseException("Unknown month name: " + monthName, ex);
                    }
                }
            }

            throw new JsonParseException("Unable to determine month from legacy LocalDate: " + obj);
        }

        private static int getRequiredInt(JsonObject obj, String... keys) {
            Integer value = getOptionalIntNullable(obj, keys);
            if (value == null) {
                throw new JsonParseException("Missing expected numeric field " + Arrays.toString(keys) + " in " + obj);
            }
            return value;
        }

        private static int getOptionalInt(JsonObject obj, int defaultValue, String... keys) {
            Integer value = getOptionalIntNullable(obj, keys);
            return value != null ? value : defaultValue;
        }

        private static Integer getOptionalIntNullable(JsonObject obj, String... keys) {
            for (String key : keys) {
                if (!obj.has(key)) {
                    continue;
                }

                JsonElement element = obj.get(key);
                if (element == null || element.isJsonNull()) {
                    continue;
                }

                if (!element.isJsonPrimitive()) {
                    continue;
                }

                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    return primitive.getAsInt();
                }

                if (primitive.isString()) {
                    String raw = primitive.getAsString();
                    if (raw.isEmpty()) {
                        continue;
                    }
                    try {
                        return Integer.parseInt(raw);
                    } catch (NumberFormatException ignored) {
                        // Intentionally continue to try other keys or fallback
                    }
                }
            }
            return null;
        }
    }

    /**
     * Song statistics
     */
    public static class SongStatistics {
        public String title;
        public String artist;
        public String album;
        public int playCount = 0;
        public long totalListenTimeMs = 0;
        public LocalDateTime firstPlayed;
        public LocalDateTime lastPlayed;

        public SongStatistics() {}

        public SongStatistics(MediaPlayerData data) {
            this.title = data.getTitle();
            this.artist = data.getArtist();
            this.album = data.getAlbum();
            this.firstPlayed = LocalDateTime.now();
            this.lastPlayed = LocalDateTime.now();
        }

        public String getFormattedListenTime() {
            return formatDuration(totalListenTimeMs);
        }
    }

    /**
     * Artist statistics
     */
    public static class ArtistStatistics {
        public String artist;
        public int playCount = 0;
        public long totalListenTimeMs = 0;
        public LocalDateTime firstPlayed;
        public LocalDateTime lastPlayed;

        public ArtistStatistics() {}

        public ArtistStatistics(String artist) {
            this.artist = artist;
            this.firstPlayed = LocalDateTime.now();
            this.lastPlayed = LocalDateTime.now();
        }

        public String getFormattedListenTime() {
            return formatDuration(totalListenTimeMs);
        }
    }

    /**
     * Play session tracking
     */
    public static class PlaySession {
        public String songKey;
        public String title;
        public String artist;
        public String album;
        public long startTime;
        public long endTime;
        public long lastUpdateTime;
        public long durationMs;
        public float progress;

        public String getFormattedStartTime() {
            return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime),
                java.time.ZoneId.systemDefault()
            ).format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        public String getSessionLength() {
            long length = endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
            return formatDuration(length);
        }
    }

    /**
     * Total statistics summary
     */
    public static class TotalStatistics {
        public int totalSongs;
        public int totalArtists;
        public int totalPlayCount;
        public long totalListenTimeMs;
        public int totalSessions;
        public long averageSessionLengthMs;

        public String getFormattedTotalTime() {
            return formatDuration(totalListenTimeMs);
        }

        public String getFormattedAverageSession() {
            return formatDuration(averageSessionLengthMs);
        }
    }

    /**
     * Daily statistics
     */
    public static class DailyStatistics {
        public LocalDate date;
        public int sessionCount;
        public long totalListenTimeMs;
        public int uniqueSongs;
        public int uniqueArtists;

        public String getFormattedListenTime() {
            return formatDuration(totalListenTimeMs);
        }
    }
}
