package dev.hunchclient.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/**
 * Central system for managing custom entity glow effects.
 * Based on Skyblocker's MobGlow system.
 * Updated for 1.21.9+ RenderState system.
 */
public class MobGlow {
    public static final int NO_GLOW = EntityRenderState.NO_OUTLINE;

    /**
     * Attached to EntityRenderStates to indicate that they have the custom glow applied.
     */
    public static final RenderStateDataKey<Boolean> ENTITY_HAS_CUSTOM_GLOW = RenderStateDataKey.create(() -> "HunchClient entity has custom glow");

    /**
     * Attached to WorldRenderStates to indicate that the custom glow is being used this frame.
     */
    public static final RenderStateDataKey<Boolean> FRAME_USES_CUSTOM_GLOW = RenderStateDataKey.create(() -> "HunchClient frame uses custom glow");

    private static final List<MobGlowAdder> ADDERS = new ArrayList<>();

    /**
     * Cache for mob glow. Absence means the entity does not have custom glow.
     * If an entity is in the cache, it must have custom glow.
     */
    private static final Object2IntMap<Entity> CACHE = new Object2IntOpenHashMap<>();
    private static long refreshDelayMs = 0;
    private static long lastClearTime = 0;

    /**
     * Initialize the MobGlow system
     */
    public static void init() {
        ClientTickEvents.END_WORLD_TICK.register(world -> tickCache());
    }

    private static void tickCache() {
        if (refreshDelayMs <= 0) {
            clearCache();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastClearTime >= refreshDelayMs) {
            clearCache();
        }
    }

    public static void setRefreshDelayMs(long delayMs) {
        refreshDelayMs = Math.max(0, delayMs);
        // Ensure the timer resets so the new delay takes effect immediately
        lastClearTime = System.currentTimeMillis();
    }

    public static long getRefreshDelayMs() {
        return refreshDelayMs;
    }

    /**
     * Check if at least one mob has custom glow
     * @return true if cache is not empty
     */
    public static boolean atLeastOneMobHasCustomGlow() {
        return !CACHE.isEmpty();
    }

    /**
     * Register a glow adder
     * @param adder The glow adder to register
     */
    public static void registerGlowAdder(MobGlowAdder adder) {
        ADDERS.add(adder);
    }

    /**
     * Check if entity has custom glow and compute if necessary
     * @param entity The entity to check
     * @return true if entity has custom glow
     */
    public static boolean hasOrComputeMobGlow(Entity entity) {
        int color = computeMobGlow(entity);
        if (color != NO_GLOW) {
            CACHE.put(entity, color);
            return true;
        }
        return false;
    }

    /**
     * Get the glow color for an entity
     * @param entity The entity
     * @return The glow color, or 0 if not in cache
     */
    public static int getMobGlow(Entity entity) {
        return CACHE.getInt(entity);
    }

    /**
     * Get the glow color for an entity or default
     * @param entity The entity
     * @param defaultColor The default color
     * @return The glow color or default
     */
    public static int getMobGlowOrDefault(Entity entity, int defaultColor) {
        return CACHE.getOrDefault(entity, defaultColor);
    }

    /**
     * Clear the glow cache
     */
    public static void clearCache() {
        CACHE.clear();
        lastClearTime = System.currentTimeMillis();
    }

    /**
     * Computes the glow color for the given entity.
     * Only non-zero colors are valid.
     * @param entity The entity
     * @return The glow color in RGB format
     */
    private static int computeMobGlow(Entity entity) {
        for (MobGlowAdder adder : ADDERS) {
            if (adder.isEnabled()) {
                int glowColour = adder.computeColour(entity);

                if (glowColour != NO_GLOW) return glowColour;
            }
        }

        return NO_GLOW;
    }

    /**
     * Returns name of entity by finding closest armor stand and getting name of that
     * @param entity the entity to check
     * @return the name string of the entity's label
     */
    public static String getArmorStandName(Entity entity) {
        List<ArmorStand> armorStands = getArmorStands(entity);
        if (armorStands.isEmpty()) {
            return "";
        }
        return armorStands.get(0).getName().getString();
    }

    /**
     * Get armor stands near an entity
     * @param entity The entity
     * @return List of armor stands
     */
    public static List<ArmorStand> getArmorStands(Entity entity) {
        return getArmorStands(entity.level(), entity.getBoundingBox());
    }

    /**
     * Get armor stands in a box
     * @param world The world
     * @param box The bounding box
     * @return List of armor stands
     */
    public static List<ArmorStand> getArmorStands(Level world, AABB box) {
        return world.getEntitiesOfClass(ArmorStand.class, box.inflate(0, 2, 0), EntitySelector.ENTITY_NOT_BEING_RIDDEN);
    }
}
