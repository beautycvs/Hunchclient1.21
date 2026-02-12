package dev.hunchclient.module.impl.dungeons.secrets;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A* Pathfinding for dungeon navigation
 * Creates intelligent paths that avoid walls and obstacles
 */
public class DungeonPathfinder {

    private static final int MAX_PATHFIND_DISTANCE = 120;
    private static final int MAX_ITERATIONS = 5000;

    // Neighbor offsets for 3D pathfinding (26 directions)
    private static final BlockPos[] NEIGHBORS = {
        // Same Y level (8 directions)
        new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
        new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
        new BlockPos(1, 0, 1), new BlockPos(-1, 0, 1),
        new BlockPos(1, 0, -1), new BlockPos(-1, 0, -1),
        // Up (9 directions)
        new BlockPos(0, 1, 0),
        new BlockPos(1, 1, 0), new BlockPos(-1, 1, 0),
        new BlockPos(0, 1, 1), new BlockPos(0, 1, -1),
        new BlockPos(1, 1, 1), new BlockPos(-1, 1, 1),
        new BlockPos(1, 1, -1), new BlockPos(-1, 1, -1),
        // Down (9 directions)
        new BlockPos(0, -1, 0),
        new BlockPos(1, -1, 0), new BlockPos(-1, -1, 0),
        new BlockPos(0, -1, 1), new BlockPos(0, -1, -1),
        new BlockPos(1, -1, 1), new BlockPos(-1, -1, 1),
        new BlockPos(1, -1, -1), new BlockPos(-1, -1, -1)
    };

    /**
     * Find a path from start to goal using A*
     * Returns null if no path found
     */
    public static List<Vec3> findPath(Vec3 start, Vec3 goal, Level world) {
        BlockPos startPos = BlockPos.containing(start);
        BlockPos goalPos = BlockPos.containing(goal);

        if (startPos.equals(goalPos)) {
            return List.of(start, goal);
        }

        // Check if goal is too far
        if (startPos.distManhattan(goalPos) > MAX_PATHFIND_DISTANCE) {
            // Fallback to direct line
            return List.of(start, goal);
        }

        // A* algorithm
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        PathNode startNode = new PathNode(startPos, null, 0, heuristic(startPos, goalPos));
        openSet.add(startNode);
        allNodes.put(startPos, startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;

            PathNode current = openSet.poll();

            if (current.pos.equals(goalPos)) {
                return reconstructPath(current, start, goal);
            }

            closedSet.add(current.pos);

            for (BlockPos offset : NEIGHBORS) {
                BlockPos neighborPos = current.pos.offset(offset);

                if (closedSet.contains(neighborPos)) {
                    continue;
                }

                if (!isWalkable(world, neighborPos)) {
                    continue;
                }

                double moveCost = offset.getX() != 0 && offset.getZ() != 0 ? 1.414 : 1.0; // Diagonal costs more
                if (offset.getY() != 0) moveCost += 0.5; // Vertical movement penalty

                double tentativeGScore = current.gScore + moveCost;

                PathNode neighborNode = allNodes.get(neighborPos);
                if (neighborNode == null) {
                    neighborNode = new PathNode(neighborPos, current, tentativeGScore, heuristic(neighborPos, goalPos));
                    allNodes.put(neighborPos, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeGScore < neighborNode.gScore) {
                    openSet.remove(neighborNode);
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeGScore;
                    neighborNode.fScore = tentativeGScore + heuristic(neighborPos, goalPos);
                    openSet.add(neighborNode);
                }
            }
        }

        // No path found - fallback to direct line
        return List.of(start, goal);
    }

    /**
     * Check if a position is walkable (air or passable block)
     */
    private static boolean isWalkable(Level world, BlockPos pos) {
        if (world == null) return false;

        // Check the block at position
        BlockState state = world.getBlockState(pos);

        // Allow air and non-solid blocks
        if (state.isAir() || !state.isCollisionShapeFullBlock(world, pos)) {
            // Check if we have solid ground below (within 1 block)
            BlockState below = world.getBlockState(pos.below());
            if (below.isCollisionShapeFullBlock(world, pos.below())) {
                return true; // Standing on solid ground
            }

            // Allow if we're in air (for flying/falling sections)
            return state.isAir();
        }

        return false;
    }

    /**
     * Heuristic function (Euclidean distance)
     */
    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    /**
     * Reconstruct path from goal to start
     */
    private static List<Vec3> reconstructPath(PathNode goalNode, Vec3 start, Vec3 goal) {
        List<Vec3> path = new ArrayList<>();

        // Add start position
        path.add(start);

        // Reconstruct path from goal to start
        List<BlockPos> blockPath = new ArrayList<>();
        PathNode current = goalNode;
        while (current.parent != null) {
            blockPath.add(current.pos);
            current = current.parent;
        }

        // Reverse to get start to goal
        Collections.reverse(blockPath);

        // Simplify path (remove unnecessary waypoints)
        blockPath = simplifyPath(blockPath);

        // Convert to Vec3d (centered on blocks)
        for (BlockPos pos : blockPath) {
            path.add(Vec3.atCenterOf(pos));
        }

        // Add goal position
        path.add(goal);

        return path;
    }

    /**
     * Simplify path by removing unnecessary intermediate points (Ramer-Douglas-Peucker algorithm)
     */
    private static List<BlockPos> simplifyPath(List<BlockPos> path) {
        if (path.size() <= 2) {
            return path;
        }

        // Simple line-of-sight simplification
        List<BlockPos> simplified = new ArrayList<>();
        simplified.add(path.get(0));

        int currentIndex = 0;
        while (currentIndex < path.size() - 1) {
            int farthestVisible = currentIndex + 1;

            // Find the farthest point we can see from current
            for (int i = currentIndex + 2; i < path.size(); i++) {
                if (hasLineOfSight(path.get(currentIndex), path.get(i))) {
                    farthestVisible = i;
                } else {
                    break;
                }
            }

            simplified.add(path.get(farthestVisible));
            currentIndex = farthestVisible;
        }

        return simplified;
    }

    /**
     * Check if there's line of sight between two positions
     */
    private static boolean hasLineOfSight(BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        Vec3 step = direction.normalize().scale(0.5);

        int steps = (int) (distance / 0.5);
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.add(step.scale(i));
            BlockPos pos = BlockPos.containing(point);

            Minecraft client = Minecraft.getInstance();
            if (client.level != null) {
                BlockState state = client.level.getBlockState(pos);
                if (!state.isAir() && state.isCollisionShapeFullBlock(client.level, pos)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Node for A* pathfinding
     */
    private static class PathNode {
        BlockPos pos;
        PathNode parent;
        double gScore; // Cost from start
        double fScore; // gScore + heuristic

        PathNode(BlockPos pos, PathNode parent, double gScore, double hScore) {
            this.pos = pos;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = gScore + hScore;
        }
    }
}
