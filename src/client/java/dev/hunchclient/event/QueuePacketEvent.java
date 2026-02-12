package dev.hunchclient.event;

import net.minecraft.network.protocol.Packet;

/**
 * Event fired before an outgoing packet is sent.
 * Modules can set the action to QUEUE to delay the packet.
 *
 * Based on LiquidBounce's QueuePacketEvent concept.
 */
public class QueuePacketEvent {

    private final Packet<?> packet;
    private Action action = Action.PASS;

    public QueuePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    /**
     * Get the packet being sent.
     */
    public Packet<?> getPacket() {
        return packet;
    }

    /**
     * Get the current action for this packet.
     */
    public Action getAction() {
        return action;
    }

    /**
     * Set the action for this packet.
     * Use Action.QUEUE to delay the packet.
     */
    public void setAction(Action action) {
        this.action = action;
    }

    /**
     * Actions that can be taken for a packet.
     */
    public enum Action {
        /** Send the packet normally */
        PASS,
        /** Queue the packet for later */
        QUEUE,
        /** Cancel the packet entirely (don't send) */
        CANCEL
    }
}
