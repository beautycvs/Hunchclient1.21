package dev.hunchclient.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MusicTrackerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void preventMenuMusic(CallbackInfo ci) {
        // Cancel vanilla music when Limbo music is playing
        if (dev.hunchclient.util.LimboMusicManager.getInstance().isPlaying()) {
            ci.cancel();
            return;
        }

        // Cancel vanilla music when in menu (no world loaded and screen is open)
        if (minecraft.level == null && minecraft.screen != null) {
            ci.cancel();
        }
    }
}
