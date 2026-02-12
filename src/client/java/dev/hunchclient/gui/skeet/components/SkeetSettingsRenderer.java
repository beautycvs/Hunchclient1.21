package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders module settings using Skeet components
 * Works with both SettingsProvider modules and legacy custom modules
 */
public class SkeetSettingsRenderer {

    private final Module module;
    private final List<SettingComponent> components = new ArrayList<>();
    private final Font textRenderer;

    private int contentHeight = 0;
    private boolean pendingRebuild = false;

    /**
     * Wrapper for component + setting (for visibility checks)
     */
    private static class SettingComponent {
        final SkeetComponent component;
        final ModuleSetting setting;

        SettingComponent(SkeetComponent component, ModuleSetting setting) {
            this.component = component;
            this.setting = setting;
        }

        boolean isVisible() {
            return setting == null || setting.isVisible();
        }
    }

    public SkeetSettingsRenderer(Module module) {
        this.module = module;
        this.textRenderer = Minecraft.getInstance().font;
        buildComponents();
    }

    /**
     * Build all setting components for this module
     */
    private void buildComponents() {
        components.clear();
        contentHeight = SkeetTheme.SPACING_MD;

        // Check if module implements SettingsProvider
        if (module instanceof SettingsProvider provider) {
            buildGenericSettings(provider);
        } else {
            // Legacy custom module settings (TODO: Convert these to SettingsProvider)
            buildLegacySettings();
        }
    }

    /**
     * Build settings from SettingsProvider
     * NOTE: Builds ALL components, visibility is checked during render
     */
    private void buildGenericSettings(SettingsProvider provider) {
        for (ModuleSetting setting : provider.getSettings()) {
            // Always build components, check visibility during render
            switch (setting.getType()) {
                case CHECKBOX -> addCheckboxSetting((CheckboxSetting) setting);
                case TEXT_BOX -> addTextBoxSetting((TextBoxSetting) setting);
                case DROPDOWN -> addDropdownSetting((DropdownSetting) setting);
                case SLIDER -> addSliderSetting((SliderSetting) setting);
                case COLOR_PICKER -> addColorPickerSetting((ColorPickerSetting) setting);
                case BUTTON -> addButtonSetting((ButtonSetting) setting);
            }
        }
    }

    /**
     * Build legacy custom settings (for modules that don't use SettingsProvider yet)
     */
    private void buildLegacySettings() {
        // TODO: Add legacy module settings here
        // For now, just show a message that module needs conversion
        contentHeight += 20;
    }

    /**
     * Add checkbox setting
     */
    private void addCheckboxSetting(CheckboxSetting setting) {
        SkeetToggle toggle = new SkeetToggle(
            0, contentHeight,
            300,
            setting.getName(),
            setting.getValue(),
            enabled -> setting.toggle()
        );
        components.add(new SettingComponent(toggle, setting));
        contentHeight += 20;
    }

    /**
     * Add text box setting
     */
    private void addTextBoxSetting(TextBoxSetting setting) {
        // Label
        contentHeight += textRenderer.lineHeight + 2;

        // Text field
        SkeetTextField textField = new SkeetTextField(
            0, contentHeight,
            300,
            setting.getPlaceholder(),
            setting.getValue(),
            value -> setting.setValue(value)
        );
        components.add(new SettingComponent(textField, setting));
        contentHeight += 18 + SkeetTheme.SPACING_MD;
    }

    /**
     * Add dropdown setting (real expandable dropdown)
     */
    private void addDropdownSetting(DropdownSetting setting) {
        // Convert String[] to List<String>
        List<String> optionsList = List.of(setting.getOptions());

        SkeetRealDropdown dropdown = new SkeetRealDropdown(
            0, contentHeight,
            300,
            setting.getName(),
            optionsList,
            setting.getSelectedIndex(),
            value -> {
                int index = optionsList.indexOf(value);
                if (index >= 0) {
                    setting.setSelectedIndex(index);
                }
            }
        );
        components.add(new SettingComponent(dropdown, setting));
        contentHeight += dropdown.getBaseHeight();
    }

    /**
     * Add slider setting
     */
    private void addSliderSetting(SliderSetting setting) {
        SkeetSlider slider = new SkeetSlider(
            0, contentHeight,
            300,
            setting.getName(),
            setting.getValue(),
            setting.getMin(),
            setting.getMax(),
            value -> setting.setValue(value)
        );

        // Apply display options
        if (setting.isPercentage()) {
            slider.asPercentage();
        } else {
            slider.withDecimals(setting.getDecimals());
            if (!setting.getSuffix().isEmpty()) {
                slider.withSuffix(setting.getSuffix());
            }
        }

        components.add(new SettingComponent(slider, setting));
        contentHeight += slider.getHeight();
    }

    /**
     * Add color picker setting
     */
    private void addColorPickerSetting(ColorPickerSetting setting) {
        SkeetColorPicker colorPicker = new SkeetColorPicker(
            0, contentHeight,
            300,
            setting.getName(),
            setting.getColor(),
            color -> setting.setColor(color)
        );

        components.add(new SettingComponent(colorPicker, setting));
        contentHeight += colorPicker.getHeight();
    }

    /**
     * Add button setting
     */
    private void addButtonSetting(ButtonSetting setting) {
        Runnable action = () -> {
            setting.execute();
            if ("hitsound_reload".equals(setting.getKey())) {
                pendingRebuild = true;
            }
        };
        SkeetButtonSetting button = new SkeetButtonSetting(
            0, contentHeight,
            300,
            setting.getName(),
            action
        );

        components.add(new SettingComponent(button, setting));
        contentHeight += button.getHeight() + 4; // Extra spacing for buttons
    }

    /**
     * Render all settings (only visible ones)
     */
    public void render(GuiGraphics context, int x, int y, int width, int mouseX, int mouseY, float delta) {
        if (pendingRebuild) {
            buildComponents();
            pendingRebuild = false;
        }
        // Update component positions and render only visible ones
        int currentY = y + SkeetTheme.SPACING_MD;
        for (SettingComponent sc : components) {
            if (!sc.isVisible()) continue; // Skip invisible settings

            sc.component.setPosition(x + SkeetTheme.SPACING_MD, currentY);
            sc.component.setSize(width - SkeetTheme.SPACING_MD * 2, sc.component.getHeight());
            sc.component.render(context, mouseX, mouseY, delta);
            currentY += sc.component.getHeight();
        }

        // Render legacy settings info if needed
        if (!(module instanceof SettingsProvider) && components.isEmpty()) {
            String msg = "Module not using SettingsProvider yet";
            context.drawString(textRenderer, msg, x + SkeetTheme.SPACING_MD, y + SkeetTheme.SPACING_MD, SkeetTheme.TEXT_DIM, false);
        }
    }

    /**
     * Forward mouse click to visible components
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (SettingComponent sc : components) {
            if (!sc.isVisible()) continue;
            if (sc.component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forward mouse release to visible components
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (SettingComponent sc : components) {
            if (!sc.isVisible()) continue;
            if (sc.component.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forward mouse drag to visible components
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (SettingComponent sc : components) {
            if (!sc.isVisible()) continue;
            if (sc.component.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle character typing (for text fields and sliders with direct input)
     */
    public boolean charTyped(char chr, int modifiers) {
        for (SettingComponent sc : components) {
            if (!sc.isVisible()) continue;
            if (sc.component instanceof SkeetTextField textField) {
                if (textField.isFocused() && textField.charTyped(chr)) {
                    return true;
                }
            }
            // Also forward to sliders (for direct value input)
            if (sc.component instanceof SkeetSlider slider) {
                if (slider.charTyped(chr, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handle key press (for text fields and sliders with direct input)
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SettingComponent sc : components) {
            if (!sc.isVisible()) continue;
            if (sc.component instanceof SkeetTextField textField) {
                if (textField.isFocused()) {
                    if (keyCode == 259) { // BACKSPACE
                        return textField.handleBackspace();
                    } else if (keyCode == 257) { // ENTER
                        return textField.handleEnter();
                    }
                }
            }
            // Also forward to sliders (for direct value input)
            if (sc.component instanceof SkeetSlider slider) {
                if (slider.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Unfocus all text fields
     */
    public void unfocusAll() {
        for (SettingComponent sc : components) {
            if (sc.component instanceof SkeetTextField textField) {
                textField.setFocused(false);
            }
        }
    }

    /**
     * Get total height of all visible settings
     */
    public int getContentHeight() {
        int height = SkeetTheme.SPACING_MD;
        for (SettingComponent sc : components) {
            if (sc.isVisible()) {
                height += sc.component.getHeight();
            }
        }
        return height + SkeetTheme.SPACING_MD;
    }

    /**
     * Check if any text field is focused
     */
    public boolean hasFocusedField() {
        for (SettingComponent sc : components) {
            if (sc.isVisible() && sc.component instanceof SkeetTextField textField && textField.isFocused()) {
                return true;
            }
        }
        return false;
    }
}
