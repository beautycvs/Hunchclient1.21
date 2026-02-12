package dev.hunchclient.module.impl.sbd.cache;

import dev.hunchclient.module.impl.sbd.api.HypixelApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a party member with cached Hypixel stats
 */
public class PartyMember {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyMember.class);

    private final String username;
    private String uuid;
    private HypixelApiClient.PlayerStats stats;
    private boolean dataLoaded = false;
    private boolean loading = false;
    private long lastUpdate = 0;
    private CompletableFuture<Void> loadingFuture = null;

    public PartyMember(String username) {
        this.username = username;
    }

    /**
     * Initialize by fetching UUID and stats
     * Returns a future that completes when data is loaded
     */
    public CompletableFuture<Void> init() {
        // If already loaded, return immediately
        if (dataLoaded) {
            return CompletableFuture.completedFuture(null);
        }

        // If loading is in progress, return the existing future
        if (loading && loadingFuture != null) {
            return loadingFuture;
        }

        loading = true;
        loadingFuture = HypixelApiClient.fetchUUID(username)
            .thenCompose(fetchedUuid -> {
                if (fetchedUuid == null || fetchedUuid.isEmpty()) {
                    loading = false;
                    dataLoaded = true; // Mark as loaded so we don't retry
                    return CompletableFuture.completedFuture(null);
                }

                this.uuid = fetchedUuid;
                return HypixelApiClient.fetchStats(uuid);
            })
            .thenAccept(fetchedStats -> {
                if (fetchedStats != null) {
                    this.stats = fetchedStats;
                    this.dataLoaded = true;
                    this.lastUpdate = System.currentTimeMillis();
                } else {
                    dataLoaded = true; // Mark as loaded so we don't retry
                }
                loading = false;
            })
            .exceptionally(throwable -> {
                LOGGER.error("Error loading data for {}: {}", username, throwable.getMessage());
                loading = false;
                dataLoaded = true; // Mark as loaded so we don't retry
                return null;
            });

        return loadingFuture;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public HypixelApiClient.PlayerStats getStats() {
        return stats;
    }

    public boolean isDataLoaded() {
        return dataLoaded;
    }

    public boolean isLoading() {
        return loading;
    }

    public int getCataLevel() {
        return stats != null ? stats.cataLevel : 0;
    }

    public int getTotalSecrets() {
        return stats != null ? stats.totalSecrets : 0;
    }

    public double getSecretAverage() {
        return stats != null ? stats.getSecretAverage() : 0.0;
    }

    public long getPBForFloor(String floor) {
        if (stats == null) {
            return -1;
        }

        return switch (floor) {
            case "F7" -> stats.pbF7;
            case "M4" -> stats.pbM4;
            case "M5" -> stats.pbM5;
            case "M6" -> stats.pbM6;
            case "M7" -> stats.pbM7;
            default -> -1;
        };
    }

    public String getPBString(String floor) {
        if (stats == null) {
            return "?";
        }
        long pb = getPBForFloor(floor);
        return stats.getPBString(pb);
    }

    public String getInfoString(String floor) {
        if (!dataLoaded) {
            return username + " §8[§eLoading...§8]";
        }
        if (stats == null) {
            return username + " §8[§cNo Data§8]";
        }

        return String.format("%s §8|§6 %d §8|§a %d §8|§b %.1f §8|§9 %s §8(§e%s§8)",
            username,
            stats.cataLevel,
            stats.totalSecrets,
            stats.getSecretAverage(),
            getPBString(floor),
            floor
        );
    }

    public long getLastUpdate() {
        return lastUpdate;
    }
}
