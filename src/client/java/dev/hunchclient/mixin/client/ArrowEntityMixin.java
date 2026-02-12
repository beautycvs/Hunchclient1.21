package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detect when arrows/projectiles hit blocks (for Ivor Fohr terminal and ICant4)
 */
@Mixin(AbstractArrow.class)
public class ArrowEntityMixin {

    /**
     * Called when projectile hits a block
     * Method exists in PersistentProjectileEntity (parent of ArrowEntity)
     */
    @Inject(method = "onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V", at = @At("HEAD"))
    private void hunchclient$onArrowHitBlock(BlockHitResult hitResult, CallbackInfo ci) {
        BlockPos hitPos = hitResult.getBlockPos();

        // Debug message
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
         //   mc.player.sendMessage(net.minecraft.text.Text.literal("§a[DEBUG] Arrow hit block at " + hitPos), false);
        }

        // Notify F7Sim about the hit
        IF7Sim f7sim = ModuleBridge.f7sim();
        if (f7sim != null && f7sim.isIvorFohrRunning()) {
            f7sim.onIvorFohrArrowHit(hitPos);
        }
    }
}
