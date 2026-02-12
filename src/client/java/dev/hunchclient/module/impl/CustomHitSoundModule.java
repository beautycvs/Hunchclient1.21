package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Custom Hit Sound Module
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only (sound replacement)
 * - No packets sent
 * - No server interaction
 *
 * Features:
 * - Custom sound folder for user-provided .ogg files
 * - Replaces arrow hit sounds and melee hit sounds
 * - Custom etherwarp sound
 * - Random pitch variation
 * - Sound selection from folder
 */
public class CustomHitSoundModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.ICustomHitSound {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final RandomSource RANDOM = RandomSource.create();

    // Sound folder path
    private static final Path SOUNDS_FOLDER = Paths.get("config", "hunchclient", "sounds");

    // Config
    private boolean randomPitch = true;
    private float volume = 1.0f;
    // Arrow tracking
    private static final long ARROW_WINDOW_MS = 750L;
    private static final double ARROW_MATCH_RADIUS = 9.0;
    private boolean awaitingArrowHit = false;
    private long lastArrowShotTime = 0L;
    private boolean hasPendingXp = false;
    private double pendingXpX = 0.0;
    private double pendingXpY = 0.0;
    private double pendingXpZ = 0.0;
    private long pendingXpTime = 0L;

    // Sound files
    private List<String> hitSoundFiles = new ArrayList<>();
    private List<String> etherwarpSoundFiles = new ArrayList<>();
    private int selectedHitSoundIndex = 0;
    private int selectedEtherwarpSoundIndex = 0;

    // Custom sound events (registered dynamically)
    // Custom etherwarp sound settings
    private boolean customEtherwarpEnabled = false;
    private float etherwarpVolume = 1.0f;
    private boolean etherwarpRandomPitch = false;

    public CustomHitSoundModule() {
        super("CustomHitSound", "Custom hit sounds with user-provided .ogg files", Category.VISUALS, true);
        ensureSoundsFolderExists();
        loadSoundFiles();
    }

    @Override
    protected void onEnable() {
        // Will be called from mixin
    }

    @Override
    protected void onDisable() {
    }

    /**
     * Ensures the sounds folder exists and creates a readme
     */
    private void ensureSoundsFolderExists() {
        try {
            if (!Files.exists(SOUNDS_FOLDER)) {
                Files.createDirectories(SOUNDS_FOLDER);

                // Create README
                Path readmePath = SOUNDS_FOLDER.resolve("README.txt");
                String readme = """
                    HunchClient Custom Sounds Folder
                    =================================

                    Place your custom .ogg sound files in this folder.

                    Supported formats:
                    - .ogg files only (Minecraft's sound format)

                    File naming:
                    - For hit sounds: name them however you want (e.g., "hit1.ogg", "cool_sound.ogg")
                    - For etherwarp sounds: same as above

                    Usage:
                    1. Place .ogg files in this folder
                    2. Reload the game or use /hc reloadsounds
                    3. Select your sound in the GUI or with /hc setsound <index>

                    Converting sounds to .ogg:
                    - Use ffmpeg: ffmpeg -i input.mp3 -c:a libvorbis output.ogg
                    - Use online converters: https://convertio.co/mp3-ogg/

                    Where to find sounds:
                    - https://freesound.org/
                    - https://minecraft.wiki/ (extract from resource pack)
                    - Your own recordings

                    Tips:
                    - Keep files small (under 1MB)
                    - Shorter sounds work better (<1 second)
                    - Higher quality = larger file size
                    """;
                Files.writeString(readmePath, readme);
            }
        } catch (IOException e) {
            System.err.println("[HunchClient] Failed to create sounds folder: " + e.getMessage());
        }
    }

    /**
     * Loads all .ogg files from the sounds folder
     */
    public void loadSoundFiles() {
        hitSoundFiles.clear();
        etherwarpSoundFiles.clear();

        try {
            if (!Files.exists(SOUNDS_FOLDER)) {
                ensureSoundsFolderExists();
                return;
            }

            List<String> allFiles = Files.walk(SOUNDS_FOLDER, 1)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".ogg"))
                .map(p -> p.getFileName().toString())
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());

            hitSoundFiles.addAll(allFiles);
            etherwarpSoundFiles.addAll(allFiles);

            clampSoundIndexes();

            if (hitSoundFiles.isEmpty()) {
                System.out.println("[HunchClient] No .ogg files found in sounds folder");
            } else {
                System.out.println("[HunchClient] Loaded " + hitSoundFiles.size() + " sound files");
            }
        } catch (IOException e) {
            System.err.println("[HunchClient] Failed to load sound files: " + e.getMessage());
        }
    }

    /**
     * Called from mixin when a sound is played
     * Returns true if the sound should be cancelled
     */
    public boolean onSoundPlay(String soundName, double x, double y, double z, float volume, float pitch) {
        if (!isEnabled()) {
            return false;
        }
        if (soundName == null) return false;

        long now = System.currentTimeMillis();
        if (awaitingArrowHit && now - lastArrowShotTime > ARROW_WINDOW_MS) {
            awaitingArrowHit = false;
            hasPendingXp = false;
        }

        // Custom Etherwarp Sound (highest priority)
        if (customEtherwarpEnabled && soundName.endsWith("entity.ender_dragon.hurt")) {
            playCustomEtherwarpSound(x, y, z);
            return true; // Cancel original sound
        }

        if (soundName.endsWith("entity.arrow.shoot")) {
            awaitingArrowHit = true;
            hasPendingXp = false;
            lastArrowShotTime = now;
            return false;
        }

        if (soundName.endsWith("entity.experience_orb.pickup") && awaitingArrowHit) {
            if (now - lastArrowShotTime <= ARROW_WINDOW_MS) {
                hasPendingXp = true;
                pendingXpX = x;
                pendingXpY = y;
                pendingXpZ = z;
                pendingXpTime = now;
            }
            return false;
        }

        if (isHitConfirmationSound(soundName) && awaitingArrowHit) {
            boolean matched = false;
            if (hasPendingXp && now - pendingXpTime <= ARROW_WINDOW_MS) {
                double dist = distanceSquared(pendingXpX, pendingXpY, pendingXpZ, x, y, z);
                if (dist <= ARROW_MATCH_RADIUS * ARROW_MATCH_RADIUS) {
                    playCustomHitSound(pendingXpX, pendingXpY, pendingXpZ);
                    matched = true;
                }
            }

            awaitingArrowHit = false;
            hasPendingXp = false;

            if (matched) {
                return true;
            } else if (soundName.endsWith("entity.arrow.hit_player")) {
                playCustomHitSound(x, y, z);
                return true;
            }
        }

        return false;
    }

    private boolean isHitConfirmationSound(String soundName) {
        return soundName.endsWith("entity.arrow.hit") ||
               soundName.endsWith("entity.arrow.hit_player") ||
               soundName.contains(".hurt") ||
               soundName.contains(".death");
    }

    private double distanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Plays the selected custom hit sound
     */
    private void playCustomHitSound(double x, double y, double z) {
        if (hitSoundFiles.isEmpty()) {
            playFallbackSound(x, y, z, this.volume, randomPitch);
            return;
        }

        String soundFile = hitSoundFiles.get(selectedHitSoundIndex);
        Path soundPath = SOUNDS_FOLDER.resolve(soundFile);

        if (!Files.exists(soundPath)) {
            playFallbackSound(x, y, z, this.volume, randomPitch);
            return;
        }

        // Play the sound file directly using Java Sound API
        playCustomSoundFile(soundPath, this.volume, randomPitch, x, y, z);
    }

    /**
     * Plays the selected custom etherwarp sound
     */
    private void playCustomEtherwarpSound(double x, double y, double z) {
        double px = x;
        double py = y;
        double pz = z;
        if (mc.player != null) {
            px = mc.player.getX();
            py = mc.player.getY();
            pz = mc.player.getZ();
        }

        if (etherwarpSoundFiles.isEmpty()) {
            playFallbackSound(px, py, pz, etherwarpVolume, etherwarpRandomPitch);
            return;
        }

        String soundFile = etherwarpSoundFiles.get(selectedEtherwarpSoundIndex);
        Path soundPath = SOUNDS_FOLDER.resolve(soundFile);

        if (!Files.exists(soundPath)) {
            playFallbackSound(px, py, pz, etherwarpVolume, etherwarpRandomPitch);
            return;
        }

        // Play the sound file directly using Java Sound API
        playCustomSoundFile(soundPath, etherwarpVolume, etherwarpRandomPitch, px, py, pz);
    }

    /**
     * Plays a custom sound file directly from disk using Minecraft's OGG decoder
     */
    private void playCustomSoundFile(Path soundPath, float volume, boolean useRandomPitch, double x, double y, double z) {
        if (mc.level == null) return;

        final double fx = x;
        final double fy = y;
        final double fz = z;

        // Play the OGG file directly with OpenAL
        new Thread(() -> {
            try {
                // Use Minecraft's OGG decoder to load the sound
                var inputStream = Files.newInputStream(soundPath);
                var oggAudioStream = new net.minecraft.client.sounds.JOrbisAudioStream(inputStream);

                float pitch = useRandomPitch ? (0.8f + RANDOM.nextFloat() * 0.4f) : 1.0f;

                // Read all audio data into a ByteBuffer
                var audioFormat = oggAudioStream.getFormat();
                int sampleRate = (int) audioFormat.getSampleRate();
                int channelCount = audioFormat.getChannels();

                // Read audio data
                java.nio.ByteBuffer audioData = oggAudioStream.readAll();

                // Create OpenAL buffer
                int alFormat = channelCount == 1 ?
                    org.lwjgl.openal.AL10.AL_FORMAT_MONO16 :
                    org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;

                int buffer = org.lwjgl.openal.AL10.alGenBuffers();
                org.lwjgl.openal.AL10.alBufferData(buffer, alFormat, audioData, sampleRate);

                // Create OpenAL source
                int source = org.lwjgl.openal.AL10.alGenSources();
                org.lwjgl.openal.AL10.alSourcei(source, org.lwjgl.openal.AL10.AL_BUFFER, buffer);
                org.lwjgl.openal.AL10.alSourcef(source, org.lwjgl.openal.AL10.AL_GAIN, volume);
                org.lwjgl.openal.AL10.alSourcef(source, org.lwjgl.openal.AL10.AL_PITCH, pitch);
                org.lwjgl.openal.AL10.alSource3f(source, org.lwjgl.openal.AL10.AL_POSITION, (float) fx, (float) fy, (float) fz);

                // Play the sound
                org.lwjgl.openal.AL10.alSourcePlay(source);

                // Wait for sound to finish, then cleanup
                while (org.lwjgl.openal.AL10.alGetSourcei(source, org.lwjgl.openal.AL10.AL_SOURCE_STATE) == org.lwjgl.openal.AL10.AL_PLAYING) {
                    Thread.sleep(10);
                }

                org.lwjgl.openal.AL10.alDeleteSources(source);
                org.lwjgl.openal.AL10.alDeleteBuffers(buffer);
                oggAudioStream.close();

            } catch (Exception e) {
                mc.execute(() -> playFallbackSound(fx, fy, fz, volume, useRandomPitch));
            }
        }).start();
    }

    /**
     * Plays a fallback sound (note.pling)
     */
    private void playFallbackSound(double x, double y, double z, float volume, boolean useRandomPitch) {
        if (mc.level == null) return;

        float pitch = useRandomPitch ? (0.8f + RANDOM.nextFloat() * 0.4f) : 1.0f;

        SimpleSoundInstance sound = new SimpleSoundInstance(
            SoundEvents.NOTE_BLOCK_PLING.value(),
            SoundSource.PLAYERS,
            volume,
            pitch,
            RANDOM,
            x,
            y,
            z
        );

        mc.getSoundManager().play(sound);
    }

    private void clampSoundIndexes() {
        if (hitSoundFiles.isEmpty()) {
            selectedHitSoundIndex = 0;
        } else if (selectedHitSoundIndex >= hitSoundFiles.size()) {
            selectedHitSoundIndex = hitSoundFiles.size() - 1;
        }

        if (etherwarpSoundFiles.isEmpty()) {
            selectedEtherwarpSoundIndex = 0;
        } else if (selectedEtherwarpSoundIndex >= etherwarpSoundFiles.size()) {
            selectedEtherwarpSoundIndex = etherwarpSoundFiles.size() - 1;
        }
    }

    // Config getters/setters

    public boolean isRandomPitch() {
        return randomPitch;
    }

    public void setRandomPitch(boolean randomPitch) {
        this.randomPitch = randomPitch;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public boolean isCustomEtherwarpEnabled() {
        return customEtherwarpEnabled;
    }

    public void setCustomEtherwarpEnabled(boolean enabled) {
        this.customEtherwarpEnabled = enabled;
    }

    public float getEtherwarpVolume() {
        return etherwarpVolume;
    }

    public void setEtherwarpVolume(float volume) {
        this.etherwarpVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public boolean isEtherwarpRandomPitch() {
        return etherwarpRandomPitch;
    }

    public void setEtherwarpRandomPitch(boolean enabled) {
        this.etherwarpRandomPitch = enabled;
    }

    public List<String> getHitSoundFiles() {
        return new ArrayList<>(hitSoundFiles);
    }

    public List<String> getEtherwarpSoundFiles() {
        return new ArrayList<>(etherwarpSoundFiles);
    }

    public int getSelectedHitSoundIndex() {
        return selectedHitSoundIndex;
    }

    public void setSelectedHitSoundIndex(int index) {
        if (index >= 0 && index < hitSoundFiles.size()) {
            this.selectedHitSoundIndex = index;
        }
    }

    public int getSelectedEtherwarpSoundIndex() {
        return selectedEtherwarpSoundIndex;
    }

    public void setSelectedEtherwarpSoundIndex(int index) {
        if (index >= 0 && index < etherwarpSoundFiles.size()) {
            this.selectedEtherwarpSoundIndex = index;
        }
    }

    public String getSelectedHitSoundName() {
        if (hitSoundFiles.isEmpty()) return "None";
        return hitSoundFiles.get(selectedHitSoundIndex);
    }

    public String getSelectedEtherwarpSoundName() {
        if (etherwarpSoundFiles.isEmpty()) return "None";
        return etherwarpSoundFiles.get(selectedEtherwarpSoundIndex);
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        object.addProperty("randomPitch", this.randomPitch);
        object.addProperty("volume", this.volume);
        object.addProperty("selectedHitSoundIndex", this.selectedHitSoundIndex);
        object.addProperty("selectedEtherwarpSoundIndex", this.selectedEtherwarpSoundIndex);
        object.addProperty("customEtherwarpEnabled", this.customEtherwarpEnabled);
        object.addProperty("etherwarpVolume", this.etherwarpVolume);
        object.addProperty("etherwarpRandomPitch", this.etherwarpRandomPitch);
        return object;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        if (data.has("randomPitch")) {
            setRandomPitch(data.get("randomPitch").getAsBoolean());
        }
        if (data.has("volume")) {
            setVolume(data.get("volume").getAsFloat());
        }
        if (data.has("customEtherwarpEnabled")) {
            setCustomEtherwarpEnabled(data.get("customEtherwarpEnabled").getAsBoolean());
        }
        if (data.has("etherwarpVolume")) {
            setEtherwarpVolume(data.get("etherwarpVolume").getAsFloat());
        }
        if (data.has("etherwarpRandomPitch")) {
            setEtherwarpRandomPitch(data.get("etherwarpRandomPitch").getAsBoolean());
        }
        if (data.has("selectedHitSoundIndex")) {
            setSelectedHitSoundIndex(data.get("selectedHitSoundIndex").getAsInt());
        }
        if (data.has("selectedEtherwarpSoundIndex")) {
            setSelectedEtherwarpSoundIndex(data.get("selectedEtherwarpSoundIndex").getAsInt());
        }

        clampSoundIndexes();
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Hit sound selector
        String[] hitOptions = hitSoundFiles.isEmpty()
            ? new String[]{"None"}
            : hitSoundFiles.toArray(new String[0]);
        settings.add(new DropdownSetting(
            "Hit Sound",
            "Select the custom hit sound",
            "hitsound_selection",
            hitOptions,
            () -> hitSoundFiles.isEmpty() ? 0 : selectedHitSoundIndex,
            this::setSelectedHitSoundIndex
        ));

        // Etherwarp selector (visible only when custom etherwarp is enabled)
        String[] etherOptions = etherwarpSoundFiles.isEmpty()
            ? new String[]{"None"}
            : etherwarpSoundFiles.toArray(new String[0]);
        settings.add(new DropdownSetting(
            "Etherwarp Sound",
            "Select the custom etherwarp sound",
            "hitsound_ether_selection",
            etherOptions,
            () -> etherwarpSoundFiles.isEmpty() ? 0 : selectedEtherwarpSoundIndex,
            this::setSelectedEtherwarpSoundIndex
        ).setVisible(this::isCustomEtherwarpEnabled));

        // Hit Sound Volume
        settings.add(new SliderSetting(
            "Hit Volume",
            "Volume of custom hit sounds",
            "hitsound_volume",
            0f, 1f,
            () -> volume,
            val -> volume = val
        ).withDecimals(2).asPercentage());

        // Random Pitch
        settings.add(new CheckboxSetting(
            "Random Pitch",
            "Randomize pitch for hit sounds",
            "hitsound_random_pitch",
            () -> randomPitch,
            val -> randomPitch = val
        ));

        // Custom Etherwarp Sound
        settings.add(new CheckboxSetting(
            "Custom Etherwarp",
            "Replace etherwarp sound with custom sound",
            "hitsound_custom_etherwarp",
            () -> customEtherwarpEnabled,
            val -> customEtherwarpEnabled = val
        ));

        // Etherwarp Volume (only visible when custom etherwarp is enabled)
        settings.add(new SliderSetting(
            "Etherwarp Volume",
            "Volume of custom etherwarp sound",
            "hitsound_etherwarp_volume",
            0f, 1f,
            () -> etherwarpVolume,
            val -> etherwarpVolume = val
        ).withDecimals(2).asPercentage().setVisible(() -> customEtherwarpEnabled));

        // Etherwarp Random Pitch (only visible when custom etherwarp is enabled)
        settings.add(new CheckboxSetting(
            "Etherwarp Random Pitch",
            "Randomize pitch for etherwarp sound",
            "hitsound_etherwarp_random_pitch",
            () -> etherwarpRandomPitch,
            val -> etherwarpRandomPitch = val
        ).setVisible(() -> customEtherwarpEnabled));

        settings.add(new ButtonSetting(
            "Reload Sounds",
            "Rescan the custom sound folder",
            "hitsound_reload",
            this::loadSoundFiles
        ));

        return settings;
    }
}
