package dev.hunchclient.module.impl.lancho;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Request Queue with rate limiting for Lancho
 */
public class RequestQueue {

    private final HttpClient httpClient;
    private final ConnectionManager connectionManager;
    private final LanchoSettings settings;
    private final Minecraft mc = Minecraft.getInstance();

    private final Queue<AIRequest> queue = new LinkedList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean isProcessingRequest = false;
    private long lastRequestTime = 0;

    private final int minRequestDelay; // Minimum delay between requests (ms)
    private final int maxQueueSize;    // Maximum queue size

    public RequestQueue(HttpClient httpClient, ConnectionManager connectionManager, LanchoSettings settings, int minRequestDelay, int maxQueueSize) {
        this.httpClient = httpClient;
        this.connectionManager = connectionManager;
        this.settings = settings;
        this.minRequestDelay = minRequestDelay;
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Add request to queue
     */
    public void enqueue(String prompt, String chatType, String sender, String personality) {
        enqueueWithCallback(prompt, chatType, sender, personality, null);
    }

    /**
     * Add request to queue with callback for response
     */
    public void enqueueWithCallback(String prompt, String chatType, String sender, String personality, java.util.function.Consumer<String> callback) {
        if (queue.size() >= maxQueueSize) {
            sendMessage("§c[Lancho] §eRequest queue is full (" + maxQueueSize + " requests). Please wait.");
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        AIRequest request = new AIRequest(prompt, chatType, sender, personality, callback);
        queue.add(request);

        if (queue.size() > 1) {
            sendMessage("§6[Lancho] §eYour request is queued (position: " + queue.size() + "/" + maxQueueSize + ")");
        }

        processQueue();
    }

    /**
     * Process next request in queue
     */
    public void processQueue() {
        // Already processing?
        if (isProcessingRequest) {
            return;
        }

        // Queue empty?
        if (queue.isEmpty()) {
            return;
        }

        // Not connected?
        if (!connectionManager.isConnected()) {
            if (!connectionManager.isConnecting()) {
                connectionManager.connect();
            }
            scheduleRetry(1000);
            return;
        }

        // Rate limiting - only apply if there was a previous request
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime;

        if (lastRequestTime > 0 && timeSinceLastRequest < minRequestDelay) {
            long remainingTime = minRequestDelay - timeSinceLastRequest;
            scheduleRetry(remainingTime);
            return;
        }

        // Get next request
        AIRequest request = queue.poll();
        if (request == null) {
            return;
        }

        isProcessingRequest = true;

        // Send request
        sendAIRequest(request);
    }

    /**
     * Schedule queue retry
     */
    private void scheduleRetry(long delayMs) {
        scheduler.schedule(this::processQueue, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Send AI request to server
     */
    private void sendAIRequest(AIRequest request) {
        String playerName = mc.player != null ? mc.player.getName().getString() : "unknown";
        String sessionId = generateSessionId(request.sender, request.chatType);

        JsonObject requestData = new JsonObject();
        requestData.addProperty("prompt", request.prompt);
        requestData.addProperty("chatType", request.chatType);
        requestData.addProperty("sender", playerName);
        requestData.addProperty("originalSender", request.sender);
        requestData.addProperty("personality", request.personality);
        requestData.addProperty("streaming", false);
        requestData.addProperty("sessionId", sessionId);

        // Add server secret
        requestData.addProperty("secret", getServerSecret());

        // Add conversation history for context (if useHistory is enabled)
        if (settings.useHistory) {
            JsonArray historyArray = new JsonArray();
            java.util.List<LanchoHistoryManager.ConversationMessage> history = LanchoHistoryManager.getSessionHistory(sessionId);

            // Only send last 10 messages to avoid payload bloat
            int startIndex = Math.max(0, history.size() - 10);
            for (int i = startIndex; i < history.size(); i++) {
                LanchoHistoryManager.ConversationMessage msg = history.get(i);
                JsonObject historyMsg = new JsonObject();
                historyMsg.addProperty("role", msg.role);
                historyMsg.addProperty("content", msg.content);
                historyArray.add(historyMsg);
            }
            requestData.add("history", historyArray);

            System.out.println("[Lancho] Including " + historyArray.size() + " history messages for context");
        }

        // Add settings
        JsonObject settings = gatherSettings();
        requestData.add("settings", settings);

        String clientId = connectionManager.getClientId();
        String endpoint = "/ai-request?clientId=" + (clientId != null ? clientId : "unknown");
        System.out.println("[Lancho] Sending request to " + endpoint + " (prompt length=" + request.prompt.length() + ")");
        System.out.println("[Lancho] === FULL REQUEST DATA ===");
        System.out.println("[Lancho] Prompt: " + requestData.get("prompt").getAsString());
        System.out.println("[Lancho] ChatType: " + requestData.get("chatType").getAsString());
        System.out.println("[Lancho] Personality: " + requestData.get("personality").getAsString());
        if (requestData.has("settings")) {
            JsonObject settingsObj = requestData.getAsJsonObject("settings");
            System.out.println("[Lancho] Settings:");
            System.out.println("[Lancho]   - messageLength: " + settingsObj.get("messageLength").getAsString());
            System.out.println("[Lancho]   - maxOutputTokens: " + settingsObj.get("maxOutputTokens").getAsInt());
            System.out.println("[Lancho]   - lengthInstruction: " + settingsObj.get("lengthInstruction").getAsString());
            System.out.println("[Lancho]   - personalityStrictness: " + settingsObj.get("personalityStrictness").getAsInt());
            System.out.println("[Lancho]   - toxicityLevel: " + settingsObj.get("toxicityLevel").getAsInt());
        }
        System.out.println("[Lancho] === END REQUEST DATA ===");

        httpClient.request("POST", endpoint, requestData)
            .thenAccept(response -> {
                System.out.println("[Lancho] Response status: " + response.status);
                if (!response.isSuccess()) {
                    // Handle 401 - session expired
                    if (response.status == 401) {
                        sendMessage("§6[Lancho] §eSession expired - reconnecting...");
                        connectionManager.disconnect();
                        connectionManager.resetReconnectAttempts();
                        connectionManager.connect().thenRun(() -> {
                            // Re-queue request
                            enqueue(request.prompt, request.chatType, request.sender, request.personality);
                        });
                    } else {
                        sendMessage("§6[Lancho] §cRequest failed: HTTP " + response.status);
                    }
                    onRequestCompleted();
                    return;
                }

                if (response.data == null) {
                    sendMessage("\u00a76[Lancho] \u00a7cServer returned no data for the request.");
                    onRequestCompleted();
                    return;
                }

                JsonObject data = response.data;

                // Check for server-side error
                if (data.has("success") && !data.get("success").getAsBoolean()) {
                    String errorMsg = "Unknown server error";
                    if (data.has("error") && !data.get("error").isJsonNull()) {
                        errorMsg = data.get("error").getAsString();
                    }
                    sendMessage("\u00a76[Lancho] \u00a7cServer error: " + errorMsg);
                    System.err.println("[Lancho] Server returned error: " + errorMsg);
                    if (request.callback != null) {
                        mc.execute(() -> request.callback.accept(null));
                    }
                    onRequestCompleted();
                    return;
                }

                String responseText = null;
                if (data.has("text") && !data.get("text").isJsonNull()) {
                    responseText = data.get("text").getAsString();
                } else if (data.has("messages") && data.get("messages").isJsonArray()) {
                    JsonArray messages = data.getAsJsonArray("messages");
                    if (messages.size() > 0 && !messages.get(0).isJsonNull()) {
                        responseText = messages.get(0).getAsString();
                    }
                }

                if (responseText != null && !responseText.isEmpty()) {
                    // Save to local history
                    LanchoHistoryManager.saveMessage(sessionId, request.sender, request.prompt, responseText);

                    // If this is a DM request with a callback, use callback instead of sending to chat
                    if ("dm".equals(request.chatType) && request.callback != null) {
                        // Schedule callback on main thread
                        String finalResponse = responseText;
                        mc.execute(() -> request.callback.accept(finalResponse));
                    } else {
                        // Normal chat response - MessageSender now handles UTF-8 byte length to prevent kicks!
                        MessageSender.sendLongMessage(mc, responseText, request.chatType);
                    }
                } else {
                    if (request.callback != null) {
                        mc.execute(() -> request.callback.accept(null));
                    } else {
                        sendMessage("\u00a76[Lancho] \u00a7cNo response text received from server.");
                    }
                }

                onRequestCompleted();
            })
            .exceptionally(throwable -> {
                if (request.callback != null) {
                    mc.execute(() -> request.callback.accept(null));
                } else {
                    sendMessage("§6[Lancho] §cRequest error: " + throwable.getMessage());
                }
                onRequestCompleted();
                return null;
            });
    }

    /**
     * Called when request is completed
     */
    private void onRequestCompleted() {
        isProcessingRequest = false;
        lastRequestTime = System.currentTimeMillis();

        // Process next request after delay
        if (!queue.isEmpty()) {
            scheduleRetry(minRequestDelay);
        }
    }

    /**
     * Generate session ID for conversation history
     */
    private String generateSessionId(String sender, String chatType) {
        return connectionManager.getClientId() + "-" + sender + "-" + chatType + "-persistent";
    }

    /**
     * Gather all settings for AI request
     */
    private JsonObject gatherSettings() {
        JsonObject settingsJson = new JsonObject();

        int lengthSlider = getLengthSlider(settings.messageLength);
        int targetTokens = getTargetTokens(settings.messageLength);
        int maxTokens = Math.max(targetTokens, calculateMaxTokens(settings.messageLength));
        String lengthInstruction = getLengthInstruction(settings.messageLength);

        // Message settings
        settingsJson.addProperty("maxOutputTokens", maxTokens);
        settingsJson.addProperty("messageLength", settings.messageLength);
        settingsJson.addProperty("lengthSlider", lengthSlider);
        settingsJson.addProperty("lengthTargetTokens", targetTokens);
        settingsJson.addProperty("lengthInstruction", lengthInstruction);

        // Personality settings
        settingsJson.addProperty("personalityStrictness", settings.personalityStrictness);
        settingsJson.addProperty("toxicityLevel", settings.toxicityLevel);
        settingsJson.addProperty("sassyLevel", settings.sassyLevel);
        settingsJson.addProperty("zestyLevel", settings.zestyLevel);
        settingsJson.addProperty("submissivenessLevel", settings.submissivenessLevel);
        settingsJson.addProperty("emojiFrequency", settings.emojiFrequency);
        settingsJson.addProperty("personalityIndex", settings.personalityIndex);

        // Generation settings
        settingsJson.addProperty("temperature", 0.7);
        settingsJson.addProperty("topK", 40);
        settingsJson.addProperty("topP", 0.95);
        settingsJson.addProperty("topKSlider", 40);
        settingsJson.addProperty("topPSlider", 70);

        // Behaviour settings
        settingsJson.addProperty("streamingEnabled", settings.streamingEnabled);
        settingsJson.addProperty("webSearchEnabled", settings.webSearchEnabled);
        settingsJson.addProperty("useHistory", settings.useHistory);
        settingsJson.addProperty("useEmotes", settings.useEmotes);
        settingsJson.addProperty("guildLongReplies", false);
        settingsJson.addProperty("messageStreamDelay", 1000);
        settingsJson.addProperty("debugMode", false);

        // Context settings
        settingsJson.addProperty("includeHypixelData", false);
        settingsJson.addProperty("whitelist", "");

        // Formatting settings
        settingsJson.addProperty("chatFormatting", true);

        // Integration flags (legacy compatibility)
        settingsJson.addProperty("geminiAIEnabled", false);
        settingsJson.addProperty("gemini_ai_guild_chat", false);
        settingsJson.addProperty("hypixelApiKey", "");

        // Model preference
        settingsJson.addProperty("preferredModel", "gemini-2.5-flash");

        return settingsJson;
    }

    /**
     * Calculate max tokens based on message length preset
     * CRITICAL: Lower tokens = shorter responses to prevent chat packet overflow!
     * Token limit must account for /pc command (4 chars) + [Lancho] prefix (10 chars) + safety (30 chars)
     * = 44 chars overhead, leaving only 212 chars for actual text!
     */
    private int calculateMaxTokens(String messageLength) {
        switch (messageLength) {
            case "very_short": return 50;    // ~80 chars total = safe for 1/3 message
            case "short": return 80;         // ~140 chars total = safe for 2/3 message
            case "medium": return 120;       // ~200 chars total = safe for 1 full message
            case "long": return 180;         // ~300 chars total = will split into 2 messages
            case "very_long": return 220;    // ~400 chars total = will split into 2 messages
            default: return 120;
        }
    }

    /**
     * Get length slider value (0-100) based on message length
     */
    private int getLengthSlider(String messageLength) {
        switch (messageLength) {
            case "very_short": return 20;
            case "short": return 40;
            case "medium": return 60;
            case "long": return 80;
            case "very_long": return 100;
            default: return 60;
        }
    }

    /**
     * Get target tokens based on message length (what we aim for)
     * Aligned with lengthInstruction character limits
     */
    private int getTargetTokens(String messageLength) {
        switch (messageLength) {
            case "very_short": return 25;    // Target ~80 chars (1/3 message)
            case "short": return 45;         // Target ~140 chars (2/3 message)
            case "medium": return 70;        // Target ~220 chars (1 full message)
            case "long": return 120;         // Target ~400 chars (1.5-2 messages)
            case "very_long": return 150;    // Target ~475 chars (2 messages MAX)
            default: return 70;
        }
    }

    /**
     * Get length instruction for AI based on message length
     * CRITICAL: System prompt instruction - AI must NEVER mention or reference these limits in responses!
     * These are internal guidelines only. AI should respond naturally within the character limits.
     *
     * IMPORTANT: Minecraft has a 256 char hard limit. After adding commands (/pc = 4 chars) and
     * prefixes ([Lancho] = 10 chars, | = 2 chars), we have:
     * - First message: 256 - 5 safety - 4 cmd - 10 prefix = 237 chars max
     * - Next messages: 256 - 5 safety - 4 cmd - 2 prefix = 245 chars max
     * - Two messages total: 237 + 245 = 482 chars absolute maximum
     */
    private String getLengthInstruction(String messageLength) {
        switch (messageLength) {
            case "very_short": return "CRITICAL: Keep response under 80 characters total (1/3 of a message). Very brief, 1-2 short sentences. Example: 'Hey! Doing good, you?' (22 chars). NEVER exceed 80 chars.";
            case "short": return "CRITICAL: Keep response between 100-140 characters (2/3 of a message). Brief but complete response. NEVER exceed 140 chars.";
            case "medium": return "CRITICAL: Keep response between 180-220 characters (1 full message). Complete thought with personality. NEVER exceed 220 chars.";
            case "long": return "CRITICAL: Keep response between 300-380 characters (1.5-2 messages). Detailed and engaging. NEVER exceed 380 chars.";
            case "very_long": return "CRITICAL: Keep response between 400-470 characters (2 messages MAX). Thorough with full personality. NEVER exceed 470 chars.";
            default: return "CRITICAL: Keep response between 180-220 characters (1 full message). NEVER exceed 220 chars.";
        }
    }

    /**
     * Get server secret from settings
     */
    private String getServerSecret() {
        return settings.serverSecret;
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
     * Shutdown scheduler to prevent thread leaks.
     * CRITICAL: Always call this when disabling the module!
     */
    public void shutdown() {
        System.out.println("[RequestQueue] Shutting down scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                System.out.println("[RequestQueue] Scheduler did not terminate in time, forcing shutdown...");
                scheduler.shutdownNow();
            }
            System.out.println("[RequestQueue] Scheduler shutdown complete");
        } catch (InterruptedException e) {
            System.err.println("[RequestQueue] Shutdown interrupted, forcing shutdown...");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get queue size
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Is currently processing a request?
     */
    public boolean isProcessing() {
        return isProcessingRequest;
    }

    /**
     * AI Request data class
     */
    private static class AIRequest {
        final String prompt;
        final String chatType;
        final String sender;
        final String personality;
        final java.util.function.Consumer<String> callback; // Callback for DM responses

        AIRequest(String prompt, String chatType, String sender, String personality) {
            this(prompt, chatType, sender, personality, null);
        }

        AIRequest(String prompt, String chatType, String sender, String personality, java.util.function.Consumer<String> callback) {
            this.prompt = prompt;
            this.chatType = chatType;
            this.sender = sender;
            this.personality = personality;
            this.callback = callback;
        }
    }
}
