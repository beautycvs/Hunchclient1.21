package dev.hunchclient.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Event fired when a block state changes in the world
 * Used for AutoSS to detect sea lantern pattern changes
 */
public class BlockChangeEvent {
    private final BlockPos pos;
    private final BlockState oldState;
    private final BlockState newState;

    private BlockChangeEvent(BlockPos pos, BlockState oldState, BlockState newState) {
        this.pos = pos;
        this.oldState = oldState;
        this.newState = newState;
    }

    public static BlockChangeEvent of(BlockPos pos, BlockState oldState, BlockState newState) {
        return new BlockChangeEvent(pos, oldState, newState);
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getOldState() {
        return oldState;
    }

    public BlockState getNewState() {
        return newState;
    }

    public BlockState getUpdate() {
        return newState;
    }
}
