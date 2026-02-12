package dev.hunchclient.module;

import com.google.gson.JsonObject;

/**
 * Modules that can persist their settings implement this interface.
 * The returned JsonObject should only contain module-specific values.
 */
public interface ConfigurableModule {

    /**
     * Serialises the module specific settings into a Json object.
     */
    JsonObject saveConfig();

    /**
     * Restores module specific settings from the provided Json object.
     */
    void loadConfig(JsonObject data);
}

