package dev.hunchclient.mixin.client;

import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.BlockChangeEvent;
import dev.hunchclient.event.EventBus;
import dev.hunchclient.event.PacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to fire PacketEvent for Terminal Solver
 * Intercepts OpenScreenS2CPacket and ScreenHandlerSlotUpdateS2CPacket
 */
@Mixin(ClientPacketListener.class)
public class PacketEventMixin {

    /**
     * Fire PacketEvent.Receive for OpenScreenS2CPacket (terminal detection)
     */
    @Inject(method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V", at = @At("HEAD"))
    private void hunchclient$onOpenScreenPacket(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        PacketEvent.Receive event = PacketEvent.Receive.of(packet);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire PacketEvent.Receive for ContainerClosePacket (terminal close from server)
     * CRITICAL: This fires BEFORE Minecraft processes it, allowing immediate mouse abort!
     */
    @Inject(method = "handleContainerClose(Lnet/minecraft/network/protocol/game/ClientboundContainerClosePacket;)V", at = @At("HEAD"))
    private void hunchclient$onContainerClosePacket(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        PacketEvent.Receive event = PacketEvent.Receive.of(packet);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire PacketEvent.Receive for ScreenHandlerSlotUpdateS2CPacket (terminal updates)
     * Also cancel negative slots (like -999) to prevent Minecraft crash
     */
    @Inject(method = "handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onSlotUpdatePacket(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        // Cancel negative slots to prevent Minecraft crash (slot -999 = clicked outside)
        if (packet.getSlot() < 0) {
            ci.cancel();
            return;
        }
        PacketEvent.Receive event = PacketEvent.Receive.of(packet);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire PacketEvent.Receive for ClientboundContainerSetContentPacket (full container updates)
     * CRITICAL for High Ping Mode - this is the "full resync" packet after a click!
     */
    @Inject(method = "handleContainerContent(Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;)V", at = @At("HEAD"))
    private void hunchclient$onContainerContentPacket(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        PacketEvent.Receive event = PacketEvent.Receive.of(packet);
        HunchModClient.EVENT_BUS.post(event);
    }

    /**
     * Fire PacketEvent.Receive for ParticleS2CPacket (CustomMageBeam)
     * Cancellable to allow hiding firework particles
     */
    @Inject(method = "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onParticlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        PacketEvent.Receive event = PacketEvent.Receive.of(packet);
        HunchModClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Fire BlockChangeEvent for single block updates from server
     * CRITICAL for ICant4 - server-sent block updates don't go through Level.setBlock()
     */
    @Inject(method = "handleBlockUpdate(Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;)V", at = @At("TAIL"))
    private void hunchclient$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos pos = packet.getPos();
        BlockState newState = packet.getBlockState();
        BlockState oldState = mc.level.getBlockState(pos); // Note: already updated, but we fire event anyway

        BlockChangeEvent event = BlockChangeEvent.of(pos, oldState, newState);
        EventBus.getInstance().postBlockChange(event);
    }

    /**
     * Fire BlockChangeEvent for multi-block (chunk section) updates from server
     * Handles ClientboundSectionBlocksUpdatePacket for batch updates
     */
    @Inject(method = "handleChunkBlocksUpdate(Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;)V", at = @At("TAIL"))
    private void hunchclient$onChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Iterate through all block changes in the packet
        packet.runUpdates((pos, state) -> {
            BlockState oldState = mc.level.getBlockState(pos); // Already updated
            BlockChangeEvent event = BlockChangeEvent.of(pos.immutable(), oldState, state);
            EventBus.getInstance().postBlockChange(event);
        });
    }

    /**
     * Fire PacketEvent.Send when any packet is sent
     * Note: sendPacket is in ClientCommonNetworkHandler (parent class)
     * For now we only need Receive events for Terminal Solver
     */
    // @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    // private void hunchclient$onPacketSend(Packet<?> packet, CallbackInfo ci) {
    //     PacketEvent.Send event = new PacketEvent.Send(packet);
    //     HunchModClient.EVENT_BUS.post(event);
    //     if (event.isCancelled()) {
    //         ci.cancel();
    //     }
    // }
}
