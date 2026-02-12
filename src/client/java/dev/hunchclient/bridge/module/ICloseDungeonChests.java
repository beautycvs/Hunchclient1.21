package dev.hunchclient.bridge.module;

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

/**
 * Bridge interface for CloseDungeonChestsModule - called by mixins.
 */
public interface ICloseDungeonChests {
    boolean handleOpenScreen(ClientboundOpenScreenPacket packet);
}
