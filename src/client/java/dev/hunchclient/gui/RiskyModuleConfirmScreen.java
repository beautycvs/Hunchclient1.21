package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.Module;
import dev.hunchclient.util.GuiConfig;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Warning confirmation screen for VERY_RISKY modules
 * Shows a warning and requires user confirmation before enabling
 */
public class RiskyModuleConfirmScreen extends Screen {

    private final Screen parent;
    private final Module module;
    private final Consumer<Boolean> onConfirm;

    private Checkbox dontShowAgainCheckbox;

    // Layout constants
    private static final int DIALOG_WIDTH = 350;
    private static final int DIALOG_HEIGHT = 180;

    public RiskyModuleConfirmScreen(Screen parent, Module module, Consumer<Boolean> onConfirm) {
        super(Component.literal("Warning"));
        this.parent = parent;
        this.module = module;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // Enable button
        this.addRenderableWidget(Button.builder(Component.literal("Enable"), button -> {
            // Save acknowledgment if checkbox is checked
            if (dontShowAgainCheckbox != null && dontShowAgainCheckbox.selected()) {
                GuiConfig.getInstance().acknowledgeModule(module.getName());
            }
            onConfirm.accept(true);
            this.onClose();
        }).bounds(dialogX + 20, dialogY + DIALOG_HEIGHT - 45, 145, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            onConfirm.accept(false);
            this.onClose();
        }).bounds(dialogX + DIALOG_WIDTH - 165, dialogY + DIALOG_HEIGHT - 45, 145, 20).build());

        // "Don't show again" checkbox
        dontShowAgainCheckbox = Checkbox.builder(Component.literal("Don't warn me again for this module"), this.font)
            .pos(dialogX + 20, dialogY + DIALOG_HEIGHT - 75)
            .build();
        this.addRenderableWidget(dontShowAgainCheckbox);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Dim background
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // Dialog background
        context.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, SkeetTheme.BG_PRIMARY());

        // Border
        drawBorder(context, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, SkeetTheme.STATUS_WARNING());

        // Title bar background
        context.fill(dialogX + 1, dialogY + 1, dialogX + DIALOG_WIDTH - 1, dialogY + 25, SkeetTheme.BG_SECONDARY());

        // Warning icon and title
        String title = "\u26a0 Warning - Risky Module";
        context.drawString(this.font, title, dialogX + 10, dialogY + 8, SkeetTheme.STATUS_WARNING(), false);

        // Module name
        String moduleName = "Module: " + module.getName();
        context.drawCenteredString(this.font, moduleName, this.width / 2, dialogY + 40, SkeetTheme.ACCENT_PRIMARY());

        // Warning text lines
        String[] warningLines = {
            "This module is flagged as VERY RISKY.",
            "Using it may result in detection or bans.",
            "",
            "Are you sure you want to enable it?"
        };

        int lineY = dialogY + 60;
        for (String line : warningLines) {
            if (!line.isEmpty()) {
                context.drawCenteredString(this.font, line, this.width / 2, lineY, SkeetTheme.TEXT_PRIMARY());
            }
            lineY += 12;
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
