package dev.hunchclient.module.impl.lancho;

/**
 * Lancho AI Settings
 */
public class LanchoSettings {
    // AI Personality
    public String personality = "default";
    public int personalityStrictness = 60;
    public int toxicityLevel = 15;
    public int sassyLevel = 25;
    public int zestyLevel = 20;
    public int submissivenessLevel = 25;
    public int emojiFrequency = 20;

    // Message Settings
    public String messageLength = "medium";
    public boolean webSearchEnabled = true;
    public boolean useHistory = true;
    public boolean useEmotes = true;
    public boolean streamingEnabled = false; // Disabled by default (polling disabled)

    // Chat Toggles
    public boolean guildChatEnabled = true;
    public boolean allChatEnabled = false;

    // Server Config
    public String serverSecret = "";
    public String serverUrl = "http://34.7.234.242:3000";

    // Personality index for server compatibility
    public int personalityIndex = 0;
}
