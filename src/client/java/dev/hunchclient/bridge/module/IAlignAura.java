package dev.hunchclient.bridge.module;

/**
 * Bridge interface for AlignAuraModule - called by mixins.
 */
public interface IAlignAura {
    boolean isEnabled();
    void onChatMessage(String message);
}
