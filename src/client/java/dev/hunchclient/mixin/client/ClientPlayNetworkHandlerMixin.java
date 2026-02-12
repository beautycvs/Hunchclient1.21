package dev.hunchclient.mixin.client;

import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.ChatMessageEvent;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import dev.hunchclient.module.IAutoScreenshot;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.dungeons.AlignAuraModule;
import dev.hunchclient.module.impl.dungeons.AutoSSModule;
import dev.hunchclient.module.impl.dungeons.AutoMaskSwapModule;
import dev.hunchclient.module.impl.dungeons.CloseDungeonChestsModule;
import dev.hunchclient.module.impl.dungeons.SecretAuraModule;
import dev.hunchclient.module.impl.misc.WindowsMediaControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    /**
     * Flag to prevent infinite recursion when re-handling packets.
     * When we call packet.handle(), it triggers this mixin again - this flag prevents that.
     */
    @Unique
    private static final ThreadLocal<Boolean> hunchclient$isReprocessing = ThreadLocal.withInitial(() -> false);

    @ModifyVariable(
        method = "handleSystemChat",
        at = @At("HEAD"),
        argsOnly = true
    )
    private ClientboundSystemChatPacket hunchclient$sanitizeGameMessage(ClientboundSystemChatPacket packet) {
        INameProtect module = ModuleBridge.nameProtect();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module == null) {
            return packet;
        }

        try {
            Component content = packet.content();
            if (content != null) {
                Component sanitized = module.sanitizeText(content);
                if (sanitized != content) {
                    return new ClientboundSystemChatPacket(sanitized, packet.overlay());
                }
            }
        } catch (Exception ignored) {
        }

        return packet;
    }

    /**
     * Intercept chat packets and fire ChatMessageEvent on the MAIN THREAD.
     * This allows event handlers to cancel chat messages.
     *
     * Flow:
     * 1. Packet arrives on Netty thread
     * 2. We cancel it immediately
     * 3. Schedule processing on main thread
     * 4. Fire ChatMessageEvent
     * 5. If not cancelled, re-handle the packet
     */
    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onChatMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        // Skip if we're re-processing (prevents infinite loop)
        if (hunchclient$isReprocessing.get()) {
            // Still notify modules during reprocessing
            hunchclient$notifyModules(packet);
            return;
        }

        // Cancel the packet immediately (runs on Netty thread)
        ci.cancel();

        // Schedule processing on main thread
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                Component content = packet.content();
                if (content == null) return;

                // Fire ChatMessageEvent (cancellable)
                ChatMessageEvent event = new ChatMessageEvent(content, packet.overlay());
                HunchModClient.EVENT_BUS.post(event);

                // If cancelled, don't process the packet
                if (event.isCancelled()) {
                    return;
                }

                // Re-handle the packet on main thread
                ClientPacketListener listener = mc.getConnection();
                if (listener != null) {
                    hunchclient$isReprocessing.set(true);
                    try {
                        packet.handle(listener);
                    } finally {
                        hunchclient$isReprocessing.set(false);
                    }
                }
            } catch (Exception e) {
                System.err.println("[HunchClient] Error processing chat packet: " + e.getMessage());
            }
        });
    }

    /**
     * Notify modules about the chat message.
     * Called during packet reprocessing (on main thread).
     */
    @Unique
    private void hunchclient$notifyModules(ClientboundSystemChatPacket packet) {
        try {
            Component content = packet.content();
            if (content != null) {
                String message = content.getString();

                // AlignAura P3 detection
                AlignAuraModule alignAura = ModuleCache.get(AlignAuraModule.class);
                if (alignAura != null && alignAura.isEnabled()) {
                    alignAura.onChatMessage(message);
                }

                // AutoSS chat detection
                IAutoScreenshot autoSS = (IAutoScreenshot) ModuleCache.get(AutoSSModule.class);
                if (autoSS != null) {
                    autoSS.onChatMessage(message);
                }

                // BossBlockMiner boss detection (ALWAYS listen, even when disabled - for instant boss config loading)
                dev.hunchclient.module.impl.dungeons.BossBlockMinerModule bossBlockMiner =
                    ModuleCache.get(dev.hunchclient.module.impl.dungeons.BossBlockMinerModule.class);
                if (bossBlockMiner != null) {
                    bossBlockMiner.onChatMessage(message);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Intercept player chat packets and fire ChatMessageEvent.
     * This allows modules to react to player messages (e.g., for test mode).
     */
    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void hunchclient$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        try {
            // Get the message content (unsigned or signed)
            Component content = packet.unsignedContent() != null
                ? packet.unsignedContent()
                : Component.literal(packet.body().content());

            // Decorate with chat type (adds player name prefix etc.)
            Component decorated = packet.chatType().decorate(content);

            // Fire ChatMessageEvent (playerChat = true)
            ChatMessageEvent event = new ChatMessageEvent(decorated, false, true);
            HunchModClient.EVENT_BUS.post(event);
        } catch (Exception e) {
            // Fallback: just use raw content
            try {
                String rawContent = packet.body().content();
                ChatMessageEvent event = new ChatMessageEvent(Component.literal(rawContent), false, true);
                HunchModClient.EVENT_BUS.post(event);
            } catch (Exception ignored) {
            }
        }
    }

    @Inject(method = "handleServerData", at = @At("HEAD"))
    private void onServerMetadata(ClientboundServerDataPacket packet, CallbackInfo ci) {
        // Check if server metadata contains "limbo"
        String description = packet.motd().getString().toLowerCase();
        System.out.println("[HunchClient] Server metadata: " + description);

        if (description.contains("limbo")) {
            System.out.println("[HunchClient] LIMBO PACKET DETECTED!");
            playLimboSound();
        }
    }

    private void playLimboSound() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSoundManager() != null && dev.hunchclient.util.TitleMusicRegistry.LIMBO_SOUND != null) {
                // Start the Limbo Music Manager to play Eleanor Rigby on loop
                dev.hunchclient.util.LimboMusicManager.getInstance().startLimboMusic();
            }
        } catch (Exception e) {
            System.err.println("[HunchClient] Limbo sound failed: " + e.getMessage());
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void hunchclient$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        // CloseDungeonChests module
        CloseDungeonChestsModule closeModule = ModuleCache.get(CloseDungeonChestsModule.class);
        if (closeModule != null && closeModule.handleOpenScreen(packet)) {
            ci.cancel();
            return;
        }

        // SecretAura auto-close
        SecretAuraModule secretAura = ModuleCache.get(SecretAuraModule.class);
        if (secretAura != null && secretAura.handleOpenScreen(packet)) {
            ci.cancel();
            return;
        }

        // AutoMaskSwap GUI suppression
        AutoMaskSwapModule maskSwap = ModuleCache.get(AutoMaskSwapModule.class);
        if (maskSwap != null && maskSwap.handleOpenScreen(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void hunchclient$onGameJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        // Restart SMTC Reader on world load/join to ensure fresh connection
        try {
            WindowsMediaControl.restart();
            System.out.println("[HunchClient] SMTC Reader restarted on world join");
        } catch (Exception e) {
            System.err.println("[HunchClient] Failed to restart SMTC Reader: " + e.getMessage());
        }
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void hunchclient$onPlayerRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        // Restart SMTC Reader on respawn (dimension change, death, etc.)
        try {
            WindowsMediaControl.restart();
            System.out.println("[HunchClient] SMTC Reader restarted on player respawn");
        } catch (Exception e) {
            System.err.println("[HunchClient] Failed to restart SMTC Reader: " + e.getMessage());
        }
    }

    // DISABLED: Packet order validation (mixin can't find inherited method)
    // TODO: Move to ClientCommonNetworkHandlerMixin
    /*
    @Inject(method = "sendPacket(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$validatePacketOrder(Packet<?> packet, CallbackInfo ci) {
        if (!PacketOrderValidator.getInstance().validatePacket(packet)) {
            ci.cancel();
        }
    }
    */
}
