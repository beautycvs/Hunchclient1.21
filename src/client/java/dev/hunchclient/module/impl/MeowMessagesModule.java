package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * MeowMessages Module - Transforms messages into catspeak
 *
 * WATCHDOG SAFE: YES
 * - Client-side chat message transformation only
 * - No packets sent beyond normal chat
 * - No automation of gameplay
 */
public class MeowMessagesModule extends Module implements ConfigurableModule {

    private static final String[] CAT_SOUNDS = {"meow", "mew", "mrow", "nya", "purr", "mrrp", "hiss", "nyaa~"};
    private static final String[] CAT_SUFFIXES = {"~", "...", " nya~", " :3", " rawr"};
    private static final Set<String> COMMAND_WHITELIST = new HashSet<>();

    static {
        COMMAND_WHITELIST.add("gc");
        COMMAND_WHITELIST.add("pc");
        COMMAND_WHITELIST.add("ac");
        COMMAND_WHITELIST.add("msg");
        COMMAND_WHITELIST.add("tell");
        COMMAND_WHITELIST.add("r");
        COMMAND_WHITELIST.add("say");
        COMMAND_WHITELIST.add("w");
        COMMAND_WHITELIST.add("reply");
    }

    private final Random random = new Random();
    private boolean enabled = true;

    public MeowMessagesModule() {
        super("MeowMessages", "Transforms messages into catspeak", Category.MISC, false);
    }

    @Override
    protected void onEnable() {
        // No initialization needed
    }

    @Override
    protected void onDisable() {
        // No cleanup needed
    }

    /**
     * Transform message into catspeak
     * Called from ClientCommandMixin
     */
    public String transformMessage(String message) {
        if (!isEnabled()) {
            return message;
        }

        // Ignore messages with [Puncho], [Lancho], or already containing cat sounds
        // CRITICAL: Lancho messages are length-sensitive and can cause kicks if modified!
        if (message.contains("[Puncho]") || message.contains("[Lancho]") || message.contains("| ") || containsCatSound(message)) {
            return message;
        }

        // Check if it's a command
        if (message.startsWith("/")) {
            String[] parts = message.substring(1).split(" ", 2);
            String command = parts[0].toLowerCase();

            // Only transform whitelisted commands with arguments
            if (COMMAND_WHITELIST.contains(command) && parts.length > 1) {
                return "/" + command + " " + transform(parts[1]);
            }
            return message; // Don't transform other commands
        }

        // Transform regular chat messages
        return transform(message);
    }

    /**
     * Transform text into catspeak
     */
    private String transform(String text) {
        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            result.append(words[i]);

            // 35% chance to add a cat sound after a word
            if (random.nextDouble() < 0.35) {
                result.append(" ");
                result.append(CAT_SOUNDS[random.nextInt(CAT_SOUNDS.length)]);
            }
        }

        // 40% chance to add a suffix to the end
        if (random.nextDouble() < 0.4) {
            result.append(CAT_SUFFIXES[random.nextInt(CAT_SUFFIXES.length)]);
        }

        return result.toString();
    }

    /**
     * Check if message contains any cat sound
     */
    private boolean containsCatSound(String message) {
        for (String sound : CAT_SOUNDS) {
            if (message.contains(sound)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("enabled", enabled);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("enabled")) {
            enabled = data.get("enabled").getAsBoolean();
        }
    }
}
