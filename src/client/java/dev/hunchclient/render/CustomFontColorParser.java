package dev.hunchclient.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses Minecraft color codes and MiniMessage-style gradients for custom font rendering.
 * Supports:
 * - Legacy § codes (§c, §a, etc.)
 * - Hex colors (§x§R§R§G§G§B§B)
 * - MiniMessage gradients (<gradient:#FF0000:#00FF00>text</gradient>)
 * - Formatting codes (§l bold, §o italic, etc.)
 */
public class CustomFontColorParser {

    private static final char SECTION = '\u00A7';
    private static final int DEFAULT_COLOR = 0xFFFFFF; // White
    private static final String GRADIENT_CLOSE = "</gradient>";

    // Direct color code mapping (bypasses ChatFormatting.getByCode which can return null)
    private static final Map<Character, Integer> COLOR_CODE_MAP = Map.ofEntries(
        Map.entry('0', 0x000000), // BLACK
        Map.entry('1', 0x0000AA), // DARK_BLUE
        Map.entry('2', 0x00AA00), // DARK_GREEN
        Map.entry('3', 0x00AAAA), // DARK_AQUA
        Map.entry('4', 0xAA0000), // DARK_RED
        Map.entry('5', 0xAA00AA), // DARK_PURPLE
        Map.entry('6', 0xFFAA00), // GOLD
        Map.entry('7', 0xAAAAAA), // GRAY
        Map.entry('8', 0x555555), // DARK_GRAY
        Map.entry('9', 0x5555FF), // BLUE
        Map.entry('a', 0x55FF55), // GREEN
        Map.entry('b', 0x55FFFF), // AQUA
        Map.entry('c', 0xFF5555), // RED
        Map.entry('d', 0xFF55FF), // LIGHT_PURPLE
        Map.entry('e', 0xFFFF55), // YELLOW
        Map.entry('f', 0xFFFFFF)  // WHITE
    );

    /**
     * Parse text with color codes into colored segments
     */
    public static List<ColoredTextSegment> parse(String input) {
        return parse(input, DEFAULT_COLOR);
    }

    /**
     * Parse text with color codes into colored segments with a default color
     */
    public static List<ColoredTextSegment> parse(String input, int defaultColor) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        // Check for MiniMessage gradient tags
        if (hasGradientTags(input)) {
            return parseWithGradients(input, defaultColor);
        }

        return parseLegacy(input, defaultColor);
    }

    private static boolean hasGradientTags(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.contains("<gradient:") && lower.contains(GRADIENT_CLOSE);
    }

    /**
     * Parse legacy § codes and hex colors
     */
    private static List<ColoredTextSegment> parseLegacy(String input, int defaultColor) {
        List<ColoredTextSegment> segments = new ArrayList<>();
        int currentColor = defaultColor;
        boolean bold = false, italic = false, underline = false, strikethrough = false, obfuscated = false;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Not a color code or last character
            if (c != SECTION || i + 1 >= input.length()) {
                buffer.append(c);
                continue;
            }

            // Flush current buffer before processing color code
            if (buffer.length() > 0) {
                segments.add(new ColoredTextSegment(buffer.toString(), currentColor, bold, italic, underline, strikethrough, obfuscated));
                buffer.setLength(0);
            }

            char code = Character.toLowerCase(input.charAt(++i));

            // Check for hex color (§x§R§R§G§G§B§B)
            if (code == 'x' && i + 12 < input.length()) {
                Integer hexColor = parseHexColorCode(input, i);
                if (hexColor != null) {
                    currentColor = hexColor;
                    i += 12; // Skip the hex color characters
                    continue;
                }
            }

            // Check for color code (0-9, a-f)
            Integer colorValue = COLOR_CODE_MAP.get(code);
            if (colorValue != null) {
                currentColor = colorValue;
                // Color codes reset formatting
                bold = italic = underline = strikethrough = obfuscated = false;
                continue;
            }

            // Check for reset code
            if (code == 'r') {
                currentColor = defaultColor;
                bold = italic = underline = strikethrough = obfuscated = false;
                continue;
            }

            // Check for formatting codes
            switch (code) {
                case 'k' -> obfuscated = true;
                case 'l' -> bold = true;
                case 'm' -> strikethrough = true;
                case 'n' -> underline = true;
                case 'o' -> italic = true;
                default -> buffer.append(SECTION).append(code); // Unknown code, keep as text
            }
        }

        // Flush remaining buffer
        if (buffer.length() > 0) {
            segments.add(new ColoredTextSegment(buffer.toString(), currentColor, bold, italic, underline, strikethrough, obfuscated));
        }

        return segments;
    }

    /**
     * Parse text with MiniMessage gradient support
     */
    private static List<ColoredTextSegment> parseWithGradients(String input, int defaultColor) {
        List<ColoredTextSegment> segments = new ArrayList<>();
        int i = 0;

        while (i < input.length()) {
            // Look for gradient tag
            int gradientStart = input.toLowerCase(Locale.ROOT).indexOf("<gradient:", i);

            if (gradientStart == -1) {
                // No more gradients, parse the rest as legacy
                String remaining = input.substring(i);
                segments.addAll(parseLegacy(remaining, defaultColor));
                break;
            }

            // Add any text before the gradient
            if (gradientStart > i) {
                String before = input.substring(i, gradientStart);
                segments.addAll(parseLegacy(before, defaultColor));
            }

            // Find the gradient closing tag
            int gradientEnd = input.toLowerCase(Locale.ROOT).indexOf(GRADIENT_CLOSE, gradientStart);
            if (gradientEnd == -1) {
                // No closing tag, treat as regular text
                String remaining = input.substring(gradientStart);
                segments.addAll(parseLegacy(remaining, defaultColor));
                break;
            }

            // Extract gradient tag
            int contentStart = input.indexOf('>', gradientStart);
            if (contentStart == -1 || contentStart >= gradientEnd) {
                i = gradientStart + 1;
                continue;
            }

            String gradientTag = input.substring(gradientStart + 10, contentStart); // Skip "<gradient:"
            String gradientContent = input.substring(contentStart + 1, gradientEnd);

            // Parse gradient colors
            int[] colors = parseGradientColors(gradientTag);
            if (colors != null && colors.length >= 2) {
                segments.addAll(applyGradient(gradientContent, colors));
            } else {
                // Invalid gradient, treat as regular text
                segments.addAll(parseLegacy(gradientContent, defaultColor));
            }

            i = gradientEnd + GRADIENT_CLOSE.length();
        }

        return segments;
    }

    /**
     * Parse hex color code in format §x§R§R§G§G§B§B
     */
    private static Integer parseHexColorCode(String input, int startIndex) {
        int[] nibbles = new int[6];

        for (int n = 0; n < 6; n++) {
            int sectionIndex = startIndex + 1 + 2 * n;
            if (sectionIndex >= input.length() || sectionIndex + 1 >= input.length()
                || input.charAt(sectionIndex) != SECTION) {
                return null;
            }

            char hexChar = input.charAt(sectionIndex + 1);
            int value = Character.digit(hexChar, 16);
            if (value < 0) {
                return null;
            }
            nibbles[n] = value;
        }

        int r = (nibbles[0] << 4) | nibbles[1];
        int g = (nibbles[2] << 4) | nibbles[3];
        int b = (nibbles[4] << 4) | nibbles[5];
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Parse gradient colors from tag format: #FF0000:#00FF00:#0000FF
     */
    private static int[] parseGradientColors(String colorString) {
        String[] tokens = colorString.split(":");
        List<Integer> colors = new ArrayList<>();

        for (String token : tokens) {
            Integer color = parseHexColor(token.trim());
            if (color != null) {
                colors.add(color);
            }
        }

        if (colors.size() < 2) {
            return null;
        }

        return colors.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Parse hex color from #RRGGBB format
     */
    private static Integer parseHexColor(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        String hex = value.trim();
        if (hex.charAt(0) == '#') {
            hex = hex.substring(1);
        }

        if (hex.length() != 6) {
            return null;
        }

        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Apply gradient to text content
     */
    private static List<ColoredTextSegment> applyGradient(String content, int[] colors) {
        List<ColoredTextSegment> segments = new ArrayList<>();

        if (content.isEmpty() || colors.length < 2) {
            return segments;
        }

        // Get code points (handles emojis and special characters)
        int[] codePoints = content.codePoints().toArray();
        if (codePoints.length == 0) {
            return segments;
        }

        int colorSegments = colors.length - 1;

        for (int i = 0; i < codePoints.length; i++) {
            int color = colors[0];

            if (colorSegments > 0 && codePoints.length > 1) {
                // Calculate position in gradient (0.0 to 1.0)
                double progress = (double) i / (codePoints.length - 1);

                // Scale to number of color segments
                double scaled = clamp01(progress) * colorSegments;
                int colorIndex = (int) Math.floor(scaled);

                if (colorIndex >= colorSegments) {
                    colorIndex = colorSegments - 1;
                    scaled = colorSegments;
                }

                // Interpolate between two colors
                double localProgress = scaled - colorIndex;
                color = lerpColor(colors[colorIndex], colors[colorIndex + 1], localProgress);
            }

            String charStr = new String(Character.toChars(codePoints[i]));
            segments.add(new ColoredTextSegment(charStr, color));
        }

        return segments;
    }

    /**
     * Linear interpolation between two RGB colors
     */
    private static int lerpColor(int start, int end, double t) {
        double clamped = clamp01(t);

        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;

        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;

        int r = (int) Math.round(sr + (er - sr) * clamped);
        int g = (int) Math.round(sg + (eg - sg) * clamped);
        int b = (int) Math.round(sb + (eb - sb) * clamped);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Clamp value between 0 and 1
     */
    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
