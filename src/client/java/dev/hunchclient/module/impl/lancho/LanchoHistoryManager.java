package dev.hunchclient.module.impl.lancho;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages local storage of Lancho conversation history
 */
public class LanchoHistoryManager {

    private static final Path HISTORY_DIR = Paths.get("config", "hunchclient", "lancho");
    private static final Path HISTORY_FILE = HISTORY_DIR.resolve("chat_history.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY_MESSAGES = 500; // Keep last 500 messages

    /**
     * Save a conversation message to local history
     */
    public static void saveMessage(String sessionId, String sender, String prompt, String response) {
        try {
            Files.createDirectories(HISTORY_DIR);

            // Load existing history
            List<ConversationMessage> history = loadHistory();

            // Add new message pair (prompt + response)
            long timestamp = System.currentTimeMillis();
            history.add(new ConversationMessage(sessionId, sender, prompt, "user", timestamp));
            history.add(new ConversationMessage(sessionId, "Lancho", response, "assistant", timestamp + 1));

            // Limit history size
            if (history.size() > MAX_HISTORY_MESSAGES) {
                history = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
            }

            // Save to file
            JsonArray jsonArray = new JsonArray();
            for (ConversationMessage msg : history) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("sessionId", msg.sessionId);
                msgObj.addProperty("sender", msg.sender);
                msgObj.addProperty("content", msg.content);
                msgObj.addProperty("role", msg.role);
                msgObj.addProperty("timestamp", msg.timestamp);
                jsonArray.add(msgObj);
            }

            String json = GSON.toJson(jsonArray);
            Files.writeString(
                HISTORY_FILE,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );

            System.out.println("[LanchoHistory] Saved message to history (total: " + history.size() + " messages)");

        } catch (IOException e) {
            System.err.println("[LanchoHistory] Failed to save message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load conversation history from local storage
     */
    public static List<ConversationMessage> loadHistory() {
        List<ConversationMessage> history = new ArrayList<>();

        if (!Files.exists(HISTORY_FILE)) {
            System.out.println("[LanchoHistory] No history file found");
            return history;
        }

        try {
            String json = Files.readString(HISTORY_FILE, StandardCharsets.UTF_8);
            JsonArray jsonArray = GSON.fromJson(json, JsonArray.class);

            if (jsonArray != null) {
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject msgObj = jsonArray.get(i).getAsJsonObject();
                    String sessionId = msgObj.has("sessionId") ? msgObj.get("sessionId").getAsString() : "";
                    String sender = msgObj.get("sender").getAsString();
                    String content = msgObj.get("content").getAsString();
                    String role = msgObj.has("role") ? msgObj.get("role").getAsString() : "user";
                    long timestamp = msgObj.has("timestamp") ? msgObj.get("timestamp").getAsLong() : System.currentTimeMillis();

                    history.add(new ConversationMessage(sessionId, sender, content, role, timestamp));
                }
            }

            System.out.println("[LanchoHistory] Loaded " + history.size() + " messages from history");

        } catch (Exception e) {
            System.err.println("[LanchoHistory] Failed to load history: " + e.getMessage());
            e.printStackTrace();
        }

        return history;
    }

    /**
     * Get history for a specific session ID
     */
    public static List<ConversationMessage> getSessionHistory(String sessionId) {
        List<ConversationMessage> allHistory = loadHistory();
        List<ConversationMessage> sessionHistory = new ArrayList<>();

        for (ConversationMessage msg : allHistory) {
            if (msg.sessionId.equals(sessionId)) {
                sessionHistory.add(msg);
            }
        }

        return sessionHistory;
    }

    /**
     * Clear all history
     */
    public static void clearHistory() {
        try {
            if (Files.exists(HISTORY_FILE)) {
                Files.delete(HISTORY_FILE);
                System.out.println("[LanchoHistory] History cleared");
            }
        } catch (IOException e) {
            System.err.println("[LanchoHistory] Failed to clear history: " + e.getMessage());
        }
    }

    /**
     * Conversation message data class
     */
    public static class ConversationMessage {
        public final String sessionId;
        public final String sender;
        public final String content;
        public final String role; // "user" or "assistant"
        public final long timestamp;

        public ConversationMessage(String sessionId, String sender, String content, String role, long timestamp) {
            this.sessionId = sessionId;
            this.sender = sender;
            this.content = content;
            this.role = role;
            this.timestamp = timestamp;
        }
    }
}
