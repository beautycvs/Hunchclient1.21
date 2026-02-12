package dev.hunchclient.module.impl;

import dev.hunchclient.module.Module;
import java.util.Random;

/**
 * KaomojiReplacer Module - Replaces "o/" with random kaomojis
 *
 * WATCHDOG SAFE: YES
 * - Client-side chat message transformation only
 * - No packets sent beyond normal chat
 * - No automation of gameplay
 */
public class KaomojiReplacerModule extends Module {

    private static final String[] KAOMOJI_LIST = {
        "(^_^)/", "(^o^)/", "(≧∇≦)/", "(´･∀･`)/", "(｡･ω･)ﾉﾞ",
        "(⌒▽⌒)ノ", "(≧▽≦)ノ", "(・∀・)ノ", "(^_−)ノ", "(o^▽^o)ノ",
        "(✿◠‿◠)ノ", "(✧ω✧)/", "(•̀ᴗ•́)و ̑̑✧ノ", "(^人^)", "(^▽^)ノ",
        "(´｡• ω •｡`)ﾉ", "(￣▽￣)ノ", "(｡･∀･)ﾉﾞ", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "(^_^)／",
        "(/・ω・)/", "( ・_・)ノ", "(o´ω`o)ﾉ", "(≧ω≦)ﾉ", "ヾ(＾-＾)ノ",
        "ヾ(・ω・*)", "ヾ(＠⌒ー⌒＠)ノ", "ヾ(☆▽☆)", "ヾ(・∀・)ﾉ", "ヾ(＾∇＾)"
    };

    private final Random random = new Random();

    public KaomojiReplacerModule() {
        super("KaomojiReplacer", "Replaces 'o/' with random kaomojis", Category.MISC, false);
    }

    @Override
    protected void onEnable() {
        // No initialization needed
    }

    @Override
    protected void onDisable() {
        // No cleanup needed
    }

    /**
     * Replace all "o/" in message with random kaomojis
     * Called from ClientCommandMixin
     */
    public String replaceKaomojis(String message) {
        if (!isEnabled() || !message.contains("o/")) {
            return message;
        }

        // Check if message already contains a kaomoji (prevent loops)
        for (String kaomoji : KAOMOJI_LIST) {
            if (message.contains(kaomoji)) {
                return message;
            }
        }

        // Replace each "o/" with a random kaomoji
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int index;

        while ((index = message.indexOf("o/", lastIndex)) != -1) {
            // Append text before "o/"
            result.append(message, lastIndex, index);
            // Append random kaomoji
            result.append(KAOMOJI_LIST[random.nextInt(KAOMOJI_LIST.length)]);
            // Move past "o/"
            lastIndex = index + 2;
        }

        // Append remaining text
        result.append(message.substring(lastIndex));

        return result.toString();
    }

    /**
     * Check if message contains any kaomoji from our list
     */
    public boolean containsKaomoji(String message) {
        for (String kaomoji : KAOMOJI_LIST) {
            if (message.contains(kaomoji)) {
                return true;
            }
        }
        return false;
    }
}
