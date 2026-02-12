package dev.hunchclient.module.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Color picker setting for modules
 * Stores color as ARGB int (0xAARRGGBB)
 */
public class ColorPickerSetting implements ModuleSetting {

    private final String name;
    private final String description;
    private final String key;
    private final Supplier<Integer> getter;
    private final Consumer<Integer> setter;
    private Supplier<Boolean> visibilitySupplier = () -> true;

    public ColorPickerSetting(String name, String description, String key,
                             Supplier<Integer> getter, Consumer<Integer> setter) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.getter = getter;
        this.setter = setter;
    }

    public ColorPickerSetting setVisible(Supplier<Boolean> visibilitySupplier) {
        this.visibilitySupplier = visibilitySupplier;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visibilitySupplier.get();
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
        return SettingType.COLOR_PICKER;
    }

    public int getColor() {
        return getter.get();
    }

    public void setColor(int color) {
        setter.accept(color);
    }

    // Component getters/setters
    public int getAlpha() {
        return (getColor() >> 24) & 0xFF;
    }

    public int getRed() {
        return (getColor() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (getColor() >> 8) & 0xFF;
    }

    public int getBlue() {
        return getColor() & 0xFF;
    }

    public void setAlpha(int alpha) {
        int color = getColor();
        int rgb = color & 0x00FFFFFF;
        setColor((alpha << 24) | rgb);
    }

    public void setRed(int red) {
        int color = getColor();
        int agb = color & 0xFF00FFFF;
        setColor(agb | (red << 16));
    }

    public void setGreen(int green) {
        int color = getColor();
        int arb = color & 0xFFFF00FF;
        setColor(arb | (green << 8));
    }

    public void setBlue(int blue) {
        int color = getColor();
        int arg = color & 0xFFFFFF00;
        setColor(arg | blue);
    }

    public void setRGB(int r, int g, int b) {
        int alpha = getAlpha();
        setColor((alpha << 24) | (r << 16) | (g << 8) | b);
    }

    public void setARGB(int a, int r, int g, int b) {
        setColor((a << 24) | (r << 16) | (g << 8) | b);
    }

    /**
     * Get color as hex string (without alpha)
     */
    public String getHexRGB() {
        return String.format("%06X", getColor() & 0xFFFFFF);
    }

    /**
     * Get color as hex string (with alpha)
     */
    public String getHexARGB() {
        return String.format("%08X", getColor());
    }

    /**
     * Set color from hex string (6 or 8 chars)
     */
    public void setFromHex(String hex) {
        try {
            hex = hex.replace("#", "").replace("0x", "");
            if (hex.length() == 6) {
                // RGB only, keep current alpha
                int rgb = Integer.parseInt(hex, 16);
                int alpha = getAlpha();
                setColor((alpha << 24) | rgb);
            } else if (hex.length() == 8) {
                // ARGB
                setColor((int) Long.parseLong(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // Invalid hex, ignore
        }
    }
}
