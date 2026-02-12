package dev.hunchclient.bridge.module;

/**
 * Bridge interface for AutoKickModule - called by mixins.
 */
public interface IAutoKick {
    boolean isEnabled();
    boolean handleChatMessage(String message);
}
