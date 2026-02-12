package dev.hunchclient.gui;

import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Renders generic settings from SettingsProvider modules
 */
public class GenericSettingsRenderer {

    private static final int SKEET_ACCENT = 0xFF6699CC;
    private static final int SKEET_TEXT = 0xFFF0F0F0;
    private static final int SKEET_TEXT_DIM = 0xFFAAAAAA;
    private static final int SKEET_TAB_BG = 0xFF1F1F1F;
    private static final int SKEET_TAB_ACTIVE = 0xFF2D2D2D;
    private static final int SKEET_BORDER = 0xFF2A2A2A;

    // Track focused text boxes (module:settingKey -> input value)
    private static final Map<String, String> TEXT_BOX_INPUTS = new HashMap<>();
    private static String focusedTextBox = null;

    // Animation state for focus transitions
    private static final Map<String, Float> FOCUS_ANIMATIONS = new HashMap<>();
    private static long lastUpdateTime = System.currentTimeMillis();

    public static int drawGenericSettings(GuiGraphics context, Font textRenderer, Module module, SettingsProvider settingsProvider, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        for (ModuleSetting setting : settingsProvider.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }

            switch (setting.getType()) {
                case CHECKBOX -> y = drawCheckboxSetting(context, textRenderer, module, (CheckboxSetting) setting, x, y, width);
                case TEXT_BOX -> y = drawTextBoxSetting(context, textRenderer, module, (TextBoxSetting) setting, x, y, width);
                case DROPDOWN -> y = drawDropdownSetting(context, textRenderer, module, (DropdownSetting) setting, x, y, width);
                default -> {
                    // Unsupported setting type, skip
                }
            }
        }

        y += 10;
        return y;
    }

    private static int drawCheckboxSetting(GuiGraphics context, Font textRenderer, Module module, CheckboxSetting setting, int x, int y, int width) {
        String status = setting.getValue() ? "§aON" : "§cOFF";
        String line = "    " + setting.getName() + ": " + status + " ";
        context.drawString(textRenderer, line, x + 15, y, SKEET_TEXT, false);

        String toggle = "[Toggle]";
        int toggleX = x + 15 + textRenderer.width(line);
        context.drawString(textRenderer, toggle, toggleX, y, SKEET_TEXT_DIM, false);

        ModuleSettingsPanel.registerButton(module, "setting_checkbox_" + setting.getKey(), toggleX, y, textRenderer.width(toggle), textRenderer.lineHeight);

        return y + 15;
    }

    private static int drawTextBoxSetting(GuiGraphics context, Font textRenderer, Module module, TextBoxSetting setting, int x, int y, int width) {
        context.drawString(textRenderer, "    " + setting.getName() + ":", x + 15, y, SKEET_TEXT, false);
        y += 15;

        // Draw text field
        int fieldX = x + 15;
        int fieldY = y;
        int fieldWidth = width - 40;
        int fieldHeight = 18;

        String focusKey = module.getName() + ":" + setting.getKey();
        boolean focused = focusKey.equals(focusedTextBox);

        // Update focus animation
        updateFocusAnimation(focusKey, focused);
        float focusAmount = FOCUS_ANIMATIONS.getOrDefault(focusKey, 0.0f);

        // Interpolate field background color
        int fieldBgColor = interpolateColor(SKEET_TAB_BG, SKEET_TAB_ACTIVE, focusAmount);
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, fieldBgColor);

        // Interpolate border color
        int borderColor = interpolateColor(SKEET_BORDER, SKEET_ACCENT, focusAmount);
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + 1, borderColor);
        context.fill(fieldX, fieldY + fieldHeight - 1, fieldX + fieldWidth, fieldY + fieldHeight, borderColor);
        context.fill(fieldX, fieldY, fieldX + 1, fieldY + fieldHeight, borderColor);
        context.fill(fieldX + fieldWidth - 1, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, borderColor);

        // Field text
        String displayText;
        if (focused) {
            String input = TEXT_BOX_INPUTS.getOrDefault(focusKey, setting.getValue());
            displayText = input + (System.currentTimeMillis() % 1000 < 500 ? "_" : "");
        } else {
            String value = setting.getValue();
            if (value.isEmpty()) {
                displayText = setting.getPlaceholder().isEmpty() ? "Click to edit..." : setting.getPlaceholder();
            } else {
                displayText = value;
            }
        }

        // Truncate if too long
        int maxWidth = fieldWidth - 8;
        while (textRenderer.width(displayText) > maxWidth && !displayText.isEmpty()) {
            displayText = displayText.substring(0, displayText.length() - 1);
        }

        context.drawString(textRenderer, displayText, fieldX + 4, fieldY + 5, focused ? SKEET_TEXT : SKEET_TEXT_DIM, false);

        // Register as clickable area
        ModuleSettingsPanel.registerButton(module, "setting_textbox_" + setting.getKey(), fieldX, fieldY, fieldWidth, fieldHeight);

        return y + fieldHeight + 10;
    }

    private static int drawDropdownSetting(GuiGraphics context, Font textRenderer, Module module, DropdownSetting setting, int x, int y, int width) {
        String currentValue = setting.getSelectedOption();
        String line = "    " + setting.getName() + ": " + currentValue + " ";
        context.drawString(textRenderer, line, x + 15, y, SKEET_TEXT, false);

        int controlX = x + 15 + textRenderer.width(line);
        String prevBtn = "[<]";
        String nextBtn = "[>]";

        context.drawString(textRenderer, prevBtn, controlX, y, SKEET_TEXT_DIM, false);
        ModuleSettingsPanel.registerButton(module, "setting_dropdown_prev_" + setting.getKey(), controlX, y, textRenderer.width(prevBtn), textRenderer.lineHeight);

        int nextX = controlX + textRenderer.width(prevBtn) + 4;
        context.drawString(textRenderer, nextBtn, nextX, y, SKEET_TEXT_DIM, false);
        ModuleSettingsPanel.registerButton(module, "setting_dropdown_next_" + setting.getKey(), nextX, y, textRenderer.width(nextBtn), textRenderer.lineHeight);

        return y + 15;
    }

    public static void handleTextBoxClick(Module module, String settingKey) {
        String focusKey = module.getName() + ":" + settingKey;
        if (focusKey.equals(focusedTextBox)) {
            // Already focused, do nothing
            return;
        }

        // Save previous focused text box
        if (focusedTextBox != null && module instanceof SettingsProvider provider) {
            saveTextBoxInput(module, provider);
        }

        // Focus new text box
        focusedTextBox = focusKey;

        // Load current value into input buffer
        if (module instanceof SettingsProvider provider) {
            for (ModuleSetting setting : provider.getSettings()) {
                if (setting instanceof TextBoxSetting textBox && setting.getKey().equals(settingKey)) {
                    TEXT_BOX_INPUTS.put(focusKey, textBox.getValue());
                    break;
                }
            }
        }
    }

    public static void unfocusTextBox(Module module) {
        if (focusedTextBox != null && module instanceof SettingsProvider provider) {
            saveTextBoxInput(module, provider);
            focusedTextBox = null;
        }
    }

    private static void saveTextBoxInput(Module module, SettingsProvider provider) {
        if (focusedTextBox == null) return;

        String[] parts = focusedTextBox.split(":", 2);
        if (parts.length != 2) return;

        String settingKey = parts[1];
        String input = TEXT_BOX_INPUTS.get(focusedTextBox);

        for (ModuleSetting setting : provider.getSettings()) {
            if (setting instanceof TextBoxSetting textBox && setting.getKey().equals(settingKey)) {
                textBox.setValue(input);
                break;
            }
        }
    }

    public static boolean handleChar(Module module, char chr) {
        if (focusedTextBox == null) return false;

        String[] parts = focusedTextBox.split(":", 2);
        if (parts.length != 2 || !parts[0].equals(module.getName())) return false;

        String currentInput = TEXT_BOX_INPUTS.getOrDefault(focusedTextBox, "");
        TEXT_BOX_INPUTS.put(focusedTextBox, currentInput + chr);
        return true;
    }

    public static boolean handleBackspace(Module module) {
        if (focusedTextBox == null) return false;

        String[] parts = focusedTextBox.split(":", 2);
        if (parts.length != 2 || !parts[0].equals(module.getName())) return false;

        String currentInput = TEXT_BOX_INPUTS.getOrDefault(focusedTextBox, "");
        if (!currentInput.isEmpty()) {
            TEXT_BOX_INPUTS.put(focusedTextBox, currentInput.substring(0, currentInput.length() - 1));
        }
        return true;
    }

    public static boolean isFocused() {
        return focusedTextBox != null;
    }

    public static void clearFocus() {
        focusedTextBox = null;
    }

    /**
     * Update focus animation for smooth transitions
     */
    private static void updateFocusAnimation(String key, boolean focused) {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000.0f, 0.1f);
        lastUpdateTime = currentTime;

        float current = FOCUS_ANIMATIONS.getOrDefault(key, 0.0f);
        float target = focused ? 1.0f : 0.0f;
        float speed = 8.0f; // Animation speed

        // Smooth interpolation
        float newValue = current + (target - current) * Math.min(1.0f, deltaTime * speed);
        newValue = Mth.clamp(newValue, 0.0f, 1.0f);

        FOCUS_ANIMATIONS.put(key, newValue);
    }

    /**
     * Interpolate between two colors
     */
    private static int interpolateColor(int color1, int color2, float ratio) {
        ratio = Mth.clamp(ratio, 0.0f, 1.0f);

        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        int a = (int) (a1 + (a2 - a1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
