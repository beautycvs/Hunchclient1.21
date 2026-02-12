package dev.hunchclient.module.impl.terminalsolver.gui;

import dev.hunchclient.render.NVGRenderer;
import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.Utils;
import net.minecraft.world.item.ItemStack;

/** GUI for the Numbers terminal. */
public class NumbersGui extends TermGui {
    public static final NumbersGui INSTANCE = new NumbersGui();

    public static Color orderColor = Colors.MINECRAFT_GREEN;           // Slot 1: Green
    public static Color orderColor2 = new Color(255, 215, 0);         // Slot 2: Yellow/Gold
    public static Color orderColor3 = new Color(255, 80, 80);         // Slot 3+: Red
    public static boolean showNumbers = true;

    @Override
    public void renderTerminal(int slotCount) {
        renderBackground(slotCount, getDefaultSlotWidth());

        for (int index = 9; index <= slotCount; index++) {
            if (Utils.equalsOneOf(index % 9, new int[]{0, 8})) continue;

            if (currentTerm == null || currentTerm.items == null) continue;
            ItemStack item = currentTerm.items[index];
            if (item == null || item.getCount() <= 0) continue;

            int amount = item.getCount();
            int solutionIndex = getCurrentSolution().indexOf(index);

            Color color;
            if (solutionIndex == 0) {
                color = orderColor;
            } else if (solutionIndex == 1) {
                color = orderColor2;
            } else if (solutionIndex == 2) {
                color = orderColor3;
            } else {
                color = Colors.TRANSPARENT;
            }

            float[] pos = renderSlot(index, color, orderColor);
            float slotX = pos[0];
            float slotY = pos[1];

            float slotSize = 55f * customTermSize;
            float fontSize = 30f * customTermSize;

            if (showNumbers && solutionIndex != -1) {
                String text = String.valueOf(amount);
                float textWidth = NVGRenderer.textWidth(text, fontSize, getTerminalFont());
                float textX = slotX + (slotSize - textWidth) / 2f;
                float textY = slotY + (slotSize + fontSize) / 2f - fontSize * 0.9f;

                NVGRenderer.textShadow(text, textX, textY, 30f * customTermSize, Colors.WHITE.getRgba(), getTerminalFont());
            }
        }
    }

    @Override
    protected Color getAccentColor() {
        return orderColor;
    }
}
