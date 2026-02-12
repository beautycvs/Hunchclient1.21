package dev.hunchclient.module.impl.dungeons.secrets;

import dev.hunchclient.render.RenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import com.google.gson.JsonObject;

/**
 * Mine Point - Extends SecretWaypoint for Dungeonbreaker blocks
 * Manages mining state, rendering, and auto-reset
 */
public class MinePoint extends SecretWaypoint {
    private boolean mined = false;
    private long minedTime = 0;
    private static final long RESPAWN_TIME_MS = 5000; // 5 seconds default respawn
    private final BlockPos relativePos;
    private final boolean custom;

    public MinePoint(int index, JsonObject pointJson, String name, BlockPos actualPos, BlockPos relativePos) {
        super(index, Category.MINEPOINT, Component.nullToEmpty(name), actualPos);
        this.relativePos = relativePos;
        this.custom = pointJson.has("custom") && pointJson.get("custom").getAsBoolean();
    }

    public MinePoint(int index, BlockPos relativePos, String name, BlockPos actualPos, boolean custom) {
        super(index, Category.MINEPOINT, Component.nullToEmpty(name), actualPos);
        this.relativePos = relativePos;
        this.custom = custom;
    }

    @Override
    public boolean shouldRender() {
        checkRespawn();
        return true; // Always render mine points (color indicates state)
    }

    public boolean isMined() {
        checkRespawn();
        return mined;
    }

    public void setMined() {
        this.mined = true;
        this.minedTime = System.currentTimeMillis();
    }

    public void resetMined() {
        this.mined = false;
        this.minedTime = 0;
    }

    private void checkRespawn() {
        if (mined && minedTime > 0) {
            long elapsed = System.currentTimeMillis() - minedTime;
            if (elapsed >= RESPAWN_TIME_MS) {
                resetMined();
            }
        }
    }

    @Override
    public void extractRendering(RenderContext context) {
        checkRespawn();

        // Create box around block
        AABB box = AABB.encapsulatingFullBlocks(pos, pos.offset(1, 1, 1));

        // Color based on state:
        // Green = not mined (ready to mine)
        // Red = mined (recently mined, waiting for respawn)
        float[] color = mined ? new float[]{1.0f, 0.0f, 0.0f} : new float[]{0.0f, 1.0f, 0.0f};
        float alpha = mined ? 0.3f : 0.5f;

        context.submitFilledBox(box, color, alpha, true);
    }

    public long getTimeSinceMined() {
        if (!mined || minedTime == 0) return 0;
        return System.currentTimeMillis() - minedTime;
    }

    public long getTimeUntilRespawn() {
        if (!mined) return 0;
        long elapsed = getTimeSinceMined();
        return Math.max(0, RESPAWN_TIME_MS - elapsed);
    }

    @Override
    public String toString() {
        return "MinePoint{index=" + secretIndex + ", pos=" + pos + ", mined=" + mined + "}";
    }

    public BlockPos getRelativePos() {
        return relativePos;
    }

    public boolean isCustom() {
        return custom;
    }
}
