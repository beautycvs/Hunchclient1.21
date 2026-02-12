package dev.hunchclient.bridge.module;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public interface INameProtect {
    String sanitizeString(String text);
    Component sanitizeText(Component text);
    FormattedText sanitizeStringVisitable(FormattedText visitable);
    FormattedCharSequence sanitizeOrderedText(FormattedCharSequence orderedText);
}
