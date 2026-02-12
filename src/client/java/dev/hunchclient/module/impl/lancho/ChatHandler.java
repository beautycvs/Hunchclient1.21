package dev.hunchclient.module.impl.lancho;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Chat Handler for parsing party/guild/all chat messages
 */
public class ChatHandler {

    private final RequestQueue requestQueue;
    private final LanchoSettings settings;
    private final Minecraft mc = Minecraft.getInstance();

    // Prevent duplicate processing
    private final Set<String> recentMessages = new HashSet<>();
    private final Set<String> activeRequests = new HashSet<>();

    // Chat patterns
    private static final Pattern PARTY_CHAT = Pattern.compile("^Party > (.+)$");
    private static final Pattern GUILD_CHAT = Pattern.compile("^Guild > (.+)$");
    private static final Pattern ALL_CHAT = Pattern.compile("^\\[\\d+\\](.+)$");
    private static final Pattern GUILD_CHAT_ALT = Pattern.compile("^\\[.*?\\]\\s*(.+?):\\s*(.+)$");
    private static final Pattern SENDER_CONTENT = Pattern.compile("^(?:\\[.*?\\]\\s+)?([^:]+):\\s*(.+)$");

    // Trigger words
    private static final String[] TRIGGERS = {"puncho", "=ai", "lancho"};

    public ChatHandler(RequestQueue requestQueue, LanchoSettings settings) {
        this.requestQueue = requestQueue;
        this.settings = settings;

        // Clean up old messages every 5 seconds
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    recentMessages.clear();
                    activeRequests.clear();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    /**
     * Handle incoming chat message
     */
    public void handleChatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String cleanMessage = stripFormatting(message);

        System.out.println("[Lancho] Processing chat: " + cleanMessage);

        // IGNORE our own messages - check for [Lancho] prefix or continuation lines starting with "| "
        if (cleanMessage.contains("[Lancho]")) {
            System.out.println("[Lancho] Ignoring own message (contains [Lancho])");
            return;
        }

        // Ignore continuation messages (start with sender: | )
        String playerName = mc.player != null ? mc.player.getName().getString() : "";
        if (!playerName.isEmpty() && cleanMessage.matches("^(Party|Guild) > .*?" + Pattern.quote(playerName) + ": \\| .*")) {
            System.out.println("[Lancho] Ignoring own continuation message");
            return;
        }

        // Ignore continuation in all chat format: [Level] [Rank] PlayerName: | message
        if (!playerName.isEmpty() && cleanMessage.matches("^\\[\\d+\\].*?" + Pattern.quote(playerName) + ": \\| .*")) {
            System.out.println("[Lancho] Ignoring own all-chat continuation");
            return;
        }

        // Prevent duplicate processing
        String messageKey = cleanMessage;
        if (recentMessages.contains(messageKey)) {
            System.out.println("[Lancho] Duplicate message, skipping");
            return;
        }
        recentMessages.add(messageKey);

        // Parse message type
        ChatMessage parsed = parseMessage(cleanMessage);
        if (parsed == null) {
            System.out.println("[Lancho] Failed to parse message, checking for direct trigger...");

            // Fallback: Check if message directly contains trigger (for Singleplayer/testing)
            if (containsTrigger(cleanMessage)) {
                String fallbackPlayerName = mc.player != null ? mc.player.getName().getString() : "Player";
                parsed = new ChatMessage(fallbackPlayerName, cleanMessage, "test");
                System.out.println("[Lancho] Direct trigger detected in singleplayer!");
            } else {
                System.out.println("[Lancho] No trigger found, ignoring");
                return;
            }
        }

        // Check chat type toggles
        if (parsed.chatType.equals("guild") && !settings.guildChatEnabled) {
            return;
        }
        if (parsed.chatType.equals("all") && !settings.allChatEnabled) {
            return;
        }

        // Check for triggers
        if (!containsTrigger(parsed.content)) {
            return;
        }

        // Extract prompt
        String prompt = extractPrompt(parsed.content);
        if (prompt.isEmpty()) {
            prompt = "hello";
        }

        // Prevent duplicate requests
        String requestKey = parsed.sender + ":" + prompt + ":" + parsed.chatType;
        if (activeRequests.contains(requestKey)) {
            return;
        }
        activeRequests.add(requestKey);

        // Send to queue
        sendMessage("§6[Lancho] §7" + parsed.sender + " is asking... ⚡");
        requestQueue.enqueue(prompt, parsed.chatType, parsed.sender, settings.personality);
    }

    /**
     * Parse chat message
     */
    private ChatMessage parseMessage(String cleanMessage) {
        // Party chat
        Matcher partyMatcher = PARTY_CHAT.matcher(cleanMessage);
        if (partyMatcher.matches()) {
            return parseContent(partyMatcher.group(1), "party");
        }

        // Guild chat
        Matcher guildMatcher = GUILD_CHAT.matcher(cleanMessage);
        if (guildMatcher.matches()) {
            return parseContent(guildMatcher.group(1), "guild");
        }

        // All chat
        Matcher allMatcher = ALL_CHAT.matcher(cleanMessage);
        if (allMatcher.matches()) {
            return parseContent(allMatcher.group(1), "all");
        }

        // Guild chat alternate format
        Matcher guildAltMatcher = GUILD_CHAT_ALT.matcher(cleanMessage);
        if (guildAltMatcher.matches()) {
            String sender = guildAltMatcher.group(1).trim();
            String content = guildAltMatcher.group(2).trim();
            return new ChatMessage(sender, content, "guild");
        }

        // Default - try to parse sender:content
        Matcher senderMatcher = SENDER_CONTENT.matcher(cleanMessage);
        if (senderMatcher.matches()) {
            String sender = senderMatcher.group(1).trim();
            String content = senderMatcher.group(2).trim();
            return new ChatMessage(sender, content, "party");
        }

        return null;
    }

    /**
     * Parse content to extract sender and content
     */
    private ChatMessage parseContent(String messageContent, String chatType) {
        Matcher matcher = SENDER_CONTENT.matcher(messageContent);
        if (matcher.matches()) {
            String sender = matcher.group(1).trim();
            String content = matcher.group(2).trim();
            return new ChatMessage(sender, content, chatType);
        }

        // Fallback
        return new ChatMessage("Unknown", messageContent, chatType);
    }

    /**
     * Check if content contains trigger word
     */
    private boolean containsTrigger(String content) {
        String lower = content.toLowerCase();
        for (String trigger : TRIGGERS) {
            if (lower.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract prompt from content (remove triggers)
     */
    private String extractPrompt(String content) {
        // Remove all trigger words
        String prompt = content;
        for (String trigger : TRIGGERS) {
            prompt = prompt.replaceAll("(?i)" + Pattern.quote(trigger), "");
        }

        // Remove leading conversational fluff
        prompt = prompt.replaceAll("^\\s*(?:hey|yo|hi|hallo|bitte|please)?\\s*[:,]?\\s*", "");

        return prompt.trim();
    }

    /**
     * Strip Minecraft formatting codes
     */
    private String stripFormatting(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Send chat message (thread-safe)
     */
    private void sendMessage(String message) {
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    /**
     * Chat message data class
     */
    private static class ChatMessage {
        final String sender;
        final String content;
        final String chatType;

        ChatMessage(String sender, String content, String chatType) {
            this.sender = sender;
            this.content = content;
            this.chatType = chatType;
        }
    }
}
