package dev.hunchclient.bridge.module;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

public interface IChatUtils {
    boolean isEnabled();
    boolean isCopyChat();
    boolean isCompactChat();
    boolean shouldRemovePreviousDuplicate(String message);
    Component processCompactChat(Component message);
    int getChatMessageLimit();
    boolean shouldConvertToMiddleClick(Screen screen, Slot slot, int button);
}
