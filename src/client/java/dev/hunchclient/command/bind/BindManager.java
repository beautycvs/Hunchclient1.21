package dev.hunchclient.command.bind;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages key bindings for modules and commands
 */
public class BindManager {

    private static BindManager instance;
    private final Map<Integer, Bind> binds = new ConcurrentHashMap<>();
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Minecraft mc = Minecraft.getInstance();
    private boolean listening = true;

    private BindManager() {
        // Register tick event to handle key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!listening || client.screen != null) {
                return;
            }
            processKeyBinds();
        });
    }

    public static BindManager getInstance() {
        if (instance == null) {
            instance = new BindManager();
        }
        return instance;
    }

    /**
     * Process key binds each tick
     */
    private void processKeyBinds() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        // Check each bind
        for (Map.Entry<Integer, Bind> entry : binds.entrySet()) {
            int keyCode = entry.getKey();
            Bind bind = entry.getValue();

            boolean isPressed;
            if (bind.isMouse()) {
                // Mouse button
                isPressed = GLFW.glfwGetMouseButton(mc.getWindow().handle(), bind.getMouseButton()) == GLFW.GLFW_PRESS;
            } else {
                // Keyboard key
                isPressed = InputConstants.isKeyDown(mc.getWindow(), keyCode);
            }

            // Check for key press (not hold)
            if (isPressed && !pressedKeys.contains(keyCode)) {
                pressedKeys.add(keyCode);
                toggleModule(bind.getModuleName());
            } else if (!isPressed && pressedKeys.contains(keyCode)) {
                pressedKeys.remove(keyCode);
            }
        }
    }

    /**
     * Toggle a module by name
     */
    private void toggleModule(String moduleName) {
        ModuleManager manager = ModuleManager.getInstance();
        Module module = manager.getModuleByName(moduleName);

        if (module != null && module.isToggleable()) {
            module.toggle();
            // Don't send chat message for keybind toggles - visual feedback only
        }
    }

    /**
     * Add a new bind
     */
    public void addBind(Bind bind) {
        binds.put(bind.getKeyCode(), bind);
        saveBinds();
    }

    /**
     * Remove a bind by key code
     */
    public void removeBind(int keyCode) {
        binds.remove(keyCode);
        pressedKeys.remove(keyCode);
        saveBinds();
    }

    /**
     * Get a bind by key code
     */
    public Bind getBind(int keyCode) {
        return binds.get(keyCode);
    }

    /**
     * Get all binds
     */
    public List<Bind> getAllBinds() {
        return new ArrayList<>(binds.values());
    }

    /**
     * Clear all binds
     */
    public void clearAllBinds() {
        binds.clear();
        pressedKeys.clear();
        saveBinds();
    }

    /**
     * Set whether the bind manager is listening for key presses
     */
    public void setListening(boolean listening) {
        this.listening = listening;
        if (!listening) {
            pressedKeys.clear();
        }
    }

    /**
     * Check if a key is bound
     */
    public boolean isBound(int keyCode) {
        return binds.containsKey(keyCode);
    }

    /**
     * Get the module name bound to a key
     */
    public String getBoundModule(int keyCode) {
        Bind bind = binds.get(keyCode);
        return bind != null ? bind.getModuleName() : null;
    }

    /**
     * Save binds to file
     */
    public void saveBinds() {
        File file = new File(mc.gameDirectory, "hunchclient/binds.json");
        file.getParentFile().mkdirs();

        JsonObject json = new JsonObject();
        JsonArray bindArray = new JsonArray();

        for (Bind bind : binds.values()) {
            JsonObject bindObj = new JsonObject();
            bindObj.addProperty("keyCode", bind.getKeyCode());
            bindObj.addProperty("keyName", bind.getKeyName());
            bindObj.addProperty("moduleName", bind.getModuleName());
            bindArray.add(bindObj);
        }

        json.add("binds", bindArray);

        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            HunchClient.LOGGER.info("Saved {} binds to file", binds.size());
        } catch (IOException e) {
            HunchClient.LOGGER.error("Failed to save binds", e);
        }
    }

    /**
     * Load binds from file
     */
    public void loadBinds() {
        File file = new File(mc.gameDirectory, "hunchclient/binds.json");
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray bindArray = json.getAsJsonArray("binds");

            binds.clear();
            for (JsonElement element : bindArray) {
                JsonObject bindObj = element.getAsJsonObject();
                int keyCode = bindObj.get("keyCode").getAsInt();
                String keyName = bindObj.get("keyName").getAsString();
                String moduleName = bindObj.get("moduleName").getAsString();

                Bind bind = new Bind(keyCode, keyName, moduleName);
                binds.put(keyCode, bind);
            }

            HunchClient.LOGGER.info("Loaded {} binds from file", binds.size());
        } catch (Exception e) {
            HunchClient.LOGGER.error("Failed to load binds", e);
        }
    }
}