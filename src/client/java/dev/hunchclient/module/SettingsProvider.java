package dev.hunchclient.module;

import dev.hunchclient.module.setting.ModuleSetting;

import java.util.List;

/**
 * Interface for modules that want to provide GUI settings
 */
public interface SettingsProvider {

    /**
     * Get list of settings for this module
     * @return List of settings to display in GUI
     */
    List<ModuleSetting> getSettings();
}
