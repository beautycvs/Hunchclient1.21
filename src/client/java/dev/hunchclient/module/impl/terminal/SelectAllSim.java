package dev.hunchclient.module.impl.terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Select All terminal simulator.
 * Chooses a target color, fills the grid with matching and decoy items, and tracks clicks even on naturally-glinting items.
 */
public class SelectAllSim extends TermSimGUI {

    private final DyeColor color;
    private final List<Item> correctItems = new ArrayList<>();
    // Track which slots we've clicked (fixes Nether Star issue - it has natural glint!)
    private final Set<Integer> clickedSlots = new HashSet<>();

    // Holder to pass color from title generation to field initialization
    private static DyeColor lastGeneratedColor = null;

    public SelectAllSim(Inventory playerInventory) {
        super(TerminalType.SELECT, playerInventory, generateTitleAndStoreColor());
        this.color = lastGeneratedColor;

        // Build the list of valid items for this color (handles dye edge cases)
        correctItems.addAll(getPossibleItems(color));
    }

    private static List<Item> getPossibleItems(DyeColor color) {
        List<Item> items = new ArrayList<>();

        // Add standard color variants
        String colorName = color.name().toLowerCase();
        items.add(BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", colorName + "_stained_glass")));
        items.add(BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", colorName + "_wool")));
        items.add(BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", colorName + "_concrete")));

        // Handle dye items that do not follow the standard naming
        Item dyeItem = switch (color) {
            case WHITE -> net.minecraft.world.item.Items.BONE_MEAL;
            case BLUE -> net.minecraft.world.item.Items.LAPIS_LAZULI;
            case BLACK -> net.minecraft.world.item.Items.INK_SAC;
            case BROWN -> net.minecraft.world.item.Items.COCOA_BEANS;
            default -> BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", colorName + "_dye"));
        };
        items.add(dyeItem);

        return items;
    }

    private static String generateTitleAndStoreColor() {
        DyeColor randomColor = DyeColor.values()[new java.util.Random().nextInt(DyeColor.values().length)];
        lastGeneratedColor = randomColor;
        return "Select all the " + randomColor.name().replace("_", " ").toLowerCase() + " items!";
    }

    @Override
    protected void initializeTerminal() {
        clickedSlots.clear(); // Clear click tracking for new terminal

        // Eligible slots inside the 5x7 play area
        List<Integer> guaranteedRange = new ArrayList<>();
        for (int i = 10; i <= 16; i++) guaranteedRange.add(i);
        for (int i = 19; i <= 25; i++) guaranteedRange.add(i);
        for (int i = 28; i <= 34; i++) guaranteedRange.add(i);
        for (int i = 37; i <= 43; i++) guaranteedRange.add(i);
        int guaranteedSlot = guaranteedRange.get(random.nextInt(guaranteedRange.size()));

        for (int index = 0; index < inventory.getContainerSize(); index++) {
            int row = index / 9;
            int col = index % 9;

            ItemStack stack;

            if (row >= 1 && row <= 4 && col >= 1 && col <= 7) {
                Item templateItem = correctItems.get(random.nextInt(correctItems.size()));

                // Guaranteed correct item
                if (index == guaranteedSlot) {
                    stack = new ItemStack(templateItem);
                }
                // 25% chance for another correct item
                else if (Math.random() > 0.75) {
                    stack = new ItemStack(templateItem);
                }
                // Otherwise use a wrong color variant
                else {
                    DyeColor[] allColors = DyeColor.values();
                    List<DyeColor> wrongColors = new ArrayList<>();
                    for (DyeColor c : allColors) {
                        if (c != color) wrongColors.add(c);
                    }
                    DyeColor wrongColor = wrongColors.get(random.nextInt(wrongColors.size()));

                    String wrongColorName = wrongColor.name().toLowerCase();
                    String correctColorName = color.name().toLowerCase();

                    if (templateItem instanceof DyeItem) {
                        stack = new ItemStack(BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", wrongColorName + "_dye")));
                    } else if (templateItem instanceof BlockItem) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(templateItem);
                        String path = id.getPath().replace(correctColorName, wrongColorName);
                        stack = new ItemStack(BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path)));
                    } else {
                        stack = new ItemStack(templateItem);
                    }
                }
            } else {
                stack = pane(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE);
            }

            setStack(index, stack);
        }
    }

    @Override
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        ItemStack stack = getStack(slot);

        if (stack.isEmpty()) {
            return null; // Ignore empty slots
        }

        // Check if it's a black pane (background)
        if (stack.is(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE)) {
            return null; // Ignore background clicks
        }

        // Check if already clicked (using our explicit tracking, not glint!)
        // This fixes the Nether Star issue - it has natural glint so we can't rely on ENCHANTMENT_GLINT_OVERRIDE
        if (clickedSlots.contains(slot)) {
            return null; // Already clicked, ignore
        }

        if (!correctItems.contains(stack.getItem())) {
            // WRONG CLICK - Play error sound and show red flash
            playClickSound(false);
            triggerVisualFeedback(false); // Red flash
            return false; // WRONG COLOR - FAIL!
        }

        // Correct item! Add glint for visual feedback
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        setStack(slot, stack);

        // Track this slot as clicked
        clickedSlots.add(slot);

        // CORRECT CLICK - Play success sound and show green flash
        playClickSound(true);
        triggerVisualFeedback(true); // Green flash

        return true; // Correct click
    }

    @Override
    protected boolean checkCompletion() {
        // Use explicit click tracking instead of glint (fixes natural-glint items)
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty() && correctItems.contains(stack.getItem())) {
                // Check if this slot has been clicked
                if (!clickedSlots.contains(i)) {
                    return false; // Still unclicked correct items
                }
            }
        }
        return true;
    }

    private ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }
}
