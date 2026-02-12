package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleManagerMixin {

	@Inject(
		method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onAddParticle(ParticleOptions effect, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
		// Note: Firework particles (CustomMageBeam) are now handled via PacketEventMixin
		if (shouldCancel(effect)) {
			cir.setReturnValue(null);
			cir.cancel();
		}
	}

	@Unique
	private static boolean shouldCancel(ParticleOptions effect) {
		IRenderOptimize ro = ModuleBridge.renderOpt();
		if (ro == null) return false;
		return (ro.shouldHideExplosionParticles() && isExplosionEffect(effect))
			|| (ro.shouldHideFallingDustParticles() && isFallingBlockEffect(effect));
	}

	@Unique
	private static boolean isExplosionEffect(ParticleOptions effect) {
		return effect.getType() == ParticleTypes.EXPLOSION || effect.getType() == ParticleTypes.EXPLOSION_EMITTER;
	}

	@Unique
	private static boolean isFallingBlockEffect(ParticleOptions effect) {
		return effect.getType() == ParticleTypes.FALLING_DUST;
	}
}
