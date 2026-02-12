package dev.hunchclient.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerLimboMixin {

    private static String lastServerName = "";

    @Inject(method = "tick", at = @At("HEAD"))
    private void checkLimboLocation(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        if (client != null && client.player != null) {
            // Check for Hypixel Limbo via scoreboard or other indicators
            String currentWorld = getCurrentWorldName(client);

            if (currentWorld.toLowerCase().contains("limbo") && !lastServerName.toLowerCase().contains("limbo")) {
                playLimboSound();
                System.out.println("[HunchClient] LIMBO DETECTED - Playing Eleanor Rigby!");
            }

            lastServerName = currentWorld;
        }
    }

    private String getCurrentWorldName(Minecraft client) {
        // Try to get world name from various sources
        if (client.level != null && client.level.getScoreboard() != null) {
            var objective = client.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
            if (objective != null) {
                return objective.getDisplayName().getString();
            }
        }
        return "";
    }

    private void playLimboSound() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSoundManager() != null && dev.hunchclient.util.TitleMusicRegistry.LIMBO_SOUND != null) {
                net.minecraft.client.resources.sounds.SimpleSoundInstance sound =
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        dev.hunchclient.util.TitleMusicRegistry.LIMBO_SOUND,
                        1.0f
                    );
                client.getSoundManager().play(sound);
            }
        } catch (Exception e) {
            System.err.println("[HunchClient] Failed to play Limbo sound: " + e.getMessage());
        }
    }
}
