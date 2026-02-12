package dev.hunchclient.gui;

import dev.hunchclient.render.FontConfig;
import dev.hunchclient.render.FontManager;
import dev.hunchclient.render.NVGRenderer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Font selection screen with dropdown and reload button
 * Allows users to select fonts without restarting Minecraft
 */
public class FontSelectionScreen extends Screen {
    private final Screen parent;
    private CycleButton<String> globalFontButton;
    private CycleButton<String> terminalFontButton;
    private Button reloadButton;
    private Button doneButton;

    public FontSelectionScreen(Screen parent) {
        super(Component.literal("Font Selection"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        List<String> fontNames = FontManager.getAvailableFontNames();

        if (fontNames.isEmpty()) {
            fontNames = List.of("No fonts available");
        }

        int centerX = this.width / 2;
        int startY = 60;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 30;

        // Global Font Dropdown
        String currentGlobalFont = FontConfig.getSelectedFontName();
        if (currentGlobalFont == null && !fontNames.isEmpty()) {
            currentGlobalFont = fontNames.get(0);
        }

        final String initialGlobalFont = currentGlobalFont;
        globalFontButton = CycleButton.<String>builder(name -> Component.literal("Global: " + name))
                .withValues(fontNames)
                .withInitialValue(initialGlobalFont != null ? initialGlobalFont : fontNames.get(0))
                .create(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight, Component.literal("Global Font"),
                        (button, value) -> {
                            FontConfig.setSelectedFont(value);
                            NVGRenderer.clearFontCache();
                        });
        this.addRenderableWidget(globalFontButton);

        // Terminal Font Dropdown
        String currentTerminalFont = FontConfig.getTerminalFontName();
        if (currentTerminalFont == null && !fontNames.isEmpty()) {
            currentTerminalFont = fontNames.get(0);
        }

        final String initialTerminalFont = currentTerminalFont;
        terminalFontButton = CycleButton.<String>builder(name -> Component.literal("Terminal: " + name))
                .withValues(fontNames)
                .withInitialValue(initialTerminalFont != null ? initialTerminalFont : fontNames.get(0))
                .create(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight, Component.literal("Terminal Font"),
                        (button, value) -> {
                            FontConfig.setTerminalFont(value);
                            NVGRenderer.clearFontCache();
                        });
        this.addRenderableWidget(terminalFontButton);

        // Reload Fonts Button
        reloadButton = Button.builder(
                Component.literal("Reload Fonts"),
                button -> {
                    FontManager.reloadFonts();
                    minecraft.setScreen(new FontSelectionScreen(parent)); // Refresh this screen
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
                .build();
        this.addRenderableWidget(reloadButton);

        // Done Button
        doneButton = Button.builder(
                Component.literal("Done"),
                button -> minecraft.setScreen(parent))
                .bounds(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight)
                .build();
        this.addRenderableWidget(doneButton);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Instructions
        String fontsDir = FontManager.getFontsDirectory().toAbsolutePath().toString();
        context.drawCenteredString(this.font,
                Component.literal("Place .ttf/.otf fonts in:"),
                this.width / 2, 40, 0xAAAAAA);
        context.drawCenteredString(this.font,
                Component.literal(fontsDir),
                this.width / 2, 50, 0x888888);

        // Available fonts count
        int fontCount = FontManager.getAvailableFontNames().size();
        context.drawCenteredString(this.font,
                Component.literal("Available fonts: " + fontCount),
                this.width / 2, this.height - 40, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
