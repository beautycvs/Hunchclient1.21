package dev.hunchclient.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;

/** GUI-related events. */
public abstract class GuiEvent extends CancellableEvent {
    public final Screen screen;

    public GuiEvent(Screen screen) {
        this.screen = screen;
    }

    public static class Open extends GuiEvent {
        private Open(Screen screen) {
            super(screen);
        }

        public static Open of(Screen screen) {
            return new Open(screen);
        }
    }

    public static class Close extends GuiEvent {
        private Close(Screen screen) {
            super(screen);
        }
        public static Close of(Screen screen) {
            return new Close(screen);
        }
    }

    /**
     * Fired when a screen is removed from display (server-side close)
     * This is different from Close - removed() is called when server forcefully closes the inventory
     */
    public static class Removed extends GuiEvent {
        private Removed(Screen screen) {
            super(screen);
        }
        public static Removed of(Screen screen) {
            return new Removed(screen);
        }
    }

    public static class SlotClick extends GuiEvent {
        public final int slotId;
        public final int button;

        public SlotClick(Screen screen, int slotId, int button) {
            super(screen);
            this.slotId = slotId;
            this.button = button;
        }
    }

    public static class MouseClick extends GuiEvent {
        public final int mouseX;
        public final int mouseY;
        public final int button;

        private MouseClick(Screen screen, int mouseX, int mouseY, int button) {
            super(screen);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.button = button;
        }

        public static MouseClick of(Screen screen, int mouseX, int mouseY, int button) {
            return new MouseClick(screen, mouseX, mouseY, button);
        }
    }

    public static class KeyPress extends GuiEvent {
        public final int keyCode;
        public final int scanCode;
        public final int modifiers;

        public KeyPress(Screen screen, int keyCode, int scanCode, int modifiers) {
            super(screen);
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }
    }

    public static class Draw extends GuiEvent {
        public final GuiGraphics drawContext;
        public final int mouseX;
        public final int mouseY;

        private Draw(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY) {
            super(screen);
            this.drawContext = drawContext;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public static Draw of(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY) {
            return new Draw(screen, drawContext, mouseX, mouseY);
        }
    }

    public static class DrawBackground extends GuiEvent {
        public final GuiGraphics drawContext;

        private DrawBackground(Screen screen, GuiGraphics drawContext) {
            super(screen);
            this.drawContext = drawContext;
        }

        public static DrawBackground of(Screen screen, GuiGraphics drawContext) {
            return new DrawBackground(screen, drawContext);
        }
    }

    public static class DrawForeground extends GuiEvent {
        public final GuiGraphics drawContext;
        public final int mouseX;
        public final int mouseY;

        private DrawForeground(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY) {
            super(screen);
            this.drawContext = drawContext;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public static DrawForeground of(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY) {
            return new DrawForeground(screen, drawContext, mouseX, mouseY);
        }
    }

    public static class DrawSlot extends GuiEvent {
        public final GuiGraphics drawContext;
        public final Slot slot;

        private DrawSlot(Screen screen, GuiGraphics drawContext, Slot slot) {
            super(screen);
            this.drawContext = drawContext;
            this.slot = slot;
        }

        public static DrawSlot of(Screen screen, GuiGraphics drawContext, Slot slot) {
            return new DrawSlot(screen, drawContext, slot);
        }
    }

    public static class NVGRender extends GuiEvent {
        public NVGRender(Screen screen) {
            super(screen);
        }
    }

    public static class CustomTermGuiClick extends GuiEvent {
        public final int slot;
        public final int button;

        public CustomTermGuiClick(Screen screen, int slot, int button) {
            super(screen);
            this.slot = slot;
            this.button = button;
        }
    }

    public static class DrawTooltip extends GuiEvent {
        public final GuiGraphics drawContext;
        public final int mouseX;
        public final int mouseY;

        private DrawTooltip(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY) {
            super(screen);
            this.drawContext = drawContext;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }
        public static DrawTooltip of(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY) {
            return new DrawTooltip(screen, drawContext, mouseX, mouseY);
        }
    }

    public static class MouseScroll extends GuiEvent {
        public final double mouseX;
        public final double mouseY;
        public final double horizontalAmount;
        public final double verticalAmount;

        private MouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            super(screen);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.horizontalAmount = horizontalAmount;
            this.verticalAmount = verticalAmount;
        }

        public static MouseScroll of(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            return new MouseScroll(screen, mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }
}
