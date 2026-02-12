package dev.hunchclient.util;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Raytrace utilities - Port of CGA's BlockUtils.collisionRayTrace
 * Used for custom AABB raycasting in SecretAura
 */
public class RaytraceUtils {

    /**
     * Collision raytrace from CGA's BlockUtils.collisionRayTrace
     * Creates a ray from start to end and checks if it hits the AABB at the block position
     *
     * @param blockPos The block position
     * @param aabb The custom AABB (bounding box) for the block
     * @param start Start position (usually player eye pos)
     * @param end End position (usually block center)
     * @return BlockHitResult if ray hits the AABB, null otherwise
     */
    public static BlockHitResult collisionRayTrace(BlockPos blockPos, AABB aabb, Vec3 start, Vec3 end) {
        // Offset AABB to world coordinates
        AABB worldAABB = aabb.move(blockPos);

        // Raytrace against the AABB
        Optional<Vec3> hitVec = worldAABB.clip(start, end);
        if (hitVec.isEmpty()) {
            return null;
        }

        Vec3 hit = hitVec.get();

        // Determine which side was hit
        Direction side = getSideHit(worldAABB, hit, start);

        // Create BlockHitResult
        return new BlockHitResult(hit, side, blockPos, false);
    }

    /**
     * Determine which side of the AABB was hit by the ray
     * This mimics vanilla Minecraft's side detection logic
     */
    private static Direction getSideHit(AABB aabb, Vec3 hitVec, Vec3 startVec) {
        // Calculate which face of the box is closest to the hit point
        double epsilon = 0.0001;

        // Check each face
        if (Math.abs(hitVec.x - aabb.minX) < epsilon) return Direction.WEST;
        if (Math.abs(hitVec.x - aabb.maxX) < epsilon) return Direction.EAST;
        if (Math.abs(hitVec.y - aabb.minY) < epsilon) return Direction.DOWN;
        if (Math.abs(hitVec.y - aabb.maxY) < epsilon) return Direction.UP;
        if (Math.abs(hitVec.z - aabb.minZ) < epsilon) return Direction.NORTH;
        if (Math.abs(hitVec.z - aabb.maxZ) < epsilon) return Direction.SOUTH;

        // Fallback: determine by direction from start to hit
        Vec3 direction = hitVec.subtract(startVec).normalize();
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);

        if (absX > absY && absX > absZ) {
            return direction.x > 0 ? Direction.EAST : Direction.WEST;
        } else if (absY > absZ) {
            return direction.y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return direction.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
