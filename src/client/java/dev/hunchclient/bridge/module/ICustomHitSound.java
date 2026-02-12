package dev.hunchclient.bridge.module;

public interface ICustomHitSound {
    boolean isEnabled();
    boolean onSoundPlay(String soundName, double x, double y, double z, float volume, float pitch);
}
