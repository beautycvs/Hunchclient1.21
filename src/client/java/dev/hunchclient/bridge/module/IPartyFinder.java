package dev.hunchclient.bridge.module;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public interface IPartyFinder {
    boolean isEnabled();
    void modifyTooltip(ItemStack stack, List<Component> tooltip);
}
