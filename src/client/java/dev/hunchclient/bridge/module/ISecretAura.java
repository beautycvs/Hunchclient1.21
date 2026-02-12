package dev.hunchclient.bridge.module;

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

/**
 * Bridge interface for SecretAuraModule - called by mixins.
 */
public interface ISecretAura {
    boolean handleOpenScreen(ClientboundOpenScreenPacket packet);
}
