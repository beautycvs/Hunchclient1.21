package dev.hunchclient.mixin.client;

import dev.hunchclient.mixin.accessor.ChatHudAccessor;
import dev.hunchclient.module.IAutoScreenshot;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import dev.hunchclient.module.impl.AutoLeapModule;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.dungeons.AutoSSModule;
import dev.hunchclient.module.impl.dungeons.FuckDioriteModule;
import dev.hunchclient.module.impl.dungeons.SSHelperModule;
import dev.hunchclient.module.impl.LanchoModule;
import dev.hunchclient.module.impl.misc.ChatUtilsModule;
import dev.hunchclient.module.impl.sbd.AutoKickModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component hunchclient$sanitizeChat(Component message) {
        INameProtect nameProtect = ModuleBridge.nameProtect();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (nameProtect != null) {
            return nameProtect.sanitizeText(message);
        }
        return message;
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(Component message, CallbackInfo ci) {
        // Debug: Log all chat messages
        String messageString = message.getString();
        // System.out.println("[HunchClient] Chat message detected: " + messageString);

        // Check for SBD AutoKick
        AutoKickModule autoKick = ModuleCache.get(AutoKickModule.class);
        if (autoKick != null && autoKick.isEnabled()) {
            autoKick.handleChatMessage(messageString);
        }

        // Check for AutoLeap teleport message
        AutoLeapModule autoLeap = ModuleCache.get(AutoLeapModule.class);
        if (autoLeap != null && autoLeap.handleLeapMessage(messageString)) {
            ci.cancel(); // Cancel the original "You have teleported to..." message
            return;
        }

        // Check for Limbo spawn message (matches "You were spawned in Limbo.")
        if (messageString.toLowerCase().contains("spawned in limbo")) {
            playLimboSound();
            // System.out.println("[HunchClient] LIMBO CHAT DETECTED!");
        }

        // Forward to Lancho module
        LanchoModule lancho = ModuleCache.get(LanchoModule.class);
        if (lancho != null && lancho.isEnabled()) {
            System.out.println("[HunchClient] Forwarding to Lancho: " + messageString);
            lancho.handleChatMessage(messageString);
        } else {
            // System.out.println("[HunchClient] Lancho is null or disabled, skipping");
        }

        IAutoScreenshot autoSS = (IAutoScreenshot) ModuleCache.get(AutoSSModule.class);
        if (autoSS != null) {
            autoSS.onChatMessage(messageString);
        }

        // Forward to SSHelper module
        SSHelperModule ssHelper = ModuleCache.get(SSHelperModule.class);
        if (ssHelper != null && ssHelper.isEnabled()) {
            ssHelper.onChatMessage(message);
        }

        // Forward to FuckDiorite module (Maxor trigger)
        FuckDioriteModule fuckDiorite = ModuleCache.get(FuckDioriteModule.class);
        if (fuckDiorite != null && fuckDiorite.isEnabled()) {
            fuckDiorite.onChatMessage(messageString);
        }
    }

    private void playLimboSound() {
        try {
            // System.out.println("[HunchClient] LIMBO CHAT DETECTED DER SOUND SOLL DANACH LOOPEN FÜR IMMER UND NICHT MEHR AUFHÖREN BIS MAN RELOADET; ER DARF AUCH NICHT DURCH MAIN MUSIC UNTERBROCHEN WERDEN!!!!");
            dev.hunchclient.util.LimboMusicManager.getInstance().startLimboMusic();
        } catch (Exception e) {
            // System.err.println("[HunchClient] Failed to start Limbo music: " + e.getMessage());
        }
    }

    // ========== ChatUtils: Remove Chat Limit ==========

    /**
     * Modify the message limit constant (default 100) to unlimited when enabled.
     * Target the private addMessage method that actually stores messages.
     */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", constant = @Constant(intValue = 100), require = 0)
    private int hunchclient$modifyChatLimit(int original) {
        return ChatUtilsModule.getChatMessageLimit();
    }

    // ========== ChatUtils: Compact Chat ==========

    /**
     * Remove previous duplicate message and update counter.
     * This runs before addMessage to find and remove duplicates.
     */
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void hunchclient$compactChatRemoveDuplicate(Component message, CallbackInfo ci) {
        ChatUtilsModule chatUtils = ModuleCache.get(ChatUtilsModule.class);
        if (chatUtils == null || !chatUtils.isEnabled() || !chatUtils.isCompactChat()) {
            return;
        }

        String messageStr = ChatFormatting.stripFormatting(message.getString());
        if (messageStr == null || messageStr.isBlank()) {
            return;
        }

        // Check if this is a duplicate that should have its previous entry removed
        if (chatUtils.shouldRemovePreviousDuplicate(messageStr)) {
            hunchclient$removePreviousDuplicate(messageStr);
        }
    }

    /**
     * Remove the previous duplicate message from chat.
     */
    @Unique
    private void hunchclient$removePreviousDuplicate(String messageStr) {
        try {
            ChatHudAccessor accessor = (ChatHudAccessor) this;
            List<GuiMessage> messages = accessor.getMessages();
            List<GuiMessage.Line> visibleMessages = accessor.getVisibleMessages();

            // Find and remove the previous duplicate from messages list
            int removedIndex = -1;
            for (int i = 0; i < Math.min(messages.size(), 20); i++) {
                GuiMessage line = messages.get(i);
                String lineStr = ChatFormatting.stripFormatting(line.content().getString());
                // Check if base message matches (strip the (xN) suffix if present)
                String baseLineStr = lineStr.replaceAll(" \\(x\\d+\\)$", "");
                if (baseLineStr.equals(messageStr)) {
                    messages.remove(i);
                    removedIndex = i;
                    break;
                }
            }

            // Also remove from visible messages by matching the addition order
            // The visible messages are linked to the main messages by additionTime/order
            if (removedIndex >= 0 && !visibleMessages.isEmpty()) {
                // Remove visible lines that belong to the removed message
                // We do this by checking recent visible messages
                for (int i = visibleMessages.size() - 1; i >= 0 && i >= visibleMessages.size() - 10; i--) {
                    GuiMessage.Line visible = visibleMessages.get(i);
                    // Convert OrderedText to string via StringBuilder
                    StringBuilder sb = new StringBuilder();
                    visible.content().accept((index, style, codePoint) -> {
                        sb.appendCodePoint(codePoint);
                        return true;
                    });
                    String visibleStr = ChatFormatting.stripFormatting(sb.toString());
                    String baseVisibleStr = visibleStr.replaceAll(" \\(x\\d+\\)$", "");
                    if (baseVisibleStr.equals(messageStr)) {
                        visibleMessages.remove(i);
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail if accessor doesn't work
        }
    }

    /**
     * Process message through compact chat to stack duplicates.
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private Component hunchclient$compactChat(Component message) {
        ChatUtilsModule chatUtils = ModuleCache.get(ChatUtilsModule.class);
        if (chatUtils != null) {
            return chatUtils.processCompactChat(message);
        }
        return message;
    }
}
