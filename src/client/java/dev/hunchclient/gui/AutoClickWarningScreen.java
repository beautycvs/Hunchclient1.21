package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.util.GuiConfig;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Warning confirmation screen for enabling Auto-Click
 * Shows a serious warning about the risks of using auto-click
 */
public class AutoClickWarningScreen extends Screen {

    private static final String ACKNOWLEDGMENT_KEY = "autoclick_warning_acknowledged";

    private final Screen parent;
    private final Consumer<Boolean> onConfirm;

    private Checkbox dontShowAgainCheckbox;

    // Layout constants
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 240;

    public AutoClickWarningScreen(Screen parent, Consumer<Boolean> onConfirm) {
        super(Component.literal("Auto-Click Warning"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    /**
     * Check if user has already acknowledged the warning
     */
    public static boolean isAcknowledged() {
        return GuiConfig.getInstance().isModuleAcknowledged(ACKNOWLEDGMENT_KEY);
    }

    @Override
    protected void init() {
        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // Enable button (red, dangerous)
        this.addRenderableWidget(Button.builder(Component.literal("I Understand, Enable"), button -> {
            // Save acknowledgment if checkbox is checked
            if (dontShowAgainCheckbox != null && dontShowAgainCheckbox.selected()) {
                GuiConfig.getInstance().acknowledgeModule(ACKNOWLEDGMENT_KEY);
            }
            onConfirm.accept(true);
            this.onClose();
        }).bounds(dialogX + 20, dialogY + DIALOG_HEIGHT - 45, 170, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            onConfirm.accept(false);
            this.onClose();
        }).bounds(dialogX + DIALOG_WIDTH - 190, dialogY + DIALOG_HEIGHT - 45, 170, 20).build());

        // "Don't show again" checkbox
        dontShowAgainCheckbox = Checkbox.builder(Component.literal("Don't warn me again"), this.font)
            .pos(dialogX + 20, dialogY + DIALOG_HEIGHT - 75)
            .build();
        this.addRenderableWidget(dontShowAgainCheckbox);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Dim background
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // Dialog background
        context.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, SkeetTheme.BG_PRIMARY());

        // Border (red for danger)
        int dangerColor = 0xFFFF4444;
        drawBorder(context, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, dangerColor);

        // Title bar background (red gradient)
        context.fill(dialogX + 1, dialogY + 1, dialogX + DIALOG_WIDTH - 1, dialogY + 30, 0xFF882222);

        // Warning icon and title
        String title = "\u26a0 WARNING - Auto-Click Enabled";
        context.drawString(this.font, title, dialogX + 12, dialogY + 10, 0xFFFF6666, true);

        // Warning text lines
        String[] warningLines = {
            "",
            "You are about to enable AUTO-CLICK functionality.",
            "",
            "This feature will automatically click terminals for you",
            "using mouse emulation (ydotool/Robot).",
            "",
            "RISKS:",
            "  - May be detected by anti-cheat systems",
            "  - Could result in account penalties or bans",
            "  - Uses system-level mouse control",
            "",
            "Only use this in private/testing environments!"
        };

        int lineY = dialogY + 40;
        for (String line : warningLines) {
            int color = line.startsWith("RISKS") ? 0xFFFF6666 : SkeetTheme.TEXT_PRIMARY();
            if (line.startsWith("  -")) {
                color = 0xFFFFAAAA; // Lighter red for bullet points
            }
            if (!line.isEmpty()) {
                context.drawString(this.font, line, dialogX + 15, lineY, color, false);
            }
            lineY += 11;
        }

        // Render widgets (buttons, checkbox)
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        // Top
        context.fill(x, y, x + width, y + 2, color);
        // Bottom
        context.fill(x, y + height - 2, x + width, y + height, color);
        // Left
        context.fill(x, y, x + 2, y + height, color);
        // Right
        context.fill(x + width - 2, y, x + width, y + height, color);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        // Escape key cancels
        if (keyCode == 256) { // ESC
            onConfirm.accept(false);
            this.onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
