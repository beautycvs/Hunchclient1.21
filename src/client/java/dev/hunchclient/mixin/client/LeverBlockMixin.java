package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.ISecretHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends lever hitbox to full block when SecretHitboxes module is enabled
 * Only affects clicking/raycasting, NOT physical collisions
 */
@Mixin(LeverBlock.class)
public abstract class LeverBlockMixin extends BlockBehaviour {

    public LeverBlockMixin(Properties settings) {
        super(settings);
    }

    // 90% cube centered in the block (keeps lever easy to click without full block reach)
    private static final VoxelShape EXTENDED_SHAPE = Shapes.box(0.05, 0.05, 0.05, 0.95, 0.95, 0.95);

    /**
     * Extend the outline shape (used for raycasting/clicking)
     */
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void extendLeverOutline(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        ISecretHitboxes module = ModuleBridge.secretHitboxes();
        if (module != null && module.isLeverEnabled()) {
            cir.setReturnValue(EXTENDED_SHAPE);
        }
    }

    /**
     * Keep collision shape empty (levers have no collision)
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
