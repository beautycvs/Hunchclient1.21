package dev.hunchclient.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hunchclient.freecam.FreecamState;
import dev.hunchclient.module.impl.FreecamModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to make Freecam completely undetectable by anti-cheat systems like Grim.
 *
 * Instead of blocking all packets (which Grim detects as "packet canceller"),
 * we send fake "idle" packets that make the player look like they're AFK.
 *
 * The server sees the player standing still with no input - completely normal.
 */
@Mixin(LocalPlayer.class)
public class FreecamLocalPlayerMixin {

    /**
     * CRITICAL FIX: Force isControlledCamera() to return TRUE during freecam.
     * Without this, sendPosition() skips all packet sending because
     * getCameraEntity() returns FreeCamera instead of the player.
     */
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void hunchclient$forceControlledCamera(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamModule.isActiveAndNotPlayerControl()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Intercept ALL packet sends in sendPosition() and replace with saved position.
     * This makes the player look like they're standing still at the saved location.
     * Using WrapOperation for compatibility with other mods like ViaFabricPlus.
     */
    @WrapOperation(method = "sendPosition",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void hunchclient$spoofPositionPackets(ClientPacketListener listener, Packet<?> packet, Operation<Void> original) {
        if (!FreecamModule.isActiveAndNotPlayerControl()) {
            // Normal mode - call the original (allows other mods to also wrap)
            original.call(listener, packet);
            return;
        }

        // Freecam active - send Pos packet with saved position
        // This tells server "I'm still at saved position" - like a player standing still
        Vec3 savedPos = new Vec3(
            FreecamState.getSavedX(),
            FreecamState.getSavedY(),
            FreecamState.getSavedZ()
        );
        original.call(listener, new ServerboundMovePlayerPacket.Pos(
            savedPos,
            FreecamState.isSavedOnGround(),
            FreecamState.isSavedHorizontalCollision()
        ));
    }

    /**
     * Block sprint state changes - AFK players don't sprint
     */
    @Inject(method = "sendIsSprintingIfNeeded", at = @At("HEAD"), cancellable = true)
    private void hunchclient$blockSprintPacket(CallbackInfo ci) {
        if (FreecamModule.isActiveAndNotPlayerControl()) {
            ci.cancel();
        }
    }

    /**
     * Wrap ALL packet sends in tick() - filter based on packet type.
     * Using WrapOperation for compatibility with other mods like ViaFabricPlus.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void hunchclient$wrapTickPackets(ClientPacketListener listener, Packet<?> packet, Operation<Void> original) {
        if (!FreecamModule.isActiveAndNotPlayerControl()) {
            // Normal mode - allow the packet through
            original.call(listener, packet);
            return;
        }

        // Freecam active - block input and vehicle packets
        // AFK players don't send input changes or move vehicles
        if (packet instanceof ServerboundPlayerInputPacket ||
            packet instanceof ServerboundMoveVehiclePacket ||
            packet instanceof ServerboundMovePlayerPacket.Rot) {
            // Don't send - AFK players have no input/movement
            return;
        }

        // Allow other packets through
        original.call(listener, packet);
    }

    /**
     * Block abilities update packets (flying mode changes, etc.)
     */
    @Inject(method = "onUpdateAbilities", at = @At("HEAD"), cancellable = true)
    private void hunchclient$blockAbilitiesUpdate(CallbackInfo ci) {
        if (FreecamModule.isActiveAndNotPlayerControl()) {
            ci.cancel();
        }
    }

    /**
     * Block swing (attack animation) - clicks should be blocked entirely
     */
    @Inject(method = "swing", at = @At("HEAD"), cancellable = true)
    private void hunchclient$blockSwing(CallbackInfo ci) {
        if (FreecamModule.isActiveAndNotPlayerControl()) {
            ci.cancel();
        }
    }

    /**
     * Block item drop
     */
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void hunchclient$blockDrop(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamModule.isActiveAndNotPlayerControl()) {
            cir.setReturnValue(false);
        }
    }
}
