package dev.hunchclient.module;

/**
 * Public API for AutoScreenshot functionality - called by mixins
 * This interface stays readable, implementation gets obfuscated
 */
public interface IAutoScreenshot {
    void onChatMessage(String message);
}
