package dev.hunchclient.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

/**
 * Accessor for ChatComponent to enable CopyChat and CompactChat features.
 * Field/method names updated for Mojang mappings (1.21.10)
 */
@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
    @Accessor("allMessages")
    List<GuiMessage> getMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> getVisibleMessages();

    @Invoker("screenToChatX")
    double invokeToChatLineX(double x);

    @Invoker("screenToChatY")
    double invokeToChatLineY(double y);

    @Invoker("getMessageLineIndexAt")
    int invokeGetMessageLineIndex(double chatLineX, double chatLineY);
}
