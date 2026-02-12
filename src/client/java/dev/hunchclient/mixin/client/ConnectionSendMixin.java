package dev.hunchclient.mixin.client;

import dev.hunchclient.event.QueuePacketEvent;
import dev.hunchclient.network.PacketQueueManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept packets for the PacketQueueManager.
 * This enables Blink functionality by allowing packets to be queued.
 *
 * NOTE: FakeLag is now handled by LagChannelHandler injected into Netty pipeline.
 * This blocks at a deeper level (Netty I/O thread) for true Clumsy-style lag.
 */
@Mixin(Connection.class)
public class ConnectionSendMixin {

    /**
     * Intercept OUTGOING packets for BLINK mode (packet queuing).
     * FakeLag is handled by LagChannelHandler in the Netty pipeline.
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onPacketSend(Packet<?> packet, CallbackInfo ci) {
        PacketQueueManager manager = PacketQueueManager.getInstance();

        // BLINK MODE: Queue packet for manual flush
        QueuePacketEvent.Action action = manager.handleOutgoingPacket(packet);

        // If action is QUEUE or CANCEL, don't send the packet now
        if (action == QueuePacketEvent.Action.QUEUE || action == QueuePacketEvent.Action.CANCEL) {
            ci.cancel();
        }
        // PASS = send normally (don't cancel)
    }
}
