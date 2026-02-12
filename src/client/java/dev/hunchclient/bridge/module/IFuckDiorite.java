package dev.hunchclient.bridge.module;

/**
 * Bridge interface for FuckDioriteModule - called by mixins.
 */
public interface IFuckDiorite {
    boolean isEnabled();
    void onChatMessage(String message);
}
