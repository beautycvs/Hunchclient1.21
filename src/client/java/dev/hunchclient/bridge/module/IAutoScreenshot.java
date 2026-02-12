package dev.hunchclient.bridge.module;

/**
 * Bridge interface for AutoScreenshotModule - called by mixins.
 */
public interface IAutoScreenshot {
    void onChatMessage(String message);
}
