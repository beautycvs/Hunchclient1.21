package dev.hunchclient.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Parses legacy §-formatted strings and a small MiniMessage subset (bold, italic, underline,
 * strikethrough, obfuscated, gradient) into Minecraft {@link MutableComponent}.
 */
public final class SectionCodeParser {

    private static final char SECTION = '\u00A7';
    private static final String GRADIENT_CLOSE = "</gradient>";

    private SectionCodeParser() {}

    public static MutableComponent parse(String input) {
        return parse(input, Style.EMPTY);
    }

    public static MutableComponent parse(String input, Style baseStyle) {
        Style defaultStyle = baseStyle == null ? Style.EMPTY : baseStyle;
        if (input == null || input.isEmpty()) {
            MutableComponent empty = Component.empty();
            empty.setStyle(defaultStyle);
            return empty;
        }

        if (hasMiniMessageIndicators(input)) {
            MutableComponent mini = parseMiniMessage(input, defaultStyle);
            if (mini != null) {
                return mini;
            }
        }

        return parseLegacy(input, defaultStyle);
    }

    private static boolean hasMiniMessageIndicators(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.contains("<gradient:") || lower.contains(GRADIENT_CLOSE)
            || lower.contains("<b>") || lower.contains("</b>")
            || lower.contains("<bold>") || lower.contains("</bold>")
            || lower.contains("<i>") || lower.contains("</i>")
            || lower.contains("<italic>") || lower.contains("</italic>")
            || lower.contains("<u>") || lower.contains("</u>")
            || lower.contains("<underline>") || lower.contains("</underline>")
            || lower.contains("<st>") || lower.contains("</st>")
            || lower.contains("<strikethrough>") || lower.contains("</strikethrough>")
            || lower.contains("<obf>") || lower.contains("</obf>")
            || lower.contains("<obfuscated>") || lower.contains("</obfuscated>")
            || lower.contains("<reset>");
    }

    private static MutableComponent parseLegacy(String input, Style defaultStyle) {
        MutableComponent out = Component.empty();
        out.setStyle(defaultStyle);
        Style style = defaultStyle;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != SECTION || i + 1 >= input.length()) {
                buffer.append(c);
                continue;
            }

            flushLegacyBuffer(out, buffer, style);
            char code = Character.toLowerCase(input.charAt(++i));

            if (code == 'x' && i + 12 < input.length()) {
                int[] nibbles = new int[6];
                boolean ok = true;
                for (int n = 0; n < 6; n++) {
                    int sectionIndex = i + 1 + 2 * n;
                    if (sectionIndex >= input.length() || sectionIndex + 1 >= input.length()
                        || input.charAt(sectionIndex) != SECTION) {
                        ok = false;
                        break;
                    }
                    char hexChar = input.charAt(sectionIndex + 1);
                    int value = Character.digit(hexChar, 16);
                    if (value < 0) {
                        ok = false;
                        break;
                    }
                    nibbles[n] = value;
                }

                if (ok) {
                    int r = (nibbles[0] << 4) | nibbles[1];
                    int g = (nibbles[2] << 4) | nibbles[3];
                    int b = (nibbles[4] << 4) | nibbles[5];
                    style = defaultStyle.withColor(TextColor.fromRgb((r << 16) | (g << 8) | b));
                    i += 12;
                    i--;
                    continue;
                } else {
                    buffer.append(SECTION).append(code);
                    continue;
                }
            }

            ChatFormatting formatting = ChatFormatting.getByCode(code);
            if (formatting == null) {
                buffer.append(SECTION).append(code);
                continue;
            }

            if (formatting == ChatFormatting.RESET) {
                style = defaultStyle;
                continue;
            }

            if (formatting.isColor()) {
                TextColor color = TextColor.fromLegacyFormat(formatting);
                style = color != null ? defaultStyle.withColor(color) : defaultStyle;
                continue;
            }

            switch (formatting) {
                case BOLD -> style = style.withBold(true);
                case ITALIC -> style = style.withItalic(true);
                case UNDERLINE -> style = style.withUnderlined(true);
                case STRIKETHROUGH -> style = style.withStrikethrough(true);
                case OBFUSCATED -> style = style.withObfuscated(true);
                default -> buffer.append(SECTION).append(code);
            }
        }

        flushLegacyBuffer(out, buffer, style);
        return out;
    }

    private static void flushLegacyBuffer(MutableComponent out, StringBuilder buffer, Style style) {
        if (buffer.length() == 0) {
            return;
        }
        MutableComponent literal = Component.literal(buffer.toString());
        literal.setStyle(style);
        out.append(literal);
        buffer.setLength(0);
    }

    private static MutableComponent parseMiniMessage(String input, Style defaultStyle) {
        MutableComponent out = Component.empty();
        out.setStyle(defaultStyle);
        StringBuilder buffer = new StringBuilder();
        Deque<StyleState> styleStack = new ArrayDeque<>();
        Style currentStyle = defaultStyle;
        boolean handledTag = false;

        for (int i = 0; i < input.length();) {
            char c = input.charAt(i);
            if (c == '<') {
                int close = input.indexOf('>', i);
                if (close == -1) {
                    buffer.append(c);
                    i++;
                    continue;
                }

                String rawTag = input.substring(i + 1, close).trim();
                if (rawTag.isEmpty()) {
                    buffer.append(input, i, close + 1);
                    i = close + 1;
                    continue;
                }

                String lowerTag = rawTag.toLowerCase(Locale.ROOT);
                boolean isClosing = lowerTag.startsWith("/");
                String canonical = canonicalStyleName(isClosing ? lowerTag.substring(1) : lowerTag);
                if (canonical != null) {
                    handledTag = true;
                    appendLegacySegment(out, buffer, currentStyle);
                    if (!isClosing) {
                        styleStack.push(new StyleState(canonical, currentStyle));
                        currentStyle = applyStyleForTag(canonical, currentStyle);
                    } else {
                        currentStyle = restoreAfterClose(styleStack, defaultStyle, canonical);
                    }
                    i = close + 1;
                    continue;
                }

                if ("reset".equals(lowerTag)) {
                    handledTag = true;
                    appendLegacySegment(out, buffer, currentStyle);
                    styleStack.clear();
                    currentStyle = defaultStyle;
                    i = close + 1;
                    continue;
                }

                if (lowerTag.startsWith("gradient:")) {
                    int colon = rawTag.indexOf(':');
                    int end = indexOfIgnoreCase(input, GRADIENT_CLOSE, close + 1);
                    if (colon < 0 || end == -1) {
                        buffer.append(input, i, close + 1);
                        i = close + 1;
                        continue;
                    }

                    int[] colors = parseGradientColors(rawTag.substring(colon + 1));
                    if (colors == null) {
                        buffer.append(input, i, end + GRADIENT_CLOSE.length());
                        i = end + GRADIENT_CLOSE.length();
                        continue;
                    }

                    handledTag = true;
                    appendLegacySegment(out, buffer, currentStyle);
                    String inner = input.substring(close + 1, end);
                    out.append(applyGradient(inner, currentStyle, colors));
                    i = end + GRADIENT_CLOSE.length();
                    continue;
                }

                buffer.append(input, i, close + 1);
                i = close + 1;
                continue;
            }

            buffer.append(c);
            i++;
        }

        appendLegacySegment(out, buffer, currentStyle);
        if (!handledTag) {
            return null;
        }
        return out;
    }

    private static void appendLegacySegment(MutableComponent out, StringBuilder buffer, Style style) {
        if (buffer.length() == 0) {
            return;
        }
        MutableComponent segment = parseLegacy(buffer.toString(), style);
        out.append(segment);
        buffer.setLength(0);
    }

    private static String canonicalStyleName(String tag) {
        return switch (tag) {
            case "b", "bold" -> "bold";
            case "i", "italic" -> "italic";
            case "u", "underline" -> "underline";
            case "st", "strikethrough" -> "strikethrough";
            case "obf", "obfuscated" -> "obfuscated";
            default -> null;
        };
    }

    private static Style applyStyleForTag(String canonical, Style current) {
        return switch (canonical) {
            case "bold" -> current.withBold(true);
            case "italic" -> current.withItalic(true);
            case "underline" -> current.withUnderlined(true);
            case "strikethrough" -> current.withStrikethrough(true);
            case "obfuscated" -> current.withObfuscated(true);
            default -> current;
        };
    }

    private static Style restoreAfterClose(Deque<StyleState> stack, Style defaultStyle, String canonical) {
        if (!stack.isEmpty() && stack.peek().tag().equals(canonical)) {
            return stack.pop().styleBefore();
        }
        return defaultStyle;
    }

    private static int[] parseGradientColors(String raw) {
        String[] tokens = raw.split(":");
        int[] tmp = new int[tokens.length];
        int count = 0;
        for (String token : tokens) {
            Integer parsed = parseHexColor(token);
            if (parsed != null) {
                tmp[count++] = parsed;
            }
        }
        if (count < 2) {
            return null;
        }
        int[] colors = new int[count];
        System.arraycopy(tmp, 0, colors, 0, count);
        return colors;
    }

    private static Integer parseHexColor(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.charAt(0) == '#') {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static MutableComponent applyGradient(String content, Style baseStyle, int[] colors) {
        MutableComponent result = Component.empty();
        if (content.isEmpty()) {
            return result;
        }

        int[] codePoints = content.codePoints().toArray();
        if (codePoints.length == 0) {
            return result;
        }

        int segments = colors.length - 1;
        for (int i = 0; i < codePoints.length; i++) {
            int color = colors[0];
            if (segments > 0) {
                double progress = codePoints.length == 1 ? 0.0 : (double) i / (codePoints.length - 1);
                double scaled = clamp01(progress) * segments;
                int index = (int) Math.floor(scaled);
                if (index >= segments) {
                    index = segments - 1;
                    scaled = segments;
                }
                double localT = scaled - index;
                color = lerpColor(colors[index], colors[index + 1], localT);
            }

            Style charStyle = baseStyle.withColor(TextColor.fromRgb(color));
            MutableComponent part = Component.literal(new String(Character.toChars(codePoints[i])));
            part.setStyle(charStyle);
            result.append(part);
        }

        return result;
    }

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

    private static double clamp01(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }

    private static int indexOfIgnoreCase(String input, String search, int fromIndex) {
        int max = input.length() - search.length();
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            if (input.regionMatches(true, i, search, 0, search.length())) {
                return i;
            }
        }
        return -1;
    }

    private record StyleState(String tag, Style styleBefore) {}
}
