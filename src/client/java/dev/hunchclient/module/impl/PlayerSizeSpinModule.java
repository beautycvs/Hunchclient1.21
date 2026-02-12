package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerSizeSpin Module - Modifies player model size and spin
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only rendering transformation
 * - No packets sent
 * - Visual only
 */
public class PlayerSizeSpinModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final String REMOTE_URL = "https://34.7.234.242/helper/playermodel-server.json";
    private static final int REFRESH_SECONDS = 300; // 5 minutes

    private final Map<String, PlayerModel> playerModels = new ConcurrentHashMap<>();
    private long lastFetch = 0;
    private final Gson gson = new Gson();
    private static PlayerSizeSpinModule instance;

    // Local configurable settings for self
    // Default to 2x scale for testing - you can change this via GUI later
    private float scaleX = 2.0f;
    private float scaleY = 2.0f;
    private float scaleZ = 2.0f;
    private boolean spinEnabled = false;
    private float spinSpeed = 1.0f;
    private boolean invertSpin = false;
    private boolean upsideDown = false;

    public PlayerSizeSpinModule() {
        super("PlayerSizeSpin", "Modifies player model size and spin", Category.VISUALS, false);

        instance = this;

        // Fetch remote models IMMEDIATELY on construction (before background thread)
        // This ensures server overrides work even if module is never toggled
        fetchRemoteModels();

        // Start background thread to keep server data updated even when module is disabled
        startBackgroundFetcher();
    }

    @Override
    protected void onEnable() {
        fetchRemoteModels();
    }

    @Override
    protected void onDisable() {
        // Keep models in memory
    }

    /**
     * Start a background thread that fetches server data continuously
     * This runs REGARDLESS of module enabled state, so server data is always available
     */
    private void startBackgroundFetcher() {
        Thread fetcherThread = new Thread(() -> {
            while (true) {
                try {
                    long now = System.currentTimeMillis();
                    if (now - lastFetch > REFRESH_SECONDS * 1000L) {
                        fetchRemoteModels();
                    }
                    Thread.sleep(10000); // Check every 10 seconds
                } catch (InterruptedException e) {
                    break; // Exit if interrupted
                } catch (Exception e) {
                    // Silently continue on error
                }
            }
        }, "PlayerSizeModule-Fetcher");
        fetcherThread.setDaemon(true); // Daemon thread exits when main program exits
        fetcherThread.start();
    }

    /**
     * Resolve render settings for a player by name
     * Called from EntityRenderMixin
     *
     * Logic:
     * - Module ENABLED + isSelf → Use local settings (user override)
     * - Module DISABLED + isSelf → Use server settings (server can override)
     * - Not self → Always use server settings
     */
    public PlayerModel getPlayerModel(String playerName, boolean isSelf) {
        if (playerName == null || playerName.isEmpty()) {
            return null;
        }

        // If module is ENABLED and this is self, ALWAYS use local settings
        // This allows user to override server settings for themselves
        if (isSelf && isEnabled()) {
            return new PlayerModel(scaleX, scaleY, scaleZ, spinEnabled, spinSpeed, invertSpin, upsideDown);
        }

        // Module DISABLED or not self → use server settings
        PlayerModel model = playerModels.get(playerName.toLowerCase());
        if (model != null && !isIdentity(model)) {
            return model;
        }

        return null;
    }

    /**
     * Set player model
     */
    public void setPlayerModel(String playerName, float scaleX, float scaleY, float scaleZ, boolean spin, float spinSpeed) {
        playerModels.put(playerName.toLowerCase(), new PlayerModel(scaleX, scaleY, scaleZ, spin, spinSpeed, false, false));
    }

    /**
     * Remove player model
     */
    public void removePlayerModel(String playerName) {
        playerModels.remove(playerName.toLowerCase());
    }

    /**
     * Clear all player models
     */
    public void clearPlayerModels() {
        playerModels.clear();
    }

    /**
     * Get all player models
     */
    public Map<String, PlayerModel> getPlayerModels() {
        return new HashMap<>(playerModels);
    }

    // Getters with validation
    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getScaleZ() {
        return scaleZ;
    }

    public boolean isSpinEnabled() {
        return spinEnabled;
    }

    public float getSpinSpeed() {
        return spinSpeed;
    }

    public boolean isInvertSpin() {
        return invertSpin;
    }

    public boolean isUpsideDown() {
        return upsideDown;
    }

    // Setters with range validation
    public void setScaleX(float scaleX) {
        this.scaleX = clamp(scaleX, 0.1f, 5.0f);
    }

    public void setScaleY(float scaleY) {
        this.scaleY = clamp(scaleY, 0.1f, 5.0f);
    }

    public void setScaleZ(float scaleZ) {
        this.scaleZ = clamp(scaleZ, 0.1f, 5.0f);
    }

    public void setSpinEnabled(boolean spinEnabled) {
        this.spinEnabled = spinEnabled;
    }

    public void setSpinSpeed(float spinSpeed) {
        this.spinSpeed = clamp(spinSpeed, 0.1f, 10.0f);
    }

    public void setInvertSpin(boolean invertSpin) {
        this.invertSpin = invertSpin;
    }

    public void setUpsideDown(boolean upsideDown) {
        this.upsideDown = upsideDown;
    }

    /**
     * Set local model (for self) - legacy method
     */
    public void setLocalModel(float scaleX, float scaleY, float scaleZ, boolean spin, float spinSpeed) {
        setScaleX(scaleX);
        setScaleY(scaleY);
        setScaleZ(scaleZ);
        setSpinEnabled(spin);
        setSpinSpeed(spinSpeed);
    }

    /**
     * Get local model - legacy method
     */
    public PlayerModel getLocalModel() {
        return new PlayerModel(scaleX, scaleY, scaleZ, spinEnabled, spinSpeed, invertSpin, upsideDown);
    }

    private float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private boolean isIdentity(PlayerModel model) {
        return model == null
            || (approxEquals(model.scaleX, 1.0f)
            && approxEquals(model.scaleY, 1.0f)
            && approxEquals(model.scaleZ, 1.0f)
            && !model.spin);
    }

    private boolean approxEquals(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }

    private JsonObject fetchJsonFromUrl(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlString).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HunchClient/1.0");

            if (connection.getResponseCode() != 200) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Fetch models from remote server
     */
    private void fetchRemoteModels() {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = fetchJsonFromUrl(REMOTE_URL);
                if (json != null) {
                    // Check for "players" or "models" key, or treat whole object as map
                    JsonObject modelsJson = null;
                    if (json.has("players")) {
                        modelsJson = json.getAsJsonObject("players");
                    } else if (json.has("models")) {
                        modelsJson = json.getAsJsonObject("models");
                    } else {
                        modelsJson = json;
                    }

                    // Apply models
                    for (String playerName : modelsJson.keySet()) {
                        JsonObject modelData = modelsJson.getAsJsonObject(playerName);
                        float scaleX = modelData.has("scaleX") ? modelData.get("scaleX").getAsFloat() : 1.0f;
                        float scaleY = modelData.has("scaleY") ? modelData.get("scaleY").getAsFloat() : 1.0f;
                        float scaleZ = modelData.has("scaleZ") ? modelData.get("scaleZ").getAsFloat() : 1.0f;
                        boolean spin = modelData.has("spin") && modelData.get("spin").getAsBoolean();
                        float spinSpeed = modelData.has("spinSpeed") ? modelData.get("spinSpeed").getAsFloat() : 1.0f;
                        boolean invertSpin = modelData.has("invertSpin") && modelData.get("invertSpin").getAsBoolean();
                        boolean upsideDown = modelData.has("upsideDown") && modelData.get("upsideDown").getAsBoolean();

                        playerModels.put(playerName.toLowerCase(), new PlayerModel(scaleX, scaleY, scaleZ, spin, spinSpeed, invertSpin, upsideDown));
                    }

                    lastFetch = System.currentTimeMillis();
                    System.out.println("[PlayerSizeSpin] Loaded " + modelsJson.size() + " models from server");
                }
            } catch (Exception e) {
                // Silently fail, use local models
            }
        });
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("scaleX", scaleX);
        config.addProperty("scaleY", scaleY);
        config.addProperty("scaleZ", scaleZ);
        config.addProperty("spinEnabled", spinEnabled);
        config.addProperty("spinSpeed", spinSpeed);
        config.addProperty("invertSpin", invertSpin);
        config.addProperty("upsideDown", upsideDown);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("scaleX")) {
            setScaleX(data.get("scaleX").getAsFloat());
        }
        if (data.has("scaleY")) {
            setScaleY(data.get("scaleY").getAsFloat());
        }
        if (data.has("scaleZ")) {
            setScaleZ(data.get("scaleZ").getAsFloat());
        }
        if (data.has("spinEnabled")) {
            setSpinEnabled(data.get("spinEnabled").getAsBoolean());
        }
        if (data.has("spinSpeed")) {
            setSpinSpeed(data.get("spinSpeed").getAsFloat());
        }
        if (data.has("invertSpin")) {
            setInvertSpin(data.get("invertSpin").getAsBoolean());
        }
        if (data.has("upsideDown")) {
            setUpsideDown(data.get("upsideDown").getAsBoolean());
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Scale X
        settings.add(new SliderSetting(
            "Scale X",
            "Player width scale",
            "playersizespin_scale_x",
            0.1f, 5.0f,
            () -> scaleX,
            val -> scaleX = val
        ).withDecimals(1).withSuffix("x"));

        // Scale Y
        settings.add(new SliderSetting(
            "Scale Y",
            "Player height scale",
            "playersizespin_scale_y",
            0.1f, 5.0f,
            () -> scaleY,
            val -> scaleY = val
        ).withDecimals(1).withSuffix("x"));

        // Scale Z
        settings.add(new SliderSetting(
            "Scale Z",
            "Player depth scale",
            "playersizespin_scale_z",
            0.1f, 5.0f,
            () -> scaleZ,
            val -> scaleZ = val
        ).withDecimals(1).withSuffix("x"));

        // Spin Enabled
        settings.add(new CheckboxSetting(
            "Spin",
            "Enable player spinning",
            "playersizespin_spin",
            () -> spinEnabled,
            val -> spinEnabled = val
        ));

        // Spin Speed (conditional)
        settings.add(new SliderSetting(
            "Spin Speed",
            "Speed of player rotation",
            "playersizespin_spin_speed",
            0.1f, 10.0f,
            () -> spinSpeed,
            val -> spinSpeed = val
        ).withDecimals(1).withSuffix("x").setVisible(() -> spinEnabled));

        // Invert Spin (conditional)
        settings.add(new CheckboxSetting(
            "Invert Spin",
            "Reverse spin direction",
            "playersizespin_invert",
            () -> invertSpin,
            val -> invertSpin = val
        ).setVisible(() -> spinEnabled));

        // Upside Down
        settings.add(new CheckboxSetting(
            "Upside Down",
            "Flip player upside down",
            "playersizespin_upside_down",
            () -> upsideDown,
            val -> upsideDown = val
        ));

        return settings;
    }

    /**
     * Player model data class
     */
    public static class PlayerModel {
        public final float scaleX;
        public final float scaleY;
        public final float scaleZ;
        public final boolean spin;
        public final float spinSpeed;
        public final boolean invertSpin;
        public final boolean upsideDown;

        public PlayerModel(float scaleX, float scaleY, float scaleZ, boolean spin, float spinSpeed, boolean invertSpin, boolean upsideDown) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.spin = spin;
            this.spinSpeed = spinSpeed;
            this.invertSpin = invertSpin;
            this.upsideDown = upsideDown;
        }
    }
}
