package dev.hunchclient.module.impl.lancho;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

/**
 * Message Sender with long message splitting
 * CRITICAL FIX: Minecraft checks CHARACTER length, not byte length!
 */
public class MessageSender {

    private static ScheduledExecutorService scheduler;
    private static final Object schedulerLock = new Object();
    private static final int MINECRAFT_CHAR_LIMIT = 256; // Minecraft character limit
    private static final int SAFETY_BUFFER = 50; // MASSIVE safety margin - better too short than kicked!
    private static final String FIRST_PREFIX = "[Lancho] ";
    private static final String NEXT_PREFIX = "| ";
    private static final int MESSAGE_DELAY_MS = 1200;

    /**
     * Send long message, splitting if necessary
     * CRITICAL: Minecraft checks CHARACTER length (max 256), not byte length!
     */
    public static void sendLongMessage(Minecraft mc, String text, String chatType) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Sanitize text for chat
        text = sanitizeForChat(text);

        // Calculate command overhead in characters
        String command = getCommand(chatType);
        int commandOverhead = command.length();

        // Calculate max usable characters for text content
        // First message: /pc [Lancho] TEXT -> 4 + 10 + text = 256 max
        // So text can be: 256 - 10 (safety) - 4 (command) - 10 (prefix) = 232 chars
        int maxUsableFirst = MINECRAFT_CHAR_LIMIT - SAFETY_BUFFER - commandOverhead - FIRST_PREFIX.length();

        // Continuation: /pc | TEXT -> 4 + 2 + text = 256 max
        // So text can be: 256 - 10 (safety) - 4 (command) - 2 (prefix) = 240 chars
        int maxUsableNext = MINECRAFT_CHAR_LIMIT - SAFETY_BUFFER - commandOverhead - NEXT_PREFIX.length();

        System.out.println("[MessageSender] Text length: " + text.length() + " chars");
        System.out.println("[MessageSender] Max first message: " + maxUsableFirst + " chars");
        System.out.println("[MessageSender] Max next message: " + maxUsableNext + " chars");

        // Single message - send directly if it fits
        if (text.length() <= maxUsableFirst) {
            sendChatMessage(mc, FIRST_PREFIX + text, chatType);
            return;
        }

        // Split into parts based on CHARACTER length
        List<String> parts = new ArrayList<>();
        String remaining = text;

        while (!remaining.isEmpty()) {
            int maxChars = parts.isEmpty() ? maxUsableFirst : maxUsableNext;

            if (remaining.length() <= maxChars) {
                parts.add(remaining);
                break;
            }

            // Find a good break point at word boundary
            String part = findSafeCutPoint(remaining, maxChars);
            parts.add(part);
            remaining = remaining.substring(part.length()).trim();
        }

        System.out.println("[MessageSender] Split into " + parts.size() + " parts");

        // Send parts with delay
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            String prefix = i == 0 ? FIRST_PREFIX : NEXT_PREFIX;
            long delay = (long) i * MESSAGE_DELAY_MS;
            final int partNum = i + 1;
            final int totalParts = parts.size();

            getScheduler().schedule(() -> {
                String fullMsg = prefix + part;
                String withCommand = getCommand(chatType) + fullMsg;
                System.out.println("[MessageSender] Sending part " + partNum + "/" + totalParts +
                    " (text: " + part.length() + " chars, full msg: " + withCommand.length() + " chars)");
                sendChatMessage(mc, fullMsg, chatType);
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Find a safe cut point that respects character limit and tries to break at word boundaries
     */
    private static String findSafeCutPoint(String text, int maxChars) {
        // If the whole text fits, return it
        if (text.length() <= maxChars) {
            return text;
        }

        // Try to find a word boundary near the cut point
        int lastSpace = text.lastIndexOf(" ", maxChars);

        // Only use word boundary if it's reasonably close (within 60% of max)
        if (lastSpace > maxChars * 0.6) {
            return text.substring(0, lastSpace);
        }

        // Otherwise cut at max chars
        return text.substring(0, maxChars);
    }

    /**
     * Get chat command prefix for chat type
     */
    private static String getCommand(String chatType) {
        switch (chatType) {
            case "party": return "/pc ";
            case "guild": return "/gc ";
            case "all": return "/ac ";
            default: return "";
        }
    }

    /**
     * Send chat message based on chat type
     * CRITICAL: Validates against CHARACTER length (max 256)!
     */
    private static void sendChatMessage(Minecraft mc, String message, String chatType) {
        if (mc.player == null) {
            return;
        }

        try {
            String finalMessage;
            switch (chatType) {
                case "party":
                    finalMessage = "/pc " + message;
                    break;
                case "guild":
                    finalMessage = "/gc " + message;
                    break;
                case "all":
                    finalMessage = "/ac " + message;
                    break;
                default:
                    // Private message - send to player chat (no limit needed)
                    // Must run on main thread due to font rendering
                    String localMsg = message;
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6" + localMsg), false);
                        }
                    });
                    return;
            }

            // CRITICAL SAFETY CHECK: Ensure we NEVER exceed 256 CHARACTERS!
            // Check MULTIPLE times because emojis can expand unexpectedly!
            int attempts = 0;
            while (finalMessage.length() > MINECRAFT_CHAR_LIMIT && attempts < 5) {
                System.err.println("[Lancho] CRITICAL: Message too long (" + finalMessage.length() + " chars, max " + MINECRAFT_CHAR_LIMIT + ")!");
                System.err.println("[Lancho] Message: " + finalMessage);

                // Emergency truncation - cut off 20 chars at a time to be safe
                int newLength = Math.max(MINECRAFT_CHAR_LIMIT - 20 - (attempts * 10), 100);
                finalMessage = finalMessage.substring(0, newLength);
                System.err.println("[Lancho] Truncated to " + finalMessage.length() + " chars (attempt " + (attempts + 1) + ")");
                attempts++;
            }

            // Final check before sending
            if (finalMessage.length() > MINECRAFT_CHAR_LIMIT) {
                System.err.println("[Lancho] EMERGENCY: Still too long after " + attempts + " attempts! Cutting to 200 chars!");
                finalMessage = finalMessage.substring(0, 200);
            }

            mc.player.connection.sendChat(finalMessage);
        } catch (Exception e) {
            System.err.println("[Lancho] Failed to send message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sanitize text for Minecraft chat
     */
    private static String sanitizeForChat(String text) {
        if (text == null) {
            return "";
        }

        // Remove markdown
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "$1"); // **bold**
        text = text.replaceAll("\\*(.*?)\\*", "$1");       // *italic*
        text = text.replaceAll("`(.*?)`", "$1");           // `code`
        text = text.replaceAll("```[\\s\\S]*?```", "[code]"); // ```code blocks```

        // Remove newlines
        text = text.replaceAll("\\n+", " ");
        text = text.replaceAll("\\s+", " ");

        // Remove URLs and IPs
        text = text.replaceAll("https?://[^\\s]+", "[link]");
        text = text.replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "[IP]");

        return text.trim();
    }

    /**
     * Shutdown scheduler
     */
    public static void shutdown() {
        synchronized (schedulerLock) {
            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
        }
    }

    private static ScheduledExecutorService getScheduler() {
        synchronized (schedulerLock) {
            if (scheduler == null) {
                try {
                    scheduler = Executors.newSingleThreadScheduledExecutor();
                } catch (Exception e) {
                    System.err.println("[Lancho] Failed to create scheduler: " + e.getMessage());
                    // Optionally, re-throw as a runtime exception if the scheduler is critical
                    // throw new RuntimeException("Failed to initialize Lancho scheduler", e);
                }
            }
        }
        return scheduler;
    }
}
