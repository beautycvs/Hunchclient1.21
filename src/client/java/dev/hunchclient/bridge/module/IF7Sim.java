package dev.hunchclient.bridge.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public interface IF7Sim {
    boolean isEnabled();
    boolean isTerminatorMode();
    boolean isIvorFohrRunning();
    void onIvorFohrArrowHit(BlockPos pos);
    void onBowUseAttempt(Player player, InteractionHand hand);
    boolean isBlockRespawnEnabled();
    boolean hasAvailablePickaxeCharge();
    boolean isClientsideBlockPlacingEnabled();
    void scheduleGhostBlockRemoval(BlockPos pos, BlockState state);
    void onBlockBroken(BlockPos pos, BlockState state);
}
