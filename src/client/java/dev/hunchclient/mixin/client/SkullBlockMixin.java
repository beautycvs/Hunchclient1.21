package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.ISecretHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SkullBlock;
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
 * Extends skull hitbox when it's a wither essence
 * Only affects clicking/raycasting, NOT physical collisions
 */
@Mixin(SkullBlock.class)
public abstract class SkullBlockMixin extends BlockBehaviour {

    public SkullBlockMixin(Properties settings) {
        super(settings);
    }

    // Full block for clicking
    private static final VoxelShape EXTENDED_SHAPE = Shapes.block();
    // Original skull shape for collisions (0.25 to 0.75 in all directions)
    private static final VoxelShape SKULL_COLLISION = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75);

    /**
     * Extend the outline shape (used for raycasting/clicking)
     */
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void extendSkullOutline(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        ISecretHitboxes module = ModuleBridge.secretHitboxes();
        if (module != null && module.isEssence(pos)) {
            cir.setReturnValue(EXTENDED_SHAPE);
        }
    }

    /**
     * Keep collision shape to the original small skull hitbox
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SKULL_COLLISION;
    }
}
