package dev.hunchclient.bridge;

import dev.hunchclient.network.PacketQueueManager;
import net.minecraft.network.protocol.Packet;

public final class NetworkBridge {

    private NetworkBridge() {}

    /**
     * Returns ordinal of QueuePacketEvent.Action:
     * 0 = ALLOW, 1 = QUEUE, 2 = CANCEL
     */
    public static int handleOutgoingPacket(Packet<?> packet) {
        return PacketQueueManager.getInstance().handleOutgoingPacket(packet).ordinal();
    }
}
