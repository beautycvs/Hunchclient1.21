package dev.hunchclient.module.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Checkbox (boolean) setting for modules
 */
public class CheckboxSetting implements ModuleSetting {

    private final String name;
    private final String description;
    private final String key;
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private Supplier<Boolean> visibilitySupplier = () -> true;

    public CheckboxSetting(String name, String description, String key,
                          Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.getter = getter;
        this.setter = setter;
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
        return SettingType.CHECKBOX;
    }

    public boolean getValue() {
        return getter.get();
    }

    public void setValue(boolean value) {
        setter.accept(value);
    }

    public void toggle() {
        setValue(!getValue());
    }

    public CheckboxSetting setVisible(Supplier<Boolean> visibilitySupplier) {
        this.visibilitySupplier = visibilitySupplier;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visibilitySupplier.get();
    }
}
