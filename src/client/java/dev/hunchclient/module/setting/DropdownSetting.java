package dev.hunchclient.module.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Dropdown selection setting for modules
 */
public class DropdownSetting implements ModuleSetting {

    private final String name;
    private final String description;
    private final String key;
    private final String[] options;
    private final Supplier<Integer> getter; // Index of selected option
    private final Consumer<Integer> setter;
    private Supplier<Boolean> visibilitySupplier = () -> true;

    public DropdownSetting(String name, String description, String key, String[] options,
                          Supplier<Integer> getter, Consumer<Integer> setter) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.options = options;
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
        return SettingType.DROPDOWN;
    }

    public String[] getOptions() {
        return options;
    }

    public int getSelectedIndex() {
        return getter.get();
    }

    public String getSelectedOption() {
        int index = getSelectedIndex();
        if (index >= 0 && index < options.length) {
            return options[index];
        }
        return options.length > 0 ? options[0] : "";
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < options.length) {
            setter.accept(index);
        }
    }

    public DropdownSetting setVisible(Supplier<Boolean> visibilitySupplier) {
        this.visibilitySupplier = visibilitySupplier;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visibilitySupplier.get();
    }

    public void cycle() {
        int current = getSelectedIndex();
        int next = (current + 1) % options.length;
        setSelectedIndex(next);
    }

    public void cycleBackwards() {
        int current = getSelectedIndex();
        int previous = (current - 1 + options.length) % options.length;
        setSelectedIndex(previous);
    }
}
