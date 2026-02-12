package dev.hunchclient.mixin.client;

import dev.hunchclient.event.BlockChangeEvent;
import dev.hunchclient.event.EventBus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to detect block changes for modules like Terminal Solver.
 */
@Mixin(ClientLevel.class)
public class ClientWorldMixin {

    @Inject(method = "setBlock", at = @At("HEAD"))
    @SuppressWarnings("resource")
    private void onBlockStateChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        ClientLevel world = (ClientLevel) (Object) this;
        BlockState oldState = world.getBlockState(pos);

        if (!oldState.equals(state)) {
            BlockChangeEvent event = BlockChangeEvent.of(pos, oldState, state);
            EventBus.getInstance().postBlockChange(event);
        }
    }

    // NOTE: In 1.21.10, handleBlockChangedAck only takes int (sequence number)
    // Block change detection now relies solely on setBlock() above.
    // The old handleBlockUpdate approach is no longer viable.
}
