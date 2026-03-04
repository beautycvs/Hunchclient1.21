package dev.hunchclient.gui.skeet.components;

import dev.hunchclient.gui.RiskyModuleConfirmScreen;
import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.Module;
import dev.hunchclient.util.GuiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Skeet-styled module card component
 * Displays a module with enable/disable state and expansion
 */
public class SkeetModuleCard extends SkeetComponent {

    private final Module module;
    private final Font textRenderer;
    private SkeetSettingsRenderer settingsRenderer;

    private boolean expanded = false;
    private float hoverAmount = 0.0f;
    private float expandAmount = 0.0f;
    private final float animSpeed = 0.15f;

    // Layout
    private static final int HEADER_HEIGHT = 35;
    private static final int PADDING = 10;

    public SkeetModuleCard(int x, int y, int width, Module module) {
        super(x, y, width, HEADER_HEIGHT);
        this.module = module;
        this.textRenderer = Minecraft.getInstance().font;

        // Load saved expanded state
        this.expanded = dev.hunchclient.util.GuiConfig.getInstance().isModuleExpanded(module.getName());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        // Update animations
        updateHover(mouseX, mouseY);
        if (hovered) {
            hoverAmount = Math.min(1.0f, hoverAmount + animSpeed);
        } else {
            hoverAmount = Math.max(0.0f, hoverAmount - animSpeed);
        }

        float targetExpand = expanded ? 1.0f : 0.0f;
        if (expandAmount < targetExpand) {
            expandAmount = Math.min(targetExpand, expandAmount + animSpeed);
        } else if (expandAmount > targetExpand) {
            expandAmount = Math.max(targetExpand, expandAmount - animSpeed);
        }

        // Background with hover effect
        int bgColor = SkeetTheme.blend(SkeetTheme.BG_SECONDARY(), SkeetTheme.BG_HOVER(), hoverAmount);
        context.fill(x, y, x + width, y + HEADER_HEIGHT, bgColor);

        // Left accent bar (shows enable state)
        int accentColor = module.isEnabled() ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.BORDER_DEFAULT();
        context.fill(x, y, x + 3, y + HEADER_HEIGHT, accentColor);

        // Expand icon
        String expandIcon = expanded ? "[-]" : "[+]";
        int iconColor = hoverAmount > 0.5f ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_SECONDARY();
        context.drawString(textRenderer, expandIcon, x + PADDING, y + 8, iconColor, false);

// Wrap lines in GUI in case text is too long
int nameX = x + PADDING + textRenderer.width(expandIcon) + 6;
int maxNameWidth = width - PADDING * 3 
        - textRenderer.width(expandIcon) 
        - textRenderer.width("OFF") - 20;

String moduleName = module.getName();
int nameColor = module.isEnabled() ? SkeetTheme.ACCENT_PRIMARY() : SkeetTheme.TEXT_PRIMARY();

// Split into rough chunks by spaces
String[] rough = moduleName.split(" ");
java.util.List<String> lines = new java.util.ArrayList<>();
StringBuilder currentLine = new StringBuilder();

// Build lines
for (String chunk : rough) {
    if (textRenderer.width(currentLine + chunk) <= maxNameWidth) {
        if (currentLine.length() > 0) currentLine.append(" ");
        currentLine.append(chunk);
    } else {
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        currentLine = new StringBuilder(chunk);
    }
}
if (currentLine.length() > 0) {
    lines.add(currentLine.toString());
}

// Decide scaling: check if *any* line is too wide
float scale = 1.0f;
for (String line : lines) {
    if (textRenderer.width(line) > maxNameWidth) {
        scale = 0.6f;
        break;
    }
}

int drawY = 0;
int lineHeight = textRenderer.lineHeight + 2;
for (String line : lines) {
    String drawLine = line;
    while (drawLine.length() > 3 && textRenderer.width(drawLine) > maxNameWidth) {
        drawLine = drawLine.substring(0, drawLine.length() - 1);
    }
    context.drawString(textRenderer, drawLine, nameX, y + 8 + drawY, nameColor, false);
    drawY += lineHeight;
}

        // Warning icon if risky (RISKY or VERY_RISKY)
        if (module.isRisky()) {
            String warning = "\u26a0";
            int warnX = nameX + textRenderer.width(module.getName()) + 4;
            context.drawString(textRenderer, warning, warnX, y + 8, SkeetTheme.STATUS_WARNING(), false);
        }

        // Status badge (right side)
        String status = module.isEnabled() ? "ON" : "OFF";
        int statusColor = module.isEnabled() ? SkeetTheme.STATUS_SUCCESS() : SkeetTheme.STATUS_ERROR();
        int statusX = x + width - textRenderer.width(status) - PADDING;
        context.drawString(textRenderer, status, statusX, y + 8, statusColor, false);

        // Description (smaller text)
        context.drawString(textRenderer, module.getDescription(), x + PADDING + 5, y + 22, SkeetTheme.TEXT_DIM(), false);

        // Bottom border
        context.fill(x, y + HEADER_HEIGHT - 1, x + width, y + HEADER_HEIGHT, SkeetTheme.BORDER_DEFAULT());

        // Expanded settings area (if expanded)
        if (expandAmount > 0.01f) {
            // Lazy initialize settings renderer
            if (settingsRenderer == null) {
                settingsRenderer = new SkeetSettingsRenderer(module);
            }

            int settingsY = y + HEADER_HEIGHT;
            int settingsHeight = settingsRenderer.getContentHeight();

            // Fade in settings background
            int settingsBg = SkeetTheme.withAlpha(SkeetTheme.BG_PRIMARY(), expandAmount);
            context.fill(x, settingsY, x + width, settingsY + (int) (settingsHeight * expandAmount), settingsBg);

            // Render settings with clipping
            if (expandAmount > 0.1f) {
                context.enableScissor(x, settingsY, x + width, settingsY + (int) (settingsHeight * expandAmount));
                settingsRenderer.render(context, x, settingsY, width, mouseX, mouseY, delta);
                context.disableScissor();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled) return false;

        // Check if clicking header
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
            if (button == 0) {
                // Left click - toggle module
                if (!module.isEnabled() && module.requiresConfirmation()
                        && !GuiConfig.getInstance().isModuleAcknowledged(module.getName())) {
                    // VERY_RISKY module - show confirmation screen
                    Minecraft client = Minecraft.getInstance();
                    if (client != null && client.screen != null) {
                        client.setScreen(new RiskyModuleConfirmScreen(
                            client.screen,
                            module,
                            confirmed -> {
                                if (confirmed) {
                                    module.setEnabled(true);
                                }
                            }
                        ));
                    }
                } else {
                    // Normal toggle (SAFE, RISKY, or already acknowledged)
                    module.toggle();
                }
                return true;
            } else if (button == 1) {
                // Right click - expand/collapse settings
                expanded = !expanded;

                // Save expanded state
                dev.hunchclient.util.GuiConfig.getInstance().setModuleExpanded(module.getName(), expanded);

                // Clear settings renderer on collapse to reinitialize next time
                if (!expanded) {
                    settingsRenderer = null;
                }
                return true;
            }
        }

        // Forward to settings if expanded
        if (expanded && settingsRenderer != null) {
            int settingsY = y + HEADER_HEIGHT;
            if (mouseY >= settingsY) {
                return settingsRenderer.mouseClicked(mouseX, mouseY, button);
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded && settingsRenderer != null) {
            return settingsRenderer.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (expanded && settingsRenderer != null) {
            return settingsRenderer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    /**
     * Handle character typing (forwarded from parent)
     */
    public boolean charTyped(char chr, int modifiers) {
        if (expanded && settingsRenderer != null) {
            return settingsRenderer.charTyped(chr, modifiers);
        }
        return false;
    }

    /**
     * Handle key press (forwarded from parent)
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expanded && settingsRenderer != null) {
            return settingsRenderer.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public Module getModule() {
        return module;
    }

    /**
     * Get total card height (including expanded settings)
     */
    public int getTotalHeight() {
        int totalHeight = HEADER_HEIGHT;
        if (expanded && settingsRenderer != null) {
            totalHeight += (int) (settingsRenderer.getContentHeight() * expandAmount);
        }
        return totalHeight;
    }
}
