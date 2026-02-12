package dev.hunchclient.module.impl.sbd.cache;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for party member data
 * Similar to Data.js in the original ChatTriggers module
 */
public class PartyMemberCache {
    private static final Map<String, PartyMember> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Get or create a cached party member
     */
    public static PartyMember get(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }

        String key = username.toLowerCase(Locale.ROOT);
        PartyMember member = CACHE.get(key);

        if (member == null) {
            member = new PartyMember(username);
            CACHE.put(key, member);
        }

        // Refresh if data is old
        if (!member.isDataLoaded() && !member.isLoading()) {
            member.init();
        } else if (member.isDataLoaded() && isExpired(member)) {
            // Refresh in background
            member.init();
        }

        return member;
    }

    /**
     * Check if a member is in cache
     */
    public static boolean has(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        String key = username.toLowerCase(Locale.ROOT);
        return CACHE.containsKey(key);
    }

    /**
     * Remove a member from cache
     */
    public static void remove(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        String key = username.toLowerCase(Locale.ROOT);
        CACHE.remove(key);
    }

    /**
     * Clear all cached data
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Clean up expired entries
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(entry -> {
            PartyMember member = entry.getValue();
            return member.isDataLoaded() && (now - member.getLastUpdate()) > CACHE_EXPIRY_MS;
        });
    }

    private static boolean isExpired(PartyMember member) {
        long now = System.currentTimeMillis();
        return (now - member.getLastUpdate()) > CACHE_EXPIRY_MS;
    }

    public static int size() {
        return CACHE.size();
    }
}
