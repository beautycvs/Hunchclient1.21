package dev.hunchclient.module.impl.misc;

/**
 * Data class containing current media playback information from SMTC
 */
public class MediaPlayerData {
    private String title = "";
    private String artist = "";
    private String album = "";
    private PlaybackStatus status = PlaybackStatus.STOPPED;

    // Timeline data (optional, not all players provide this)
    private long positionMs = 0;
    private long durationMs = 0;
    private boolean hasTimeline = false;

    // Timestamp when data was last updated (for smooth interpolation)
    private long lastUpdateTime = 0;

    // Whether any media session is active
    private boolean isActive = false;

    // Album art / thumbnail
    private String thumbnailUrl = null;
    private boolean hasThumbnail = false;

    public enum PlaybackStatus {
        PLAYING,
        PAUSED,
        STOPPED,
        UNKNOWN
    }

    public MediaPlayerData() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // Getters
    public String getTitle() {
        return title != null ? title : "";
    }

    public String getArtist() {
        return artist != null ? artist : "";
    }

    public String getAlbum() {
        return album != null ? album : "";
    }

    public PlaybackStatus getStatus() {
        return status != null ? status : PlaybackStatus.UNKNOWN;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean hasTimeline() {
        return hasTimeline;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public boolean isActive() {
        return isActive;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    /**
     * Get thumbnail path (alias for getThumbnailUrl since SMTC returns file paths)
     */
    public String getThumbnailPath() {
        return thumbnailUrl;
    }

    public boolean hasThumbnail() {
        return hasThumbnail;
    }

    // Setters
    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public void setStatus(PlaybackStatus status) {
        this.status = status;
    }

    public void setPositionMs(long positionMs) {
        this.positionMs = positionMs;
        this.hasTimeline = true;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        this.hasTimeline = true;
    }

    public void setHasTimeline(boolean hasTimeline) {
        this.hasTimeline = hasTimeline;
    }

    public void updateTimestamp() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        this.hasThumbnail = thumbnailUrl != null && !thumbnailUrl.isEmpty();
    }

    /**
     * Get current interpolated position (smooth animation)
     * Calculates position based on last update time if playing
     */
    public long getInterpolatedPositionMs() {
        if (!hasTimeline || status != PlaybackStatus.PLAYING) {
            return positionMs;
        }

        long elapsed = System.currentTimeMillis() - lastUpdateTime;
        long interpolated = positionMs + elapsed;

        // Clamp to duration
        return Math.min(interpolated, durationMs);
    }

    /**
     * Get progress as percentage (0.0 to 1.0)
     */
    public float getProgress() {
        if (!hasTimeline || durationMs == 0) {
            return 0.0f;
        }
        return Math.min(1.0f, (float) getInterpolatedPositionMs() / durationMs);
    }

    /**
     * Format milliseconds to MM:SS
     */
    public static String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Get formatted current position
     */
    public String getFormattedPosition() {
        return formatTime(getInterpolatedPositionMs());
    }

    /**
     * Get formatted duration
     */
    public String getFormattedDuration() {
        return formatTime(durationMs);
    }

    @Override
    public String toString() {
        return String.format("MediaPlayerData{title='%s', artist='%s', status=%s, position=%s/%s}",
                title, artist, status, getFormattedPosition(), getFormattedDuration());
    }
}
