package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.dungeons.DungeonOptimizerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Shadow private Minecraft minecraft;
    @Shadow private ItemStack destroyingItem;
    @Shadow private BlockPos destroyBlockPos;
    @Shadow private int destroyDelay;

    /**
     * F7Sim: Prevent block breaking without pickaxe (catches creative mode instant-break)
     */
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void hunchclient$preventBreakWithoutPickaxe(BlockPos pos, net.minecraft.core.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null || !this.minecraft.isLocalServer() || this.minecraft.player == null) {
            return;
        }

        IF7Sim f7Sim = ModuleBridge.f7sim();
        if (f7Sim == null || !f7Sim.isEnabled() || !f7Sim.isBlockRespawnEnabled()) {
            return;
        }

        // Only allow breaking with pickaxe
        ItemStack mainHand = this.minecraft.player.getMainHandItem();
        if (!isPickaxe(mainHand)) {
            // Cancel attack/break if not holding pickaxe
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // Check if pickaxe has available charges
        if (!f7Sim.hasAvailablePickaxeCharge()) {
            // No charges left - cancel break
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void hunchclient$cancelInteract(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = this.minecraft.level;
        if (world == null || hitResult == null || player == null) {
            return;
        }

        IF7Sim f7Sim = ModuleBridge.f7sim();

        // F7Sim: Clientside-only block placing (ghost blocks like Hypixel)
        if (f7Sim != null && f7Sim.isEnabled() && f7Sim.isClientsideBlockPlacingEnabled()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
                BlockState currentState = world.getBlockState(placePos);

                if (world.isInWorldBounds(placePos) && currentState.canBeReplaced()) {
                    BlockState placeState = blockItem.getBlock().defaultBlockState();
                    BlockPos immutablePos = placePos.immutable();

                    // Place block ONLY on ClientWorld's chunk (bypass setBlockState to avoid serverside update)
                    if (world instanceof net.minecraft.client.multiplayer.ClientLevel clientWorld) {
                        // Get the chunk and set block directly (clientside only!)
                        net.minecraft.world.level.chunk.LevelChunk chunk = clientWorld.getChunkAt(immutablePos);
                        if (chunk != null) {
                            // Set block directly in chunk without triggering world updates
                            chunk.setBlockState(immutablePos, placeState, 0);

                            // Mark chunk for re-render
                            clientWorld.setBlocksDirty(immutablePos, currentState, placeState);
                        }

                        // Schedule block removal and original state restoration after ping delay (like Hypixel)
                        f7Sim.scheduleGhostBlockRemoval(immutablePos, currentState);
                    }

                    // Visual/audio feedback
                    world.playSound(player, placePos, placeState.getSoundType().getPlaceSound(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    player.swing(hand);

                    // CRITICAL: CONSUME prevents ANY packet from being sent to server!
                    cir.setReturnValue(InteractionResult.CONSUME);
                    cir.cancel();
                    return;
                }
            }
        }

        // F7Sim: Command blocks are in the wall, armor stands are in front
        // No need to block command blocks - they're not directly reachable anyway

        // DungeonOptimizer module (replaces CancelInteractModule)
        DungeonOptimizerModule dungeonOpt = ModuleCache.get(DungeonOptimizerModule.class);
        if (dungeonOpt != null) {
            ItemStack heldStack = player.getItemInHand(hand);

            // Check for weapon placing prevention
            if (dungeonOpt.shouldPreventPlacingWeapon(heldStack)) {
                cir.setReturnValue(InteractionResult.PASS);
                cir.cancel();
                return;
            }

            // Check for head placing prevention
            if (dungeonOpt.shouldPreventPlacingHead(heldStack)) {
                cir.setReturnValue(InteractionResult.PASS);
                cir.cancel();
                return;
            }

            // Cancel interact check
            if (dungeonOpt.shouldCancelBlockInteraction(world, hitResult.getBlockPos())) {
                cir.setReturnValue(InteractionResult.PASS);
                cir.cancel();
            }
        }
    }

    /**
     * No Break Reset: Prevent mining progress reset when switching items.
     * Note: In 1.21.10, isDestroying() no longer takes BlockPos parameter.
     * Using this.destroyBlockPos shadow field instead.
     */
    @Inject(method = "isDestroying", at = @At("RETURN"), cancellable = true)
    private void hunchclient$noBreakReset(CallbackInfoReturnable<Boolean> cir) {
        DungeonOptimizerModule module = ModuleCache.get(DungeonOptimizerModule.class);
        if (module == null || this.minecraft.player == null) {
            return;
        }

        ItemStack currentStack = this.minecraft.player.getMainHandItem();
        // Use shadow field destroyBlockPos since isDestroying() no longer takes a BlockPos parameter
        boolean result = module.shouldContinueBreaking(this.destroyBlockPos, cir.getReturnValue(), this.destroyBlockPos, currentStack, this.destroyingItem);
        cir.setReturnValue(result);
    }

    /**
     * F7Sim: Remove break delay in creative mode when using pickaxe
     * Also prevent breaking without pickaxe
     */
    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void hunchclient$removeBreakDelay(BlockPos pos, net.minecraft.core.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null || !this.minecraft.isLocalServer() || this.minecraft.player == null) {
            return;
        }

        IF7Sim f7Sim = ModuleBridge.f7sim();
        if (f7Sim == null || !f7Sim.isEnabled() || !f7Sim.isBlockRespawnEnabled()) {
            return;
        }

        // Only allow breaking with pickaxe
        ItemStack mainHand = this.minecraft.player.getMainHandItem();
        if (!isPickaxe(mainHand)) {
            // Cancel breaking if not holding pickaxe
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // Check if pickaxe has available charges
        if (!f7Sim.hasAvailablePickaxeCharge()) {
            // No charges left - cancel break
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // Remove break delay when in creative mode with pickaxe
        if (this.minecraft.player.getAbilities().instabuild) {
            this.destroyDelay = 0;
        }
    }

    private boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(net.minecraft.world.item.Items.WOODEN_PICKAXE) ||
               stack.is(net.minecraft.world.item.Items.STONE_PICKAXE) ||
               stack.is(net.minecraft.world.item.Items.IRON_PICKAXE) ||
               stack.is(net.minecraft.world.item.Items.GOLDEN_PICKAXE) ||
               stack.is(net.minecraft.world.item.Items.DIAMOND_PICKAXE) ||
               stack.is(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
    }

    /**
     * F7Sim: Trigger block respawn when block is broken (nach normalem Vanilla-Breaking)
     */
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void hunchclient$onBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null || !this.minecraft.isLocalServer() || this.minecraft.level == null || this.minecraft.player == null) {
            return;
        }

        IF7Sim f7Sim = ModuleBridge.f7sim();
        if (f7Sim == null || !f7Sim.isEnabled() || !f7Sim.isBlockRespawnEnabled()) {
            return;
        }

        // Save block state before it's broken (vanilla breaking happens normally)
        // The PlayerEntityBlockBreakMixin controls the speed (fast with pickaxe, 0 without)
        BlockState state = this.minecraft.level.getBlockState(pos);
        if (!state.isAir()) {
            f7Sim.onBlockBroken(pos, state);
        }
    }
}
