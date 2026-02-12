package dev.hunchclient.module.setting;

/**
 * Button setting for modules - executes an action when clicked
 */
public class ButtonSetting implements ModuleSetting {

    private final String name;
    private final String description;
    private final String key;
    private final Runnable onClick;

    public ButtonSetting(String name, String description, String key, Runnable onClick) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.onClick = onClick;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public SettingType getType() {
        return SettingType.BUTTON;
    }

    public void execute() {
        if (onClick != null) {
            onClick.run();
        }
    }
}
