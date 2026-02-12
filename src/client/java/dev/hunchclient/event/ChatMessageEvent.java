package dev.hunchclient.event;

import net.minecraft.network.chat.Component;

/**
 * Event fired when a chat message is received from the server.
 * This event is fired on the MAIN THREAD (not Netty thread).
 *
 * Cancel this event to prevent the chat message from being displayed.
 *
 * Usage:
 * <pre>
 * @EventHandler
 * public void onChatMessage(ChatMessageEvent event) {
 *     if (shouldBlock(event.getMessage())) {
 *         event.cancel();
 *     }
 * }
 * </pre>
 */
public class ChatMessageEvent extends CancellableEvent {

    private final Component content;
    private final String message;
    private final boolean overlay;
    private final boolean playerChat;

    public ChatMessageEvent(Component content, boolean overlay) {
        this(content, overlay, false);
    }

    public ChatMessageEvent(Component content, boolean overlay, boolean playerChat) {
        this.content = content;
        this.message = content.getString();
        this.overlay = overlay;
        this.playerChat = playerChat;
    }

    /**
     * Get the chat message content as Component (with formatting).
     */
    public Component getContent() {
        return content;
    }

    /**
     * Get the chat message as plain string (without formatting codes).
     */
    public String getMessage() {
        return message;
    }

    /**
     * Whether this is an overlay message (action bar).
     */
    public boolean isOverlay() {
        return overlay;
    }

    /**
     * Whether this is a player chat message (not system/server message).
     * Player messages come from other players, system messages come from the server.
     */
    public boolean isPlayerChat() {
        return playerChat;
    }
}
