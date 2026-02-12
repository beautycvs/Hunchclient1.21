package dev.hunchclient.util.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Limbo soundtrack anchored to a world position so it feels like it emanates from the device.
 */
public class LimboDeviceSoundInstance extends LimboCreepySoundInstance {

    public LimboDeviceSoundInstance(SoundEvent sound, Vec3 position) {
        super(sound);
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        updatePosition(position);
    }

    public void updatePosition(Vec3 position) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
