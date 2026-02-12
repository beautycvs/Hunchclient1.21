package dev.hunchclient.mixin.client;

import dev.hunchclient.util.PacketDebugger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Connection mixin for packet debugging.
 *
 * Note: Freecam packet blocking is now handled at the source in FreecamLocalPlayerMixin,
 * which prevents packets from being created in the first place. This is more efficient
 * and completely undetectable by anti-cheat systems.
 */
@Mixin(Connection.class)
public class ClientConnectionMixin {

    /**
     * DEBUG: Log all outgoing packets to see what Vanilla sends
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void hunchclient$debugPackets(Packet<?> packet, CallbackInfo ci) {
        if (!PacketDebugger.isDebugEnabled()) return;

        // Log movement and interact packets
        if (packet instanceof ServerboundMovePlayerPacket movePacket) {
            String type = "Unknown";
            if (movePacket instanceof ServerboundMovePlayerPacket.PosRot) type = "Full";
            else if (movePacket instanceof ServerboundMovePlayerPacket.Pos) type = "PositionAndOnGround";
            else if (movePacket instanceof ServerboundMovePlayerPacket.Rot) type = "LookAndOnGround";
            else if (movePacket instanceof ServerboundMovePlayerPacket.StatusOnly) type = "OnGroundOnly";

            System.out.println("[PACKET] Movement: " + type);
        } else if (packet instanceof ServerboundUseItemPacket) {
            System.out.println("[PACKET] InteractItem (Bow shot)");
        }
    }
}
