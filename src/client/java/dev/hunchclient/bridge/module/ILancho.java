package dev.hunchclient.bridge.module;

/**
 * Bridge interface for LanchoModule - called by mixins.
 */
public interface ILancho {
    boolean isEnabled();
    void handleChatMessage(String message);
}
