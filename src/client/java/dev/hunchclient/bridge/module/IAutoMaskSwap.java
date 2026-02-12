package dev.hunchclient.bridge.module;

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

/**
 * Bridge interface for AutoMaskSwapModule - called by mixins.
 */
public interface IAutoMaskSwap {
    boolean handleOpenScreen(ClientboundOpenScreenPacket packet);
}
