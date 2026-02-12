package dev.hunchclient.module.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Slider setting for numeric values
 */
public class SliderSetting implements ModuleSetting {

    private final String name;
    private final String description;
    private final String key;
    private final float min;
    private final float max;
    private final Supplier<Float> getter;
    private final Consumer<Float> setter;

    // Display options
    private int decimals = 1;
    private String suffix = "";
    private boolean percentage = false;
    private Supplier<Boolean> visibilitySupplier = () -> true;

    public SliderSetting(String name, String description, String key,
                        float min, float max,
                        Supplier<Float> getter, Consumer<Float> setter) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.min = min;
        this.max = max;
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
        return SettingType.SLIDER;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public float getValue() {
        return getter.get();
    }

    public void setValue(float value) {
        setter.accept(Math.max(min, Math.min(max, value)));
    }

    // Display configuration
    public SliderSetting withDecimals(int decimals) {
        this.decimals = decimals;
        return this;
    }

    public SliderSetting withSuffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public SliderSetting asPercentage() {
        this.percentage = true;
        return this;
    }

    public SliderSetting setVisible(Supplier<Boolean> visibilitySupplier) {
        this.visibilitySupplier = visibilitySupplier;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visibilitySupplier.get();
    }

    public int getDecimals() {
        return decimals;
    }

    public String getSuffix() {
        return suffix;
    }

    public boolean isPercentage() {
        return percentage;
    }

    /**
     * Format value for display
     */
    public String formatValue() {
        float value = getValue();
        if (percentage) {
            return String.format("%.0f%%", value * 100);
        }
        String format = "%." + decimals + "f";
        return String.format(format, value) + suffix;
    }
}
