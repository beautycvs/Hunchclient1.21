package dev.hunchclient.util;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
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

public final class ConfigManager {

    private static final Path CONFIG_DIR = Paths.get("config", "hunchclient");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.dat");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
    }

    public static void save() throws IOException {
        Files.createDirectories(CONFIG_DIR);

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("enabled", module.isEnabled());

            if (module instanceof ConfigurableModule configurable) {
                moduleObject.add("data", configurable.saveConfig());
            }

            modules.add(module.getName(), moduleObject);
        }

        root.add("modules", modules);

        // Save GUI settings
        JsonObject guiSettings = GuiConfig.getInstance().save();
        if (guiSettings != null && guiSettings.size() > 0) {
            root.add("gui", guiSettings);
        }

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

    public static void load() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            throw new IOException("Config file not found");
        }

        String encoded = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
        encoded = encoded.replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            throw new IOException("Config file is empty");
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
        if (root == null || !root.has("modules")) {
            return;
        }

        JsonObject modules = root.getAsJsonObject("modules");
        if (modules == null) {
            return;
        }

        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleObject = modules.getAsJsonObject(module.getName());
            if (moduleObject == null) {
                continue;
            }

            // CRITICAL FIX: Skip ImageHUDModule completely during startup
            // GLFW is not initialized yet, enabling it would crash the game
            // User must manually enable ImageHUD after game is loaded
            boolean isImageHUD = "HUD".equals(module.getName());

            if (isImageHUD) {
                // Store config but don't apply it and don't enable module
                if (module instanceof ConfigurableModule configurable && moduleObject.has("data") && moduleObject.get("data").isJsonObject()) {
                    configurable.loadConfig(moduleObject.getAsJsonObject("data"));
                }
                // SKIP enabling - keep it disabled
                continue;
            }

            if (module instanceof ConfigurableModule configurable && moduleObject.has("data") && moduleObject.get("data").isJsonObject()) {
                configurable.loadConfig(moduleObject.getAsJsonObject("data"));
            }

            if (moduleObject.has("enabled")) {
                boolean shouldEnable = moduleObject.get("enabled").getAsBoolean();
                module.setEnabled(shouldEnable);
            }
        }

        // Load GUI settings
        if (root.has("gui")) {
            JsonObject guiSettings = root.getAsJsonObject("gui");
            GuiConfig.getInstance().load(guiSettings);
        }
    }

    /**
     * Save configuration to a specific file
     */
    public static void saveToFile(java.io.File file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("enabled", module.isEnabled());

            if (module instanceof ConfigurableModule configurable) {
                moduleObject.add("data", configurable.saveConfig());
            }

            modules.add(module.getName(), moduleObject);
        }

        root.add("modules", modules);

        String json = GSON.toJson(root);
        Files.writeString(
            file.toPath(),
            json,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    /**
     * Load configuration from a specific file
     */
    public static void loadFromFile(java.io.File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Config file not found");
        }

        String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Config file contains invalid JSON", e);
        }
        if (root == null || !root.has("modules")) {
            return;
        }

        JsonObject modules = root.getAsJsonObject("modules");
        if (modules == null) {
            return;
        }

        for (Module module : ModuleManager.getInstance().getModules()) {
            JsonObject moduleObject = modules.getAsJsonObject(module.getName());
            if (moduleObject == null) {
                continue;
            }

            // Skip ImageHUDModule
            boolean isImageHUD = "HUD".equals(module.getName());
            if (isImageHUD) {
                if (module instanceof ConfigurableModule configurable && moduleObject.has("data") && moduleObject.get("data").isJsonObject()) {
                    configurable.loadConfig(moduleObject.getAsJsonObject("data"));
                }
                continue;
            }

            if (module instanceof ConfigurableModule configurable && moduleObject.has("data") && moduleObject.get("data").isJsonObject()) {
                configurable.loadConfig(moduleObject.getAsJsonObject("data"));
            }

            if (moduleObject.has("enabled")) {
                boolean shouldEnable = moduleObject.get("enabled").getAsBoolean();
                module.setEnabled(shouldEnable);
            }
        }
    }
}
