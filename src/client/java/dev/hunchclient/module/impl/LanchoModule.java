package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.module.impl.lancho.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Lancho - Internet-based Distributed AI Chatbot
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only
 * - Chat commands and responses
 * - No gameplay modifications
 * - Undetectable
 *
 * Features:
 * - Connects to remote AI server (34.7.234.242:3000)
 * - Responds to "puncho", "=ai", "lancho" triggers in party/guild/all chat
 * - Request queue with 15s rate limiting
 * - Multiple AI personalities
 * - Web search integration
 * - Session-based conversation history
 */
public class LanchoModule extends Module implements ConfigurableModule, SettingsProvider {

    // Version & Info
    public static final String VERSION = "1.0.0";
    public static final String NAME = "Lancho";
    public static final String DESCRIPTION = "Internet-based Distributed AI Chatbot";
    private static final String[] PERSONALITY_PRESETS = {
        "default",
        "tiktok",
        "femboy",
        "rightextremist",
        "marxistleninist"
    };

    // Connection Config
    private String serverUrl = "http://34.7.234.242:3000";
    private String serverSecret = ""; // MUST be set before connecting
    private int httpTimeout = 30000;
    private int maxReconnectAttempts = 10;
    private int reconnectDelay = 5000;

    // Request Queue Config
    private int minRequestDelay = 15000; // 15 seconds between requests
    private int maxQueueSize = 50;

    // AI Settings
    private String personality = "default"; // default, tiktok, femboy, rightextremist, marxistleninist, etc.
    private int personalityStrictness = 60; // 1-100
    private int toxicityLevel = 15; // 1-100
    private int sassyLevel = 25; // 1-100
    private int zestyLevel = 20; // 1-100
    private int submissivenessLevel = 25; // 1-100
    private int emojiFrequency = 20; // 1-100
    private String messageLength = "medium"; // very_short, short, medium, long, very_long
    private boolean webSearchEnabled = true;
    private boolean useHistory = true;
    private boolean useEmotes = true;
    private boolean guildChatEnabled = true;
    private boolean allChatEnabled = false;

    // Components
    private HttpClient httpClient;
    private ConnectionManager connectionManager;
    private RequestQueue requestQueue;
    private ChatHandler chatHandler;
    private LanchoSettings settings;
    private LanchoCommand commandHandler;

    // Client Info
    private final Minecraft mc = Minecraft.getInstance();

    // Singleton instance for global access
    private static LanchoModule instance;

    public LanchoModule() {
        super("Lancho", "AI Chatbot for party/guild chat", Category.MISC, true);
        instance = this;
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Lancho] §aEnabled! Initializing..."), false);
        }

        // Create settings object
        settings = new LanchoSettings();
        settings.serverSecret = this.serverSecret;
        settings.serverUrl = this.serverUrl;
        settings.personality = this.personality;
        settings.personalityIndex = resolvePersonalityIndex(this.personality);
        settings.personalityStrictness = this.personalityStrictness;
        settings.toxicityLevel = this.toxicityLevel;
        settings.sassyLevel = this.sassyLevel;
        settings.zestyLevel = this.zestyLevel;
        settings.submissivenessLevel = this.submissivenessLevel;
        settings.emojiFrequency = this.emojiFrequency;
        settings.messageLength = this.messageLength;
        settings.webSearchEnabled = this.webSearchEnabled;
        settings.useHistory = this.useHistory;
        settings.useEmotes = this.useEmotes;
        settings.guildChatEnabled = this.guildChatEnabled;
        settings.allChatEnabled = this.allChatEnabled;
        commandHandler = new LanchoCommand(this);

        // Initialize components
        httpClient = new HttpClient(serverUrl, httpTimeout);
        connectionManager = new ConnectionManager(httpClient, settings.serverSecret, maxReconnectAttempts, reconnectDelay);
        requestQueue = new RequestQueue(httpClient, connectionManager, settings, minRequestDelay, maxQueueSize);
        chatHandler = new ChatHandler(requestQueue, settings);

        // Connect to server
        if (!settings.serverSecret.isEmpty()) {
            connectionManager.connect();
        } else {
            if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Lancho] §cServer secret not set! Use /lancho secret <secret>"), false);
            }
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Lancho] §cDisabled! Disconnecting..."), false);
        }

        // Disconnect and cleanup
        if (connectionManager != null) {
            connectionManager.disconnect();
            connectionManager.shutdown();
        }

        if (requestQueue != null) {
            requestQueue.shutdown();
        }

        HttpClient.shutdown();
        MessageSender.shutdown();
    }

    @Override
    public void onTick() {
        // Queue processing is handled asynchronously
        // No need to do anything here
    }

    /**
     * Handle incoming chat message
     */
    public void handleChatMessage(String message) {
        if (chatHandler != null && isEnabled()) {
            chatHandler.handleChatMessage(message);
        }
    }

    /**
     * Send a direct message to Lancho AI (for chat window)
     */
    public void sendDirectMessage(String message, java.util.function.Consumer<String> callback) {
        if (requestQueue == null || connectionManager == null) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        if (!connectionManager.isConnected()) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        // Send request with "dm" chat type and callback
        String playerName = mc.player != null ? mc.player.getName().getString() : "Player";
        requestQueue.enqueueWithCallback(message, "dm", playerName, settings.personality, callback);
    }

    // =================== GETTERS / SETTERS ===================

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;

        // Reconnect if already enabled
        if (isEnabled() && connectionManager != null) {
            connectionManager.disconnect();
            connectionManager.shutdown();
            HttpClient.shutdown();
            httpClient = new HttpClient(serverUrl, httpTimeout);
            connectionManager = new ConnectionManager(httpClient, settings.serverSecret, maxReconnectAttempts, reconnectDelay);
            if (requestQueue != null) {
                requestQueue.shutdown();
            }
            requestQueue = new RequestQueue(httpClient, connectionManager, settings, minRequestDelay, maxQueueSize);
            chatHandler = new ChatHandler(requestQueue, settings);
            settings.serverUrl = serverUrl;
            connectionManager.connect();
        }
    }

    public String getServerSecret() {
        return serverSecret;
    }

    public void setServerSecret(String serverSecret) {
        this.serverSecret = serverSecret;
        if (settings != null) {
            settings.serverSecret = serverSecret;
        }

        // Update connection manager
        if (connectionManager != null) {
            connectionManager.setServerSecret(serverSecret);

            // Auto-connect if module is enabled and we have a secret
            if (isEnabled() && !serverSecret.isEmpty()) {
                connectionManager.disconnect();
                connectionManager.resetReconnectAttempts();

                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[Lancho] §7Connecting to server..."), false);
                }

                connectionManager.connect();
            }
        }
    }

    public boolean isConnected() {
        return connectionManager != null && connectionManager.isConnected();
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public RequestQueue getRequestQueue() {
        return requestQueue;
    }

    // =================== AI SETTINGS GETTERS / SETTERS ===================

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
        if (settings != null) {
            settings.personality = personality;
            settings.personalityIndex = resolvePersonalityIndex(personality);
        }
    }

    public int getPersonalityStrictness() {
        return personalityStrictness;
    }

    public void setPersonalityStrictness(int personalityStrictness) {
        this.personalityStrictness = personalityStrictness;
        if (settings != null) settings.personalityStrictness = personalityStrictness;
    }

    public int getToxicityLevel() {
        return toxicityLevel;
    }

    public void setToxicityLevel(int toxicityLevel) {
        this.toxicityLevel = toxicityLevel;
        if (settings != null) settings.toxicityLevel = toxicityLevel;
    }

    public int getSassyLevel() {
        return sassyLevel;
    }

    public void setSassyLevel(int sassyLevel) {
        this.sassyLevel = sassyLevel;
        if (settings != null) settings.sassyLevel = sassyLevel;
    }

    public int getZestyLevel() {
        return zestyLevel;
    }

    public void setZestyLevel(int zestyLevel) {
        this.zestyLevel = zestyLevel;
        if (settings != null) settings.zestyLevel = zestyLevel;
    }

    public int getSubmissivenessLevel() {
        return submissivenessLevel;
    }

    public void setSubmissivenessLevel(int submissivenessLevel) {
        this.submissivenessLevel = submissivenessLevel;
        if (settings != null) settings.submissivenessLevel = submissivenessLevel;
    }

    public int getEmojiFrequency() {
        return emojiFrequency;
    }

    public void setEmojiFrequency(int emojiFrequency) {
        this.emojiFrequency = emojiFrequency;
        if (settings != null) settings.emojiFrequency = emojiFrequency;
    }

    public String getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(String messageLength) {
        this.messageLength = messageLength;
        if (settings != null) settings.messageLength = messageLength;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
        if (settings != null) settings.webSearchEnabled = webSearchEnabled;
    }

    public boolean isGuildChatEnabled() {
        return guildChatEnabled;
    }

    public void setGuildChatEnabled(boolean guildChatEnabled) {
        this.guildChatEnabled = guildChatEnabled;
        if (settings != null) settings.guildChatEnabled = guildChatEnabled;
    }

    public boolean isAllChatEnabled() {
        return allChatEnabled;
    }

    public void setAllChatEnabled(boolean allChatEnabled) {
        this.allChatEnabled = allChatEnabled;
        if (settings != null) settings.allChatEnabled = allChatEnabled;
    }

    public boolean isUseHistory() {
        return useHistory;
    }

    public void setUseHistory(boolean useHistory) {
        this.useHistory = useHistory;
        if (settings != null) settings.useHistory = useHistory;
    }

    public boolean isUseEmotes() {
        return useEmotes;
    }

    public void setUseEmotes(boolean useEmotes) {
        this.useEmotes = useEmotes;
        if (settings != null) settings.useEmotes = useEmotes;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        object.addProperty("serverUrl", this.serverUrl);
        object.addProperty("serverSecret", this.serverSecret);
        object.addProperty("personality", this.personality);
        object.addProperty("personalityStrictness", this.personalityStrictness);
        object.addProperty("toxicityLevel", this.toxicityLevel);
        object.addProperty("sassyLevel", this.sassyLevel);
        object.addProperty("zestyLevel", this.zestyLevel);
        object.addProperty("submissivenessLevel", this.submissivenessLevel);
        object.addProperty("emojiFrequency", this.emojiFrequency);
        object.addProperty("messageLength", this.messageLength);
        object.addProperty("webSearchEnabled", this.webSearchEnabled);
        object.addProperty("useHistory", this.useHistory);
        object.addProperty("useEmotes", this.useEmotes);
        object.addProperty("guildChatEnabled", this.guildChatEnabled);
        object.addProperty("allChatEnabled", this.allChatEnabled);
        return object;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        if (data.has("serverUrl")) {
            String savedUrl = data.get("serverUrl").getAsString();
            // Migrate old domain to new IP
            if (savedUrl.contains("hunchclient.dev")) {
                savedUrl = savedUrl.replace("hunchclient.dev", "34.7.234.242");
            }
            setServerUrl(savedUrl);
        }
        if (data.has("serverSecret")) {
            setServerSecret(data.get("serverSecret").getAsString());
        }
        if (data.has("personality")) {
            setPersonality(data.get("personality").getAsString());
        }
        if (data.has("personalityStrictness")) {
            setPersonalityStrictness(data.get("personalityStrictness").getAsInt());
        }
        if (data.has("toxicityLevel")) {
            setToxicityLevel(data.get("toxicityLevel").getAsInt());
        }
        if (data.has("sassyLevel")) {
            setSassyLevel(data.get("sassyLevel").getAsInt());
        }
        if (data.has("zestyLevel")) {
            setZestyLevel(data.get("zestyLevel").getAsInt());
        }
        if (data.has("submissivenessLevel")) {
            setSubmissivenessLevel(data.get("submissivenessLevel").getAsInt());
        }
        if (data.has("emojiFrequency")) {
            setEmojiFrequency(data.get("emojiFrequency").getAsInt());
        }
        if (data.has("messageLength")) {
            setMessageLength(data.get("messageLength").getAsString());
        }
        if (data.has("webSearchEnabled")) {
            setWebSearchEnabled(data.get("webSearchEnabled").getAsBoolean());
        }
        if (data.has("useHistory")) {
            setUseHistory(data.get("useHistory").getAsBoolean());
        }
        if (data.has("useEmotes")) {
            setUseEmotes(data.get("useEmotes").getAsBoolean());
        }
        if (data.has("guildChatEnabled")) {
            setGuildChatEnabled(data.get("guildChatEnabled").getAsBoolean());
        }
        if (data.has("allChatEnabled")) {
            setAllChatEnabled(data.get("allChatEnabled").getAsBoolean());
        }
    }

    public static LanchoModule getInstance() {
        return instance;
    }
    public LanchoCommand getCommandHandler() {
        return commandHandler;
    }

    private int resolvePersonalityIndex(String personality) {
        if (personality == null) {
            return 0;
        }
        String normalized = personality.toLowerCase();
        for (int i = 0; i < PERSONALITY_PRESETS.length; i++) {
            if (PERSONALITY_PRESETS[i].equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return 0;
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Server Secret TextBox
        settings.add(new TextBoxSetting(
            "Server Secret",
            "Authentication secret for Lancho server",
            "lancho_server_secret",
            () -> serverSecret,
            val -> setServerSecret(val),
            "Enter server secret..."
        ));

        // Personality Dropdown
        settings.add(new DropdownSetting(
            "Personality",
            "Select AI personality preset",
            "lancho_personality",
            PERSONALITY_PRESETS,
            () -> resolvePersonalityIndex(personality),
            index -> setPersonality(PERSONALITY_PRESETS[index])
        ));

        // Personality Strictness
        settings.add(new SliderSetting(
            "Strictness",
            "How strictly to follow personality",
            "lancho_strictness",
            0f, 100f,
            () -> (float) personalityStrictness,
            val -> setPersonalityStrictness((int) val.floatValue())
        ).withDecimals(0).withSuffix("%"));

        // Toxicity Level
        settings.add(new SliderSetting(
            "Toxicity",
            "Level of toxic/edgy responses",
            "lancho_toxicity",
            0f, 100f,
            () -> (float) toxicityLevel,
            val -> setToxicityLevel((int) val.floatValue())
        ).withDecimals(0).withSuffix("%"));

        // Sassy Level
        settings.add(new SliderSetting(
            "Sassiness",
            "Level of sassy/snarky responses",
            "lancho_sassy",
            0f, 100f,
            () -> (float) sassyLevel,
            val -> setSassyLevel((int) val.floatValue())
        ).withDecimals(0).withSuffix("%"));

        // Zesty Level
        settings.add(new SliderSetting(
            "Zestiness",
            "Level of zesty/flamboyant responses",
            "lancho_zesty",
            0f, 100f,
            () -> (float) zestyLevel,
            val -> setZestyLevel((int) val.floatValue())
        ).withDecimals(0).withSuffix("%"));

        // Submissiveness Level
        settings.add(new SliderSetting(
            "Submissiveness",
            "Level of submissive/agreeable responses",
            "lancho_submissiveness",
            0f, 100f,
            () -> (float) submissivenessLevel,
            val -> setSubmissivenessLevel((int) val.floatValue())
        ).withDecimals(0).withSuffix("%"));

        // Emoji Frequency
        settings.add(new SliderSetting(
            "Emoji Frequency",
            "How often to use emojis",
            "lancho_emoji_freq",
            0f, 100f,
            () -> (float) emojiFrequency,
            val -> setEmojiFrequency((int) val.floatValue())
        ).withDecimals(0).withSuffix("%"));

        // Message Length
        settings.add(new DropdownSetting(
            "Message Length",
            "Preferred response length",
            "lancho_msg_length",
            new String[]{"very_short", "short", "medium", "long", "very_long"},
            () -> {
                String[] lengths = {"very_short", "short", "medium", "long", "very_long"};
                for (int i = 0; i < lengths.length; i++) {
                    if (lengths[i].equals(messageLength)) return i;
                }
                return 2; // default to medium
            },
            index -> {
                String[] lengths = {"very_short", "short", "medium", "long", "very_long"};
                setMessageLength(lengths[index]);
            }
        ));

        // Web Search
        settings.add(new CheckboxSetting(
            "Web Search",
            "Enable internet search for answers",
            "lancho_web_search",
            () -> webSearchEnabled,
            val -> setWebSearchEnabled(val)
        ));

        // Use History
        settings.add(new CheckboxSetting(
            "Conversation History",
            "Remember previous messages in session",
            "lancho_use_history",
            () -> useHistory,
            val -> setUseHistory(val)
        ));

        // Use Emotes
        settings.add(new CheckboxSetting(
            "Use Emotes",
            "Use Hypixel chat emotes in responses",
            "lancho_use_emotes",
            () -> useEmotes,
            val -> setUseEmotes(val)
        ));

        // Guild Chat
        settings.add(new CheckboxSetting(
            "Guild Chat",
            "Respond to guild chat triggers",
            "lancho_guild_chat",
            () -> guildChatEnabled,
            val -> setGuildChatEnabled(val)
        ));

        // All Chat
        settings.add(new CheckboxSetting(
            "All Chat",
            "Respond to all chat triggers",
            "lancho_all_chat",
            () -> allChatEnabled,
            val -> setAllChatEnabled(val)
        ));

        return settings;
    }
}
