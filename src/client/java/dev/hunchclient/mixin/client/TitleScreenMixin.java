package dev.hunchclient.mixin.client;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    // Music is now handled globally by MenuMusicManager in the tick loop
    // No need to manually start it here anymore
}
