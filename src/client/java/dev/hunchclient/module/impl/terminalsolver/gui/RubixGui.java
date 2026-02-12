package dev.hunchclient.module.impl.terminalsolver.gui;

import dev.hunchclient.render.NVGRenderer;
import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;

import java.util.HashSet;
import java.util.Set;

/** GUI for the Rubix terminal. */
public class RubixGui extends TermGui {
    public static final RubixGui INSTANCE = new RubixGui();

    public static Color rubixColor1 = Colors.MINECRAFT_DARK_AQUA;
    public static Color rubixColor2 = new Color(0, 100, 100);
    public static Color oppositeRubixColor1 = new Color(170, 85, 0);
    public static Color oppositeRubixColor2 = new Color(210, 85, 0);

    @Override
    public void renderTerminal(int slotCount) {
        renderBackground(slotCount, getDefaultSlotWidth());

        // Get distinct indices
        Set<Integer> distinctIndices = new HashSet<>(getCurrentSolution());

        for (int index : distinctIndices) {
            int amount = 0;
            for (int slot : getCurrentSolution()) {
                if (slot == index) amount++;
            }

            int clicksRequired = (amount < 3) ? amount : (amount - 5);
            if (clicksRequired == 0) continue;

            float[] pos = renderSlot(index, getColor(clicksRequired), getColor((amount < 3) ? (clicksRequired + 1) : (clicksRequired - 1)));
            float slotX = pos[0];
            float slotY = pos[1];

            float slotSize = 55f * customTermSize;
            float fontSize = 30f * customTermSize;

            String text = String.valueOf(clicksRequired);
            float textWidth = NVGRenderer.textWidth(text, fontSize, getTerminalFont());
            float textX = slotX + (slotSize - textWidth) / 2f;
            float textY = slotY + (slotSize + fontSize) / 2f - fontSize * 0.9f;

            NVGRenderer.textShadow(text, textX, textY, 30f * customTermSize, Colors.WHITE.getRgba(), getTerminalFont());
        }
    }

    private Color getColor(int clicksRequired) {
        return switch (clicksRequired) {
            case 1 -> rubixColor1;
            case 2 -> rubixColor2;
            case -1 -> oppositeRubixColor1;
            default -> oppositeRubixColor2;
        };
    }

    @Override
    protected int getDefaultSlotWidth() {
        return 3;
    }

    @Override
    protected Color getAccentColor() {
        return rubixColor1;
    }
}
