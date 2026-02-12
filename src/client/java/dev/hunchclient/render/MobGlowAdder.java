package dev.hunchclient.render;

import net.minecraft.world.entity.Entity;

/**
 * Interface for modules that want to add custom glow effects to entities.
 * Based on Skyblocker's MobGlow system.
 */
public interface MobGlowAdder {

    /**
     * Checks if this glow adder is currently enabled
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Computes the glow color for the given entity
     * @param entity The entity to compute the glow color for
     * @return The glow color in RGB format (0xRRGGBB), or MobGlow.NO_GLOW if no glow
     */
    int computeColour(Entity entity);
}
