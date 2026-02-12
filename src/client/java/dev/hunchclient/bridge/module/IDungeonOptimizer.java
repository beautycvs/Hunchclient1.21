package dev.hunchclient.bridge.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bridge interface for DungeonOptimizerModule - called by mixins.
 */
public interface IDungeonOptimizer {
    boolean shouldPreventPlacingWeapon(ItemStack stack);
    boolean shouldPreventPlacingHead(ItemStack stack);
    boolean shouldCancelBlockInteraction(Level world, BlockPos blockPos);
    boolean shouldContinueBreaking(BlockPos queriedPos, boolean vanillaResult, BlockPos currentBreakingPos, ItemStack currentStack, ItemStack selectedStack);
}
