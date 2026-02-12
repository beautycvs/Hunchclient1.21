package dev.hunchclient.module.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Text input setting for modules
 */
public class TextBoxSetting implements ModuleSetting {

    private final String name;
    private final String description;
    private final String key;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final String placeholder;

    public TextBoxSetting(String name, String description, String key,
                         Supplier<String> getter, Consumer<String> setter, String placeholder) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.getter = getter;
        this.setter = setter;
        this.placeholder = placeholder;
    }

    public TextBoxSetting(String name, String description, String key,
                         Supplier<String> getter, Consumer<String> setter) {
        this(name, description, key, getter, setter, "");
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
        return SettingType.TEXT_BOX;
    }

    public String getValue() {
        return getter.get();
    }

    public void setValue(String value) {
        setter.accept(value);
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
