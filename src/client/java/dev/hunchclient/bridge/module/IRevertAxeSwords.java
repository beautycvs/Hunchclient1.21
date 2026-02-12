package dev.hunchclient.bridge.module;

import net.minecraft.world.item.ItemStack;

public interface IRevertAxeSwords {
    boolean isEnabled();
    ItemStack getVisualReplacement(ItemStack stack);
}
