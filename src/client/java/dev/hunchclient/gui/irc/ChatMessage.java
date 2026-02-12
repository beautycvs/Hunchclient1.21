package dev.hunchclient.gui.irc;

/**
 * Represents a single IRC chat message
 */
public class ChatMessage {
    private final String sender;
    private final String content;
    private final long timestamp;
    private final boolean isPrivate;
    private final String recipient; // For DMs
    private final boolean isFromMe;

    public ChatMessage(String sender, String content, long timestamp, boolean isFromMe) {
        this(sender, content, timestamp, false, null, isFromMe);
    }

    public ChatMessage(String sender, String content, long timestamp, boolean isPrivate, String recipient, boolean isFromMe) {
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
        this.isPrivate = isPrivate;
        this.recipient = recipient;
        this.isFromMe = isFromMe;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public String getRecipient() {
        return recipient;
    }

    public boolean isFromMe() {
        return isFromMe;
    }

    public String getFormattedTime() {
        long seconds = timestamp / 1000;
        long hours = (seconds / 3600) % 24;
        long minutes = (seconds / 60) % 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
