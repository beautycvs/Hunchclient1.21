package dev.hunchclient.util;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.misc.F7SimModule;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Utility class for checking if an item is a Terminator bow
 * Includes F7Sim support - treats all bows as Terminators when F7Sim is enabled
 */
public class TerminatorUtil {

    /**
     * Check if an item is a Terminator bow
     * @param stack The item stack to check
     * @return true if it's a Terminator, or if F7Sim is enabled and it's any bow
     */
    public static boolean isTerminator(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // F7Sim mode: Treat all bows as Terminators for testing
        F7SimModule f7sim = ModuleManager.getInstance().getModule(F7SimModule.class);
        if (f7sim != null && f7sim.isEnabled() && stack.getItem() == Items.BOW) {
            return true;
        }

        // Regular check: Look for TERMINATOR in NBT data
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag nbt = customData.copyTag();
            if (nbt.contains("ExtraAttributes")) {
                var extraAttrOpt = nbt.getCompound("ExtraAttributes");
                if (extraAttrOpt.isPresent()) {
                    CompoundTag extraAttr = extraAttrOpt.get();
                    if (extraAttr.contains("id")) {
                        var idOpt = extraAttr.getString("id");
                        if (idOpt.isPresent()) {
                            String id = idOpt.get();
                            return "TERMINATOR".equals(id);
                        }
                    }
                }
            }
        }

        // Check by item name as fallback
        String itemName = stack.getHoverName().getString().toLowerCase();
        return itemName.contains("terminator");
    }
}
