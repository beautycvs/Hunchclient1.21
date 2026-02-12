package dev.hunchclient.module.impl.terminal;

import net.minecraft.world.phys.Vec3;

/**
 * Represents a terminal position with type information for F7 simulation
 */
public class TerminalPosition {
    private final Vec3 position;
    private final TerminalType type;
    private boolean completed;

    public TerminalPosition(double x, double y, double z, TerminalType type) {
        this.position = new Vec3(x, y, z);
        this.type = type;
        this.completed = false;
    }

    public Vec3 getPosition() {
        return position;
    }

    public TerminalType getType() {
        return type;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void reset() {
        this.completed = false;
    }
}
