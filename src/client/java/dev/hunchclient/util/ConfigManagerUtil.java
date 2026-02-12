package dev.hunchclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * Utility class for managing config file I/O operations
 * Handles Base64 encoding/decoding and JSON serialization
 */
public class ConfigManagerUtil {

    private static final Path CONFIG_DIR = Paths.get("config", "hunchclient");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.dat");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManagerUtil() {
    }

    /**
     * Load config from file or create a new empty config if file doesn't exist
     */
    public static JsonObject loadOrCreateConfig() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            return new JsonObject();
        }

        String encoded = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
        encoded = encoded.replaceAll("\\s", "");
        
        if (encoded.isEmpty()) {
            return new JsonObject();
        }

        String json;
        try {
            json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("Config file is not valid Base64", e);
        }

        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Config file contains invalid JSON", e);
        }

        return root != null ? root : new JsonObject();
    }

    /**
     * Save config to file
     */
    public static void saveConfig(JsonObject root) throws IOException {
        Files.createDirectories(CONFIG_DIR);

        String json = GSON.toJson(root);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        
        Files.writeString(
            CONFIG_FILE,
            encoded,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }
}

