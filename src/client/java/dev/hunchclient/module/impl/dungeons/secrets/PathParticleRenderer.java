/*
* Secret Routes Mod - Secret Route Waypoints for Hypixel Skyblock Dungeons
 * Copyright 2025 yourboykyle & R-aMcC
 *
 * <DO NOT REMOVE THIS COPYRIGHT NOTICE>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.hunchclient.module.impl.dungeons.secrets;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Renders navigation paths using 3D particles in the world
 * Like a GPS/Navi system with colored particle trails
 */
public class PathParticleRenderer {

    private static final double PARTICLE_SPACING = 0.3; // Distance between particles
    private static final int PARTICLES_PER_SEGMENT = 3; // Particles per path segment for density

    /**
     * Render a path using colored dust particles
     */
    public static void renderPathParticles(List<Vec3> path, float[] color, ParticleRenderMode mode) {
        if (path == null || path.size() < 2) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 from = path.get(i);
            Vec3 to = path.get(i + 1);

            renderSegmentParticles(world, from, to, color, mode);
        }
    }

    /**
     * Render particles for a single path segment
     */
    private static void renderSegmentParticles(ClientLevel world, Vec3 from, Vec3 to, float[] color, ParticleRenderMode mode) {
        Vec3 direction = to.subtract(from);
        double distance = direction.length();
        Vec3 step = direction.normalize().scale(PARTICLE_SPACING);

        int particleCount = (int) Math.ceil(distance / PARTICLE_SPACING);

        for (int i = 0; i <= particleCount; i++) {
            Vec3 pos = from.add(step.scale(i));

            // Spawn particles based on mode
            switch (mode) {
                case DUST -> spawnDustParticle(world, pos, color);
                case FLAME -> spawnFlameParticle(world, pos);
                case PORTAL -> spawnPortalParticle(world, pos);
                case ENCHANT -> spawnEnchantParticle(world, pos);
                case END_ROD -> spawnEndRodParticle(world, pos);
                case GLOW -> spawnGlowParticle(world, pos);
            }
        }
    }

    /**
     * Spawn a colored particle (uses glow particles for visibility)
     * TODO: Implement proper DustParticleEffect for RGB customization in future
     */
    private static void spawnDustParticle(ClientLevel world, Vec3 pos, float[] color) {
        // For now, use glow particles which are highly visible
        // In the future, we can add proper RGB dust particles
        world.addParticle(ParticleTypes.GLOW, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
    }

    /**
     * Spawn flame particle
     */
    private static void spawnFlameParticle(ClientLevel world, Vec3 pos) {
        world.addParticle(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
    }

    /**
     * Spawn portal particle (purple effect)
     */
    private static void spawnPortalParticle(ClientLevel world, Vec3 pos) {
        world.addParticle(ParticleTypes.PORTAL, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
    }

    /**
     * Spawn enchantment table particle (blue glyph effect)
     */
    private static void spawnEnchantParticle(ClientLevel world, Vec3 pos) {
        world.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
    }

    /**
     * Spawn end rod particle (white beam)
     */
    private static void spawnEndRodParticle(ClientLevel world, Vec3 pos) {
        world.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
    }

    /**
     * Spawn glow particle (colored glow squid effect)
     */
    private static void spawnGlowParticle(ClientLevel world, Vec3 pos) {
        world.addParticle(ParticleTypes.GLOW, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
    }

    /**
     * Particle rendering modes
     */
    public enum ParticleRenderMode {
        DUST,       // Colored dust (RGB customizable)
        FLAME,      // Fire particles
        PORTAL,     // Purple portal particles
        ENCHANT,    // Blue enchantment glyphs
        END_ROD,    // White end rod beam
        GLOW        // Glow squid particles
    }
}
