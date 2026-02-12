package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.CommandBridge;
import dev.hunchclient.mixin.accessor.ChatHudAccessor;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.misc.ChatUtilsModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Intercepts chat messages to process commands with history support
 * Also adds visual indicator when HunchClient command is detected
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    /**
     * Inject at HEAD to intercept message before it's sent
     * IMPORTANT: Add to history for . commands so Arrow-Up works
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        // Handle . prefix commands (always our commands)
        if (chatText.startsWith(CommandBridge.SECONDARY_PREFIX)) {
            // Add to command history manually since vanilla only does this for /
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.getChat().addRecentChat(chatText);
            }

            boolean handled = CommandBridge.execute(chatText);
            if (handled) {
                mc.setScreen(null); // Close chat screen
                ci.cancel();
            }
            return;
        }

        // Handle /hunchclient prefix (our commands with /)
        if (chatText.startsWith(CommandBridge.HUNCHCLIENT_PREFIX)) {
            // Vanilla handles history for / commands, so we don't need to
            boolean handled = CommandBridge.execute(chatText);
            if (handled) {
                Minecraft.getInstance().setScreen(null);
                ci.cancel();
            }
            return;
        }

        // Handle /hc prefix (short version)
        if (chatText.startsWith(CommandBridge.HC_PREFIX)) {
            // Vanilla handles history for / commands, so we don't need to
            boolean handled = CommandBridge.execute(chatText);
            if (handled) {
                Minecraft.getInstance().setScreen(null);
                ci.cancel();
            }
            return;
        }

        // All other / commands go to vanilla/server (no interference)
    }


    /**
     * Add custom visual indicator when HunchClient command is detected
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (input == null) return;

        String text = input.getValue();

        // Check if this is a HunchClient command
        if (text.startsWith(CommandBridge.SECONDARY_PREFIX) ||
            text.startsWith(CommandBridge.HUNCHCLIENT_PREFIX) ||
            text.startsWith(CommandBridge.HC_PREFIX)) {

            // Get the chat field bounds
            int x = input.getX() - 2;
            int y = input.getY() - 2;
            int width = input.getWidth() + 4;
            int height = input.getHeight() + 4;

            // Draw a subtle purple outline (HunchClient color)
            // Top
            context.fill(x, y, x + width, y + 1, 0xFF9D4DFF);
            // Bottom
            context.fill(x, y + height - 1, x + width, y + height, 0xFF9D4DFF);
            // Left
            context.fill(x, y, x + 1, y + height, 0xFF9D4DFF);
            // Right
            context.fill(x + width - 1, y, x + width, y + height, 0xFF9D4DFF);
        }
    }

    /**
     * Handle right-click to copy chat message (CopyChat feature)
     * MC 1.21 uses Click object instead of double x, double y, int button
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        // Right-click (button 1) for CopyChat
        if (click.button() != 1) return;

        ChatUtilsModule chatUtils = ModuleCache.get(ChatUtilsModule.class);
        if (chatUtils == null || !chatUtils.isEnabled() || !chatUtils.isCopyChat()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) return;

        ChatHudAccessor chatHud = (ChatHudAccessor) client.gui.getChat();
        List<GuiMessage.Line> visibleMessages = chatHud.getVisibleMessages();

        if (visibleMessages == null || visibleMessages.isEmpty()) return;

        try {
            double mouseX = click.x();
            double mouseY = click.y();
            double chatX = chatHud.invokeToChatLineX(mouseX);
            double chatY = chatHud.invokeToChatLineY(mouseY);
            int lineIndex = chatHud.invokeGetMessageLineIndex(chatX, chatY);

            if (lineIndex < 0 || lineIndex >= visibleMessages.size()) return;

            GuiMessage.Line line = visibleMessages.get(lineIndex);
            if (line == null) return;

            // Build the message text
            StringBuilder sb = new StringBuilder();
            line.content().accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });

            // Check for multi-line messages (lines starting with space are continuations)
            for (int i = 1; i <= 9; i++) {
                int nextIdx = lineIndex - i;
                if (nextIdx < 0 || nextIdx >= visibleMessages.size()) break;

                GuiMessage.Line nextLine = visibleMessages.get(nextIdx);
                if (nextLine == null) break;

                StringBuilder lineSb = new StringBuilder();
                nextLine.content().accept((index, style, codePoint) -> {
                    lineSb.appendCodePoint(codePoint);
                    return true;
                });

                String lineStr = lineSb.toString();
                if (!lineStr.startsWith(" ")) break;
                sb.append(lineStr);
            }

            // Copy to clipboard
            client.keyboardHandler.setClipboard(sb.toString());

            // Send feedback
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal("§a[HunchClient] Copied message to clipboard!"), true);
            }

            cir.setReturnValue(true);
        } catch (Exception e) {
            // Silently fail if something goes wrong
        }
    }
}