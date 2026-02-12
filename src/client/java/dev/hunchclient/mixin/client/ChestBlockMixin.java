package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.ISecretHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends chest hitbox to full block when SecretHitboxes module is enabled.
 * Uses try-catch to ensure no VerifyError occurs even after obfuscation.
 */
@Mixin(ChestBlock.class)
public class ChestBlockMixin {

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void extendChestOutline(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        try {
            ISecretHitboxes module = ModuleBridge.secretHitboxes();
            if (module != null && module.isChestsEnabled()) {
                cir.setReturnValue(Shapes.block());
            }
        } catch (Throwable ignored) {
            // Silently fail if anything goes wrong
        }
    }
}
