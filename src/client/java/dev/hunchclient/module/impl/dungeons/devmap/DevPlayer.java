package dev.hunchclient.module.impl.dungeons.devmap;

import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;


public class DevPlayer {

    private static final Minecraft mc = Minecraft.getInstance();

    private final String name;
    private DevClass role;
    private int classLevel;
    private boolean isDead;

    @Nullable
    private Player entity = null;

    // Position tracking for smooth interpolation
    @Nullable
    private PlayerComponentPosition position = null;
    @Nullable
    private Double updateTime = null;
    @Nullable
    private PlayerComponentPosition lastPosition = null;
    @Nullable
    private Double lastUpdateTime = null;

    public DevPlayer(String name, DevClass role, int classLevel, boolean isDead) {
        this.name = name;
        this.role = role;
        this.classLevel = classLevel;
        this.isDead = isDead;
    }

    /**
     * Called each tick to update player position
     */
    public void tick() {
        if (entity == null || entity.isDeadOrDying() || entity.isRemoved()) {
            entity = null;
        }

        if (entity != null) {
            double yawRad = -(entity.getYRot() + 90) * Math.PI / 180.0;
            updatePosition(PlayerComponentPosition.fromWorld(
                entity.getX(),
                entity.getZ(),
                yawRad
            ));
        }
    }

    /**
     * Update the player's position
     */
    public void updatePosition(PlayerComponentPosition pos) {
        double time = System.nanoTime() * 1.0e-6;

        if (position == null) {
            position = pos;
            updateTime = time;
            return;
        }

        lastPosition = position;
        lastUpdateTime = updateTime;
        position = pos;
        updateTime = time;
    }

    /**
     * Get the lerped (interpolated) position for smooth rendering
     */
    @Nullable
    public PlayerComponentPosition getLerpedPosition() {
        if (position == null) return null;
        if (lastPosition == null) return position;
        if (updateTime == null) return position;
        if (lastUpdateTime == null) return position;

        double time = System.nanoTime() * 1.0e-6;
        double timeDelta = updateTime - lastUpdateTime;
        if (timeDelta <= 0) return position;

        double factor = (time - updateTime) / timeDelta;

        return new PlayerComponentPosition(
            lerp(factor, lastPosition.x, position.x),
            lerp(factor, lastPosition.z, position.z),
            lerpAngle(factor, lastPosition.r, position.r)
        );
    }

    private double lerp(double factor, double start, double end) {
        return start + factor * (end - start);
    }

    private double lerpAngle(double factor, double start, double end) {
        // Normalize the difference to [-PI, PI]
        double diff = end - start;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        return start + factor * diff;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public DevClass getRole() {
        return role;
    }

    public void setRole(DevClass role) {
        this.role = role;
    }

    public int getClassLevel() {
        return classLevel;
    }

    public void setClassLevel(int classLevel) {
        this.classLevel = classLevel;
    }

    public boolean isDead() {
        return isDead;
    }

    public void setDead(boolean dead) {
        isDead = dead;
    }

    @Nullable
    public Player getEntity() {
        return entity;
    }

    public void setEntity(@Nullable Player entity) {
        this.entity = entity;
    }

    @Nullable
    public PlayerComponentPosition getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return "DevPlayer{name='" + name + "', class=" + role + ", dead=" + isDead + "}";
    }

    /**
     * Dungeon class enum
     */
    public enum DevClass {
        ARCHER("Arch", 'a', "§c"),
        BERSERK("Bers", 'b', "§6"),
        MAGE("Mage", 'm', "§3"),
        HEALER("Heal", 'h', "§5"),
        TANK("Tank", 't', "§a"),
        UNKNOWN("Unknown", '\0', "");

        public final String shortName;
        public final char singleLetter;
        public final String colorCode;

        DevClass(String shortName, char singleLetter, String colorCode) {
            this.shortName = shortName;
            this.singleLetter = singleLetter;
            this.colorCode = colorCode;
        }

        public static DevClass fromName(String fullName) {
            if (fullName == null) return UNKNOWN;
            return switch (fullName) {
                case "Archer" -> ARCHER;
                case "Berserk" -> BERSERK;
                case "Mage" -> MAGE;
                case "Healer" -> HEALER;
                case "Tank" -> TANK;
                default -> UNKNOWN;
            };
        }
    }
}
