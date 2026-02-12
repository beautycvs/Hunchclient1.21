package dev.hunchclient.util;

import net.minecraft.client.Minecraft;

/** Interface for objects that need per-tick updates. */
public interface Tickable {
    void tick(Minecraft client);
}
