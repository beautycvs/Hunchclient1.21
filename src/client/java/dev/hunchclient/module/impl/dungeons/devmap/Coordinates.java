package dev.hunchclient.module.impl.dungeons.devmap;

import java.util.ArrayList;
import java.util.List;


public class Coordinates {

    // Dungeon corner positions
    public static final int CORNER_START_X = -200;
    public static final int CORNER_START_Z = -200;
    public static final int CORNER_END_X = -10;
    public static final int CORNER_END_Z = -10;

    // Room dimensions
    public static final int DUNGEON_ROOM_SIZE = 31;
    public static final int DUNGEON_DOOR_SIZE = 1;
    public static final int ROOM_DOOR_COMBINED_SIZE = DUNGEON_ROOM_SIZE + DUNGEON_DOOR_SIZE; // 32
    public static final int HALF_ROOM_SIZE = DUNGEON_ROOM_SIZE / 2; // 15
    public static final int HALF_COMBINED_SIZE = ROOM_DOOR_COMBINED_SIZE / 2; // 16

    /**
     * World position (real Minecraft coordinates)
     */
    public static class WorldPosition {
        public static final WorldPosition EMPTY = new WorldPosition(Integer.MIN_VALUE, Integer.MIN_VALUE);

        public final int x;
        public final int z;

        public WorldPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public ComponentPosition toComponent() {
            return new ComponentPosition(
                (x - CORNER_START_X) / HALF_COMBINED_SIZE,
                (z - CORNER_START_Z) / HALF_COMBINED_SIZE
            );
        }

        public WorldComponentPosition withComponent() {
            ComponentPosition comp = toComponent();
            return new WorldComponentPosition(x, z, comp.x, comp.z);
        }

        public boolean isEmpty() {
            return this == EMPTY || (x == Integer.MIN_VALUE && z == Integer.MIN_VALUE);
        }

        @Override
        public String toString() {
            return isEmpty() ? "World()" : "World(" + x + ", " + z + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WorldPosition)) return false;
            WorldPosition other = (WorldPosition) obj;
            return x == other.x && z == other.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }

    /**
     * Component position (grid coordinates, 0-10 range)
     * Even indices are rooms, odd indices are doors
     */
    public static class ComponentPosition {
        public static final ComponentPosition EMPTY = new ComponentPosition(Integer.MIN_VALUE, Integer.MIN_VALUE);

        public final int x;
        public final int z;

        public ComponentPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public WorldPosition toWorld() {
            return new WorldPosition(
                CORNER_START_X + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * x,
                CORNER_START_Z + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * z
            );
        }

        public WorldComponentPosition withWorld() {
            WorldPosition world = toWorld();
            return new WorldComponentPosition(world.x, world.z, x, z);
        }

        public boolean isValid() {
            return x >= 0 && x <= 10 && z >= 0 && z <= 10;
        }

        public boolean isValidRoom() {
            return (x & 1) == 0 && (z & 1) == 0;
        }

        public boolean isValidDoor() {
            return ((x & 1) ^ (z & 1)) == 1;
        }

        public List<ComponentPosition> getNeighboringRooms() {
            List<ComponentPosition> result = new ArrayList<>();
            if (isValidDoor()) {
                if ((x & 1) == 1) {
                    // Horizontal door
                    ComponentPosition left = new ComponentPosition(x - 1, z);
                    ComponentPosition right = new ComponentPosition(x + 1, z);
                    if (left.isValid()) result.add(left);
                    if (right.isValid()) result.add(right);
                } else {
                    // Vertical door
                    ComponentPosition up = new ComponentPosition(x, z - 1);
                    ComponentPosition down = new ComponentPosition(x, z + 1);
                    if (up.isValid()) result.add(up);
                    if (down.isValid()) result.add(down);
                }
            }
            return result;
        }

        public List<ComponentPosition> getNeighboringDoors() {
            List<ComponentPosition> result = new ArrayList<>();
            if (isValidRoom()) {
                ComponentPosition[] neighbors = {
                    new ComponentPosition(x, z - 1),
                    new ComponentPosition(x, z + 1),
                    new ComponentPosition(x - 1, z),
                    new ComponentPosition(x + 1, z)
                };
                for (ComponentPosition pos : neighbors) {
                    if (pos.isValid()) result.add(pos);
                }
            }
            return result;
        }

        public List<Neighbor> getNeighbors() {
            List<Neighbor> result = new ArrayList<>();
            if (!isValidRoom()) return result;

            Neighbor[] neighbors = {
                new Neighbor(new ComponentPosition(x, z - 2), new ComponentPosition(x, z - 1)),
                new Neighbor(new ComponentPosition(x, z + 2), new ComponentPosition(x, z + 1)),
                new Neighbor(new ComponentPosition(x - 2, z), new ComponentPosition(x - 1, z)),
                new Neighbor(new ComponentPosition(x + 2, z), new ComponentPosition(x + 1, z))
            };

            for (Neighbor n : neighbors) {
                if (n.room.isValid()) result.add(n);
            }
            return result;
        }

        public int getRoomIdx() {
            return (z / 2) * 6 + x / 2;
        }

        public int getDoorIdx() {
            int idx = ((x - 1) >> 1) + 6 * z;
            return idx - idx / 12;
        }

        public boolean isEmpty() {
            return this == EMPTY || (x == Integer.MIN_VALUE && z == Integer.MIN_VALUE);
        }

        @Override
        public String toString() {
            return isEmpty() ? "Component()" : "Component(" + x + ", " + z + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ComponentPosition)) return false;
            ComponentPosition other = (ComponentPosition) obj;
            return x == other.x && z == other.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        /**
         * Represents a neighboring room and the door connecting to it
         */
        public static class Neighbor {
            public final ComponentPosition room;
            public final ComponentPosition door;

            public Neighbor(ComponentPosition room, ComponentPosition door) {
                this.room = room;
                this.door = door;
            }
        }
    }

    /**
     * Combined world and component position
     */
    public static class WorldComponentPosition {
        public static final WorldComponentPosition EMPTY = new WorldComponentPosition(
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
        );

        public final int wx;
        public final int wz;
        public final int cx;
        public final int cz;

        public WorldComponentPosition(int wx, int wz, int cx, int cz) {
            this.wx = wx;
            this.wz = wz;
            this.cx = cx;
            this.cz = cz;
        }

        public WorldPosition toWorld() {
            return new WorldPosition(wx, wz);
        }

        public ComponentPosition toComponent() {
            return new ComponentPosition(cx, cz);
        }

        public boolean isEmpty() {
            return this == EMPTY || (wx == Integer.MIN_VALUE && wz == Integer.MIN_VALUE);
        }

        @Override
        public String toString() {
            return isEmpty() ? "WorldComp()" : "WorldComp(" + wx + ", " + wz + ", " + cx + ", " + cz + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WorldComponentPosition)) return false;
            WorldComponentPosition other = (WorldComponentPosition) obj;
            return wx == other.wx && wz == other.wz && cx == other.cx && cz == other.cz;
        }

        @Override
        public int hashCode() {
            int result = 31 * wx + wz;
            result = 31 * result + cx;
            result = 31 * result + cz;
            return result;
        }
    }

    /**
     * Player position in component space with rotation
     * r is radians clockwise of +x
     */
    public static class PlayerComponentPosition {
        public final double x;
        public final double z;
        public final double r; // rotation in radians

        public PlayerComponentPosition(double x, double z, double r) {
            this.x = x;
            this.z = z;
            this.r = r;
        }

        public ComponentPosition toComponent() {
            return new ComponentPosition((int) x, (int) z);
        }

        public static PlayerComponentPosition fromWorld(double wx, double wz, double r) {
            return new PlayerComponentPosition(
                (wx - CORNER_START_X) / HALF_COMBINED_SIZE,
                (wz - CORNER_START_Z) / HALF_COMBINED_SIZE,
                r
            );
        }

        @Override
        public String toString() {
            return String.format("PlayerPosition(%.3f, %.3f, %.3f)", x, z, r);
        }
    }
}
