package dev.hunchclient.util;

import dev.hunchclient.util.sound.LimboCreepySoundInstance;
import net.minecraft.client.Minecraft;

public class LimboMusicManager {
    private static LimboMusicManager INSTANCE;
    private LimboCreepySoundInstance currentLimboMusic;
    private boolean isPlaying = false;

    private LimboMusicManager() {}

    public static LimboMusicManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LimboMusicManager();
        }
        return INSTANCE;
    }

    public void startLimboMusic() {
        isPlaying = true;
        System.out.println("[LimboMusic] LIMBO TRIGGERED - Eleanor Rigby will loop forever!");
        if (currentLimboMusic != null) {
            currentLimboMusic.resetState();
        }
    }

    public void tick() {
        if (!isPlaying) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            System.out.println("[LimboMusic] Client is null!");
            return;
        }
        if (client.getSoundManager() == null) {
            System.out.println("[LimboMusic] SoundManager is null!");
            return;
        }
        if (TitleMusicRegistry.LIMBO_SOUND == null) {
            System.out.println("[LimboMusic] LIMBO_SOUND is null!");
            return;
        }

        if (currentLimboMusic == null) {
            System.out.println("[LimboMusic] Starting creepy Eleanor Rigby loop!");
            currentLimboMusic = new LimboCreepySoundInstance(TitleMusicRegistry.LIMBO_SOUND);
            client.getSoundManager().play(currentLimboMusic);
        } else if (!client.getSoundManager().isActive(currentLimboMusic)) {
            System.out.println("[LimboMusic] Limbo loop dropped, restarting current instance!");
            client.getSoundManager().play(currentLimboMusic);
        }
    }

    public void stop() {
        Minecraft client = Minecraft.getInstance();

        if (client != null && client.getSoundManager() != null && currentLimboMusic != null) {
            System.out.println("[LimboMusic] Stopping Eleanor Rigby");
            client.getSoundManager().stop(currentLimboMusic);
            currentLimboMusic = null;
        }
        isPlaying = false;
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
