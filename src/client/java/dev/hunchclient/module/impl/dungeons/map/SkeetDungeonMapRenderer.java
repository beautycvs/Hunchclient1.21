package dev.hunchclient.module.impl.dungeons.map;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.NameProtectModule;
import dev.hunchclient.module.impl.dungeons.devmap.*;
import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.*;
import dev.hunchclient.util.SectionCodeParser;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static java.lang.Math.*;


public class SkeetDungeonMapRenderer {

    private static final Minecraft mc = Minecraft.getInstance();

    // RGB animation state
    private float rgbTime = 0f;

    // Texture identifiers for icons
    private static final Map<String, ResourceLocation> CHECKMARK_TEXTURES = new HashMap<>();
    private static final Map<String, ResourceLocation> PUZZLE_TEXTURES = new HashMap<>();
    private static final Map<String, ResourceLocation> BOSS_TEXTURES = new HashMap<>();

    // Player marker textures
    private static final ResourceLocation MARKER_SELF = ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/marker_self.png");
    private static final ResourceLocation MARKER_OTHER = ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/marker_other.png");

    // Room colors (Skeet-styled - dark, muted)
    private static final int COLOR_ENTRANCE = 0xFF2D8B2D;
    private static final int COLOR_NORMAL = 0xFF6B4423;
    private static final int COLOR_MINIBOSS = 0xFF8B5A2B;
    private static final int COLOR_FAIRY = 0xFFDB7093;
    private static final int COLOR_BLOOD = 0xFFB22222;
    private static final int COLOR_PUZZLE = 0xFF9932CC;
    private static final int COLOR_TRAP = 0xFFCD853F;
    private static final int COLOR_YELLOW = 0xFFE2E232;
    private static final int COLOR_RARE = 0xFFFFD700;
    private static final int COLOR_UNKNOWN = 0xFF3A3A3A;

    // Door colors
    private static final int COLOR_DOOR_NORMAL = 0xFF4A3728;
    private static final int COLOR_DOOR_WITHER = 0xFF252525; // Slightly lighter than background
    private static final int COLOR_DOOR_BLOOD = 0xFFB22222;
    private static final int COLOR_DOOR_ENTRANCE = 0xFF228B22;

    // Checkmark colors
    private static final int COLOR_CHECK_WHITE = 0xFFFFFFFF;
    private static final int COLOR_CHECK_GREEN = 0xFF55FF55;
    private static final int COLOR_CHECK_FAILED = 0xFFFF5555;

    // Settings
    private double roomWidth = 0.8;
    private double doorWidth = 0.4;
    private boolean renderCheckmarks = true;
    private boolean renderPuzzleIcons = true;
    private boolean renderPlayerNames = true;
    private boolean renderRoomNames = false;
    private boolean renderUnknownRooms = true; // "Funny mode" - show all rooms even unexplored
    private double unknownRoomsDarken = 0.4;
    private float markerScale = 1.0f;
    private float nameScale = 1.0f;

    static {
        loadAssets();
    }

    private static void loadAssets() {
        // Checkmark textures - custom checkmarks
        CHECKMARK_TEXTURES.put("white", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/white_check.png"));
        CHECKMARK_TEXTURES.put("green", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/green_check.png"));
        CHECKMARK_TEXTURES.put("failed", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/failed_room.png"));
        CHECKMARK_TEXTURES.put("question", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/question_mark.png"));

        // Puzzle icon textures - using better Vanilla MC icons
        PUZZLE_TEXTURES.put("Creeper Beams", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/creeper_head.png"));
        PUZZLE_TEXTURES.put("Three Weirdos", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/chest.png"));
        PUZZLE_TEXTURES.put("Tic Tac Toe", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/comparator.png"));
        PUZZLE_TEXTURES.put("Water Board", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/bucket_water.png"));
        PUZZLE_TEXTURES.put("Teleport Maze", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/ender_pearl.png"));
        PUZZLE_TEXTURES.put("Blaze", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/blaze_rod.png"));
        PUZZLE_TEXTURES.put("Boulder", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/cobblestone.png"));
        PUZZLE_TEXTURES.put("Ice Fill", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/blue_ice.png"));
        PUZZLE_TEXTURES.put("Ice Path", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/leather_boots.png"));
        PUZZLE_TEXTURES.put("Quiz", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/enchanted_book.png"));
        PUZZLE_TEXTURES.put("Trap", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/arrow.png"));

        // Boss head textures for yellow rooms (by room name from rooms.json)
        BOSS_TEXTURES.put("Shadow Assassin", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_sa.png"));
        BOSS_TEXTURES.put("King Midas", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_midas.png"));
        // "Dragon" is the generic name for all dragon armor mini-bosses - use Superior as the icon
        BOSS_TEXTURES.put("Dragon", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_superior.png"));
        // Diamond Guy (diamond armor mini-boss)
        BOSS_TEXTURES.put("Diamond Guy", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_diamond.png"));
        // Alternative names that might be used
        BOSS_TEXTURES.put("Lost Adventurer", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_la.png"));
        BOSS_TEXTURES.put("Angry Archeologist", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_la.png"));
        // Fallback
        BOSS_TEXTURES.put("Default", ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/dungeons/map/boss_la.png"));
    }

    /**
     * Main render method
     */
    public void render(GuiGraphics context, int x, int y, int width, int height, float delta) {
        DevMapState state = DevMapState.getInstance();
        FloorType floor = state.getFloor();

        // Update RGB animation
        rgbTime += delta * 0.05f;

        // Draw Skeet-styled background
        int bgColor = SkeetTheme.withAlpha(SkeetTheme.BG_PRIMARY(), 230);
        context.fill(x, y, x + width, y + height, bgColor);

        // Draw inner border
        int borderColor = SkeetTheme.withAlpha(0xFF2A2A2A, 255);
        drawBorder(context, x + 1, y + 1, width - 2, height - 2, borderColor);

        // Draw RGB glow border
        drawRgbGlowBorder(context, x, y, width, height);

        // Don't draw map content if not in dungeon or floor unknown
        if (floor == FloorType.NONE) {
            String text = "Not in Dungeon";
            int textWidth = mc.font.width(text);
            context.drawString(mc.font, text, x + width / 2 - textWidth / 2, y + height / 2 - 4, 0xFF888888, true);
            return;
        }

        // Draw map content with padding
        int padding = 4;
        int mapX = x + padding;
        int mapY = y + padding;
        int mapW = width - padding * 2;
        int mapH = height - padding * 2;

        // Calculate scale
        int dungeonW = floor.roomsW;
        int dungeonH = floor.roomsH;
        double scale = min((double) mapW / dungeonW, (double) mapH / dungeonH);

        // Draw rooms and doors
        drawMap(context, mapX, mapY, scale, state);

        // Draw players
        drawPlayers(context, mapX, mapY, scale, state);
    }

    /**
     * Draw the dungeon map (rooms and doors)
     */
    private void drawMap(GuiGraphics context, int mapX, int mapY, double scale, DevMapState state) {
        DevRoom[] rooms = state.getRooms();
        DevDoor[] doors = state.getDoors();

        double roomOffset = (1.0 - roomWidth) * 0.5;

        // Track visited rooms (for merged rooms)
        Set<DevRoom> visitedRooms = new HashSet<>();

        // Draw rooms
        for (int idx = 0; idx < 36; idx++) {
            DevRoom room = rooms[idx];
            if (room == null) continue;
            if (visitedRooms.contains(room)) continue;
            visitedRooms.add(room);

            drawRoom(context, room, mapX, mapY, scale, roomOffset, state);
        }

        // Draw doors
        for (DevDoor door : doors) {
            if (door == null) continue;
            drawDoor(context, door, mapX, mapY, scale, roomOffset, state);
        }

        // Draw decorations (checkmarks, puzzle icons)
        visitedRooms.clear();
        for (int idx = 0; idx < 36; idx++) {
            DevRoom room = rooms[idx];
            if (room == null) continue;
            if (visitedRooms.contains(room)) continue;
            visitedRooms.add(room);

            drawRoomDecoration(context, room, mapX, mapY, scale, roomOffset);
        }
    }

    /**
     * Draw a room with proper shape
     */
    private void drawRoom(GuiGraphics context, DevRoom room, int mapX, int mapY, double scale, double roomOffset, DevMapState state) {
        // In non-funny mode, show room if: explored OR adjacent to an explored room
        if (!renderUnknownRooms) {
            if (!room.isExplored() && !isAdjacentToExplored(room, state)) {
                return;
            }
        }

        int color = getColorForRoom(room);

        // Darken unexplored rooms
        if (!room.isExplored()) {
            color = darkenColor(color, unknownRoomsDarken);
        }

        List<WorldComponentPosition> cells = room.getComps();
        ShapeType shape = room.getShape();

        if (cells.isEmpty() || shape == ShapeType.UNKNOWN) return;

        // Draw based on shape
        switch (shape) {
            case SHAPE_L -> drawLShapedRoom(context, cells, color, mapX, mapY, scale, roomOffset);
            case SHAPE_1X1 -> drawSimpleRoom(context, cells.get(0), 1, 1, color, mapX, mapY, scale, roomOffset);
            case SHAPE_1X2, SHAPE_1X3, SHAPE_1X4 -> {
                WorldComponentPosition corner = getCorner(cells);
                boolean horizontal = cells.get(0).cx != cells.get(1).cx;
                int w = horizontal ? cells.size() : 1;
                int h = horizontal ? 1 : cells.size();
                drawSimpleRoom(context, corner, w, h, color, mapX, mapY, scale, roomOffset);
            }
            case SHAPE_2X2 -> {
                WorldComponentPosition corner = getCorner(cells);
                drawSimpleRoom(context, corner, 2, 2, color, mapX, mapY, scale, roomOffset);
            }
            default -> {
                for (WorldComponentPosition cell : cells) {
                    drawSimpleRoom(context, cell, 1, 1, color, mapX, mapY, scale, roomOffset);
                }
            }
        }
    }

    private void drawSimpleRoom(GuiGraphics context, WorldComponentPosition corner, int w, int h, int color,
                                 int mapX, int mapY, double scale, double roomOffset) {
        int cx = corner.cx / 2;
        int cz = corner.cz / 2;

        double px = cx + roomOffset;
        double pz = cz + roomOffset;
        double pw = roomWidth + w - 1;
        double ph = roomWidth + h - 1;

        int bx = mapX + (int) (scale * px);
        int bz = mapY + (int) (scale * pz);
        int bw = (int) ceil(scale * pw);
        int bh = (int) ceil(scale * ph);

        context.fill(bx, bz, bx + bw, bz + bh, color);
    }

    private void drawLShapedRoom(GuiGraphics context, List<WorldComponentPosition> cells, int color,
                                  int mapX, int mapY, double scale, double roomOffset) {
        // Draw each cell and connections
        for (int i = 0; i < cells.size(); i++) {
            WorldComponentPosition cell = cells.get(i);
            int cx = cell.cx / 2;
            int cz = cell.cz / 2;

            drawSimpleRoom(context, cell, 1, 1, color, mapX, mapY, scale, roomOffset);

            // Draw connections
            for (int j = i + 1; j < cells.size(); j++) {
                WorldComponentPosition other = cells.get(j);
                int ox = other.cx / 2;
                int oz = other.cz / 2;

                if (abs(cx - ox) + abs(cz - oz) == 1) {
                    drawRoomConnection(context, cx, cz, ox, oz, color, mapX, mapY, scale, roomOffset);
                }
            }
        }
    }

    private void drawRoomConnection(GuiGraphics context, int cx1, int cz1, int cx2, int cz2, int color,
                                     int mapX, int mapY, double scale, double roomOffset) {
        if (cx1 > cx2 || cz1 > cz2) {
            drawRoomConnection(context, cx2, cz2, cx1, cz1, color, mapX, mapY, scale, roomOffset);
            return;
        }

        double px, pz, pw, ph;
        if (cx1 == cx2) {
            px = cx1 + roomOffset;
            pz = cz1 + roomOffset + roomWidth;
            pw = roomWidth;
            ph = roomOffset * 2.0;
        } else {
            px = cx1 + roomOffset + roomWidth;
            pz = cz1 + roomOffset;
            pw = roomOffset * 2.0;
            ph = roomWidth;
        }

        int bx = mapX + (int) (scale * px);
        int bz = mapY + (int) (scale * pz);
        int bw = (int) ceil(scale * pw);
        int bh = (int) ceil(scale * ph);

        context.fill(bx, bz, bx + bw, bz + bh, color);
    }

    /**
     * Draw a door
     */
    private void drawDoor(GuiGraphics context, DevDoor door, int mapX, int mapY, double scale, double roomOffset, DevMapState state) {
        if (!renderUnknownRooms) {
            boolean anyExplored = false;
            for (DevRoom room : door.getRooms()) {
                if (room.isExplored()) {
                    anyExplored = true;
                    break;
                }
            }
            if (!anyExplored) return;
        }

        int color = getColorForDoor(door, state);
        if (color == 0) return;

        WorldComponentPosition comp = door.getComp();
        int cx = comp.cx;
        int cz = comp.cz;

        double doorOffset = (1.0 - doorWidth) * 0.5;

        double px, pz, pw, ph;
        if ((cx & 1) == 1) {
            // Horizontal door
            px = (cx / 2) + roomOffset + roomWidth;
            pz = (cz / 2) + doorOffset;
            pw = roomOffset * 2.0;
            ph = doorWidth;
        } else {
            // Vertical door
            px = (cx / 2) + doorOffset;
            pz = (cz / 2) + roomOffset + roomWidth;
            pw = doorWidth;
            ph = roomOffset * 2.0;
        }

        int bx = mapX + (int) (scale * px);
        int bz = mapY + (int) (scale * pz);
        int bw = (int) ceil(scale * pw);
        int bh = (int) ceil(scale * ph);

        context.fill(bx, bz, bx + bw, bz + bh, color);
    }

    /**
     * Draw room decoration (checkmark, puzzle icon, or boss head)
     */
    private void drawRoomDecoration(GuiGraphics context, DevRoom room, int mapX, int mapY, double scale, double roomOffset) {
        // Match visibility logic from drawRoom
        if (!renderUnknownRooms) {
            if (!room.isExplored() && !isAdjacentToExplored(room, DevMapState.getInstance())) {
                return;
            }
        }

        // Calculate center position
        double[] center = getRoomCenter(room.getComps(), room.getShape());
        int centerX = mapX + (int) (scale * center[0]);
        int centerY = mapY + (int) (scale * center[1]);

        // Icon size based on scale
        int iconSize = (int) (scale * 0.6);
        if (iconSize < 8) iconSize = 8;
        if (iconSize > 16) iconSize = 16;

        // For PUZZLE rooms: render puzzle icon
        if (room.getType() == MapEnums.RoomType.PUZZLE && renderPuzzleIcons) {
            String puzzleName = room.getName();
            ResourceLocation puzzleTexture = null;

            if (puzzleName != null) {
                puzzleTexture = PUZZLE_TEXTURES.get(puzzleName);
            }

            // If we have a puzzle texture, draw it
            if (puzzleTexture != null) {
                drawTexture(context, puzzleTexture, centerX - iconSize / 2, centerY - iconSize / 2, iconSize, iconSize);

                // Draw checkmark overlay if cleared
                if (renderCheckmarks) {
                    int checkSize = iconSize / 2 + 2;
                    int checkX = centerX + iconSize / 2 - checkSize / 2;
                    int checkY = centerY + iconSize / 2 - checkSize / 2;
                    if (room.getCheckmark() == CheckmarkType.GREEN) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("green"), checkX, checkY, checkSize, checkSize);
                    } else if (room.getCheckmark() == CheckmarkType.FAILED) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("failed"), checkX, checkY, checkSize, checkSize);
                    } else if (room.getCheckmark() == CheckmarkType.WHITE) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("white"), checkX, checkY, checkSize, checkSize);
                    }
                }
                return;
            }
        }

        // For YELLOW boss rooms: render boss head icon
        if (room.getType() == MapEnums.RoomType.YELLOW && renderPuzzleIcons) {
            String bossName = room.getName();
            ResourceLocation bossTexture = null;

            if (bossName != null) {
                bossTexture = BOSS_TEXTURES.get(bossName);
            }

            // If we have a boss texture, draw it
            if (bossTexture != null) {
                drawTexture(context, bossTexture, centerX - iconSize / 2, centerY - iconSize / 2, iconSize, iconSize);

                // Draw checkmark overlay if cleared
                if (renderCheckmarks) {
                    int checkSize = iconSize / 2 + 2;
                    int checkX = centerX + iconSize / 2 - checkSize / 2;
                    int checkY = centerY + iconSize / 2 - checkSize / 2;
                    if (room.getCheckmark() == CheckmarkType.GREEN) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("green"), checkX, checkY, checkSize, checkSize);
                    } else if (room.getCheckmark() == CheckmarkType.WHITE) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("white"), checkX, checkY, checkSize, checkSize);
                    }
                }
                return;
            }
        }

        // For TRAP rooms: render trap icon
        if (room.getType() == MapEnums.RoomType.TRAP && renderPuzzleIcons) {
            ResourceLocation trapTexture = PUZZLE_TEXTURES.get("Trap");
            if (trapTexture != null) {
                drawTexture(context, trapTexture, centerX - iconSize / 2, centerY - iconSize / 2, iconSize, iconSize);

                // Draw checkmark overlay if cleared
                if (renderCheckmarks) {
                    int checkSize = iconSize / 2 + 2;
                    int checkX = centerX + iconSize / 2 - checkSize / 2;
                    int checkY = centerY + iconSize / 2 - checkSize / 2;
                    if (room.getCheckmark() == CheckmarkType.GREEN) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("green"), checkX, checkY, checkSize, checkSize);
                    } else if (room.getCheckmark() == CheckmarkType.WHITE) {
                        drawTexture(context, CHECKMARK_TEXTURES.get("white"), checkX, checkY, checkSize, checkSize);
                    }
                }
                return;
            }
        }

        // Default: draw checkmark textures for other room types
        ResourceLocation checkmarkTexture = null;

        if (renderCheckmarks) {
            switch (room.getCheckmark()) {
                case WHITE -> checkmarkTexture = CHECKMARK_TEXTURES.get("white");
                case GREEN -> checkmarkTexture = CHECKMARK_TEXTURES.get("green");
                case FAILED -> checkmarkTexture = CHECKMARK_TEXTURES.get("failed");
                case UNEXPLORED -> {} // Don't show anything
                case NONE -> {} // No checkmark
            }
        }

        // Draw checkmark texture
        if (checkmarkTexture != null) {
            int checkSize = iconSize;
            drawTexture(context, checkmarkTexture, centerX - checkSize / 2, centerY - checkSize / 2, checkSize, checkSize);
        }

        // Draw room name if enabled and room has a name (for non-puzzle/boss rooms)
        if (renderRoomNames && room.getName() != null && room.getType() != MapEnums.RoomType.PUZZLE && room.getType() != MapEnums.RoomType.YELLOW) {
            String roomName = room.getName();

            // Shorten long names to fit in room
            if (roomName.length() > 8) {
                roomName = roomName.substring(0, 6) + "..";
            }

            if (dev.hunchclient.render.NVGRenderer.isDrawing()) {
                float smallSize = 3.2f * nameScale;
                float textW = dev.hunchclient.render.NVGRenderer.textWidth(roomName, smallSize, dev.hunchclient.render.NVGRenderer.defaultFont);
                dev.hunchclient.render.NVGRenderer.text(roomName, centerX - textW / 2, centerY - smallSize / 2, smallSize, 0xFFCCCCCC, dev.hunchclient.render.NVGRenderer.defaultFont);
            } else {
                int textWidth = mc.font.width(roomName);
                context.drawString(mc.font, roomName, centerX - textWidth / 2, centerY - mc.font.lineHeight / 2, 0xFFCCCCCC, true);
            }
        }
    }

    /**
     * Draw a texture at the specified position using sprite rendering
     */
    private void drawTexture(GuiGraphics context, ResourceLocation texture, int x, int y, int width, int height) {
        // Use drawTexture with RenderPipelines for 1.21+
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x, y,           // Screen position
            0.0f, 0.0f,     // UV start (top-left of texture)
            width, height,  // Render size on screen
            width, height,  // Region size in texture
            width, height   // Total texture dimensions (assume square icons)
        );
    }

    /**
     * Draw a texture centered at the specified position
     */
    private void drawCenteredTexture(GuiGraphics context, ResourceLocation texture, int centerX, int centerY, int width, int height) {
        int x = centerX - width / 2;
        int y = centerY - height / 2;
        drawTexture(context, texture, x, y, width, height);
    }

    /**
     * Draw a rotated texture at the specified position
     */
    private void drawRotatedTexture(GuiGraphics context, ResourceLocation texture, int centerX, int centerY, int width, int height, float angleDegrees) {
        var matrices = context.pose();

        // Push, translate to center, rotate (negated for correct direction)
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.rotate((float) Math.toRadians(-angleDegrees));

        // Draw texture centered (offset by -half to center it)
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            -width / 2, -height / 2,
            0.0f, 0.0f,
            width, height,
            width, height,
            width, height
        );

        matrices.popMatrix();
    }

    /**
     * Draw player markers
     */
    private void drawPlayers(GuiGraphics context, int mapX, int mapY, double scale, DevMapState state) {
        FloorType floor = state.getFloor();
        if (floor == FloorType.NONE) return;

        // Self marker
        if (mc.player != null) {
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            float yaw = mc.player.getYRot();

            // Convert Minecraft yaw to screen angle
            double angle = -(yaw + 180) * Math.PI / 180.0;
            PlayerComponentPosition pos = PlayerComponentPosition.fromWorld(px, pz, angle);
            drawPlayerMarker(context, pos, mapX, mapY, scale, SkeetTheme.ACCENT_PRIMARY(), true,
                mc.player.getName().getString(), mc.player.getUUID());
        }

        // Other players
        for (DevPlayer player : state.getPlayers().values()) {
            if (player.getName().equals(mc.player != null ? mc.player.getName().getString() : "")) continue;

            PlayerComponentPosition pos = player.getLerpedPosition();
            if (pos == null) pos = player.getPosition();
            if (pos == null) continue;

            int color = getColorForClass(player.getRole());
            // Get UUID from entity if available
            java.util.UUID uuid = player.getEntity() != null ? player.getEntity().getUUID() : null;
            drawPlayerMarker(context, pos, mapX, mapY, scale, color, false, player.getName(), uuid);
        }
    }

    private void drawPlayerMarker(GuiGraphics context, PlayerComponentPosition pos, int mapX, int mapY, double scale,
                                   int color, boolean isSelf, String name, java.util.UUID uuid) {
        int px = mapX + (int) (scale * pos.x / 2.0);
        int py = mapY + (int) (scale * pos.z / 2.0);

        // Marker size (preserve 10:14 aspect ratio)
        int markerWidth = (int) (10 * markerScale);
        int markerHeight = (int) (14 * markerScale);

        // Calculate rotation angle from player direction
        // pos.r is in radians, convert to degrees
        float angleDegrees = (float) Math.toDegrees(pos.r);

        // Draw rotated texture marker
        ResourceLocation markerTexture = isSelf ? MARKER_SELF : MARKER_OTHER;
        drawRotatedTexture(context, markerTexture, px, py, markerWidth, markerHeight, angleDegrees);

        // Draw name with NameProtect support
        if (renderPlayerNames) {
            String displayName = name;

            // Apply NameProtect
            NameProtectModule nameProtect = ModuleManager.getInstance().getModule(NameProtectModule.class);
            if (nameProtect != null) {
                displayName = nameProtect.sanitizeString(displayName);
            }

            // Parse color codes and render as Text
            Component protectedText = SectionCodeParser.parse(displayName, null);
            String plainText = protectedText.getString();
            if (plainText.length() > 10) {
                // Truncate but keep styling
                displayName = plainText.substring(0, 10);
                protectedText = SectionCodeParser.parse(displayName, null);
            }

            int textWidth = mc.font.width(protectedText);
            context.drawString(mc.font, protectedText, px - textWidth / 2, py - markerHeight / 2 - 10, 0xFFFFFFFF, true);
        }
    }

    /**
     * Draw RGB glow border (Skeet style)
     */
    private void drawRgbGlowBorder(GuiGraphics context, int x, int y, int width, int height) {
        int glowLayers = 2;
        int segmentSize = 4;
        float perimeter = 2.0f * (width + height);

        for (int layer = glowLayers; layer >= 0; layer--) {
            float alphaMultiplier = layer == 0 ? 1.0f : 0.3f / (layer + 0.5f);
            int layerAlpha = (int) (255 * alphaMultiplier);
            int offset = layer;

            // Top edge
            for (int px = 0; px < width + 2 * offset; px += segmentSize) {
                float position = px / perimeter;
                int color = getRainbowColor(rgbTime, position, min(255, layerAlpha));
                int endX = min(x - offset + px + segmentSize, x + width + offset);
                context.fill(x - offset + px, y - offset, endX, y - offset + 1, color);
            }

            // Right edge
            for (int py = 0; py < height + 2 * offset; py += segmentSize) {
                float position = (width + py) / perimeter;
                int color = getRainbowColor(rgbTime, position, min(255, layerAlpha));
                int endY = min(y - offset + py + segmentSize, y + height + offset);
                context.fill(x + width + offset - 1, y - offset + py, x + width + offset, endY, color);
            }

            // Bottom edge
            for (int px = 0; px < width + 2 * offset; px += segmentSize) {
                float position = (width + height + px) / perimeter;
                int color = getRainbowColor(rgbTime, position, min(255, layerAlpha));
                int startX = max(x + width + offset - px - segmentSize, x - offset);
                context.fill(startX, y + height + offset - 1, x + width + offset - px, y + height + offset, color);
            }

            // Left edge
            for (int py = 0; py < height + 2 * offset; py += segmentSize) {
                float position = (2 * width + height + py) / perimeter;
                int color = getRainbowColor(rgbTime, position, min(255, layerAlpha));
                int startY = max(y + height + offset - py - segmentSize, y - offset);
                context.fill(x - offset, startY, x - offset + 1, y + height + offset - py, color);
            }
        }
    }

    private void drawBorder(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private int getRainbowColor(float time, float position, int alpha) {
        float speed = 1.5f;
        float waves = 2.0f;
        float offset = position * waves * 6.0f;
        float hue = (time * speed + offset) % 6.0f;

        int r, g, b;

        if (hue < 1.0f) {
            r = 255; g = (int) (255 * hue); b = 0;
        } else if (hue < 2.0f) {
            r = (int) (255 * (2.0f - hue)); g = 255; b = 0;
        } else if (hue < 3.0f) {
            r = 0; g = 255; b = (int) (255 * (hue - 2.0f));
        } else if (hue < 4.0f) {
            r = 0; g = (int) (255 * (4.0f - hue)); b = 255;
        } else if (hue < 5.0f) {
            r = (int) (255 * (hue - 4.0f)); g = 0; b = 255;
        } else {
            r = 255; g = 0; b = (int) (255 * (6.0f - hue));
        }

        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    // Helper methods

    private int getColorForRoom(DevRoom room) {
        return switch (room.getType()) {
            case ENTRANCE -> COLOR_ENTRANCE;
            case NORMAL -> room.getClearType() == ClearType.MINIBOSS ? COLOR_MINIBOSS : COLOR_NORMAL;
            case FAIRY -> COLOR_FAIRY;
            case BLOOD -> COLOR_BLOOD;
            case PUZZLE -> COLOR_PUZZLE;
            case TRAP -> COLOR_TRAP;
            case YELLOW -> COLOR_YELLOW;
            case RARE -> COLOR_RARE;
            default -> COLOR_UNKNOWN;
        };
    }

    private int getColorForDoor(DevDoor door, DevMapState state) {
        if (!door.isOpened() && door.getType() == MapEnums.DoorType.NORMAL) return 0;

        return switch (door.getType()) {
            case ENTRANCE -> COLOR_DOOR_ENTRANCE;
            case WITHER -> COLOR_DOOR_WITHER;
            case BLOOD -> COLOR_DOOR_BLOOD;
            case NORMAL -> {
                DevRoom bestRoom = null;
                int bestPrio = Integer.MAX_VALUE;
                for (DevRoom room : door.getRooms()) {
                    if (room.getType().priority < bestPrio) {
                        bestPrio = room.getType().priority;
                        bestRoom = room;
                    }
                }
                yield bestRoom != null ? getColorForRoom(bestRoom) : COLOR_DOOR_NORMAL;
            }
        };
    }

    private int getColorForClass(DevPlayer.DevClass role) {
        return switch (role) {
            case ARCHER -> 0xFFFF5555;
            case BERSERK -> 0xFFFFAA00;
            case MAGE -> 0xFF55FFFF;
            case HEALER -> 0xFFAA00AA;
            case TANK -> 0xFF55FF55;
            default -> 0xFFAAAAAA;
        };
    }

    private int darkenColor(int color, double factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Check if a room is adjacent to any explored room (via doors)
     */
    private boolean isAdjacentToExplored(DevRoom room, DevMapState state) {
        // Check all doors connected to this room
        for (DevDoor door : room.getDoors()) {
            // Check all rooms connected to this door
            for (DevRoom connectedRoom : door.getRooms()) {
                if (connectedRoom != room && connectedRoom.isExplored()) {
                    return true;
                }
            }
        }
        return false;
    }

    private WorldComponentPosition getCorner(List<WorldComponentPosition> cells) {
        return cells.stream()
            .min(Comparator.comparingInt(a -> a.cx + a.cz))
            .orElse(cells.get(0));
    }

    private double[] getRoomCenter(List<WorldComponentPosition> cells, ShapeType shape) {
        if (shape == ShapeType.SHAPE_L) {
            cells = new ArrayList<>(cells);
            cells.sort(Comparator.comparingInt(a -> a.cx + a.cz * 11));
            int idx = (cells.get(0).cx > cells.get(1).cx) ? 2 :
                      (cells.get(0).cx == cells.get(2).cx) ? 0 : 1;
            WorldComponentPosition cell = cells.get(idx);
            return new double[]{cell.cx / 2.0 + 0.5, cell.cz / 2.0 + 0.5};
        }

        double sumX = 0, sumZ = 0;
        for (WorldComponentPosition cell : cells) {
            sumX += cell.cx / 2.0;
            sumZ += cell.cz / 2.0;
        }
        return new double[]{sumX / cells.size() + 0.5, sumZ / cells.size() + 0.5};
    }

    /**
     * Draw a filled triangle
     */
    private void drawTriangle(GuiGraphics context, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // Simple scanline fill for small triangles
        int minY = min(y1, min(y2, y3));
        int maxY = max(y1, max(y2, y3));

        for (int y = minY; y <= maxY; y++) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;

            // Check intersection with each edge
            int[] edges = {x1, y1, x2, y2, x2, y2, x3, y3, x3, y3, x1, y1};
            for (int i = 0; i < 3; i++) {
                int ex1 = edges[i * 4], ey1 = edges[i * 4 + 1];
                int ex2 = edges[i * 4 + 2], ey2 = edges[i * 4 + 3];

                if ((ey1 <= y && ey2 > y) || (ey2 <= y && ey1 > y)) {
                    int x = ex1 + (y - ey1) * (ex2 - ex1) / (ey2 - ey1);
                    minX = min(minX, x);
                    maxX = max(maxX, x);
                }
            }

            if (minX <= maxX) {
                context.fill(minX, y, maxX + 1, y + 1, color);
            }
        }
    }

    /**
     * Draw a line using Bresenham's algorithm
     */
    private void drawLine(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            context.fill(x1, y1, x1 + 1, y1 + 1, color);

            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    // Settings setters
    public void setRoomWidth(double roomWidth) { this.roomWidth = roomWidth; }
    public void setDoorWidth(double doorWidth) { this.doorWidth = doorWidth; }
    public void setRoomSizePercent(float v) { this.roomWidth = v; }
    public void setDoorSizePercent(float v) { this.doorWidth = v; }
    public void setRenderCheckmarks(boolean v) { this.renderCheckmarks = v; }
    public void setRenderPuzzleIcons(boolean v) { this.renderPuzzleIcons = v; }
    public void setRenderPlayerNames(boolean v) { this.renderPlayerNames = v; }
    public void setRenderRoomNames(boolean v) { this.renderRoomNames = v; }
    public void setRenderUnknownRooms(boolean v) { this.renderUnknownRooms = v; }
    public void setMarkerScale(float v) { this.markerScale = v; }
    public void setNameScale(float v) { this.nameScale = v; }
    public void setMapSize(int width, int height) { /* Map size handled by render params */ }
    public void invalidate() { /* Force redraw - handled by DevMapState */ DevMapState.getInstance().invalidate(); }
}
