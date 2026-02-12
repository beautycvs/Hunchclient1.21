package dev.hunchclient.event;

import net.minecraft.network.protocol.Packet;

/** Packet-related events. */
public abstract class PacketEvent extends CancellableEvent {
    public final Packet<?> packet;

    public PacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public static class Receive extends PacketEvent {
        private Receive(Packet<?> packet) {
            super(packet);
        }

        public static Receive of(Packet<?> packet) {
            return new Receive(packet);
        }
    }

    public static class Send extends PacketEvent {
        public Send(Packet<?> packet) {
            super(packet);
        }
    }
}
