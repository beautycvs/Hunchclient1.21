package dev.hunchclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

public class MenuMusicManager {
    private static MenuMusicManager INSTANCE;
    private SoundInstance currentMusicInstance;
    private boolean isMusicPlaying = false;

    private MenuMusicManager() {}

    public static MenuMusicManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MenuMusicManager();
        }
        return INSTANCE;
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();

        if (client == null) return;

        // PRIORITY: If Limbo music is playing, stop menu music!
        if (LimboMusicManager.getInstance().isPlaying()) {
            if (isMusicPlaying) {
                System.out.println("[MenuMusic] Limbo music active - stopping menu music!");
                stopMusic();
            }
            return;
        }

        // Check if we're in a menu (screen open and no world loaded)
        boolean inMenu = client.screen != null && client.level == null;

        if (inMenu) {
            // We're in a menu
            if (!isMusicPlaying) {
                // Start music if not playing
                startMusic();
            } else {
                // Check if music ended and restart
                checkAndRestartMusic();
            }
        } else if (client.level != null) {
            // We're in game - stop music
            if (isMusicPlaying) {
                stopMusic();
            }
        }
    }

    private void startMusic() {
        Minecraft client = Minecraft.getInstance();

        if (client == null) {
            System.out.println("[MenuMusic] Client is null!");
            return;
        }
        if (client.getSoundManager() == null) {
            System.out.println("[MenuMusic] SoundManager is null!");
            return;
        }
        if (TitleMusicRegistry.TITLE_MUSIC_SOUND == null) {
            System.out.println("[MenuMusic] TITLE_MUSIC_SOUND is null!");
            return;
        }

        // DON'T restart if already playing!
        if (currentMusicInstance != null && client.getSoundManager().isActive(currentMusicInstance)) {
            isMusicPlaying = true;
            return;
        }

        // Stop vanilla music
        client.getMusicManager().stopPlaying();

        System.out.println("[MenuMusic] Starting title music!");
        // Start our custom music
        // In 1.21.8+, music() requires pitch parameter
        currentMusicInstance = SimpleSoundInstance.forMusic(TitleMusicRegistry.TITLE_MUSIC_SOUND, 1.0f);
        client.getSoundManager().play(currentMusicInstance);
        isMusicPlaying = true;
    }

    private void stopMusic() {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.getSoundManager() == null) {
            return;
        }

        if (currentMusicInstance != null) {
            client.getSoundManager().stop(currentMusicInstance);
            currentMusicInstance = null;
        }
        isMusicPlaying = false;
    }

    private void checkAndRestartMusic() {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.getSoundManager() == null || TitleMusicRegistry.TITLE_MUSIC_SOUND == null) {
            return;
        }

        // If music stopped playing, restart it (LOOP!)
        if (currentMusicInstance != null && !client.getSoundManager().isActive(currentMusicInstance)) {
            System.out.println("[MenuMusic] Music ended, restarting loop!");
            // In 1.21.8+, music() requires pitch parameter
            currentMusicInstance = SimpleSoundInstance.forMusic(TitleMusicRegistry.TITLE_MUSIC_SOUND, 1.0f);
            client.getSoundManager().play(currentMusicInstance);
        }
    }

    public void forceStart() {
        startMusic();
    }

    public void forceStop() {
        stopMusic();
    }
}
