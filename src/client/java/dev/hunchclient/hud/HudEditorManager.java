package dev.hunchclient.hud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Central manager for all HUD elements
 * Handles rendering, editing, and persistence
 */
public class HudEditorManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HunchClient-HUD");
    private static final Gson GSON = new Gson();
    private static final HudEditorManager INSTANCE = new HudEditorManager();

    private static final String CONFIG_FILE = "config/hunchclient/hud_layout.json";

    // All registered HUD elements (sorted by Z-order)
    private final List<HudElement> elements = new CopyOnWriteArrayList<>();

    // Edit mode state
    private boolean editMode = false;
    private HudElement selectedElement = null;
    private HudElement hoveredElement = null;

    // Dragging state
    private boolean isDragging = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private float dragOffsetX = 0;
    private float dragOffsetY = 0;

    // Resizing state
    private boolean isResizing = false;
    private ResizeHandle resizeHandle = null;
    private int resizeStartWidth = 0;
    private int resizeStartHeight = 0;

    // Grid settings
    private boolean gridEnabled = true;
    private int gridSize = 10; // pixels
    private boolean showGrid = true;

    private HudEditorManager() {
        loadConfig();
    }

    public static HudEditorManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register a HUD element
     */
    public void registerElement(HudElement element) {
        if (!elements.contains(element)) {
            elements.add(element);
            sortElements();
            LOGGER.info("Registered HUD element: {}", element.getDisplayName());
        }
    }

    /**
     * Unregister a HUD element
     */
    public void unregisterElement(HudElement element) {
        elements.remove(element);
        if (selectedElement == element) {
            selectedElement = null;
        }
        if (hoveredElement == element) {
            hoveredElement = null;
        }
    }

    /**
     * Get all registered elements
     */
    public List<HudElement> getElements() {
        return new ArrayList<>(elements);
    }

    /**
     * Sort elements by Z-order (higher = on top)
     */
    private void sortElements() {
        elements.sort(Comparator.comparingInt(HudElement::getZOrder));
    }

    /**
     * Render all HUD elements
     */
    public void render(GuiGraphics context, int screenWidth, int screenHeight) {
        // Render grid in edit mode
        if (editMode && showGrid) {
            renderGrid(context, screenWidth, screenHeight);
        }

        // Render all enabled elements
        for (HudElement element : elements) {
            if (!element.isEnabled()) {
                continue;
            }

            // Calculate absolute position
            int x = element.getAnchor().calculateX(element.getX(), screenWidth, element.getWidth());
            int y = element.getAnchor().calculateY(element.getY(), screenHeight, element.getHeight());

            // Render the element
            try {
                element.render(context, x, y, screenWidth, screenHeight, editMode);
            } catch (Exception e) {
                LOGGER.error("Error rendering HUD element {}: {}", element.getDisplayName(), e.getMessage());
            }

            // Render edit mode overlay
            if (editMode) {
                renderEditOverlay(context, element, x, y);
            }
        }
    }

    /**
     * Render grid lines
     */
    private void renderGrid(GuiGraphics context, int screenWidth, int screenHeight) {
        int gridColor = 0x20FFFFFF; // Semi-transparent white

        // Vertical lines
        for (int x = 0; x < screenWidth; x += gridSize) {
            context.fill(x, 0, x + 1, screenHeight, gridColor);
        }

        // Horizontal lines
        for (int y = 0; y < screenHeight; y += gridSize) {
            context.fill(0, y, screenWidth, y + 1, gridColor);
        }
    }

    /**
     * Render edit mode overlay for element
     */
    private void renderEditOverlay(GuiGraphics context, HudElement element, int x, int y) {
        int width = element.getWidth();
        int height = element.getHeight();

        // Determine border color
        int borderColor;
        if (element == selectedElement) {
            borderColor = 0xFF00FF00; // Green for selected
        } else if (element == hoveredElement) {
            borderColor = 0xFFFFFF00; // Yellow for hovered
        } else if (element.isLocked()) {
            borderColor = 0xFFFF0000; // Red for locked
        } else {
            borderColor = 0x80FFFFFF; // White for normal
        }

        // Draw border
        drawBorder(context, x, y, width, height, borderColor, 2);

        // Draw resize handles if selected and resizable
        if (element == selectedElement && element.isResizable() && !element.isLocked()) {
            drawResizeHandles(context, x, y, width, height);
        }

        // Draw anchor indicator
        drawAnchorIndicator(context, element);

        // Draw label
        Minecraft mc = Minecraft.getInstance();
        String label = element.getDisplayName();
        if (element.isLocked()) {
            label += " [LOCKED]";
        }
        int labelWidth = mc.font.width(label);
        context.fill(x, y - 12, x + labelWidth + 4, y - 2, 0xC0000000);
        context.drawString(mc.font, label, x + 2, y - 10, 0xFFFFFFFF, false);
    }

    /**
     * Draw border around element
     */
    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color, int thickness) {
        // Top
        context.fill(x - thickness, y - thickness, x + width + thickness, y, color);
        // Bottom
        context.fill(x - thickness, y + height, x + width + thickness, y + height + thickness, color);
        // Left
        context.fill(x - thickness, y, x, y + height, color);
        // Right
        context.fill(x + width, y, x + width + thickness, y + height, color);
    }

    /**
     * Draw resize handles on corners and edges
     */
    private void drawResizeHandles(GuiGraphics context, int x, int y, int width, int height) {
        int handleSize = 6;
        int handleColor = 0xFFFFFFFF;

        // Corners
        drawHandle(context, x - handleSize/2, y - handleSize/2, handleSize, handleColor); // Top-left
        drawHandle(context, x + width - handleSize/2, y - handleSize/2, handleSize, handleColor); // Top-right
        drawHandle(context, x - handleSize/2, y + height - handleSize/2, handleSize, handleColor); // Bottom-left
        drawHandle(context, x + width - handleSize/2, y + height - handleSize/2, handleSize, handleColor); // Bottom-right

        // Edges
        drawHandle(context, x + width/2 - handleSize/2, y - handleSize/2, handleSize, handleColor); // Top
        drawHandle(context, x + width/2 - handleSize/2, y + height - handleSize/2, handleSize, handleColor); // Bottom
        drawHandle(context, x - handleSize/2, y + height/2 - handleSize/2, handleSize, handleColor); // Left
        drawHandle(context, x + width - handleSize/2, y + height/2 - handleSize/2, handleSize, handleColor); // Right
    }

    /**
     * Draw a single resize handle
     */
    private void drawHandle(GuiGraphics context, int x, int y, int size, int color) {
        context.fill(x, y, x + size, y + size, color);
        context.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF000000);
    }

    /**
     * Draw anchor point indicator
     */
    private void drawAnchorIndicator(GuiGraphics context, HudElement element) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        HudAnchor anchor = element.getAnchor();
        int anchorX = (int)(screenWidth * getAnchorXFactor(anchor));
        int anchorY = (int)(screenHeight * getAnchorYFactor(anchor));

        // Draw small cross at anchor point
        int size = 5;
        context.fill(anchorX - size, anchorY, anchorX + size, anchorY + 1, 0x80FF0000);
        context.fill(anchorX, anchorY - size, anchorX + 1, anchorY + size, 0x80FF0000);
    }

    private float getAnchorXFactor(HudAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> 0.0f;
            case TOP_CENTER, MIDDLE_CENTER, BOTTOM_CENTER -> 0.5f;
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> 1.0f;
        };
    }

    private float getAnchorYFactor(HudAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0.0f;
            case MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT -> 0.5f;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1.0f;
        };
    }

    // ==================== Edit Mode ====================

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        if (!editMode) {
            selectedElement = null;
            hoveredElement = null;
            isDragging = false;
            isResizing = false;
            saveConfig();
        }
    }

    public void toggleEditMode() {
        setEditMode(!editMode);
    }

    public HudElement getSelectedElement() {
        return selectedElement;
    }

    public void setSelectedElement(HudElement element) {
        this.selectedElement = element;
    }

    public boolean isGridEnabled() {
        return gridEnabled;
    }

    public void setGridEnabled(boolean enabled) {
        this.gridEnabled = enabled;
    }

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int size) {
        this.gridSize = Math.max(1, size);
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean show) {
        this.showGrid = show;
    }

    // ==================== Mouse Handling ====================

    /**
     * Handle mouse click
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!editMode || button != 0) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Check for resize handle click first
        if (selectedElement != null && selectedElement.isResizable() && !selectedElement.isLocked()) {
            ResizeHandle handle = getResizeHandleAt(selectedElement, mouseX, mouseY, screenWidth, screenHeight);
            if (handle != null) {
                isResizing = true;
                resizeHandle = handle;
                resizeStartWidth = selectedElement.getWidth();
                resizeStartHeight = selectedElement.getHeight();
                dragStartX = (int) mouseX;
                dragStartY = (int) mouseY;
                return true;
            }
        }

        // Check element clicks (reverse order for top-to-bottom selection)
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (!element.isEnabled()) {
                continue;
            }

            int x = element.getAnchor().calculateX(element.getX(), screenWidth, element.getWidth());
            int y = element.getAnchor().calculateY(element.getY(), screenHeight, element.getHeight());

            if (mouseX >= x && mouseX <= x + element.getWidth() &&
                mouseY >= y && mouseY <= y + element.getHeight()) {

                selectedElement = element;

                if (!element.isLocked()) {
                    isDragging = true;
                    dragStartX = (int) mouseX;
                    dragStartY = (int) mouseY;
                    dragOffsetX = element.getX();
                    dragOffsetY = element.getY();
                }

                return true;
            }
        }

        // Clicked on empty space - deselect
        selectedElement = null;
        return false;
    }

    /**
     * Handle mouse release
     */
    public void handleMouseRelease(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check if we were dragging or resizing
            boolean wasModifying = isDragging || isResizing;

            isDragging = false;
            isResizing = false;
            resizeHandle = null;

            // Auto-save on element release (smart save - only if position/size changed)
            if (wasModifying && selectedElement != null) {
                LOGGER.info("Auto-saving HUD layout after modifying: {}", selectedElement.getDisplayName());

                // Sync back to parent modules (ImageHUD, NowPlaying, etc.)
                syncBackToModules();

                // Save both configs (HudEditorManager + individual modules)
                saveConfig();
                saveModuleConfigs();
            }
        }
    }

    /**
     * Sync HUD element positions back to their parent modules
     */
    private void syncBackToModules() {
        // Sync ImageHUD elements
        dev.hunchclient.module.impl.ImageHUDModule imageModule = dev.hunchclient.module.impl.ImageHUDModule.getInstance();
        if (imageModule != null) {
            imageModule.syncFromHudEditor();
        }

        // Sync NowPlaying element
        dev.hunchclient.module.impl.misc.NowPlayingModule nowPlayingModule = dev.hunchclient.module.impl.misc.NowPlayingModule.getInstance();
        if (nowPlayingModule != null) {
            nowPlayingModule.syncFromHudEditor();
        }

        // TODO: Add sync for other modules (DungeonMap, etc.)
    }

    /**
     * Save all parent module configs
     */
    private void saveModuleConfigs() {
        // Save ImageHUD module config
        dev.hunchclient.module.impl.ImageHUDModule imageModule = dev.hunchclient.module.impl.ImageHUDModule.getInstance();
        if (imageModule != null) {
            imageModule.saveConfig();
        }

        // Save NowPlaying module config
        dev.hunchclient.module.impl.misc.NowPlayingModule nowPlayingModule = dev.hunchclient.module.impl.misc.NowPlayingModule.getInstance();
        if (nowPlayingModule != null) {
            nowPlayingModule.saveConfig();
        }

        // Save SkeetDungeonMap module config
        dev.hunchclient.module.impl.dungeons.SkeetDungeonMapModule skeetMapModule = dev.hunchclient.module.impl.dungeons.SkeetDungeonMapModule.getInstance();
        if (skeetMapModule != null && skeetMapModule.isEnabled()) {
            // Trigger global config save which will include the module's position/scale
            try {
                dev.hunchclient.util.ConfigManager.save();
            } catch (Exception e) {
                LOGGER.error("Failed to save SkeetDungeonMap config", e);
            }
        }
    }

    /**
     * Handle mouse drag
     */
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (!editMode) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Update hovered element
        hoveredElement = null;
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (!element.isEnabled()) {
                continue;
            }

            int x = element.getAnchor().calculateX(element.getX(), screenWidth, element.getWidth());
            int y = element.getAnchor().calculateY(element.getY(), screenHeight, element.getHeight());

            if (mouseX >= x && mouseX <= x + element.getWidth() &&
                mouseY >= y && mouseY <= y + element.getHeight()) {
                hoveredElement = element;
                break;
            }
        }

        // Handle resizing
        if (isResizing && selectedElement != null && resizeHandle != null) {
            int deltaX = (int) (mouseX - dragStartX);
            int deltaY = (int) (mouseY - dragStartY);

            int newWidth = resizeStartWidth;
            int newHeight = resizeStartHeight;

            switch (resizeHandle) {
                case BOTTOM_RIGHT:
                    newWidth += deltaX;
                    newHeight += deltaY;
                    break;
                case BOTTOM_LEFT:
                    newWidth -= deltaX;
                    newHeight += deltaY;
                    break;
                case TOP_RIGHT:
                    newWidth += deltaX;
                    newHeight -= deltaY;
                    break;
                case TOP_LEFT:
                    newWidth -= deltaX;
                    newHeight -= deltaY;
                    break;
                case RIGHT:
                    newWidth += deltaX;
                    break;
                case LEFT:
                    newWidth -= deltaX;
                    break;
                case BOTTOM:
                    newHeight += deltaY;
                    break;
                case TOP:
                    newHeight -= deltaY;
                    break;
            }

            selectedElement.setSize(newWidth, newHeight);
            return;
        }

        // Handle dragging
        if (isDragging && selectedElement != null && !selectedElement.isLocked()) {
            int deltaX = (int) (mouseX - dragStartX);
            int deltaY = (int) (mouseY - dragStartY);

            // Calculate current absolute position
            int currentX = selectedElement.getAnchor().calculateX(dragOffsetX, screenWidth, selectedElement.getWidth());
            int currentY = selectedElement.getAnchor().calculateY(dragOffsetY, screenHeight, selectedElement.getHeight());

            // Apply delta
            int newX = currentX + deltaX;
            int newY = currentY + deltaY;

            // Apply grid snapping
            if (gridEnabled) {
                newX = snapToGrid(newX);
                newY = snapToGrid(newY);
            }

            // Convert back to relative position
            float relativeX = selectedElement.getAnchor().calculateRelativeX(newX, screenWidth, selectedElement.getWidth());
            float relativeY = selectedElement.getAnchor().calculateRelativeY(newY, screenHeight, selectedElement.getHeight());

            selectedElement.setX(relativeX);
            selectedElement.setY(relativeY);
        }
    }

    /**
     * Snap coordinate to grid
     */
    private int snapToGrid(int value) {
        return Math.round(value / (float) gridSize) * gridSize;
    }

    /**
     * Get resize handle at mouse position (if any)
     */
    private ResizeHandle getResizeHandleAt(HudElement element, double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int x = element.getAnchor().calculateX(element.getX(), screenWidth, element.getWidth());
        int y = element.getAnchor().calculateY(element.getY(), screenHeight, element.getHeight());
        int width = element.getWidth();
        int height = element.getHeight();

        int handleSize = 10;
        int halfHandle = handleSize / 2;

        // Check corners first (higher priority)
        if (isInHandle(mouseX, mouseY, x, y, halfHandle)) return ResizeHandle.TOP_LEFT;
        if (isInHandle(mouseX, mouseY, x + width, y, halfHandle)) return ResizeHandle.TOP_RIGHT;
        if (isInHandle(mouseX, mouseY, x, y + height, halfHandle)) return ResizeHandle.BOTTOM_LEFT;
        if (isInHandle(mouseX, mouseY, x + width, y + height, halfHandle)) return ResizeHandle.BOTTOM_RIGHT;

        // Check edges
        if (isInHandle(mouseX, mouseY, x + width / 2, y, halfHandle)) return ResizeHandle.TOP;
        if (isInHandle(mouseX, mouseY, x + width / 2, y + height, halfHandle)) return ResizeHandle.BOTTOM;
        if (isInHandle(mouseX, mouseY, x, y + height / 2, halfHandle)) return ResizeHandle.LEFT;
        if (isInHandle(mouseX, mouseY, x + width, y + height / 2, halfHandle)) return ResizeHandle.RIGHT;

        return null;
    }

    private boolean isInHandle(double mouseX, double mouseY, int handleX, int handleY, int radius) {
        return Math.abs(mouseX - handleX) <= radius && Math.abs(mouseY - handleY) <= radius;
    }

    // ==================== Persistence ====================

    /**
     * Save configuration to disk
     * NOTE: Element positions are NOT saved here - they're saved in individual module configs
     * This only saves global HUD editor settings (grid, etc.)
     */
    public void saveConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            configFile.getParentFile().mkdirs();

            JsonObject root = new JsonObject();
            root.addProperty("gridEnabled", gridEnabled);
            root.addProperty("gridSize", gridSize);
            root.addProperty("showGrid", showGrid);

            // DON'T save elements here - they're managed by individual modules
            // Each module (ImageHUD, NowPlaying, etc.) saves its own element positions

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(root, writer);
            }

            LOGGER.info("Saved HUD editor settings (grid: {}, size: {})", gridEnabled, gridSize);
        } catch (Exception e) {
            LOGGER.error("Failed to save HUD layout", e);
        }
    }

    /**
     * Load configuration from disk
     */
    public void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (!configFile.exists()) {
                return;
            }

            try (FileReader reader = new FileReader(configFile)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);

                if (root.has("gridEnabled")) gridEnabled = root.get("gridEnabled").getAsBoolean();
                if (root.has("gridSize")) gridSize = root.get("gridSize").getAsInt();
                if (root.has("showGrid")) showGrid = root.get("showGrid").getAsBoolean();

                // Note: Elements are loaded by individual modules
                // This just loads the global settings
            }

            LOGGER.info("Loaded HUD layout configuration");
        } catch (Exception e) {
            LOGGER.error("Failed to load HUD layout", e);
        }
    }

    /**
     * Resize handles
     */
    private enum ResizeHandle {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }
}
