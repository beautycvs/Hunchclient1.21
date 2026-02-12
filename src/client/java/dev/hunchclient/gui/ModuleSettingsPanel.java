package dev.hunchclient.gui;

import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.impl.dungeons.AlignAuraModule;
import dev.hunchclient.module.impl.AnimationsModule;
import dev.hunchclient.module.impl.dungeons.AutoSSModule;
import dev.hunchclient.module.impl.dungeons.BonzoStaffHelperModule;
import dev.hunchclient.module.impl.dungeons.DungeonOptimizerModule;
import dev.hunchclient.module.impl.dungeons.ChestAuraModule;
import dev.hunchclient.module.impl.CustomHitSoundModule;
import dev.hunchclient.module.impl.dungeons.DungeonBreakerHelperModule;
import dev.hunchclient.module.impl.ImageHUDModule;
import dev.hunchclient.module.impl.dungeons.EtherwarpHelperModule;
import dev.hunchclient.module.impl.misc.F7SimModule;
import dev.hunchclient.module.impl.TerminalSolverModule;
import dev.hunchclient.module.impl.KaomojiReplacerModule;
import dev.hunchclient.module.impl.dungeons.LeftClickEtherwarpModule;
import dev.hunchclient.module.impl.MeowMessagesModule;
import dev.hunchclient.module.impl.NameProtectModule;
import dev.hunchclient.module.impl.PlayerSizeSpinModule;
import dev.hunchclient.module.impl.StretchModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Panel for module-specific settings in GUI
 */
public class ModuleSettingsPanel {

    private static final int SKEET_ACCENT = 0xFF6699CC;
    private static final int SKEET_TEXT = 0xFFF0F0F0;
    private static final int SKEET_TEXT_DIM = 0xFFAAAAAA;
    private static final int SKEET_TAB_BG = 0xFF1F1F1F;
    private static final int SKEET_SUCCESS = 0xFF5BFF8B;
    private static final int SKEET_ERROR = 0xFFFF5555;

    public static final class SliderArea {
        public final Module module;
        public final String id;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final float min;
        public final float max;

        private SliderArea(Module module, String id, int x, int y, int width, int height, float min, float max) {
            this.module = module;
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.min = min;
            this.max = max;
        }

        public String key() {
            return module.getName() + ":" + id;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public static final class ButtonArea {
        public final Module module;
        public final String id;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private ButtonArea(Module module, String id, int x, int y, int width, int height) {
            this.module = module;
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public String key() {
            return module.getName() + ":" + id;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static final List<SliderArea> SLIDER_AREAS = new ArrayList<>();
    private static final List<ButtonArea> BUTTON_AREAS = new ArrayList<>();

    public static void beginFrame() {
        SLIDER_AREAS.clear();
        BUTTON_AREAS.clear();
    }

    public static List<SliderArea> getSliderAreas() {
        return SLIDER_AREAS;
    }

    public static List<ButtonArea> getButtonAreas() {
        return BUTTON_AREAS;
    }

    public static SliderArea findSliderByKey(String key) {
        return SLIDER_AREAS.stream()
            .filter(area -> area.key().equals(key))
            .findFirst()
            .orElse(null);
    }

    private static void registerSlider(Module module, String id, int x, int y, int width, int height, float min, float max) {
        SLIDER_AREAS.add(new SliderArea(module, id, x, y, width, height, min, max));
    }

    public static void registerButton(Module module, String id, int x, int y, int width, int height) {
        BUTTON_AREAS.add(new ButtonArea(module, id, x, y, width, height));
    }

    public static int drawSettings(GuiGraphics context, Font textRenderer, Module module, int x, int y, int width) {
        // Legacy custom settings for specific modules
        if (module instanceof StretchModule stretch) {
            return drawStretchSettings(context, textRenderer, stretch, x, y, width);
        } else if (module instanceof AnimationsModule animations) {
            return drawAnimationsSettings(context, textRenderer, animations, x, y, width);
        } else if (module instanceof EtherwarpHelperModule etherwarp) {
            return drawEtherwarpHelperSettings(context, textRenderer, etherwarp, x, y, width);
        } else if (module instanceof DungeonBreakerHelperModule dungeonBreaker) {
            return drawDungeonBreakerHelperSettings(context, textRenderer, dungeonBreaker, x, y, width);
        } else if (module instanceof LeftClickEtherwarpModule leftClickEther) {
            return drawLeftClickEtherwarpSettings(context, textRenderer, leftClickEther, x, y, width);
        } else if (module instanceof AutoSSModule autoSS) {
            return drawAutoSSSettings(context, textRenderer, autoSS, x, y, width);
        } else if (module instanceof AlignAuraModule alignAura) {
            return drawAlignAuraSettings(context, textRenderer, alignAura, x, y, width);
        } else if (module instanceof CustomHitSoundModule hitSound) {
            return drawCustomHitSoundSettings(context, textRenderer, hitSound, x, y, width);
        } else if (module instanceof PlayerSizeSpinModule playerSize) {
            return drawPlayerSizeSpinSettings(context, textRenderer, playerSize, x, y, width);
        } else if (module instanceof F7SimModule f7Sim) {
            return drawF7SimSettings(context, textRenderer, f7Sim, x, y, width);
        } else if (module instanceof BonzoStaffHelperModule bonzoStaff) {
            return drawBonzoStaffHelperSettings(context, textRenderer, bonzoStaff, x, y, width);
        } else if (module instanceof NameProtectModule nameProtect) {
            return drawNameProtectSettings(context, textRenderer, nameProtect, x, y, width);
        } else if (module instanceof KaomojiReplacerModule kaomoji) {
            return drawKaomojiReplacerSettings(context, textRenderer, kaomoji, x, y, width);
        } else if (module instanceof MeowMessagesModule meow) {
            return drawMeowMessagesSettings(context, textRenderer, meow, x, y, width);
        } else if (module instanceof DungeonOptimizerModule dungeonOptimizer) {
            return drawDungeonOptimizerSettings(context, textRenderer, dungeonOptimizer, x, y, width);
        } else if (module instanceof ChestAuraModule chestAura) {
            return drawChestAuraSettings(context, textRenderer, chestAura, x, y, width);
        } else if (module instanceof TerminalSolverModule terminalSolver) {
            return drawTerminalSolverSettings(context, textRenderer, terminalSolver, x, y, width);
        } else if (module instanceof ImageHUDModule imageHUD) {
            return drawImageHUDSettings(context, textRenderer, imageHUD, x, y, width);
        }

        if (module instanceof SettingsProvider settingsProvider) {
            return drawGenericSettings(context, textRenderer, module, settingsProvider, x, y, width);
        }

        return y;
    }

    public static int calculateSettingsHeight(Font textRenderer, Module module) {
        if (module instanceof StretchModule) {
            return 90; // Approximate height
        } else if (module instanceof AnimationsModule) {
            return 280; // Base settings with swing speed slider always visible
        } else if (module instanceof EtherwarpHelperModule) {
            return 350; // RGB sliders + settings
        } else if (module instanceof DungeonBreakerHelperModule) {
            return 350; // Same as EtherwarpHelper
        } else if (module instanceof LeftClickEtherwarpModule) {
            return 150; // Settings height
        } else if (module instanceof AutoSSModule autoSS) {
            int base = 200; // Base settings
            if (autoSS.isSmoothRotate()) {
                base += 35; // Rotation speed slider
            }
            return base;
        } else if (module instanceof AlignAuraModule) {
            return 120; // Info panel only
        } else if (module instanceof CustomHitSoundModule hitSound) {
            int base = 120;
            if (hitSound.isCustomEtherwarpEnabled()) {
                base += 60; // Etherwarp sound settings
            }
            return base;
        } else if (module instanceof PlayerSizeSpinModule playerSize) {
            int base = 150; // Base sliders + server section
            if (playerSize.isSpinEnabled()) {
                base += 35; // Spin speed slider
            }
            return base;
        } else if (module instanceof F7SimModule f7Sim) {
            int base = 280; // Core toggles and headings + terminal ping + block respawn sections
            if (f7Sim.isSpeedSimulationEnabled()) {
                base += 30; // Speed slider
            }
            if (f7Sim.isBonzoSimulationEnabled()) {
                base += 40; // Bonzo sliders
            }
            if (f7Sim.isBlockRespawnEnabled()) {
                base += 30; // Block respawn delay slider
            }
            return base;
        } else if (module instanceof BonzoStaffHelperModule) {
            return 200; // Sliders + stats + buttons + info
        } else if (module instanceof NameProtectModule) {
            return 80;
        } else if (module instanceof KaomojiReplacerModule) {
            return 60; // Info only
        } else if (module instanceof MeowMessagesModule) {
            return 60; // Info only
        } else if (module instanceof DungeonOptimizerModule) {
            return 180; // Six toggles + spacing
        } else if (module instanceof ChestAuraModule) {
            return 340; // Toggles + timing sliders + 3 tick delay sliders
        } else if (module instanceof TerminalSolverModule) {
            return 320; // Status, toggles, sliders, info
        } else if (module instanceof ImageHUDModule imageHUD) {
            int base = 150; // Base controls
            int imageCount = imageHUD.getImages().size();
            return base + (imageCount * 60); // Each image adds ~60px
        }
        return 50; // Default
    }

    private static int drawStretchSettings(GuiGraphics context, Font textRenderer, StretchModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        float ratio = module.getTargetAspectRatio();
        context.drawString(textRenderer, "    Aspect Ratio: " + String.format("%.3f", ratio), x + 15, y, SKEET_TEXT, false);
        y += 12;
        int sliderX = x + 15;
        int sliderWidth = width - 40;
        drawSlider(context, module, "stretch_ratio", sliderX, y, sliderWidth, ratio, 0.5f, 3.0f);
        y += 15;

        context.drawString(textRenderer, "    Presets:", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        String[] labels = {"4:3", "16:9", "16:10", "21:9"};
        int presetX = x + 15;
        for (int i = 0; i < labels.length; i++) {
            String text = "[" + labels[i] + "]";
            context.drawString(textRenderer, text, presetX, y, SKEET_TEXT_DIM, false);
            int textWidth = textRenderer.width(text);
            registerButton(module, "stretch_preset_" + labels[i].replace(":", "_"), presetX, y, textWidth, textRenderer.lineHeight);
            presetX += textWidth + 6;
        }
        y += 15;

        String status = module.isStretchEnabled() ? "§aON" : "§cOFF";
        String base = "    Active: " + status + " ";
        context.drawString(textRenderer, base, x + 15, y, SKEET_TEXT, false);
        String toggleLabel = "[Toggle]";
        int toggleX = x + 15 + textRenderer.width(base);
        context.drawString(textRenderer, toggleLabel, toggleX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "stretch_toggle", toggleX, y, textRenderer.width(toggleLabel), textRenderer.lineHeight);
        y += 20;

        return y;
    }

    private static int drawAnimationsSettings(GuiGraphics context, Font textRenderer, AnimationsModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Transform:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Size: " + String.format("%.2f", module.getSize()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_size", x + 15, y, width - 40, module.getSize(), -1.5f, 1.5f);
        y += 15;

        context.drawString(textRenderer, "    Offset X: " + String.format("%.2f", module.getOffsetX()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_offset_x", x + 15, y, width - 40, module.getOffsetX(), -2.5f, 1.5f);
        y += 15;

        context.drawString(textRenderer, "    Offset Y: " + String.format("%.2f", module.getOffsetY()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_offset_y", x + 15, y, width - 40, module.getOffsetY(), -1.5f, 1.5f);
        y += 15;

        context.drawString(textRenderer, "    Offset Z: " + String.format("%.2f", module.getOffsetZ()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_offset_z", x + 15, y, width - 40, module.getOffsetZ(), -1.5f, 3.0f);
        y += 15;

        context.drawString(textRenderer, "    Yaw: " + String.format("%.1f°", module.getYaw()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_yaw", x + 15, y, width - 40, module.getYaw(), -180.0f, 180.0f);
        y += 15;

        context.drawString(textRenderer, "    Pitch: " + String.format("%.1f°", module.getPitch()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_pitch", x + 15, y, width - 40, module.getPitch(), -180.0f, 180.0f);
        y += 15;

        context.drawString(textRenderer, "    Roll: " + String.format("%.1f°", module.getRoll()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_roll", x + 15, y, width - 40, module.getRoll(), -180.0f, 180.0f);
        y += 20;

        context.drawString(textRenderer, "  Swing & Equip:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        y = drawToggleRow(context, textRenderer, module, x, y, "Ignore Haste", module.isIgnoreHaste(), "animations_toggle_ignore_haste");
        y = drawToggleRow(context, textRenderer, module, x, y, "No Equip Reset", module.isNoEquipReset(), "animations_toggle_no_equip");
        y = drawToggleRow(context, textRenderer, module, x, y, "No Swing", module.isNoSwing(), "animations_toggle_no_swing");
        y = drawToggleRow(context, textRenderer, module, x, y, "No Terminator Swing", module.isNoTermSwing(), "animations_toggle_no_term");

        context.drawString(textRenderer, "    Swing Speed: " + String.format("%.2f", module.getSwingSpeed()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "animations_swing_speed", x + 15, y, width - 40, module.getSwingSpeed(), -2.0f, 1.0f);
        y += 20;

        String resetText = "  [Reset]";
        context.drawString(textRenderer, resetText, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "animations_reset", x + 15, y, textRenderer.width(resetText), textRenderer.lineHeight);
        y += 20;

        return y;
    }

    private static int drawToggleRow(GuiGraphics context, Font textRenderer, Module module, int x, int y, String label, boolean enabled, String buttonId) {
        String state = enabled ? "\u00a7aON" : "\u00a7cOFF";
        String line = "    " + label + ": " + state + " ";
        context.drawString(textRenderer, line, x + 15, y, SKEET_TEXT, false);
        String toggle = "[Toggle]";
        int toggleX = x + 15 + textRenderer.width(line);
        context.drawString(textRenderer, toggle, toggleX, y, SKEET_TEXT_DIM, false);
        registerButton(module, buttonId, toggleX, y, textRenderer.width(toggle), textRenderer.lineHeight);
        return y + 15;
    }

    private static int drawEtherwarpHelperSettings(GuiGraphics context, Font textRenderer, EtherwarpHelperModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        String modeText = "    Render Mode: " + module.getRenderMode().name();
        context.drawString(textRenderer, modeText, x + 15, y, SKEET_TEXT, false);
        int modeX = x + 15 + textRenderer.width(modeText) + 2;
        String prevLabel = "[Prev]";
        String nextLabel = "[Next]";
        context.drawString(textRenderer, prevLabel, modeX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "etherwarp_mode_prev", modeX, y, textRenderer.width(prevLabel), textRenderer.lineHeight);
        int nextX = modeX + textRenderer.width(prevLabel) + 4;
        context.drawString(textRenderer, nextLabel, nextX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "etherwarp_mode_next", nextX, y, textRenderer.width(nextLabel), textRenderer.lineHeight);
        y += 20;

        // Can Warp Color Picker
        float[] canWarpColor = module.getCanWarpColor();
        context.drawString(textRenderer, "  Can Warp Color:", x + 10, y, SKEET_ACCENT, false);

        // Show RGB hex
        String hex = String.format("#%02X%02X%02X",
            (int)(canWarpColor[0] * 255),
            (int)(canWarpColor[1] * 255),
            (int)(canWarpColor[2] * 255));
        context.drawString(textRenderer, hex, x + width - textRenderer.width(hex) - 10, y, SKEET_TEXT, false);
        y += 15;

        // Color picker button area - opens picker
        String pickBtn = "[Pick Color]";
        context.drawString(textRenderer, pickBtn, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "etherwarp_canwarp_picker", x + 15, y, textRenderer.width(pickBtn), textRenderer.lineHeight);

        // Color preview
        int previewSize = textRenderer.lineHeight;
        int previewX = x + 15 + textRenderer.width(pickBtn) + 6;
        int rgb = ((int)(canWarpColor[0] * 255) << 16) | ((int)(canWarpColor[1] * 255) << 8) | (int)(canWarpColor[2] * 255);
        context.fill(previewX, y, previewX + previewSize, y + previewSize, 0xFF000000 | rgb);
        context.fill(previewX, y, previewX + previewSize, y + 1, 0xFF000000);
        context.fill(previewX, y + previewSize - 1, previewX + previewSize, y + previewSize, 0xFF000000);
        context.fill(previewX, y, previewX + 1, y + previewSize, 0xFF000000);
        context.fill(previewX + previewSize - 1, y, previewX + previewSize, y + previewSize, 0xFF000000);
        y += 20;

        // Cannot Warp Color Picker
        float[] cannotWarpColor = module.getCannotWarpColor();
        context.drawString(textRenderer, "  Cannot Warp Color:", x + 10, y, SKEET_ACCENT, false);

        String hex2 = String.format("#%02X%02X%02X",
            (int)(cannotWarpColor[0] * 255),
            (int)(cannotWarpColor[1] * 255),
            (int)(cannotWarpColor[2] * 255));
        context.drawString(textRenderer, hex2, x + width - textRenderer.width(hex2) - 10, y, SKEET_TEXT, false);
        y += 15;

        String pickBtn2 = "[Pick Color]";
        context.drawString(textRenderer, pickBtn2, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "etherwarp_cannotwarp_picker", x + 15, y, textRenderer.width(pickBtn2), textRenderer.lineHeight);

        int previewX2 = x + 15 + textRenderer.width(pickBtn2) + 6;
        int rgb2 = ((int)(cannotWarpColor[0] * 255) << 16) | ((int)(cannotWarpColor[1] * 255) << 8) | (int)(cannotWarpColor[2] * 255);
        context.fill(previewX2, y, previewX2 + previewSize, y + previewSize, 0xFF000000 | rgb2);
        context.fill(previewX2, y, previewX2 + previewSize, y + 1, 0xFF000000);
        context.fill(previewX2, y + previewSize - 1, previewX2 + previewSize, y + previewSize, 0xFF000000);
        context.fill(previewX2, y, previewX2 + 1, y + previewSize, 0xFF000000);
        context.fill(previewX2 + previewSize - 1, y, previewX2 + previewSize, y + previewSize, 0xFF000000);
        y += 25;

        context.drawString(textRenderer, "    Outline Width: " + String.format("%.1f", module.getOutlineWidth()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "etherwarp_outline", x + 15, y, width - 40, module.getOutlineWidth(), 0.5f, 10.0f);
        y += 15;

        context.drawString(textRenderer, "    Fill Opacity: " + String.format("%.0f%%", module.getFillOpacity() * 100f), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "etherwarp_fill", x + 15, y, width - 40, module.getFillOpacity(), 0.0f, 1.0f);
        y += 15;

        context.drawString(textRenderer, "    Corner Ratio: " + String.format("%.2f", module.getCornerRatio()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "etherwarp_corner", x + 15, y, width - 40, module.getCornerRatio(), 0.05f, 0.9f);
        y += 15;

        String resetCorner = "[Reset Corner]";
        context.drawString(textRenderer, resetCorner, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "etherwarp_corner_reset", x + 15, y, textRenderer.width(resetCorner), textRenderer.lineHeight);
        y += 20;

        return y;
    }

    private static int drawCustomHitSoundSettings(GuiGraphics context, Font textRenderer, CustomHitSoundModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Hit Sound: " + module.getSelectedHitSoundName(), x + 15, y, SKEET_TEXT, false);
        int hitControlX = x + 15 + textRenderer.width("    Hit Sound: " + module.getSelectedHitSoundName()) + 4;
        String hitPrev = "[Prev]";
        String hitNext = "[Next]";
        context.drawString(textRenderer, hitPrev, hitControlX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "hitsound_hit_prev", hitControlX, y, textRenderer.width(hitPrev), textRenderer.lineHeight);
        context.drawString(textRenderer, hitNext, hitControlX + textRenderer.width(hitPrev) + 4, y, SKEET_TEXT_DIM, false);
        registerButton(module, "hitsound_hit_next", hitControlX + textRenderer.width(hitPrev) + 4, y, textRenderer.width(hitNext), textRenderer.lineHeight);
        y += 15;

        if (module.isCustomEtherwarpEnabled()) {
            context.drawString(textRenderer, "    Etherwarp Sound: " + module.getSelectedEtherwarpSoundName(), x + 15, y, SKEET_TEXT, false);
            int etherControlX = x + 15 + textRenderer.width("    Etherwarp Sound: " + module.getSelectedEtherwarpSoundName()) + 4;
            String etherPrev = "[Prev]";
            String etherNext = "[Next]";
            context.drawString(textRenderer, etherPrev, etherControlX, y, SKEET_TEXT_DIM, false);
            registerButton(module, "hitsound_ether_prev", etherControlX, y, textRenderer.width(etherPrev), textRenderer.lineHeight);
            context.drawString(textRenderer, etherNext, etherControlX + textRenderer.width(etherPrev) + 4, y, SKEET_TEXT_DIM, false);
            registerButton(module, "hitsound_ether_next", etherControlX + textRenderer.width(etherPrev) + 4, y, textRenderer.width(etherNext), textRenderer.lineHeight);
            y += 15;
        }

        String randomPitchStatus = module.isRandomPitch() ? "§aON" : "§cOFF";
        String randomPitchText = "    Random Pitch: " + randomPitchStatus + " ";
        context.drawString(textRenderer, randomPitchText, x + 15, y, SKEET_TEXT, false);
        String randomPitchToggle = "[Toggle]";
        int randomToggleX = x + 15 + textRenderer.width(randomPitchText);
        context.drawString(textRenderer, randomPitchToggle, randomToggleX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "hitsound_toggle_random_pitch", randomToggleX, y, textRenderer.width(randomPitchToggle), textRenderer.lineHeight);
        y += 15;

        String etherStatus = module.isCustomEtherwarpEnabled() ? "§aON" : "§cOFF";
        String etherText = "    Etherwarp Sound: " + etherStatus + " ";
        context.drawString(textRenderer, etherText, x + 15, y, SKEET_TEXT, false);
        String etherToggle = "[Toggle]";
        int etherToggleX = x + 15 + textRenderer.width(etherText);
        context.drawString(textRenderer, etherToggle, etherToggleX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "hitsound_toggle_ether_sound", etherToggleX, y, textRenderer.width(etherToggle), textRenderer.lineHeight);
        y += 15;

        if (module.isCustomEtherwarpEnabled()) {
            String etherRandom = module.isEtherwarpRandomPitch() ? "§aON" : "§cOFF";
            String etherRandomText = "    Etherwarp Random Pitch: " + etherRandom + " ";
            context.drawString(textRenderer, etherRandomText, x + 15, y, SKEET_TEXT, false);
            String etherRandomToggle = "[Toggle]";
            int etherRandomToggleX = x + 15 + textRenderer.width(etherRandomText);
            context.drawString(textRenderer, etherRandomToggle, etherRandomToggleX, y, SKEET_TEXT_DIM, false);
            registerButton(module, "hitsound_toggle_ether_random", etherRandomToggleX, y, textRenderer.width(etherRandomToggle), textRenderer.lineHeight);
            y += 15;
        }

        context.drawString(textRenderer, "    Volume: " + (int) (module.getVolume() * 100) + "%", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "hitsound_volume", x + 15, y, width - 40, module.getVolume(), 0.0f, 1.0f);
        y += 15;

        if (module.isCustomEtherwarpEnabled()) {
            context.drawString(textRenderer, "    Etherwarp Volume: " + (int) (module.getEtherwarpVolume() * 100) + "%", x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "hitsound_ether_volume", x + 15, y, width - 40, module.getEtherwarpVolume(), 0.0f, 1.0f);
            y += 15;
        }

        int soundCount = module.getHitSoundFiles().size();
        String countText = "    Available Sounds: " + soundCount + " ";
        context.drawString(textRenderer, countText, x + 15, y, SKEET_TEXT_DIM, false);
        String reload = "[Reload]";
        int reloadX = x + 15 + textRenderer.width(countText);
        context.drawString(textRenderer, reload, reloadX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "hitsound_reload", reloadX, y, textRenderer.width(reload), textRenderer.lineHeight);
        y += 20;

        return y;
    }

    private static int drawPlayerSizeSpinSettings(GuiGraphics context, Font textRenderer, PlayerSizeSpinModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Local Player Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Scale X
        context.drawString(textRenderer, "    Scale X: " + String.format("%.2f", module.getScaleX()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "playersize_scale_x", x + 15, y, width - 40, module.getScaleX(), 0.1f, 5.0f);
        y += 15;

        // Scale Y
        context.drawString(textRenderer, "    Scale Y: " + String.format("%.2f", module.getScaleY()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "playersize_scale_y", x + 15, y, width - 40, module.getScaleY(), 0.1f, 5.0f);
        y += 15;

        // Scale Z
        context.drawString(textRenderer, "    Scale Z: " + String.format("%.2f", module.getScaleZ()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "playersize_scale_z", x + 15, y, width - 40, module.getScaleZ(), 0.1f, 5.0f);
        y += 15;

        // Spin Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Spin", module.isSpinEnabled(), "playersize_toggle_spin");

        // Spin Speed (only if spin enabled)
        if (module.isSpinEnabled()) {
            context.drawString(textRenderer, "    Spin Speed: " + String.format("%.2f", module.getSpinSpeed()), x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "playersize_spin_speed", x + 15, y, width - 40, module.getSpinSpeed(), 0.1f, 10.0f);
            y += 15;
        }

        // Invert Spin
        y = drawToggleRow(context, textRenderer, module, x, y, "Invert Spin", module.isInvertSpin(), "playersize_toggle_invert");

        // Upside Down
        y = drawToggleRow(context, textRenderer, module, x, y, "Upside Down", module.isUpsideDown(), "playersize_toggle_upside");

        y += 5;
        context.drawString(textRenderer, "  Server Models:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        int activeModels = module.getPlayerModels().size();
        context.drawString(textRenderer, "    Active: " + activeModels, x + 15, y, SKEET_TEXT, false);
        y += 20;

        return y;
    }

    private static int drawF7SimSettings(GuiGraphics context, Font textRenderer, F7SimModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Core Simulation:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        y = drawToggleRow(context, textRenderer, module, x, y, "Speed Simulation", module.isSpeedSimulationEnabled(), "f7sim_toggle_speed");
        if (module.isSpeedSimulationEnabled()) {
            context.drawString(textRenderer, "    Player Speed: " + module.getPlayerSpeed(), x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "f7sim_speed", x + 15, y, width - 40, module.getPlayerSpeed(), 100f, 500f);
            y += 15;
        }

        y = drawToggleRow(context, textRenderer, module, x, y, "Lava Bounce", module.isLavaBounceEnabled(), "f7sim_toggle_lava");

        y += 5;
        context.drawString(textRenderer, "  Device Simulator:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        y = drawToggleRow(context, textRenderer, module, x, y, "Enable Device Simulator", module.isDeviceSimEnabled(), "f7sim_toggle_device");

        y += 5;
        context.drawString(textRenderer, "  Etherwarp Simulation:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        y = drawToggleRow(context, textRenderer, module, x, y, "Diamond Shovel Warp", module.isEtherwarpSimulationEnabled(), "f7sim_toggle_etherwarp");

        y += 5;
        context.drawString(textRenderer, "  Bonzo Staff Simulator:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        y = drawToggleRow(context, textRenderer, module, x, y, "Enable Bonzo Simulator", module.isBonzoSimulationEnabled(), "f7sim_toggle_bonzo");
        if (module.isBonzoSimulationEnabled()) {
            context.drawString(textRenderer, "    Ping (ms): " + module.getBonzoPingMs(), x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "f7sim_bonzo_ping", x + 15, y, width - 40, module.getBonzoPingMs(), 0f, 200f);
            y += 15;

            context.drawString(textRenderer, "    Extra Delay (ms): " + module.getBonzoExtraDelayMs(), x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "f7sim_bonzo_delay", x + 15, y, width - 40, module.getBonzoExtraDelayMs(), 0f, 200f);
            y += 15;
        }

        y += 5;
        context.drawString(textRenderer, "  Bow Simulator:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        y = drawToggleRow(context, textRenderer, module, x, y, "Terminator Mode (3 Arrows)", module.isTerminatorMode(), "f7sim_toggle_terminator");

        y += 5;
        context.drawString(textRenderer, "  Terminal Simulator:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Ping Simulation: " + module.getTerminalPingMs() + "ms", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "f7sim_terminal_ping", x + 15, y, width - 40, module.getTerminalPingMs(), 0f, 250f);
        y += 15;

        y += 5;
        context.drawString(textRenderer, "  Block Respawn:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        y = drawToggleRow(context, textRenderer, module, x, y, "Enable Block Respawn", module.isBlockRespawnEnabled(), "f7sim_toggle_blockrespawn");
        if (module.isBlockRespawnEnabled()) {
            context.drawString(textRenderer, "    Respawn Delay: " + module.getBlockRespawnDelayMs() + "ms", x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "f7sim_block_delay", x + 15, y, width - 40, module.getBlockRespawnDelayMs(), 100f, 10000f);
            y += 15;
        }

        y += 5;
        return y;
    }

    private static int drawTerminalSolverSettings(GuiGraphics context, Font textRenderer, TerminalSolverModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Custom Terminal GUI:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        int statusColor = module.renderType ? SKEET_SUCCESS : SKEET_ERROR;
        String status = module.renderType ? "ENABLED (NanoVG)" : "DISABLED (Inventory Overlay)";
        context.drawString(textRenderer, "    Status: " + status, x + 15, y, statusColor, false);
        y += 12;

        String toggleLabel = module.renderType ? "[Use Default Overlay]" : "[Enable NanoVG GUI]";
        context.drawString(textRenderer, toggleLabel, x + 15, y, SKEET_ACCENT, false);
        registerButton(module, "terminalsolver_toggle_custom_gui", x + 15, y, textRenderer.width(toggleLabel), textRenderer.lineHeight);
        y += 18;

        context.drawString(textRenderer, "    - Runs entirely clientside", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    - Requires NVGRenderer to be working", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    - Disable if you prefer vanilla slots", x + 15, y, SKEET_TEXT_DIM, false);
        y += 18;

        context.drawString(textRenderer, "  Behaviour:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        y = drawToggleRow(context, textRenderer, module, x, y, "Hide Clicked Slots", module.hideClicked, "terminalsolver_toggle_hideclicked");
        y = drawToggleRow(context, textRenderer, module, x, y, "Show Numbers", module.showNumbers, "terminalsolver_toggle_shownumbers");
        y = drawToggleRow(context, textRenderer, module, x, y, "Smooth Animations", module.customAnimations, "terminalsolver_toggle_customanimations");
        y = drawToggleRow(context, textRenderer, module, x, y, "Disable Tooltips", module.cancelToolTip, "terminalsolver_toggle_tooltips");

        y += 10;
        context.drawString(textRenderer, "  Layout:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, String.format("    Scale: %.2fx", module.customTermSize), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "terminalsolver_size", x + 15, y, width - 40, module.customTermSize, 0.75f, 1.5f);
        y += 15;

        context.drawString(textRenderer, String.format("    Gap: %.1fpx", module.gap), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "terminalsolver_gap", x + 15, y, width - 40, module.gap, 0f, 12f);
        y += 15;

        context.drawString(textRenderer, String.format("    Corner Radius: %.1fpx", module.roundness), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "terminalsolver_roundness", x + 15, y, width - 40, module.roundness, 0f, 20f);
        y += 20;

        return y;
    }

    private static int drawNameProtectSettings(GuiGraphics context, Font textRenderer, NameProtectModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        int replacementCount = module.getRemoteReplacements().size();
        context.drawString(textRenderer, "    Remote Replacements: " + replacementCount, x + 15, y, SKEET_TEXT, false);
        y += 15;

        String selfReplacement = module.getSelfNameReplacement();
        context.drawString(textRenderer, "    Your Name: " + (selfReplacement != null ? selfReplacement : "&aYou"), x + 15, y, SKEET_TEXT, false);
        y += 20;

        return y;
    }

    private static int drawLeftClickEtherwarpSettings(GuiGraphics context, Font textRenderer, LeftClickEtherwarpModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Sneak Delay Slider
        context.drawString(textRenderer, "    Sneak Delay: " + module.getSneakDelay() + "ms", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "leftclick_sneak_delay", x + 15, y, width - 40, module.getSneakDelay(), 10, 200);
        y += 15;

        // Processing Time Slider
        context.drawString(textRenderer, "    Processing Time: " + module.getProcessingTime() + "ms", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "leftclick_processing_time", x + 15, y, width - 40, module.getProcessingTime(), 20, 500);
        y += 15;

        // Adaptive Ping Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Adaptive Ping", module.isAdaptivePing(), "leftclick_toggle_adaptive_ping");

        y += 10;

        return y;
    }

    private static int drawAutoSSSettings(GuiGraphics context, Font textRenderer, AutoSSModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Delay Slider
        context.drawString(textRenderer, "    Click Delay: " + module.getDelay() + "ms", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "autoss_delay", x + 15, y, width - 40, module.getDelay(), 50, 500);
        y += 15;

        // Auto Start Delay Slider
        context.drawString(textRenderer, "    Autostart Delay: " + module.getAutoStartDelay() + "ms", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "autoss_autostart", x + 15, y, width - 40, module.getAutoStartDelay(), 50, 200);
        y += 15;

        // Force Device Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Force Device", module.isForceDevice(), "autoss_toggle_force");

        // Smooth Rotate Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Smooth Rotate", module.isSmoothRotate(), "autoss_toggle_rotate");

        // Rotation Speed Slider (only if smooth rotate enabled)
        if (module.isSmoothRotate()) {
            context.drawString(textRenderer, "    Rotation Speed: " + module.getRotationSpeed() + "ms", x + 15, y, SKEET_TEXT, false);
            y += 12;
            drawSlider(context, module, "autoss_rotation_speed", x + 15, y, width - 40, module.getRotationSpeed(), 0, 500);
            y += 15;
        }

        // Dont Check Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Faster SS (Skip Check)", module.isDontCheck(), "autoss_toggle_dontcheck");

        // Spawn Client Buttons Toggle (Hypixel fix)
        y = drawToggleRow(context, textRenderer, module, x, y, "Client-Side Buttons", module.isSpawnClientButtons(), "autoss_toggle_clientbuttons");

        // Reset button
        String resetText = "  [Reset SS]";
        context.drawString(textRenderer, resetText, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "autoss_reset", x + 15, y, textRenderer.width(resetText), textRenderer.lineHeight);
        y += 20;

        return y;
    }

    private static int drawAlignAuraSettings(GuiGraphics context, Font textRenderer, AlignAuraModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        y = drawToggleRow(context, textRenderer, module, x, y, "Legit Mode", module.isLegitMode(), "alignaura_toggle_legit");
        y += 5;

        context.drawString(textRenderer, "  Info:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Auto-aligns arrow frames", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    in Device secret puzzles", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    (9 solution patterns)", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        context.drawString(textRenderer, "  Status:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Click nearest frame first", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    P3 optimization: AUTO", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    Packet-based (SAFE)", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        return y;
    }

    private static int drawBonzoStaffHelperSettings(GuiGraphics context, Font textRenderer, BonzoStaffHelperModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Explosion Delay Slider
        context.drawString(textRenderer, "    Explosion Delay: " + module.getExplosionDelay() + " ticks", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "bonzostaff_explosion_delay", x + 15, y, width - 40, module.getExplosionDelay(), 1, 20);
        y += 15;

        // S-Tap Duration Slider
        context.drawString(textRenderer, "    S-Tap Duration: " + module.getSTapDuration() + " ticks", x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "bonzostaff_stap_duration", x + 15, y, width - 40, module.getSTapDuration(), 1, 10);
        y += 15;

        // Adaptive Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Adaptive Timing", module.isAdaptiveTiming(), "bonzostaff_toggle_adaptive");

        // EXPERIMENTAL MODE Toggle (RED WARNING)
        String expWarning = "⚠ EXPERIMENTAL (Velocity→0)";
        context.drawString(textRenderer, "    " + expWarning, x + 15, y, 0xFFFF5555, false);
        String expStatus = module.isExperimentalMode() ? "§cON" : "§7OFF";
        int expToggleX = x + width - 70;
        context.drawString(textRenderer, expStatus, expToggleX, y, module.isExperimentalMode() ? 0xFFFF0000 : SKEET_TEXT_DIM, false);
        String expToggle = "[Toggle]";
        int expBtnX = expToggleX + textRenderer.width(expStatus) + 4;
        context.drawString(textRenderer, expToggle, expBtnX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "bonzostaff_toggle_experimental", expBtnX, y, textRenderer.width(expToggle), textRenderer.lineHeight);
        y += 20;

        // Stats Display
        context.drawString(textRenderer, "  Statistics:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        context.drawString(textRenderer, "    " + module.getStatsString(), x + 15, y, SKEET_TEXT_DIM, false);
        y += 15;

        // Reset Stats Button
        String resetStats = "[Reset Stats]";
        context.drawString(textRenderer, resetStats, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "bonzostaff_reset_stats", x + 15, y, textRenderer.width(resetStats), textRenderer.lineHeight);
        y += 20;

        // Info text
        context.drawString(textRenderer, "    Check logs for detailed", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    velocity & timing analysis", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        return y;
    }

    private static int drawDungeonBreakerHelperSettings(GuiGraphics context, Font textRenderer, DungeonBreakerHelperModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        String modeText = "    Render Mode: " + module.getRenderMode().name();
        context.drawString(textRenderer, modeText, x + 15, y, SKEET_TEXT, false);
        int modeX = x + 15 + textRenderer.width(modeText) + 2;
        String prevLabel = "[Prev]";
        String nextLabel = "[Next]";
        context.drawString(textRenderer, prevLabel, modeX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "dungeonbreaker_mode_prev", modeX, y, textRenderer.width(prevLabel), textRenderer.lineHeight);
        int nextX = modeX + textRenderer.width(prevLabel) + 4;
        context.drawString(textRenderer, nextLabel, nextX, y, SKEET_TEXT_DIM, false);
        registerButton(module, "dungeonbreaker_mode_next", nextX, y, textRenderer.width(nextLabel), textRenderer.lineHeight);
        y += 20;

        // Can Break Color Picker (Green - has durability)
        float[] canBreakColor = module.getCanBreakColor();
        context.drawString(textRenderer, "  Can Break Color:", x + 10, y, SKEET_ACCENT, false);

        String hex = String.format("#%02X%02X%02X",
            (int)(canBreakColor[0] * 255),
            (int)(canBreakColor[1] * 255),
            (int)(canBreakColor[2] * 255));
        context.drawString(textRenderer, hex, x + width - textRenderer.width(hex) - 10, y, SKEET_TEXT, false);
        y += 15;

        String pickBtn = "[Pick Color]";
        context.drawString(textRenderer, pickBtn, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "dungeonbreaker_canbreak_picker", x + 15, y, textRenderer.width(pickBtn), textRenderer.lineHeight);

        // Color preview
        int previewSize = textRenderer.lineHeight;
        int previewX = x + 15 + textRenderer.width(pickBtn) + 6;
        int rgb = ((int)(canBreakColor[0] * 255) << 16) | ((int)(canBreakColor[1] * 255) << 8) | (int)(canBreakColor[2] * 255);
        context.fill(previewX, y, previewX + previewSize, y + previewSize, 0xFF000000 | rgb);
        context.fill(previewX, y, previewX + previewSize, y + 1, 0xFF000000);
        context.fill(previewX, y + previewSize - 1, previewX + previewSize, y + previewSize, 0xFF000000);
        context.fill(previewX, y, previewX + 1, y + previewSize, 0xFF000000);
        context.fill(previewX + previewSize - 1, y, previewX + previewSize, y + previewSize, 0xFF000000);
        y += 20;

        // Cannot Break Color Picker (Red - no durability)
        float[] cannotBreakColor = module.getCannotBreakColor();
        context.drawString(textRenderer, "  Cannot Break Color:", x + 10, y, SKEET_ACCENT, false);

        String hex2 = String.format("#%02X%02X%02X",
            (int)(cannotBreakColor[0] * 255),
            (int)(cannotBreakColor[1] * 255),
            (int)(cannotBreakColor[2] * 255));
        context.drawString(textRenderer, hex2, x + width - textRenderer.width(hex2) - 10, y, SKEET_TEXT, false);
        y += 15;

        String pickBtn2 = "[Pick Color]";
        context.drawString(textRenderer, pickBtn2, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "dungeonbreaker_cannotbreak_picker", x + 15, y, textRenderer.width(pickBtn2), textRenderer.lineHeight);

        int previewX2 = x + 15 + textRenderer.width(pickBtn2) + 6;
        int rgb2 = ((int)(cannotBreakColor[0] * 255) << 16) | ((int)(cannotBreakColor[1] * 255) << 8) | (int)(cannotBreakColor[2] * 255);
        context.fill(previewX2, y, previewX2 + previewSize, y + previewSize, 0xFF000000 | rgb2);
        context.fill(previewX2, y, previewX2 + previewSize, y + 1, 0xFF000000);
        context.fill(previewX2, y + previewSize - 1, previewX2 + previewSize, y + previewSize, 0xFF000000);
        context.fill(previewX2, y, previewX2 + 1, y + previewSize, 0xFF000000);
        context.fill(previewX2 + previewSize - 1, y, previewX2 + previewSize, y + previewSize, 0xFF000000);
        y += 25;

        context.drawString(textRenderer, "    Outline Width: " + String.format("%.1f", module.getOutlineWidth()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "dungeonbreaker_outline", x + 15, y, width - 40, module.getOutlineWidth(), 0.5f, 10.0f);
        y += 15;

        context.drawString(textRenderer, "    Fill Opacity: " + String.format("%.0f%%", module.getFillOpacity() * 100f), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "dungeonbreaker_fill", x + 15, y, width - 40, module.getFillOpacity(), 0.0f, 1.0f);
        y += 15;

        context.drawString(textRenderer, "    Corner Ratio: " + String.format("%.2f", module.getCornerRatio()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "dungeonbreaker_corner", x + 15, y, width - 40, module.getCornerRatio(), 0.05f, 0.9f);
        y += 15;

        String resetCorner = "[Reset Corner]";
        context.drawString(textRenderer, resetCorner, x + 15, y, SKEET_TEXT_DIM, false);
        registerButton(module, "dungeonbreaker_corner_reset", x + 15, y, textRenderer.width(resetCorner), textRenderer.lineHeight);
        y += 20;

        return y;
    }

 
    private static void drawSlider(GuiGraphics context, Module module, String sliderId, int x, int y, int width, float value, float min, float max) {
        int sliderHeight = 4;
        context.fill(x, y, x + width, y + sliderHeight, SKEET_TAB_BG);
        float normalized = Mth.clamp((value - min) / (max - min), 0.0f, 1.0f);
        int filledWidth = (int) (normalized * width);
        context.fill(x, y, x + filledWidth, y + sliderHeight, SKEET_ACCENT);
        registerSlider(module, sliderId, x, y - 3, width, sliderHeight + 6, min, max);
    }


    private static int drawKaomojiReplacerSettings(GuiGraphics context, Font textRenderer, KaomojiReplacerModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Info:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Replaces 'o/' with random", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    kaomojis in chat messages", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    30 kaomojis available", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        return y;
    }

    private static int drawMeowMessagesSettings(GuiGraphics context, Font textRenderer, MeowMessagesModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Info:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Transforms messages into", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    catspeak with meow sounds", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    and cute suffixes nya~", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        return y;
    }

    private static int drawDungeonOptimizerSettings(GuiGraphics context, Font textRenderer, DungeonOptimizerModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Interact Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        y = drawToggleRow(context, textRenderer, module, x, y, "Cancel Interact", module.isCancelInteractEnabled(), "dungeonopt_toggle_cancel");
        y = drawToggleRow(context, textRenderer, module, x, y, "Only Ability Items", module.isOnlyWithAbility(), "dungeonopt_toggle_ability");
        y = drawToggleRow(context, textRenderer, module, x, y, "No Break Reset", module.isNoBreakReset(), "dungeonopt_toggle_nobreak");

        y += 5;
        context.drawString(textRenderer, "  Placement Prevention:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        y = drawToggleRow(context, textRenderer, module, x, y, "Prevent Placing Weapons", module.isPreventPlacingWeapons(), "dungeonopt_toggle_weapons");
        y = drawToggleRow(context, textRenderer, module, x, y, "Prevent Placing Heads", module.isPreventPlacingHeads(), "dungeonopt_toggle_heads");

        y += 5;
        context.drawString(textRenderer, "  Visual:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        y = drawToggleRow(context, textRenderer, module, x, y, "Remove Damage Tag", module.isRemoveDamageTag(), "dungeonopt_toggle_damagetag");

        return y;
    }

    private static int drawChestAuraSettings(GuiGraphics context, Font textRenderer, ChestAuraModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  Settings:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Debug Messages Toggle
        y = drawToggleRow(context, textRenderer, module, x, y, "Debug Messages", module.isDebugMessages(), "chestaura_toggle_debug");

            context.drawString(textRenderer, "  Timing:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Max Placement Distance Slider
        context.drawString(textRenderer, "    Max Distance: " + String.format("%.1f", module.getMaxPlacementDistance()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "chestaura_max_distance", x + 15, y, width - 40, (float) module.getMaxPlacementDistance(), 2.0f, 10.0f);
        y += 15;

        // Pre-Place Ticks Slider


        // Min Fall Speed Slider
        context.drawString(textRenderer, "    Min Fall Speed: " + String.format("%.2f", module.getMinFallSpeed()), x + 15, y, SKEET_TEXT, false);
        y += 12;
        drawSlider(context, module, "chestaura_min_fall_speed", x + 15, y, width - 40, (float) module.getMinFallSpeed(), 0.0f, 0.5f);
        y += 15;

        y += 5;
        context.drawString(textRenderer, "  Tick-Based Delays:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Switch Delay (ticks)


        context.drawString(textRenderer, "  Info:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        context.drawString(textRenderer, "    Places chest for F7 lava", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    high bounces (Hold Shift)", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        return y;
    }

    private static int drawImageHUDSettings(GuiGraphics context, Font textRenderer, ImageHUDModule module, int x, int y, int width) {
        y += 10;
        context.drawString(textRenderer, "  HUD Images:", x + 10, y, SKEET_ACCENT, false);
        y += 15;

        // Edit Mode Button
        dev.hunchclient.hud.HudEditorManager hudManager = dev.hunchclient.hud.HudEditorManager.getInstance();
        String editModeBtn = hudManager.isEditMode() ? "§a[Exit Edit Mode]" : "[Open Edit Mode]";
        context.drawString(textRenderer, editModeBtn, x + 15, y, hudManager.isEditMode() ? 0xFF00FF00 : SKEET_TEXT_DIM, false);
        registerButton(module, "imagehud_toggle_editmode", x + 15, y, textRenderer.width(editModeBtn), textRenderer.lineHeight);
        y += 20;

        // Image List
        var images = module.getImages();
        if (images.isEmpty()) {
            context.drawString(textRenderer, "    No images added yet", x + 15, y, SKEET_TEXT_DIM, false);
            y += 15;
        } else {
            for (int i = 0; i < images.size(); i++) {
                var image = images.get(i);

                // Image header with enable toggle
                String enableStatus = image.isEnabled() ? "§aON" : "§cOFF";
                String header = "    Image " + (i + 1) + ": " + enableStatus + " ";
                context.drawString(textRenderer, header, x + 15, y, SKEET_TEXT, false);

                String toggleBtn = "[Toggle]";
                int toggleX = x + 15 + textRenderer.width(header);
                context.drawString(textRenderer, toggleBtn, toggleX, y, SKEET_TEXT_DIM, false);
                registerButton(module, "imagehud_toggle_" + i, toggleX, y, textRenderer.width(toggleBtn), textRenderer.lineHeight);
                y += 15;

                // Source (truncated if too long)
                String source = image.getSource();
                if (source.length() > 35) {
                    source = source.substring(0, 32) + "...";
                }
                context.drawString(textRenderer, "      " + source, x + 15, y, SKEET_TEXT_DIM, false);
                y += 12;

                // Speed multiplier for GIFs
                if (image.getSource().toLowerCase().endsWith(".gif")) {
                    context.drawString(textRenderer, "      Speed: " + String.format("%.1fx", image.getSpeedMultiplier()), x + 15, y, SKEET_TEXT, false);
                    y += 12;
                    drawSlider(context, module, "imagehud_speed_" + i, x + 20, y, width - 50, image.getSpeedMultiplier(), 0.1f, 5.0f);
                    y += 15;
                }

                // Buttons: Remove, Refresh (for URLs)
                String removeBtn = "[Remove]";
                context.drawString(textRenderer, removeBtn, x + 20, y, 0xFFFF5555, false);
                registerButton(module, "imagehud_remove_" + i, x + 20, y, textRenderer.width(removeBtn), textRenderer.lineHeight);

                if (image.getSource().startsWith("http://") || image.getSource().startsWith("https://")) {
                    String refreshBtn = "[Refresh]";
                    int refreshX = x + 20 + textRenderer.width(removeBtn) + 6;
                    context.drawString(textRenderer, refreshBtn, refreshX, y, SKEET_TEXT_DIM, false);
                    registerButton(module, "imagehud_refresh_" + i, refreshX, y, textRenderer.width(refreshBtn), textRenderer.lineHeight);
                }

                y += 20;
            }
        }

        // Add Image Button
        String addBtn = "[Add Image]";
        context.drawString(textRenderer, addBtn, x + 15, y, SKEET_ACCENT, false);
        registerButton(module, "imagehud_add", x + 15, y, textRenderer.width(addBtn), textRenderer.lineHeight);

        // Clear All Button
        if (!images.isEmpty()) {
            String clearBtn = "[Clear All]";
            int clearX = x + 15 + textRenderer.width(addBtn) + 8;
            context.drawString(textRenderer, clearBtn, clearX, y, 0xFFFF5555, false);
            registerButton(module, "imagehud_clear", clearX, y, textRenderer.width(clearBtn), textRenderer.lineHeight);
        }
        y += 20;

        // Info
        context.drawString(textRenderer, "  Info:", x + 10, y, SKEET_ACCENT, false);
        y += 15;
        context.drawString(textRenderer, "    Supports PNG & GIF files", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    URLs or local files", x + 15, y, SKEET_TEXT_DIM, false);
        y += 12;
        context.drawString(textRenderer, "    Path: config/hunchclient/hud_images/", x + 15, y, SKEET_TEXT_DIM, false);
        y += 20;

        return y;
    }

    private static int drawGenericSettings(GuiGraphics context, Font textRenderer, Module module, SettingsProvider settingsProvider, int x, int y, int width) {
        return GenericSettingsRenderer.drawGenericSettings(context, textRenderer, module, settingsProvider, x, y, width);
    }
}
