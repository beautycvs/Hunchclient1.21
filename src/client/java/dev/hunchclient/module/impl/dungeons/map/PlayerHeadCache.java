package dev.hunchclient.module.impl.dungeons.map;

import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Cache for player head textures loaded from Crafatar API
 * Provides 2D player face textures for dungeon map markers
 */
public class PlayerHeadCache {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "PlayerHead-Loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object CACHE_LOCK = new Object();
    private static final int MAX_CACHE_SIZE = 256;
    private static final int MAX_FAILED_CACHE_SIZE = 512;
    private static final int MAX_PENDING_LOADS = 128;
    private static final long FAILED_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long LOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

    // Cache: UUID -> Identifier of registered texture
    private static final Map<UUID, ResourceLocation> headTextures = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true);
    // Track which UUIDs are currently being loaded
    private static final Map<UUID, Long> loading = new ConcurrentHashMap<>();
    // Track failed loads to avoid retrying
    private static final Map<UUID, Long> failed = new ConcurrentHashMap<>();

    // Fallback texture (generic player head)
    private static final ResourceLocation FALLBACK_HEAD = ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/player_head.png");

    /**
     * Get the head texture Identifier for a player
     * Returns fallback if not yet loaded, triggers async load
     */
    public static ResourceLocation getHead(UUID uuid) {
        if (uuid == null) return FALLBACK_HEAD;

        ResourceLocation cached = getCachedHead(uuid);
        if (cached != null) return cached;

        // Start loading if not already
        if (!isLoading(uuid) && !isFailed(uuid) && canQueueLoad()) {
            loadHeadAsync(uuid);
        }

        return FALLBACK_HEAD;
    }

    /**
     * Get head texture for a player by name (needs UUID lookup)
     * For self, use getHeadForSelf() instead
     */
    public static ResourceLocation getHead(String playerName, UUID uuid) {
        if (uuid != null) {
            return getHead(uuid);
        }
        // No UUID available, use fallback
        return FALLBACK_HEAD;
    }

    /**
     * Get head texture for the local player
     */
    public static ResourceLocation getHeadForSelf() {
        if (mc.player == null) return FALLBACK_HEAD;
        UUID uuid = mc.player.getUUID();
        return getHead(uuid);
    }

    /**
     * Async load player head from Crafatar
     */
    private static void loadHeadAsync(UUID uuid) {
        if (!markLoading(uuid)) {
            return;
        }

        executor.submit(() -> {
            try {
                // Crafatar API - 2D face with overlay (helmet layer)
                String url = "https://crafatar.com/avatars/" + uuid.toString() + "?size=16&overlay";

                URI uri = new URI(url);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "HunchClient/1.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) {
                            // Convert to NativeImage and register
                            registerTexture(uuid, img);
                        } else {
                            markFailed(uuid);
                        }
                    }
                } else {
                    System.err.println("[PlayerHeadCache] Failed to load head for " + uuid + ": HTTP " + responseCode);
                    markFailed(uuid);
                }

                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[PlayerHeadCache] Error loading head for " + uuid + ": " + e.getMessage());
                markFailed(uuid);
            } finally {
                loading.remove(uuid);
            }
        });
    }

    /**
     * Register a BufferedImage as a Minecraft texture
     */
    private static void registerTexture(UUID uuid, BufferedImage img) {
        try {
            // Convert BufferedImage to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] pngBytes = baos.toByteArray();

            // Schedule on render thread
            mc.execute(() -> {
                try {
                    // Create NativeImage from PNG bytes
                    NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));

                    // Create and register texture (1.21+ requires Supplier<String> for debug name)
                    String uuidStr = uuid.toString().replace("-", "");
                    DynamicTexture texture = new DynamicTexture(() -> "PlayerHead_" + uuidStr, nativeImage);
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("hunchclient", "playerhead/" + uuidStr);

                    mc.getTextureManager().register(id, texture);
                    cacheHead(uuid, id);

                    System.out.println("[PlayerHeadCache] Loaded head for " + uuid);
                } catch (Exception e) {
                    System.err.println("[PlayerHeadCache] Error registering texture: " + e.getMessage());
                    markFailed(uuid);
                }
            });
        } catch (Exception e) {
            System.err.println("[PlayerHeadCache] Error converting image: " + e.getMessage());
            markFailed(uuid);
        }
    }

    /**
     * Clear the cache (call on world leave)
     */
    public static void clear() {
        // Unregister textures
        ArrayList<ResourceLocation> toRelease = new ArrayList<>();
        synchronized (CACHE_LOCK) {
            toRelease.addAll(headTextures.values());
            headTextures.clear();
        }
        mc.execute(() -> {
            for (ResourceLocation id : toRelease) {
                try {
                    mc.getTextureManager().release(id);
                } catch (Exception ignored) {}
            }
        });
        loading.clear();
        failed.clear();
    }

    /**
     * Check if a head is loaded for the given UUID
     */
    public static boolean isLoaded(UUID uuid) {
        return getCachedHead(uuid) != null;
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ResourceLocation getCachedHead(UUID uuid) {
        synchronized (CACHE_LOCK) {
            return headTextures.get(uuid);
        }
    }

    private static void cacheHead(UUID uuid, ResourceLocation id) {
        ResourceLocation evicted = null;
        synchronized (CACHE_LOCK) {
            headTextures.put(uuid, id);
            if (headTextures.size() > MAX_CACHE_SIZE) {
                var iterator = headTextures.entrySet().iterator();
                if (iterator.hasNext()) {
                    Map.Entry<UUID, ResourceLocation> eldest = iterator.next();
                    evicted = eldest.getValue();
                    iterator.remove();
                }
            }
        }
        if (evicted != null) {
            ResourceLocation toRelease = evicted;
            mc.execute(() -> {
                try {
                    mc.getTextureManager().release(toRelease);
                } catch (Exception ignored) {}
            });
        }
    }

    private static boolean isLoading(UUID uuid) {
        Long started = loading.get(uuid);
        if (started == null) {
            return false;
        }
        if (System.currentTimeMillis() - started > LOAD_TIMEOUT_MS) {
            loading.remove(uuid);
            return false;
        }
        return true;
    }

    private static boolean isFailed(UUID uuid) {
        Long failedAt = failed.get(uuid);
        if (failedAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - failedAt > FAILED_TTL_MS) {
            failed.remove(uuid);
            return false;
        }
        return true;
    }

    private static void markFailed(UUID uuid) {
        failed.put(uuid, System.currentTimeMillis());
        pruneFailed();
    }

    private static void pruneFailed() {
        long now = System.currentTimeMillis();
        failed.entrySet().removeIf(entry -> now - entry.getValue() > FAILED_TTL_MS);
        int overflow = failed.size() - MAX_FAILED_CACHE_SIZE;
        if (overflow <= 0) {
            return;
        }
        int removed = 0;
        var iterator = failed.keySet().iterator();
        while (iterator.hasNext() && removed < overflow) {
            iterator.next();
            iterator.remove();
            removed++;
        }
    }

    private static boolean canQueueLoad() {
        cleanupStaleLoads();
        return loading.size() < MAX_PENDING_LOADS;
    }

    private static boolean markLoading(UUID uuid) {
        cleanupStaleLoads();
        if (loading.size() >= MAX_PENDING_LOADS) {
            return false;
        }
        loading.put(uuid, System.currentTimeMillis());
        return true;
    }

    private static void cleanupStaleLoads() {
        long now = System.currentTimeMillis();
        loading.entrySet().removeIf(entry -> now - entry.getValue() > LOAD_TIMEOUT_MS);
    }
}
