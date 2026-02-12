package dev.hunchclient.module.setting;

/**
 * Base interface for module settings that can be rendered in GUI
 */
public interface ModuleSetting {

    /**
     * Get the display name of this setting
     */
    String getName();

    /**
     * Get the description/tooltip
     */
    String getDescription();

    /**
     * Get the unique key for this setting (used for config storage)
     */
    String getKey();

    /**
     * Get the type of this setting
     */
    SettingType getType();

    /**
     * Check if this setting is visible based on current state
     */
    default boolean isVisible() {
        return true;
    }

    enum SettingType {
        TEXT_BOX,
        CHECKBOX,
        DROPDOWN,
        SLIDER,
        COLOR_PICKER,
        BUTTON
    }
}
