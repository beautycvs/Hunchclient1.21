package dev.hunchclient.gui.skeet.tabs;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.gui.skeet.components.SkeetCheckbox;
import dev.hunchclient.gui.skeet.components.SkeetSlider;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Dungeon Map settings tab for SkeetScreen GUI
 * Contains all configuration options for the dungeon map
 */
public class DungeonMapTab extends SkeetTab {

    private final Minecraft mc = Minecraft.getInstance();

    // GUI Components
    private SkeetCheckbox mapEnabledBox;
    private SkeetCheckbox cheaterModeBox;
    private SkeetCheckbox hideInBossBox;
    private SkeetCheckbox boxWitherDoorsBox;
    private SkeetCheckbox extraInfoBox;
    private SkeetCheckbox limitRoomNameSizeBox;
    private SkeetCheckbox hideQuestionMarksBox;

    private SkeetSlider mapScaleSlider;
    private SkeetSlider checkmarkSizeSlider;
    private SkeetSlider textScaleSlider;
    private SkeetSlider borderWidthSlider;
    private SkeetSlider witherDoorFillSlider;
    private SkeetSlider witherDoorOutlineSlider;

    // Settings values (local cache)
    private boolean mapEnabled = true;
    private boolean cheaterMode = false;
    private boolean hideInBoss = false;
    private boolean boxWitherDoors = true;
    private boolean extraInfo = true;
    private boolean limitRoomNameSize = true;
    private boolean hideQuestionMarks = false;

    private float mapScale = 1.0f;
    private float checkmarkSize = 1.0f;
    private float textScale = 1.0f;
    private float borderWidth = 2.0f;
    private int witherDoorFill = 128;
    private float witherDoorOutlineWidth = 2.0f;

    private int roomNamesMode = 1;
    private int checkmarkStyle = 1;
    private int backgroundStyle = 0;

    // Scroll offset
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public DungeonMapTab() {
        super("Map", "🗺");
        loadSettings();
        initComponents();
    }

    private void loadSettings() {
        try {
            // Access DungeonMapConfig through reflection to avoid compile-time dependency
            Class<?> configClass = Class.forName("dev.hunchclient.features.dungeons.dmap.core.DungeonMapConfig");
            Object instance = configClass.getField("INSTANCE").get(null);

            mapEnabled = (boolean) configClass.getMethod("getMapEnabled").invoke(instance);
            cheaterMode = (boolean) configClass.getMethod("getDungeonMapCheater").invoke(instance);
            hideInBoss = (boolean) configClass.getMethod("getMapHideInBoss").invoke(instance);
            boxWitherDoors = (boolean) configClass.getMethod("getBoxWitherDoors").invoke(instance);
            extraInfo = (boolean) configClass.getMethod("getMapExtraInfo").invoke(instance);
            limitRoomNameSize = (boolean) configClass.getMethod("getLimitRoomNameSize").invoke(instance);
            hideQuestionMarks = (boolean) configClass.getMethod("getHideQuestionCheckmarks").invoke(instance);

            mapScale = (float) configClass.getMethod("getMapScale").invoke(instance);
            checkmarkSize = (float) configClass.getMethod("getCheckmarkSize").invoke(instance);
            textScale = (float) configClass.getMethod("getTextScale").invoke(instance);
            borderWidth = (float) configClass.getMethod("getMapBorderWidth").invoke(instance);
            witherDoorFill = (int) configClass.getMethod("getWitherDoorFill").invoke(instance);
            witherDoorOutlineWidth = (float) configClass.getMethod("getWitherDoorOutlineWidth").invoke(instance);

            roomNamesMode = (int) configClass.getMethod("getMapRoomNames").invoke(instance);
            checkmarkStyle = (int) configClass.getMethod("getDungeonMapCheckmarkStyle").invoke(instance);
            backgroundStyle = (int) configClass.getMethod("getMapBackgroundStyle").invoke(instance);
        } catch (Exception e) {
            System.err.println("Failed to load DungeonMapConfig settings: " + e.getMessage());
        }
    }

    private void updateSetting(String setterName, Object value) {
        try {
            Class<?> configClass = Class.forName("dev.hunchclient.features.dungeons.dmap.core.DungeonMapConfig");
            Object instance = configClass.getField("INSTANCE").get(null);

            // Find the setter method
            for (java.lang.reflect.Method method : configClass.getMethods()) {
                if (method.getName().equals(setterName)) {
                    method.invoke(instance, value);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update setting " + setterName + ": " + e.getMessage());
        }
    }

    private void initComponents() {
        // Create all checkboxes
        mapEnabledBox = new SkeetCheckbox("Map Enabled", mapEnabled);
        mapEnabledBox.setOnChange(value -> {
            mapEnabled = value;
            updateSetting("setMapEnabled", value);
        });

        cheaterModeBox = new SkeetCheckbox("Cheater Mode", cheaterMode);
        cheaterModeBox.setOnChange(value -> {
            cheaterMode = value;
            updateSetting("setDungeonMapCheater", value);
        });

        hideInBossBox = new SkeetCheckbox("Hide in Boss", hideInBoss);
        hideInBossBox.setOnChange(value -> {
            hideInBoss = value;
            updateSetting("setMapHideInBoss", value);
        });

        boxWitherDoorsBox = new SkeetCheckbox("Box Wither Doors", boxWitherDoors);
        boxWitherDoorsBox.setOnChange(value -> {
            boxWitherDoors = value;
            updateSetting("setBoxWitherDoors", value);
        });

        extraInfoBox = new SkeetCheckbox("Extra Info", extraInfo);
        extraInfoBox.setOnChange(value -> {
            extraInfo = value;
            updateSetting("setMapExtraInfo", value);
        });

        limitRoomNameSizeBox = new SkeetCheckbox("Limit Room Name Size", limitRoomNameSize);
        limitRoomNameSizeBox.setOnChange(value -> {
            limitRoomNameSize = value;
            updateSetting("setLimitRoomNameSize", value);
        });

        hideQuestionMarksBox = new SkeetCheckbox("Hide Question Marks", hideQuestionMarks);
        hideQuestionMarksBox.setOnChange(value -> {
            hideQuestionMarks = value;
            updateSetting("setHideQuestionCheckmarks", value);
        });

        // Create all sliders (correct constructor: x, y, width, label, value, min, max, onChange)
        mapScaleSlider = new SkeetSlider(0, 0, 200, "Map Scale", mapScale, 0.5f, 4.0f, value -> {
            mapScale = value;
            updateSetting("setMapScale", value);
        });

        checkmarkSizeSlider = new SkeetSlider(0, 0, 200, "Checkmark Size", checkmarkSize, 0.5f, 2.0f, value -> {
            checkmarkSize = value;
            updateSetting("setCheckmarkSize", value);
        });

        textScaleSlider = new SkeetSlider(0, 0, 200, "Text Scale", textScale, 0.5f, 2.0f, value -> {
            textScale = value;
            updateSetting("setTextScale", value);
        });

        borderWidthSlider = new SkeetSlider(0, 0, 200, "Border Width", borderWidth, 1f, 5f, value -> {
            borderWidth = value;
            updateSetting("setMapBorderWidth", value);
        });

        witherDoorFillSlider = new SkeetSlider(0, 0, 200, "Wither Door Fill", (float)witherDoorFill, 0f, 255f, value -> {
            witherDoorFill = value.intValue();
            updateSetting("setWitherDoorFill", value.intValue());
        });

        witherDoorOutlineSlider = new SkeetSlider(0, 0, 200, "Wither Door Outline", witherDoorOutlineWidth, 1f, 5f, value -> {
            witherDoorOutlineWidth = value;
            updateSetting("setWitherDoorOutlineWidth", value);
        });
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(x, y, x + width, y + height, SkeetTheme.BG_SECONDARY);

        // Title
        String title = "Dungeon Map Settings";
        context.drawString(mc.font, title, x + 10, y + 10, SkeetTheme.TEXT_PRIMARY, false);

        // Calculate content area
        int contentY = y + 30 - scrollOffset;
        int currentY = contentY;
        int padding = 5;
        int componentHeight = 25;

        // Enable scissor for scrolling
        context.enableScissor(x, y + 30, x + width, y + height);

        // Section: General Settings
        drawSection(context, "General", x + 10, currentY, width - 20);
        currentY += 25;

        // Checkboxes
        mapEnabledBox.setBounds(x + 15, currentY, width - 30, 20);
        mapEnabledBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        cheaterModeBox.setBounds(x + 15, currentY, width - 30, 20);
        cheaterModeBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        hideInBossBox.setBounds(x + 15, currentY, width - 30, 20);
        hideInBossBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        boxWitherDoorsBox.setBounds(x + 15, currentY, width - 30, 20);
        boxWitherDoorsBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        extraInfoBox.setBounds(x + 15, currentY, width - 30, 20);
        extraInfoBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        currentY += padding;

        // Section: Display Settings
        drawSection(context, "Display", x + 10, currentY, width - 20);
        currentY += 25;

        // Room Names Mode
        String roomNamesText = "Room Names: " + getRoomNamesText();
        context.drawString(mc.font, roomNamesText, x + 15, currentY + 5, SkeetTheme.TEXT_SECONDARY, false);
        currentY += componentHeight;

        // Checkmark Style
        String checkmarkText = "Checkmarks: " + getCheckmarkStyleText();
        context.drawString(mc.font, checkmarkText, x + 15, currentY + 5, SkeetTheme.TEXT_SECONDARY, false);
        currentY += componentHeight;

        // Background Style
        String bgText = "Background: " + getBackgroundStyleText();
        context.drawString(mc.font, bgText, x + 15, currentY + 5, SkeetTheme.TEXT_SECONDARY, false);
        currentY += componentHeight;

        limitRoomNameSizeBox.setBounds(x + 15, currentY, width - 30, 20);
        limitRoomNameSizeBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        hideQuestionMarksBox.setBounds(x + 15, currentY, width - 30, 20);
        hideQuestionMarksBox.render(context, mouseX, mouseY, delta);
        currentY += componentHeight;

        currentY += padding;

        // Section: Scale Settings
        drawSection(context, "Scale & Size", x + 10, currentY, width - 20);
        currentY += 25;

        // Map Scale Slider
        mapScaleSlider.setPosition(x + 15, currentY);
        mapScaleSlider.setSize(width - 30, 35);
        mapScaleSlider.render(context, mouseX, mouseY, delta);
        currentY += 40;

        // Checkmark Size Slider
        checkmarkSizeSlider.setPosition(x + 15, currentY);
        checkmarkSizeSlider.setSize(width - 30, 35);
        checkmarkSizeSlider.render(context, mouseX, mouseY, delta);
        currentY += 40;

        // Text Scale Slider
        textScaleSlider.setPosition(x + 15, currentY);
        textScaleSlider.setSize(width - 30, 35);
        textScaleSlider.render(context, mouseX, mouseY, delta);
        currentY += 40;

        currentY += padding;

        // Section: Border Settings
        drawSection(context, "Borders & Doors", x + 10, currentY, width - 20);
        currentY += 25;

        // Border Width Slider
        borderWidthSlider.setPosition(x + 15, currentY);
        borderWidthSlider.setSize(width - 30, 35);
        borderWidthSlider.render(context, mouseX, mouseY, delta);
        currentY += 40;

        // Wither Door Fill Slider
        witherDoorFillSlider.setPosition(x + 15, currentY);
        witherDoorFillSlider.setSize(width - 30, 35);
        witherDoorFillSlider.render(context, mouseX, mouseY, delta);
        currentY += 40;

        // Wither Door Outline Slider
        witherDoorOutlineSlider.setPosition(x + 15, currentY);
        witherDoorOutlineSlider.setSize(width - 30, 35);
        witherDoorOutlineSlider.render(context, mouseX, mouseY, delta);
        currentY += 40;

        // Disable scissor
        context.disableScissor();

        // Calculate max scroll
        maxScroll = Math.max(0, currentY - (y + height) + 50);

        // Draw scrollbar if needed
        if (maxScroll > 0) {
            drawScrollbar(context);
        }
    }

    private void drawSection(GuiGraphics context, String title, int x, int y, int width) {
        // Section header
        context.drawString(mc.font, title, x, y, SkeetTheme.ACCENT_PRIMARY, false);

        // Separator line
        context.fill(x, y + 12, x + width, y + 13, SkeetTheme.BORDER_DEFAULT);
    }

    private void drawScrollbar(GuiGraphics context) {
        int scrollbarX = x + width - 5;
        int scrollbarHeight = height - 40;
        int thumbHeight = Math.max(20, (int)((float)height / (float)(maxScroll + height) * scrollbarHeight));
        int thumbY = y + 35 + (int)((float)scrollOffset / (float)maxScroll * (scrollbarHeight - thumbHeight));

        // Track
        context.fill(scrollbarX, y + 35, scrollbarX + 3, y + 35 + scrollbarHeight, SkeetTheme.BG_PRIMARY);

        // Thumb
        context.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, SkeetTheme.ACCENT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check all components
            mapEnabledBox.mouseClicked(mouseX, mouseY, button);
            cheaterModeBox.mouseClicked(mouseX, mouseY, button);
            hideInBossBox.mouseClicked(mouseX, mouseY, button);
            boxWitherDoorsBox.mouseClicked(mouseX, mouseY, button);
            extraInfoBox.mouseClicked(mouseX, mouseY, button);
            limitRoomNameSizeBox.mouseClicked(mouseX, mouseY, button);
            hideQuestionMarksBox.mouseClicked(mouseX, mouseY, button);

            mapScaleSlider.mouseClicked(mouseX, mouseY, button);
            checkmarkSizeSlider.mouseClicked(mouseX, mouseY, button);
            textScaleSlider.mouseClicked(mouseX, mouseY, button);
            borderWidthSlider.mouseClicked(mouseX, mouseY, button);
            witherDoorFillSlider.mouseClicked(mouseX, mouseY, button);
            witherDoorOutlineSlider.mouseClicked(mouseX, mouseY, button);

            // Handle room names cycling
            int roomNamesY = y + 30 - scrollOffset + 170;
            if (mouseX >= x + 15 && mouseX <= x + width - 15 &&
                mouseY >= roomNamesY && mouseY <= roomNamesY + 20) {
                roomNamesMode = (roomNamesMode + 1) % 4;
                updateSetting("setMapRoomNames", roomNamesMode);
                return true;
            }

            // Handle checkmark style cycling
            int checkmarkY = roomNamesY + 25;
            if (mouseX >= x + 15 && mouseX <= x + width - 15 &&
                mouseY >= checkmarkY && mouseY <= checkmarkY + 20) {
                checkmarkStyle = (checkmarkStyle + 1) % 4;
                updateSetting("setDungeonMapCheckmarkStyle", checkmarkStyle);
                return true;
            }

            // Handle background style cycling
            int bgY = checkmarkY + 25;
            if (mouseX >= x + 15 && mouseX <= x + width - 15 &&
                mouseY >= bgY && mouseY <= bgY + 20) {
                backgroundStyle = (backgroundStyle + 1) % 3;
                updateSetting("setMapBackgroundStyle", backgroundStyle);
                return true;
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mapScaleSlider.mouseReleased(mouseX, mouseY, button);
        checkmarkSizeSlider.mouseReleased(mouseX, mouseY, button);
        textScaleSlider.mouseReleased(mouseX, mouseY, button);
        borderWidthSlider.mouseReleased(mouseX, mouseY, button);
        witherDoorFillSlider.mouseReleased(mouseX, mouseY, button);
        witherDoorOutlineSlider.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        mapScaleSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        checkmarkSizeSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        textScaleSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        borderWidthSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        witherDoorFillSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        witherDoorOutlineSlider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check if mouse is within bounds
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            scrollOffset -= (int)(verticalAmount * 20);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }

    private String getRoomNamesText() {
        switch (roomNamesMode) {
            case 0: return "OFF";
            case 1: return "Puzzles Only";
            case 2: return "Special Rooms";
            case 3: return "All Rooms";
            default: return "Unknown";
        }
    }

    private String getCheckmarkStyleText() {
        switch (checkmarkStyle) {
            case 0: return "OFF";
            case 1: return "Default";
            case 2: return "Secrets";
            case 3: return "Room Names";
            default: return "Unknown";
        }
    }

    private String getBackgroundStyleText() {
        switch (backgroundStyle) {
            case 0: return "Default";
            case 1: return "Map Texture";
            case 2: return "None";
            default: return "Unknown";
        }
    }
}