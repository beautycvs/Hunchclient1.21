package dev.hunchclient.module.impl.terminalsolver.gui;

import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.Utils;
import java.util.List;

/** GUI for the Starts With terminal. */
public class StartsWithGui extends TermGui {
    private static final boolean DEBUG = false;
    public static final StartsWithGui INSTANCE = new StartsWithGui();

    @Override
    public void renderTerminal(int slotCount) {
        renderBackground(slotCount, getDefaultSlotWidth());

        List<Integer> solution = getCurrentSolution();
        if (DEBUG) {
            System.out.println("[StartsWithGui] Rendering with solution: " + solution + " (size=" + solution.size() + ")");
        }

        int renderedCount = 0;
        for (int index = 9; index <= slotCount; index++) {
            if (Utils.equalsOneOf(index % 9, new int[]{0, 8})) continue;

            boolean inSolution = solution.contains(index);
            Color startColor = inSolution ? PanesGui.panesColor : Colors.TRANSPARENT;
            Color endColor = PanesGui.panesColor;

            if (colorAnimations.containsKey(index) || inSolution) {
                renderSlot(index, startColor, endColor);
                if (inSolution) renderedCount++;
            }
        }

        if (DEBUG) {
            System.out.println("[StartsWithGui] Rendered " + renderedCount + " solution slots");
        }
    }

    @Override
    protected Color getAccentColor() {
        return PanesGui.panesColor;
    }
}
