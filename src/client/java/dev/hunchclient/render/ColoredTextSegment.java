package dev.hunchclient.render;

/**
 * Represents a text segment with a specific color for custom font rendering
 */
public class ColoredTextSegment {
    public final String text;
    public final int color; // RGB color (0xRRGGBB)
    public final boolean bold;
    public final boolean italic;
    public final boolean underline;
    public final boolean strikethrough;
    public final boolean obfuscated;

    public ColoredTextSegment(String text, int color) {
        this(text, color, false, false, false, false, false);
    }

    public ColoredTextSegment(String text, int color, boolean bold, boolean italic,
                            boolean underline, boolean strikethrough, boolean obfuscated) {
        this.text = text;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    /**
     * Convert ARGB color to RGB (strip alpha channel)
     */
    public static int toRGB(int argb) {
        return argb & 0xFFFFFF;
    }
}
