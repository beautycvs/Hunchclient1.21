package dev.hunchclient.module.impl.terminalsolver.gui;

import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.Utils;

/** GUI for the Panes terminal. */
public class PanesGui extends TermGui {
    public static final PanesGui INSTANCE = new PanesGui();

    public static Color panesColor = Colors.MINECRAFT_GREEN;

    @Override
    public void renderTerminal(int slotCount) {
        renderBackground(slotCount, getDefaultSlotWidth());

        for (int index = 9; index < slotCount; index++) {
            if (Utils.equalsOneOf(index % 9, new int[]{0, 1, 7, 8})) continue;

            boolean inSolution = getCurrentSolution().contains(index);
            Color startColor = inSolution ? panesColor : Colors.TRANSPARENT;
            Color endColor = inSolution ? Colors.gray38 : panesColor;

            renderSlot(index, startColor, endColor);
        }
    }

    @Override
    protected int getDefaultSlotWidth() {
        return 5;
    }

    @Override
    protected Color getAccentColor() {
        return panesColor;
    }
}
