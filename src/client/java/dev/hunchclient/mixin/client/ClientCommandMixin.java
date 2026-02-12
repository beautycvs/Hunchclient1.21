package dev.hunchclient.mixin.client;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.IrcRelayModule;
import dev.hunchclient.module.impl.KaomojiReplacerModule;
import dev.hunchclient.module.impl.MeowMessagesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientCommandMixin {

    // Multiplayer: sendChatMessage - Intercept for IRC mode and chat transformations
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        // Handle IRC chat mode first (non-command messages)
        if (!message.startsWith("/") && handleIrcChat(message)) {
            ci.cancel();
            return;
        }

        // Apply chat transformations (KaomojiReplacer and MeowMessages)
        String transformedMessage = transformChatMessage(message);
        if (!transformedMessage.equals(message)) {
            ci.cancel();
            // Send transformed message
            Minecraft.getInstance().player.connection.sendChat(transformedMessage);
        }
    }

    private boolean handleIrcChat(String message) {
        IrcRelayModule irc = ModuleCache.get(IrcRelayModule.class);
        if (irc == null || !irc.isEnabled()) {
            return false;
        }
        return irc.handleOutgoingChat(message);
    }

    private String transformChatMessage(String message) {
        String result = message;

        // Apply MeowMessages transformation first
        MeowMessagesModule meowMessages = ModuleCache.get(MeowMessagesModule.class);
        if (meowMessages != null) {
            result = meowMessages.transformMessage(result);
        }

        // Then apply KaomojiReplacer (so kaomojis can replace o/ in meow messages too)
        KaomojiReplacerModule kaomojiReplacer = ModuleCache.get(KaomojiReplacerModule.class);
        if (kaomojiReplacer != null) {
            result = kaomojiReplacer.replaceKaomojis(result);
        }

        return result;
    }
}
