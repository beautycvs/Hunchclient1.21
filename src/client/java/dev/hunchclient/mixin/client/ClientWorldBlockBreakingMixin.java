package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientWorldBlockBreakingMixin {
	@Inject(
		method = "addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void hideBlockBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
		IRenderOptimize ro = ModuleBridge.renderOpt();
		if (ro != null && ro.shouldHideBlockBreakParticles()) {
			ci.cancel();
		}
	}
}
