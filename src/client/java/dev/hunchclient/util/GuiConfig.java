package dev.hunchclient.util;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages GUI state persistence (position, scale, scroll positions, etc.)
 */
public class GuiConfig {

    // Window state
    private int windowX = 0;
    private int windowY = 0;
    private int windowWidth = 0;  // 0 = use default
    private int windowHeight = 0; // 0 = use default

    // Tab state
    private int lastSelectedTab = 0;

    // Scroll positions per tab (key: category name, value: scroll offset)
    private final Map<String, Integer> scrollPositions = new HashMap<>();

    // Module page states (key: module name, value: expanded state)
    private final Map<String, Boolean> moduleExpanded = new HashMap<>();

    // Acknowledged risky modules (modules where user clicked "Don't show again")
    private final java.util.Set<String> acknowledgedModules = new java.util.HashSet<>();

    // IRC notifications (sound + chat output) enabled
    private boolean ircNotificationsEnabled = true;

    // Singleton instance
    private static GuiConfig instance;

    private GuiConfig() {
    }

    public static GuiConfig getInstance() {
        if (instance == null) {
            instance = new GuiConfig();
        }
        return instance;
    }

    // Window Position
    public void setWindowPosition(int x, int y) {
        this.windowX = x;
        this.windowY = y;
    }

    public int getWindowX() {
        return windowX;
    }

    public int getWindowY() {
        return windowY;
    }

    // Window Size
    public void setWindowSize(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    // Last Selected Tab
    public void setLastSelectedTab(int tabIndex) {
        this.lastSelectedTab = tabIndex;
    }

    public int getLastSelectedTab() {
        return lastSelectedTab;
    }

    // Scroll Positions
    public void setScrollPosition(String tabCategory, int scrollOffset) {
        scrollPositions.put(tabCategory, scrollOffset);
    }

    public int getScrollPosition(String tabCategory) {
        return scrollPositions.getOrDefault(tabCategory, 0);
    }

    // Module Expanded State
    public void setModuleExpanded(String moduleName, boolean expanded) {
        moduleExpanded.put(moduleName, expanded);
    }

    public boolean isModuleExpanded(String moduleName) {
        return moduleExpanded.getOrDefault(moduleName, false);
    }

    // Acknowledged Risky Modules
    public void acknowledgeModule(String moduleName) {
        acknowledgedModules.add(moduleName);
    }

    public boolean isModuleAcknowledged(String moduleName) {
        return acknowledgedModules.contains(moduleName);
    }

    public void resetAcknowledgedModules() {
        acknowledgedModules.clear();
    }

    // IRC Notifications (sound + chat output)
    public void setIrcNotificationsEnabled(boolean enabled) {
        this.ircNotificationsEnabled = enabled;
    }

    public boolean isIrcNotificationsEnabled() {
        return ircNotificationsEnabled;
    }

    /**
     * Save GUI config to JSON
     */
    public JsonObject save() {
        JsonObject json = new JsonObject();

        // Window state
        JsonObject window = new JsonObject();
        window.addProperty("x", windowX);
        window.addProperty("y", windowY);
        window.addProperty("width", windowWidth);
        window.addProperty("height", windowHeight);
        json.add("window", window);

        // Tab state
        json.addProperty("lastSelectedTab", lastSelectedTab);

        // Scroll positions
        JsonObject scrolls = new JsonObject();
        for (Map.Entry<String, Integer> entry : scrollPositions.entrySet()) {
            scrolls.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("scrollPositions", scrolls);

        // Module expanded states
        JsonObject expanded = new JsonObject();
        for (Map.Entry<String, Boolean> entry : moduleExpanded.entrySet()) {
            expanded.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("moduleExpanded", expanded);

        // Acknowledged risky modules
        com.google.gson.JsonArray acknowledged = new com.google.gson.JsonArray();
        for (String moduleName : acknowledgedModules) {
            acknowledged.add(moduleName);
        }
        json.add("acknowledgedModules", acknowledged);

        // IRC notifications enabled
        json.addProperty("ircNotificationsEnabled", ircNotificationsEnabled);

        return json;
    }

    /**
     * Load GUI config from JSON
     */
    public void load(JsonObject json) {
        if (json == null) {
            return;
        }

        // Window state
        if (json.has("window")) {
            JsonObject window = json.getAsJsonObject("window");
            if (window.has("x")) windowX = window.get("x").getAsInt();
            if (window.has("y")) windowY = window.get("y").getAsInt();
            if (window.has("width")) windowWidth = window.get("width").getAsInt();
            if (window.has("height")) windowHeight = window.get("height").getAsInt();
        }

        // Tab state
        if (json.has("lastSelectedTab")) {
            lastSelectedTab = json.get("lastSelectedTab").getAsInt();
        }

        // Scroll positions
        if (json.has("scrollPositions")) {
            JsonObject scrolls = json.getAsJsonObject("scrollPositions");
            scrollPositions.clear();
            for (String key : scrolls.keySet()) {
                scrollPositions.put(key, scrolls.get(key).getAsInt());
            }
        }

        // Module expanded states
        if (json.has("moduleExpanded")) {
            JsonObject expanded = json.getAsJsonObject("moduleExpanded");
            moduleExpanded.clear();
            for (String key : expanded.keySet()) {
                moduleExpanded.put(key, expanded.get(key).getAsBoolean());
            }
        }

        // Acknowledged risky modules
        if (json.has("acknowledgedModules")) {
            com.google.gson.JsonArray acknowledged = json.getAsJsonArray("acknowledgedModules");
            acknowledgedModules.clear();
            for (int i = 0; i < acknowledged.size(); i++) {
                acknowledgedModules.add(acknowledged.get(i).getAsString());
            }
        }

        // IRC notifications enabled
        if (json.has("ircNotificationsEnabled")) {
            ircNotificationsEnabled = json.get("ircNotificationsEnabled").getAsBoolean();
        }
    }

    /**
     * Reset to defaults
     */
    public void reset() {
        windowX = 0;
        windowY = 0;
        windowWidth = 0;
        windowHeight = 0;
        lastSelectedTab = 0;
        scrollPositions.clear();
        moduleExpanded.clear();
        acknowledgedModules.clear();
        ircNotificationsEnabled = true;
    }
}
