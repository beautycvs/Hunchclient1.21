package dev.hunchclient.module.impl.dungeons.devmap;

import dev.hunchclient.gui.skeet.SkeetTheme;
import dev.hunchclient.module.impl.dungeons.devmap.Coordinates.*;
import dev.hunchclient.module.impl.dungeons.devmap.MapEnums.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.*;


public class DevMapRenderer {

    private static final Minecraft mc = Minecraft.getInstance();

    // Cached images
    private static final Map<String, BufferedImage> CHECKMARKS = new HashMap<>();
    private static final Map<String, BufferedImage> PUZZLE_ICONS = new HashMap<>();
    private static BufferedImage MARKER_SELF;
    private static BufferedImage MARKER_OTHER;

    // Render settings
    private double roomWidth = 0.8;
    private double doorWidth = 0.4;
    private boolean renderCheckmarks = true;
    private boolean renderPuzzleIcons = true;
    private boolean renderRoomNames = false;
    private boolean renderSecretCount = false;
    private boolean renderUnknownRooms = false;
    private double iconSize = 0.6;
    private double textSize = 0.8;
    private boolean textShadow = true;
    private boolean colorRoomName = true;
    private double unknownRoomsDarken = 0.7;

    // Colors (Skeet-styled)
    private final Map<DungeonMapColor, Color> colors = new HashMap<>();

    static {
        loadAssets();
    }

    public DevMapRenderer() {
        initColors();
    }

    private void initColors() {
        // Room colors (dark, muted for Skeet style)
        colors.put(DungeonMapColor.ROOM_ENTRANCE, new Color(0x2D8B2D));
        colors.put(DungeonMapColor.ROOM_NORMAL, new Color(0x6B4423));
        colors.put(DungeonMapColor.ROOM_MINIBOSS, new Color(0x8B5A2B));
        colors.put(DungeonMapColor.ROOM_FAIRY, new Color(0xDB7093));
        colors.put(DungeonMapColor.ROOM_BLOOD, new Color(0xB22222));
        colors.put(DungeonMapColor.ROOM_PUZZLE, new Color(0x9932CC));
        colors.put(DungeonMapColor.ROOM_TRAP, new Color(0xCD853F));
        colors.put(DungeonMapColor.ROOM_YELLOW, new Color(0xE2E232));
        colors.put(DungeonMapColor.ROOM_RARE, new Color(0xFFD700));
        colors.put(DungeonMapColor.ROOM_UNKNOWN, new Color(0x3A3A3A));
        colors.put(DungeonMapColor.BACKGROUND, new Color(0x171717));

        // Door colors
        colors.put(DungeonMapColor.DOOR_ENTRANCE, new Color(0x228B22));
        colors.put(DungeonMapColor.DOOR_WITHER, new Color(0x1A1A1A));
        colors.put(DungeonMapColor.DOOR_BLOOD, new Color(0xB22222));
    }

    private static void loadAssets() {
        // Load checkmarks - custom checkmarks
        CHECKMARKS.put("white", loadImage("/assets/hunchclient/textures/dungeons/map/white_check.png"));
        CHECKMARKS.put("green", loadImage("/assets/hunchclient/textures/dungeons/map/green_check.png"));
        CHECKMARKS.put("failed", loadImage("/assets/hunchclient/textures/dungeons/map/failed_room.png"));
        CHECKMARKS.put("question", loadImage("/assets/hunchclient/textures/dungeons/map/question_mark.png"));

        // Load puzzle icons - using better Vanilla MC icons
        PUZZLE_ICONS.put("Creeper Beams", loadImage("/assets/hunchclient/textures/dungeons/map/creeper_head.png"));
        PUZZLE_ICONS.put("Three Weirdos", loadImage("/assets/hunchclient/textures/dungeons/map/chest.png"));
        PUZZLE_ICONS.put("Tic Tac Toe", loadImage("/assets/hunchclient/textures/dungeons/map/comparator.png"));
        PUZZLE_ICONS.put("Water Board", loadImage("/assets/hunchclient/textures/dungeons/map/bucket_water.png"));
        PUZZLE_ICONS.put("Teleport Maze", loadImage("/assets/hunchclient/textures/dungeons/map/ender_pearl.png"));
        PUZZLE_ICONS.put("Blaze", loadImage("/assets/hunchclient/textures/dungeons/map/blaze_rod.png"));
        PUZZLE_ICONS.put("Boulder", loadImage("/assets/hunchclient/textures/dungeons/map/cobblestone.png"));
        PUZZLE_ICONS.put("Ice Fill", loadImage("/assets/hunchclient/textures/dungeons/map/blue_ice.png"));
        PUZZLE_ICONS.put("Ice Path", loadImage("/assets/hunchclient/textures/dungeons/map/leather_boots.png"));
        PUZZLE_ICONS.put("Quiz", loadImage("/assets/hunchclient/textures/dungeons/map/enchanted_book.png"));

        // Load markers - custom arrow markers
        MARKER_SELF = loadImage("/assets/hunchclient/textures/dungeons/map/marker_self.png");
        MARKER_OTHER = loadImage("/assets/hunchclient/textures/dungeons/map/marker_other.png");
    }

    private static BufferedImage loadImage(String path) {
        try {
            InputStream stream = DevMapRenderer.class.getResourceAsStream(path);
            if (stream != null) {
                return ImageIO.read(stream);
            }
        } catch (Exception e) {
            System.err.println("[DevMapRenderer] Error loading image: " + path);
        }
        return null;
    }

    /**
     * Render the map to a BufferedImage
     */
    public BufferedImage renderToImage(int width, int height, DevMapState state) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        FloorType floor = state.getFloor();
        if (floor == FloorType.NONE) {
            g.dispose();
            return img;
        }

        int dungeonW = floor.roomsW;
        int dungeonH = floor.roomsH;

        double roomRectOffset = (1.0 - roomWidth) * 0.5;
        double compToBImgF = min((double) width / dungeonW, (double) height / dungeonH);

        // Collect rooms to render
        Set<DevRoom> visitedRooms = new HashSet<>();
        DevRoom[] rooms = state.getRooms();
        DevDoor[] doors = state.getDoors();

        // Draw rooms
        for (int idx = 0; idx < 36; idx++) {
            DevRoom room = rooms[idx];
            if (room == null) continue;
            if (visitedRooms.contains(room)) continue;
            visitedRooms.add(room);

            drawRoom(g, room, compToBImgF, roomRectOffset, state);
        }

        // Draw doors
        for (DevDoor door : doors) {
            if (door == null) continue;
            drawDoor(g, door, compToBImgF, roomRectOffset, state);
        }

        g.dispose();
        return img;
    }

    private void drawRoom(Graphics2D g, DevRoom room, double scale, double roomOffset, DevMapState state) {
        if (!room.isExplored() && !renderUnknownRooms) {
            // Only draw explored neighbors' parts
            return;
        }

        Color color = getColorForRoom(room, state);
        if (color == null) return;

        // Darken unexplored rooms
        if (!room.isExplored() && state.isDungeonStarted()) {
            color = darkenColor(color, unknownRoomsDarken);
        }

        g.setColor(color);

        List<WorldComponentPosition> cells = room.getComps();
        ShapeType shape = room.getShape();

        if (shape == ShapeType.UNKNOWN || cells.isEmpty()) return;

        // Draw based on shape
        switch (shape) {
            case SHAPE_L -> drawLShapedRoom(g, cells, scale, roomOffset);
            case SHAPE_1X1 -> drawSimpleRoom(g, cells.get(0), 1, 1, scale, roomOffset);
            case SHAPE_1X2, SHAPE_1X3, SHAPE_1X4 -> {
                WorldComponentPosition corner = getCorner(cells);
                boolean horizontal = cells.get(0).cx != cells.get(1).cx;
                int w = horizontal ? cells.size() : 1;
                int h = horizontal ? 1 : cells.size();
                drawSimpleRoom(g, corner, w, h, scale, roomOffset);
            }
            case SHAPE_2X2 -> {
                WorldComponentPosition corner = getCorner(cells);
                drawSimpleRoom(g, corner, 2, 2, scale, roomOffset);
            }
            default -> {
                // Fallback: draw each cell
                for (WorldComponentPosition cell : cells) {
                    drawSimpleRoom(g, cell, 1, 1, scale, roomOffset);
                }
            }
        }

        // Draw decoration (checkmark or puzzle icon)
        if (renderCheckmarks || renderPuzzleIcons) {
            drawRoomDecoration(g, room, cells, shape, scale, roomOffset);
        }
    }

    private void drawSimpleRoom(Graphics2D g, WorldComponentPosition corner, int w, int h, double scale, double roomOffset) {
        int cx = corner.cx / 2;
        int cz = corner.cz / 2;

        double px = cx + roomOffset;
        double pz = cz + roomOffset;
        double pw = roomWidth + w - 1;
        double ph = roomWidth + h - 1;

        int bx = (int) (scale * px);
        int bz = (int) (scale * pz);
        int bw = (int) ceil(scale * pw);
        int bh = (int) ceil(scale * ph);

        g.fillRoundRect(bx, bz, bw, bh, 2, 2);
    }

    private void drawLShapedRoom(Graphics2D g, List<WorldComponentPosition> cells, double scale, double roomOffset) {
        // Draw each cell and connections
        for (int i = 0; i < cells.size(); i++) {
            WorldComponentPosition cell = cells.get(i);
            int cx = cell.cx / 2;
            int cz = cell.cz / 2;

            // Draw cell
            drawSimpleRoom(g, cell, 1, 1, scale, roomOffset);

            // Draw connections to adjacent cells
            for (int j = i + 1; j < cells.size(); j++) {
                WorldComponentPosition other = cells.get(j);
                int ox = other.cx / 2;
                int oz = other.cz / 2;

                if (abs(cx - ox) + abs(cz - oz) == 1) {
                    drawRoomConnection(g, cx, cz, ox, oz, scale, roomOffset);
                }
            }
        }
    }

    private void drawRoomConnection(Graphics2D g, int cx1, int cz1, int cx2, int cz2, double scale, double roomOffset) {
        if (cx1 > cx2 || cz1 > cz2) {
            drawRoomConnection(g, cx2, cz2, cx1, cz1, scale, roomOffset);
            return;
        }

        double px, pz, pw, ph;
        if (cx1 == cx2) {
            // Vertical connection
            px = cx1 + roomOffset;
            pz = cz1 + roomOffset + roomWidth;
            pw = roomWidth;
            ph = roomOffset * 2.0;
        } else {
            // Horizontal connection
            px = cx1 + roomOffset + roomWidth;
            pz = cz1 + roomOffset;
            pw = roomOffset * 2.0;
            ph = roomWidth;
        }

        int bx = (int) (scale * px);
        int bz = (int) (scale * pz);
        int bw = (int) ceil(scale * pw);
        int bh = (int) ceil(scale * ph);

        g.fillRect(bx, bz, bw, bh);
    }

    private void drawDoor(Graphics2D g, DevDoor door, double scale, double roomOffset, DevMapState state) {
        // Skip if both rooms are unexplored and we don't render unknown
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

        Color color = getColorForDoor(door, state);
        if (color == null) return;

        g.setColor(color);

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

        int bx = (int) (scale * px);
        int bz = (int) (scale * pz);
        int bw = (int) ceil(scale * pw);
        int bh = (int) ceil(scale * ph);

        g.fillRect(bx, bz, bw, bh);
    }

    private void drawRoomDecoration(Graphics2D g, DevRoom room, List<WorldComponentPosition> cells, ShapeType shape, double scale, double roomOffset) {
        BufferedImage icon = null;

        // Get checkmark
        if (renderCheckmarks) {
            icon = switch (room.getCheckmark()) {
                case WHITE -> CHECKMARKS.get("white");
                case GREEN -> CHECKMARKS.get("green");
                case FAILED -> CHECKMARKS.get("failed");
                case UNEXPLORED -> renderUnknownRooms ? CHECKMARKS.get("question") : null;
                default -> null;
            };
        }

        // Get puzzle icon if no checkmark
        if (icon == null && renderPuzzleIcons && room.getName() != null) {
            icon = PUZZLE_ICONS.get(room.getName());
        }

        if (icon == null) return;

        // Calculate center position
        double[] center = getRoomCenter(cells, shape);
        double iconW = iconSize * roomWidth;

        double px = center[0] - iconW * 0.5;
        double pz = center[1] - iconW * 0.5;

        int bx = (int) (scale * px);
        int bz = (int) (scale * pz);
        int bw = (int) ceil(scale * iconW);
        int bh = (int) ceil(scale * iconW);

        g.drawImage(icon, bx, bz, bw, bh, null);
    }

    private double[] getRoomCenter(List<WorldComponentPosition> cells, ShapeType shape) {
        if (shape == ShapeType.SHAPE_L) {
            // For L-shaped rooms, find the "elbow" cell
            cells = new ArrayList<>(cells);
            cells.sort(Comparator.comparingInt(a -> a.cx + a.cz * 11));

            int idx;
            if (cells.get(0).cx > cells.get(1).cx) {
                idx = 2;
            } else if (cells.get(0).cx == cells.get(2).cx) {
                idx = 0;
            } else {
                idx = 1;
            }

            WorldComponentPosition cell = cells.get(idx);
            return new double[]{cell.cx / 2.0 + 0.5, cell.cz / 2.0 + 0.5};
        }

        // Default: center of all cells
        double sumX = 0, sumZ = 0;
        for (WorldComponentPosition cell : cells) {
            sumX += cell.cx / 2.0;
            sumZ += cell.cz / 2.0;
        }
        return new double[]{sumX / cells.size() + 0.5, sumZ / cells.size() + 0.5};
    }

    private Color getColorForRoom(DevRoom room, DevMapState state) {
        if (!room.isExplored() && !renderUnknownRooms) {
            return colors.get(DungeonMapColor.ROOM_UNKNOWN);
        }

        return switch (room.getType()) {
            case ENTRANCE -> colors.get(DungeonMapColor.ROOM_ENTRANCE);
            case NORMAL -> room.getClearType() == ClearType.MINIBOSS
                ? colors.get(DungeonMapColor.ROOM_MINIBOSS)
                : colors.get(DungeonMapColor.ROOM_NORMAL);
            case FAIRY -> colors.get(DungeonMapColor.ROOM_FAIRY);
            case BLOOD -> colors.get(DungeonMapColor.ROOM_BLOOD);
            case PUZZLE -> colors.get(DungeonMapColor.ROOM_PUZZLE);
            case TRAP -> colors.get(DungeonMapColor.ROOM_TRAP);
            case YELLOW -> colors.get(DungeonMapColor.ROOM_YELLOW);
            case RARE -> colors.get(DungeonMapColor.ROOM_RARE);
            default -> colors.get(DungeonMapColor.ROOM_UNKNOWN);
        };
    }

    private Color getColorForDoor(DevDoor door, DevMapState state) {
        if (!door.isOpened() && door.getType() == DoorType.NORMAL) {
            return null; // Don't draw closed normal doors
        }

        return switch (door.getType()) {
            case ENTRANCE -> colors.get(DungeonMapColor.DOOR_ENTRANCE);
            case WITHER -> colors.get(DungeonMapColor.DOOR_WITHER);
            case BLOOD -> colors.get(DungeonMapColor.DOOR_BLOOD);
            case NORMAL -> {
                // Use the color of the most important connected room
                DevRoom bestRoom = null;
                int bestPrio = Integer.MAX_VALUE;
                for (DevRoom room : door.getRooms()) {
                    if (room.getType().priority < bestPrio) {
                        bestPrio = room.getType().priority;
                        bestRoom = room;
                    }
                }
                yield bestRoom != null ? getColorForRoom(bestRoom, state) : colors.get(DungeonMapColor.ROOM_NORMAL);
            }
        };
    }

    private Color darkenColor(Color color, double factor) {
        return new Color(
            (int) (color.getRed() * factor),
            (int) (color.getGreen() * factor),
            (int) (color.getBlue() * factor),
            color.getAlpha()
        );
    }

    private WorldComponentPosition getCorner(List<WorldComponentPosition> cells) {
        return cells.stream()
            .min(Comparator.comparingInt(a -> a.cx + a.cz))
            .orElse(cells.get(0));
    }

    // Setters for render options

    public void setRoomWidth(double roomWidth) {
        this.roomWidth = roomWidth;
    }

    public void setDoorWidth(double doorWidth) {
        this.doorWidth = doorWidth;
    }

    public void setRenderCheckmarks(boolean renderCheckmarks) {
        this.renderCheckmarks = renderCheckmarks;
    }

    public void setRenderPuzzleIcons(boolean renderPuzzleIcons) {
        this.renderPuzzleIcons = renderPuzzleIcons;
    }

    public void setRenderRoomNames(boolean renderRoomNames) {
        this.renderRoomNames = renderRoomNames;
    }

    public void setRenderSecretCount(boolean renderSecretCount) {
        this.renderSecretCount = renderSecretCount;
    }

    public void setRenderUnknownRooms(boolean renderUnknownRooms) {
        this.renderUnknownRooms = renderUnknownRooms;
    }

    public void setIconSize(double iconSize) {
        this.iconSize = iconSize;
    }

    public void setTextSize(double textSize) {
        this.textSize = textSize;
    }

    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
    }

    public void setColorRoomName(boolean colorRoomName) {
        this.colorRoomName = colorRoomName;
    }

    public void setUnknownRoomsDarken(double unknownRoomsDarken) {
        this.unknownRoomsDarken = unknownRoomsDarken;
    }

    public void setColor(DungeonMapColor key, Color color) {
        colors.put(key, color);
    }
}
