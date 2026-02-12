package dev.hunchclient.util;

/** Color utility with HSBA support and cached RGBA conversion. */
public class Color {
    private float hue;
    private float saturation;
    private float brightness;
    private float alphaFloat;

    private boolean needsUpdate = true;
    private int cachedRgba = 0;

    // Constructors
    public Color(float hue, float saturation, float brightness, float alpha) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alphaFloat = alpha;
    }

    public Color(float hue, float saturation, float brightness) {
        this(hue, saturation, brightness, 1.0f);
    }

    public Color(int r, int g, int b, float alpha) {
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alphaFloat = alpha;
    }

    public Color(int r, int g, int b) {
        this(r, g, b, 1.0f);
    }

    public Color(int rgba) {
        this(getRed(rgba), getGreen(rgba), getBlue(rgba), getAlpha(rgba) / 255.0f);
    }

    // Getters and Setters
    public float getHue() {
        return hue;
    }

    public void setHue(float hue) {
        this.hue = hue;
        this.needsUpdate = true;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
        this.needsUpdate = true;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
        this.needsUpdate = true;
    }

    public float getAlphaFloat() {
        return alphaFloat;
    }

    public void setAlphaFloat(float alpha) {
        this.alphaFloat = alpha;
        this.needsUpdate = true;
    }

    public int getRgba() {
        if (needsUpdate) {
            int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
            cachedRgba = (rgb & 0x00FFFFFF) | ((int)(alphaFloat * 255) << 24);
            needsUpdate = false;
        }
        return cachedRgba;
    }

    public int getRed() {
        return getRed(getRgba());
    }

    public int getGreen() {
        return getGreen(getRgba());
    }

    public int getBlue() {
        return getBlue(getRgba());
    }

    public int getAlpha() {
        return getAlpha(getRgba());
    }

    public float getRedFloat() {
        return getRed() / 255.0f;
    }

    public float getGreenFloat() {
        return getGreen() / 255.0f;
    }

    public float getBlueFloat() {
        return getBlue() / 255.0f;
    }

    public boolean isTransparent() {
        return alphaFloat == 0.0f;
    }

    public Color copy() {
        return new Color(getRgba());
    }

    // Utility methods
    public Color brighter(float factor) {
        return new Color(hue, saturation, Math.min(brightness * Math.max(factor, 1.0f), 1.0f), alphaFloat);
    }

    public Color darker(float factor) {
        return new Color(hue, saturation, brightness * factor, alphaFloat);
    }

    public Color darker() {
        return darker(0.7f);
    }

    public Color withAlpha(float alpha) {
        return new Color(getRed(), getGreen(), getBlue(), alpha);
    }

    // Static utility methods
    public static int getRed(int rgba) {
        return (rgba >> 16) & 0xFF;
    }

    public static int getGreen(int rgba) {
        return (rgba >> 8) & 0xFF;
    }

    public static int getBlue(int rgba) {
        return rgba & 0xFF;
    }

    public static int getAlpha(int rgba) {
        return (rgba >> 24) & 0xFF;
    }

    /**
     * Creates a new color with the specified alpha value
     */
    public static int withAlpha(int rgba, int alpha) {
        return (rgba & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    @Override
    public String toString() {
        return String.format("Color(red=%d,green=%d,blue=%d,alpha=%d)", getRed(), getGreen(), getBlue(), getAlpha());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Color)) return false;
        Color otherColor = (Color) other;
        return getRgba() == otherColor.getRgba();
    }

    @Override
    public int hashCode() {
        return getRgba();
    }
}
