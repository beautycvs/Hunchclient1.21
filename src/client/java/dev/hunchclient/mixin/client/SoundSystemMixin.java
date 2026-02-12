package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.ICustomHitSound;
import dev.hunchclient.module.impl.dungeons.secrets.DungeonManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        if (sound == null) {
            return;
        }

        ResourceLocation soundId = sound.getLocation();
        if (soundId == null) {
            return;
        }

        boolean cancel = false;
        ICustomHitSound module = ModuleBridge.customHitSound();
        if (module != null && module.isEnabled()) {
            String soundName = soundId.toString();
            // Try to get volume and pitch, but some sound instances may have null internal sound
            float volume = 1.0f;
            float pitch = 1.0f;

            try {
                volume = sound.getVolume();
            } catch (RuntimeException ignored) {
                // Some sound instances have a null backing sound; fall back to defaults.
            }

            try {
                pitch = sound.getPitch();
            } catch (RuntimeException ignored) {
                // Keep default pitch if the instance isn't fully populated.
            }
            cancel = module.onSoundPlay(
                soundName,
                sound.getX(),
                sound.getY(),
                sound.getZ(),
                volume,
                pitch
            );
        }

        // Track bat death sounds for secret detection
        String path = soundId.getPath();

        // DEBUG: Log ALL bat sounds to see what sounds are actually played
        if (path != null && path.contains("bat")) {
            System.out.println("[BatSound] ANY bat sound detected: " + soundId + " at " + new Vec3(sound.getX(), sound.getY(), sound.getZ()));
        }

        if (path != null && path.contains("bat") && path.contains("death")) {
            Vec3 soundPos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            DungeonManager.onBatDeathSound(soundPos);

            // Debug logging
            System.out.println("[BatSound] Detected bat DEATH at " + soundPos + " (sound: " + soundId + ")");
        }

        if (cancel) {
            cir.setReturnValue(null);
        }
    }
}
