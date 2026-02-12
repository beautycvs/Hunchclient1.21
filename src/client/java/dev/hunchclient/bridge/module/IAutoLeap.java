package dev.hunchclient.bridge.module;

/**
 * Bridge interface for AutoLeapModule - called by mixins.
 */
public interface IAutoLeap {
    boolean handleLeapMessage(String message);
}
