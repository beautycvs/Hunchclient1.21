package dev.hunchclient.bridge.module;

import net.minecraft.network.chat.Component;

/**
 * Bridge interface for SSHelperModule - called by mixins.
 */
public interface ISSHelper {
    boolean isEnabled();
    void onChatMessage(Component message);
}
