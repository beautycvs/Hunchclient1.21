package dev.hunchclient.util.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

public class LimboCreepySoundInstance extends AbstractTickableSoundInstance {

    private static final float BASE_VOLUME = 0.4f;
    private static final float BASE_PITCH = 1.0f;
    private static final float PITCH_DECAY_PER_TICK = 0.9982f;
    private static final float VOLUME_DECAY_PER_TICK = 0.9987f;
    private static final float MIN_PITCH = 0.18f;
    private static final float MIN_VOLUME = 0.045f;
    private static final float WOBBLE_FAST_SPEED = 0.29f;
    private static final float WOBBLE_SLOW_SPEED = 0.071f;
    private static final float WOBBLE_INTENSITY = 0.035f;
    private static final float NOISE_INTENSITY = 0.05f;
    private static final float GLITCH_PROBABILITY = 0.006f;
    private static final float GLITCH_NOISE_MULTIPLIER = 0.35f;
    private static final int SONG_DURATION_TICKS = 280; // 14 seconds × 20 ticks/sec

    private float baseVolume = BASE_VOLUME;
    private float basePitch = BASE_PITCH;
    private int age;
    private int glitchTicks;
    private int loopCount = 0;

    public LimboCreepySoundInstance(SoundEvent sound) {
        super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = BASE_VOLUME;
        this.pitch = BASE_PITCH;
    }

    @Override
    public void tick() {
        age++;

        // Check if song completed a loop
        if (age > 0 && age % SONG_DURATION_TICKS == 0) {
            loopCount++;
            System.out.println("[LimboMusic] Loop #" + loopCount + " completed - increasing distortion!");
        }

        // Only apply decay effects if we've completed at least one loop
        if (loopCount > 0) {
            basePitch = Math.max(MIN_PITCH, basePitch * PITCH_DECAY_PER_TICK);
            baseVolume = Math.max(MIN_VOLUME, baseVolume * VOLUME_DECAY_PER_TICK);
        }

        if (glitchTicks > 0) {
            glitchTicks--;
        } else if (loopCount > 0 && this.random.nextFloat() < GLITCH_PROBABILITY) {
            glitchTicks = 12 + this.random.nextInt(28);
        }

        float wobble = 0;
        float noise = 0;

        // Only apply effects after first loop
        if (loopCount > 0) {
            wobble = Mth.sin(age * WOBBLE_SLOW_SPEED) * WOBBLE_INTENSITY
                + Mth.sin(age * WOBBLE_FAST_SPEED + 1.3f) * (WOBBLE_INTENSITY * 0.7f);
            noise = (this.random.nextFloat() - 0.5f) * NOISE_INTENSITY;
        }

        if (glitchTicks > 0) {
            noise += (this.random.nextFloat() - 0.5f) * NOISE_INTENSITY * GLITCH_NOISE_MULTIPLIER;
            basePitch = Math.max(MIN_PITCH, basePitch * 0.992f);
            baseVolume = Math.max(MIN_VOLUME, baseVolume * 0.995f);
        }

        this.pitch = Mth.clamp(basePitch + wobble + noise * 0.6f, MIN_PITCH, 1.05f);
        this.volume = Mth.clamp(baseVolume + noise * 0.4f, MIN_VOLUME, BASE_VOLUME);
    }

    public void resetState() {
        basePitch = BASE_PITCH;
        baseVolume = BASE_VOLUME;
        age = 0;
        glitchTicks = 0;
        loopCount = 0;
        this.pitch = BASE_PITCH;
        this.volume = BASE_VOLUME;
        System.out.println("[LimboMusic] State reset - starting from clean loop");
    }
}
