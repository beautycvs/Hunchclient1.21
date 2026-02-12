package dev.hunchclient.module.impl;

import dev.hunchclient.module.Module;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Revert Axe Swords - Converts SkyBlock swords back to their original axe forms visually.
 *
 * These items were originally axes in SkyBlock but were changed to swords:
 * - Daedalus Blade -> Daedalus Axe (Golden Axe)
 * - Ragnarock -> Ragnarock Axe (Golden Axe)
 * - Halberd of the Shredded -> Axe of the Shredded (Diamond Axe)
 */
public class RevertAxeSwordsModule extends Module {

    private static RevertAxeSwordsModule instance;

    // Mapping of SkyBlock item IDs to their axe replacements
    private static final Map<String, AxeReplacement> AXE_REPLACEMENTS = new HashMap<>();

    static {
        // Daedalus Blade -> Golden Axe
        AXE_REPLACEMENTS.put("DAEDALUS_AXE", new AxeReplacement(Items.GOLDEN_AXE, "Daedalus Axe"));
        AXE_REPLACEMENTS.put("STARRED_DAEDALUS_AXE", new AxeReplacement(Items.GOLDEN_AXE, "Daedalus Axe"));

        // Ragnarock -> Golden Axe (the item is called "Ragnarock" but was originally "Ragnarock Axe")
        AXE_REPLACEMENTS.put("RAGNAROCK_AXE", new AxeReplacement(Items.GOLDEN_AXE, "Ragnarock Axe"));

        // Halberd of the Shredded -> Diamond Axe (was "Axe of the Shredded")
        AXE_REPLACEMENTS.put("AXE_OF_THE_SHREDDED", new AxeReplacement(Items.DIAMOND_AXE, "Axe of the Shredded"));
    }

    public RevertAxeSwordsModule() {
        super("Revert Axe Swords", "Visually reverts swords back to their original axe forms (Daedalus, Ragnarock, Shredded)", Category.VISUALS, RiskLevel.SAFE);
        instance = this;
    }

    public static RevertAxeSwordsModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        // No special initialization needed
    }

    @Override
    protected void onDisable() {
        // No cleanup needed
    }

    /**
     * Get the visual replacement for an ItemStack if applicable.
     * Returns null if no replacement should be made.
     */
    public static ItemStack getVisualReplacement(ItemStack stack) {
        if (instance == null || !instance.isEnabled()) {
            return null;
        }

        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String skyblockId = getSkyBlockId(stack);
        if (skyblockId == null) {
            return null;
        }

        AxeReplacement replacement = AXE_REPLACEMENTS.get(skyblockId);
        if (replacement == null) {
            return null;
        }

        // Create a visual copy with the axe item type
        return stack.transmuteCopy(replacement.item, stack.getCount());
    }

    /**
     * Check if an ItemStack should be visually replaced.
     */
    public static boolean shouldReplace(ItemStack stack) {
        if (instance == null || !instance.isEnabled()) {
            return false;
        }

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String skyblockId = getSkyBlockId(stack);
        return skyblockId != null && AXE_REPLACEMENTS.containsKey(skyblockId);
    }

    /**
     * Get the replacement Item for a SkyBlock ID.
     */
    public static Item getReplacementItem(String skyblockId) {
        AxeReplacement replacement = AXE_REPLACEMENTS.get(skyblockId);
        return replacement != null ? replacement.item : null;
    }

    /**
     * Extract the SkyBlock item ID from an ItemStack.
     */
    public static String getSkyBlockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return null;
            }

            CompoundTag nbt = customData.copyTag();
            if (nbt == null) {
                return null;
            }

            // Try different NBT paths for SkyBlock item ID
            if (nbt.contains("ExtraAttributes")) {
                CompoundTag attributes = nbt.getCompound("ExtraAttributes").orElse(null);
                if (attributes != null && attributes.contains("id")) {
                    return attributes.getString("id").orElse(null);
                }
            }

            if (nbt.contains("extra_attributes")) {
                CompoundTag attributes = nbt.getCompound("extra_attributes").orElse(null);
                if (attributes != null && attributes.contains("id")) {
                    return attributes.getString("id").orElse(null);
                }
            }

            // Direct ID in root
            if (nbt.contains("id")) {
                return nbt.getString("id").orElse(null);
            }
        } catch (Exception e) {
            // Silently fail - not a SkyBlock item
        }

        return null;
    }

    /**
     * Data class for axe replacements.
     */
    private static class AxeReplacement {
        final Item item;
        final String displayName;

        AxeReplacement(Item item, String displayName) {
            this.item = item;
            this.displayName = displayName;
        }
    }
}
