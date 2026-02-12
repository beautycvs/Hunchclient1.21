package dev.hunchclient.bridge.module;

/**
 * Bridge interface for BossBlockMinerModule - called by mixins.
 */
public interface IBossBlockMiner {
    void onChatMessage(String message);
}
