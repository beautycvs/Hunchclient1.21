package dev.hunchclient.module;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Public API for NameProtect functionality - called by mixins
 * This interface stays readable, implementation gets obfuscated
 */
public interface INameProtect {
    String sanitizeString(String text);
    Component sanitizeText(Component text);
    FormattedText sanitizeStringVisitable(FormattedText visitable);
    FormattedCharSequence sanitizeOrderedText(FormattedCharSequence orderedText);
}
