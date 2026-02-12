package dev.hunchclient.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple text input screen for editing strings
 */
public class TextInputScreen extends Screen {

    private final Screen parent;
    private final Consumer<String> onTextSubmit;
    private final String initialText;
    private final String promptText;

    private EditBox textField;

    public TextInputScreen(Screen parent, String promptText, String initialText, Consumer<String> onTextSubmit) {
        super(Component.literal("Text Input"));
        this.parent = parent;
        this.promptText = promptText;
        this.initialText = initialText;
        this.onTextSubmit = onTextSubmit;
    }

    @Override
    protected void init() {
        // Create text field
        int fieldWidth = 300;
        int fieldHeight = 20;
        textField = new EditBox(
            this.font,
            this.width / 2 - fieldWidth / 2,
            this.height / 2 - 10,
            fieldWidth,
            fieldHeight,
            Component.literal("Input")
        );
        textField.setMaxLength(256);
        textField.setValue(initialText);
        textField.setFocused(true);
        this.addWidget(textField);
        this.setInitialFocus(textField);

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            onTextSubmit.accept(textField.getValue());
            this.onClose();
        }).bounds(this.width / 2 - 155, this.height / 2 + 30, 150, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            this.onClose();
        }).bounds(this.width / 2 + 5, this.height / 2 + 30, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Render widgets (buttons) - this also renders background
        super.render(context, mouseX, mouseY, delta);

        // Render prompt text
        context.drawCenteredString(this.font, promptText, this.width / 2, this.height / 2 - 40, 0xFFFFFF);

        // Render text field
        if (textField != null) {
            textField.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        // Enter key submits
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            onTextSubmit.accept(textField.getValue());
            this.onClose();
            return true;
        }
        // Escape key cancels
        if (keyCode == 256) { // ESC
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
        return true;
    }
}
