package dev.hunchclient.util;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class TitleMusicRegistry {
    public static SoundEvent TITLE_MUSIC_SOUND;
    public static SoundEvent LIMBO_SOUND;

    public static void register() {
        TITLE_MUSIC_SOUND = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            ResourceLocation.fromNamespaceAndPath("hunchclient", "title_music"),
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("hunchclient", "title_music"))
        );

        LIMBO_SOUND = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            ResourceLocation.fromNamespaceAndPath("hunchclient", "limbo_sound"),
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("hunchclient", "limbo_sound"))
        );
    }
}
