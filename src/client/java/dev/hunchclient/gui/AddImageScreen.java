package dev.hunchclient.gui;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.impl.ImageHUDModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen for adding new GIF/PNG images to HUD
 * Supports both URLs and local file paths
 */
public class AddImageScreen extends Screen {

    private final Screen parent;
    private final ImageHUDModule module;

    private EditBox urlField;
    private EditBox widthField;
    private EditBox heightField;

    private String errorMessage = null;
    private long errorMessageTime = 0;

    public AddImageScreen(Screen parent, ImageHUDModule module) {
        super(Component.literal("Add HUD Image"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 80;
        int fieldWidth = 300;
        int fieldHeight = 20;

        // URL/Filename input field
        urlField = new EditBox(
            this.font,
            centerX - fieldWidth / 2,
            startY + 30,
            fieldWidth,
            fieldHeight,
            Component.literal("URL or Filename")
        );
        urlField.setMaxLength(512);
        urlField.setHint(Component.literal("Enter URL or filename..."));
        urlField.setFocused(true);
        this.addWidget(urlField);
        this.setInitialFocus(urlField);

        // Width field
        widthField = new EditBox(
            this.font,
            centerX - fieldWidth / 2,
            startY + 70,
            140,
            fieldHeight,
            Component.literal("Width")
        );
        widthField.setMaxLength(4);
        widthField.setValue("100");
        widthField.setHint(Component.literal("Width (px)"));
        this.addWidget(widthField);

        // Height field
        heightField = new EditBox(
            this.font,
            centerX - fieldWidth / 2 + 160,
            startY + 70,
            140,
            fieldHeight,
            Component.literal("Height")
        );
        heightField.setMaxLength(4);
        heightField.setValue("100");
        heightField.setHint(Component.literal("Height (px)"));
        this.addWidget(heightField);

        // Add button
        this.addRenderableWidget(Button.builder(Component.literal("Add Image"), button -> {
            addImage();
        }).bounds(centerX - 155, startY + 110, 150, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            this.onClose();
        }).bounds(centerX + 5, startY + 110, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Dark background
        context.fill(0, 0, this.width, this.height, SkeetTheme.BG_PRIMARY);

        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 80;

        // Title
        context.drawCenteredString(
            this.font,
            "§l§nAdd HUD Image",
            centerX,
            startY - 10,
            SkeetTheme.TEXT_PRIMARY
        );

        // Labels
        context.drawString(
            this.font,
            "URL or Filename:",
            centerX - 150,
            startY + 18,
            SkeetTheme.TEXT_SECONDARY,
            false
        );

        context.drawString(
            this.font,
            "Width:",
            centerX - 150,
            startY + 58,
            SkeetTheme.TEXT_SECONDARY,
            false
        );

        context.drawString(
            this.font,
            "Height:",
            centerX + 10,
            startY + 58,
            SkeetTheme.TEXT_SECONDARY,
            false
        );

        // Render text fields
        if (urlField != null) {
            urlField.render(context, mouseX, mouseY, delta);
        }
        if (widthField != null) {
            widthField.render(context, mouseX, mouseY, delta);
        }
        if (heightField != null) {
            heightField.render(context, mouseX, mouseY, delta);
        }

        // Info text
        int infoY = startY + 145;
        context.drawCenteredString(
            this.font,
            "§7Examples:",
            centerX,
            infoY,
            SkeetTheme.TEXT_DIM
        );
        infoY += 12;
        context.drawCenteredString(
            this.font,
            "§8https://example.com/image.gif",
            centerX,
            infoY,
            SkeetTheme.TEXT_DIM
        );
        infoY += 10;
        context.drawCenteredString(
            this.font,
            "§8myimage.png §7(from config/hunchclient/hud_images/)",
            centerX,
            infoY,
            SkeetTheme.TEXT_DIM
        );

        // Error message
        if (errorMessage != null && System.currentTimeMillis() - errorMessageTime < 3000) {
            context.drawCenteredString(
                this.font,
                "§c" + errorMessage,
                centerX,
                startY + 180,
                SkeetTheme.STATUS_ERROR
            );
        }

        // Image count info
        int imageCount = module.getImageCount();
        int maxImages = module.getMaxImages();
        String countText = String.format("§7Images: §f%d§7/§f%d", imageCount, maxImages);
        context.drawString(
            this.font,
            countText,
            10,
            this.height - 20,
            SkeetTheme.TEXT_SECONDARY,
            false
        );
    }

    /**
     * Add image to HUD module
     */
    private void addImage() {
        String url = urlField.getValue().trim();

        if (url.isEmpty()) {
            showError("Please enter a URL or filename");
            return;
        }

        // Parse dimensions
        int width;
        int height;

        try {
            width = Integer.parseInt(widthField.getValue().trim());
            height = Integer.parseInt(heightField.getValue().trim());
        } catch (NumberFormatException e) {
            showError("Invalid width or height");
            return;
        }

        // Validate dimensions
        if (width < 10 || width > 2048 || height < 10 || height > 2048) {
            showError("Size must be between 10 and 2048 pixels");
            return;
        }

        // Check if max images reached
        if (module.getImageCount() >= module.getMaxImages()) {
            showError("Maximum images reached (" + module.getMaxImages() + ")");
            return;
        }

        // Add image (centered by default)
        boolean success = module.addImage(url, 50.0f, 50.0f, width, height);

        if (success) {
            // Save config
            module.saveConfig();

            // Open HUD editor to position the new image
            if (this.minecraft != null) {
                this.minecraft.setScreen(new dev.hunchclient.hud.HudEditorScreen(this));
            }
        } else {
            showError("Failed to add image");
        }
    }

    /**
     * Show error message for 3 seconds
     */
    private void showError(String message) {
        this.errorMessage = message;
        this.errorMessageTime = System.currentTimeMillis();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        // Enter key submits
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            addImage();
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
