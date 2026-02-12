package dev.hunchclient.module.impl.dungeons.secrets;

import static dev.hunchclient.module.impl.dungeons.secrets.DungeonMapUtils.*;

import dev.hunchclient.HunchClient;
import dev.hunchclient.render.RenderContext;
import dev.hunchclient.util.DungeonUtils;
import dev.hunchclient.util.Scheduler;
import dev.hunchclient.util.Tickable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/**
 * Dungeon Manager - Ported 1:1 from Skyblocker
 * Manages dungeon room detection, secret waypoints, and minepoints
 */
public class DungeonManager {
	protected static final Logger LOGGER = LoggerFactory.getLogger(DungeonManager.class);
	private static final String DUNGEONS_PATH = "dungeons";
	private static Path CUSTOM_WAYPOINTS_DIR;
	private static Path CUSTOM_MINEPOINTS_DIR; // NEW: For minepoints
	private static Path BOSS_MINEPOINTS_FILE; // NEW: Boss minepoints
	private static final Pattern KEY_FOUND = Pattern.compile("^RIGHT CLICK on (?:the BLOOD DOOR|a WITHER door) to open it. This key can only be used to open 1 door!$");
	private static final Pattern WITHER_DOOR_OPENED = Pattern.compile("^\\w+ opened a WITHER door!$");
	private static final String BLOOD_DOOR_OPENED = "The BLOOD DOOR has been opened!";
	protected static final float[] RED_COLOR_COMPONENTS = {1, 0, 0};
	protected static final float[] GREEN_COLOR_COMPONENTS = {0, 1, 0};

	protected static final Object2ByteMap<String> NUMERIC_ID = Object2ByteMaps.unmodifiable(new Object2ByteOpenHashMap<>(Map.ofEntries(
			Map.entry("minecraft:stone", (byte) 1),
			Map.entry("minecraft:diorite", (byte) 2),
			Map.entry("minecraft:polished_diorite", (byte) 3),
			Map.entry("minecraft:andesite", (byte) 4),
			Map.entry("minecraft:polished_andesite", (byte) 5),
			Map.entry("minecraft:grass_block", (byte) 6),
			Map.entry("minecraft:dirt", (byte) 7),
			Map.entry("minecraft:coarse_dirt", (byte) 8),
			Map.entry("minecraft:cobblestone", (byte) 9),
			Map.entry("minecraft:bedrock", (byte) 10),
			Map.entry("minecraft:oak_leaves", (byte) 11),
			Map.entry("minecraft:gray_wool", (byte) 12),
			Map.entry("minecraft:double_stone_slab", (byte) 13),
			Map.entry("minecraft:mossy_cobblestone", (byte) 14),
			Map.entry("minecraft:clay", (byte) 15),
			Map.entry("minecraft:stone_bricks", (byte) 16),
			Map.entry("minecraft:mossy_stone_bricks", (byte) 17),
			Map.entry("minecraft:chiseled_stone_bricks", (byte) 18),
			Map.entry("minecraft:gray_terracotta", (byte) 19),
			Map.entry("minecraft:cyan_terracotta", (byte) 20),
			Map.entry("minecraft:black_terracotta", (byte) 21)
	)));

	protected static final HashMap<String, Map<String, Map<String, int[]>>> ROOMS_DATA = new HashMap<>();
	@NotNull
	private static final Map<Vector2ic, Room> rooms = new HashMap<>();
	private static final Map<String, JsonElement> roomsJson = new HashMap<>();
	private static final Map<String, JsonElement> waypointsJson = new HashMap<>();
	private static final Map<String, JsonElement> minepointsJson = new HashMap<>(); // NEW: For minepoints
	private static final Map<String, JsonElement> routesJson = new HashMap<>(); // NEW: For secret routes
	private static final Table<String, BlockPos, SecretWaypoint> customWaypoints = HashBasedTable.create();
	private static final Table<String, BlockPos, CustomMinePoint> customMinepoints = HashBasedTable.create(); // NEW
	@Nullable
	private static CompletableFuture<Void> roomsLoaded;
	private static final AtomicBoolean loadStarted = new AtomicBoolean(false);
	@Nullable
	private static Vector2ic mapEntrancePos;
	private static int mapRoomSize;
	@Nullable
	private static Vector2ic physicalEntrancePos;
	private static Room currentRoom;
	@NotNull
	private static DungeonBoss boss = DungeonBoss.NONE;
	@Nullable
	private static AABB bloodRushDoorBox;
	private static boolean bloodOpened;
	private static boolean hasKey;

	// NEW: AllRoomScanner for detecting all rooms (including undiscovered ones)
	@Nullable
	private static AllRoomScanner allRoomScanner = null;
	private static boolean scannerInitialized = false;

	// NEW: Noamm-style dungeon system (physical scanner + map parser)
	@Nullable
	private static dev.hunchclient.module.impl.dungeons.NoammDungeonSystem noammSystem = null;

	// NEW: Boss minepoints (absolute coordinates)
	private static final List<MinePoint> bossMinepoints = new ArrayList<>();

	// Cached MapIdComponent to prevent room detection from breaking when holding a bow (item becomes feather)
	private static final MapId DEFAULT_MAP_ID_COMPONENT = new MapId(1024);
	private static MapId cachedMapIdComponent = null;

	// Fake map injection to replace feather when holding bow
	private static ItemStack fakeMapStack = null;
	private static ItemStack originalStack = null;
	private static boolean hasInjectedFakeMap = false;

	// Track if we were in a dungeon last tick (to detect dungeon exit)
	private static boolean wasInDungeonLastTick = false;

	public static boolean isRoomsLoaded() {
		return roomsLoaded != null && roomsLoaded.isDone();
	}

	public static Collection<Room> getRoomsStream() {
		return rooms.values();
	}

	public static JsonObject getRoomMetadata(String room) {
		JsonElement value = roomsJson.get(room);
		return value != null ? value.getAsJsonObject() : null;
	}

	public static JsonArray getRoomWaypoints(String room) {
		JsonElement value = waypointsJson.get(room);
		return value != null ? value.getAsJsonArray() : null;
	}

	// NEW: Get minepoints for a room
	public static JsonArray getRoomMinePoints(String room) {
		JsonElement value = minepointsJson.get(room);
		return value != null ? value.getAsJsonArray() : null;
	}

	// NEW: Get secret routes for a room
	public static JsonArray getRoomRoutes(String room) {
		JsonElement value = routesJson.get(room);
		return value != null ? value.getAsJsonArray() : null;
	}

	public static Map<BlockPos, SecretWaypoint> getCustomWaypoints(String room) {
		return customWaypoints.row(room);
	}

	public static SecretWaypoint addCustomWaypoint(String room, SecretWaypoint waypoint) {
		return customWaypoints.put(room, waypoint.pos, waypoint);
	}

	public static void addCustomWaypoints(String room, Collection<SecretWaypoint> waypoints) {
		for (SecretWaypoint waypoint : waypoints) {
			addCustomWaypoint(room, waypoint);
		}
	}

	@Nullable
	public static SecretWaypoint removeCustomWaypoint(String room, BlockPos pos) {
		return customWaypoints.remove(room, pos);
	}

	// NEW: Minepoint management
	public static Map<BlockPos, CustomMinePoint> getCustomMinepoints(String room) {
		return customMinepoints.row(room);
	}

	public static void addCustomMinepoint(String room, int index, BlockPos relativePos, String name, Room.Direction direction) {
		customMinepoints.put(room, relativePos, new CustomMinePoint(index, relativePos, name, direction));
	}

	public static void removeCustomMinepoint(String room, BlockPos relativePos) {
		customMinepoints.remove(room, relativePos);
	}

	public static void clearCustomMinepointsForRoom(String room) {
		customMinepoints.row(room).clear();
	}

	public static void clearAllCustomMinepoints() {
		customMinepoints.clear();
	}

	public static int getCustomMinepointCount(String room) {
		return customMinepoints.row(room).size();
	}

	public static List<MinePoint> getBossMinepoints() {
		return bossMinepoints;
	}

	@Nullable
	public static Vector2ic getMapEntrancePos() {
		return mapEntrancePos;
	}

	public record CustomMinePoint(int index, BlockPos relativePos, String name, Room.Direction direction) {}

	public static int getMapRoomSize() {
		return mapRoomSize;
	}

	@Nullable
	public static Vector2ic getPhysicalEntrancePos() {
		return physicalEntrancePos;
	}

	public static Room getCurrentRoom() {
		return currentRoom;
	}

	/**
	 * NEW: Get the AllRoomScanner instance (may be null if not initialized yet)
	 */
	@Nullable
	public static AllRoomScanner getAllRoomScanner() {
		return allRoomScanner;
	}

	/**
	 * NEW: Get the NoammDungeonSystem instance (may be null if not initialized yet)
	 */
	@Nullable
	public static dev.hunchclient.module.impl.dungeons.NoammDungeonSystem getNoammSystem() {
		return noammSystem;
	}

	/**
	 * NEW: Find any matched room that contains the given block position
	 * This is more reliable than getCurrentRoom() for block marking
	 */
	@Nullable
	public static Room getMatchedRoomAt(BlockPos blockPos) {
		if (physicalEntrancePos == null || mapEntrancePos == null) {
			return null;
		}

		// Calculate which physical room segment this block is in
		Vector2ic physicalPos = DungeonMapUtils.getPhysicalRoomPos(blockPos.getCenter());
		Room room = rooms.get(physicalPos);

		// Return room only if it's matched
		if (room != null && room.isMatched()) {
			return room;
		}

		// Fallback: check currentRoom if it's matched
		if (currentRoom != null && currentRoom.isMatched()) {
			return currentRoom;
		}

		return null;
	}

	@NotNull
	public static DungeonBoss getBoss() {
		return boss;
	}

	public static boolean isInBoss() {
		// Check both: message-based detection AND scoreboard-based detection
		return boss.isInBoss() || DungeonUtils.isInBossFight();
	}

	public static void init() {
		CUSTOM_WAYPOINTS_DIR = Paths.get("config", "hunchclient", "custom_secret_waypoints.json");
		CUSTOM_MINEPOINTS_DIR = Paths.get("config", "hunchclient", "custom_minepoints.json"); // NEW
		BOSS_MINEPOINTS_FILE = Paths.get("config", "hunchclient", "boss_minepoints.json"); // NEW

		// Load is deferred until a world/session is active to reduce client startup time.
		ClientLifecycleEvents.CLIENT_STOPPING.register(DungeonManager::saveCustomWaypoints);
		// PERFORMANCE: Reduced from 5 ticks to 20 ticks (1 second) - still responsive but 4x less CPU usage
		Scheduler.INSTANCE.scheduleCyclic(DungeonManager::update, 20);
		// WorldRenderExtractionCallback will be registered by BossBlockMinerModule
		ClientReceiveMessageEvents.ALLOW_GAME.register(DungeonManager::onChatMessage);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> onUseBlock(world, hitResult));
		ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) -> {
			ensureLoaded();
			reset();
		}));

		// CRITICAL FIX: Reset DungeonManager when leaving world (fixes SecretRoutes not loading after first dungeon)
		ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> reset()));
	}

	/**
	 * Ensure dungeon data is loaded once (deferred).
	 */
	public static void ensureLoaded() {
		if (roomsLoaded != null || loadStarted.get()) {
			return;
		}
		if (!loadStarted.compareAndSet(false, true)) {
			return;
		}

		// Execute with MinecraftClient as executor
		CompletableFuture.runAsync(DungeonManager::load, Minecraft.getInstance()).exceptionally(e -> {
			LOGGER.error("[Dungeon Secrets] Failed to load dungeon secrets", e);
			return null;
		});
	}

	private static void load() {
		long startTime = System.currentTimeMillis();
		List<CompletableFuture<Void>> dungeonFutures = new ArrayList<>();
		ROOMS_DATA.clear();
		roomsJson.clear();
		waypointsJson.clear();
		minepointsJson.clear();
		routesJson.clear();

		// Load .skeleton files (room block data)
		for (Map.Entry<ResourceLocation, Resource> resourceEntry : Minecraft.getInstance().getResourceManager().listResources(DUNGEONS_PATH, id -> id.getPath().endsWith(".skeleton")).entrySet()) {
			String[] path = resourceEntry.getKey().getPath().split("/");
			if (path.length != 4) {
				LOGGER.error("[Dungeon Secrets] Failed to load dungeon secrets, invalid resource identifier {}", resourceEntry.getKey());
				break;
			}
			String dungeon = path[1];
			String roomShape = path[2];
			String room = path[3].substring(0, path[3].length() - ".skeleton".length());
			ROOMS_DATA.computeIfAbsent(dungeon, dungeonKey -> new HashMap<>());
			ROOMS_DATA.get(dungeon).computeIfAbsent(roomShape, roomShapeKey -> new HashMap<>());
			dungeonFutures.add(CompletableFuture.supplyAsync(() -> readRoom(resourceEntry.getValue())).thenAcceptAsync(roomData -> {
				Map<String, int[]> roomsMap = ROOMS_DATA.get(dungeon).get(roomShape);
				synchronized (roomsMap) {
					roomsMap.put(room, roomData);
				}
				LOGGER.debug("[Dungeon Secrets] Loaded dungeon secrets dungeon {} room shape {} room {}", dungeon, roomShape, room);
			}).exceptionally(e -> {
				LOGGER.error("[Dungeon Secrets] Failed to load dungeon secrets dungeon {} room shape {} room {}", dungeon, roomShape, room, e);
				return null;
			}));
		}

		// Load JSON files
		dungeonFutures.add(CompletableFuture.runAsync(() -> {
			try {
				// Load dungeonrooms.json
				BufferedReader roomsReader = Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("hunchclient", "dungeons/dungeonrooms.json"));
				loadJson(roomsReader, roomsJson);
				roomsReader.close();

				// Load secretlocations.json
				BufferedReader waypointsReader = Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("hunchclient", "dungeons/secretlocations.json"));
				loadJson(waypointsReader, waypointsJson);
				waypointsReader.close();

				// NEW: Load minepoints.json (optional, may not exist yet)
				try {
					BufferedReader minepointsReader = Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("hunchclient", "dungeons/minepoints.json"));
					loadJson(minepointsReader, minepointsJson);
					minepointsReader.close();
					LOGGER.debug("[Dungeon Secrets] Loaded minepoints json");
				} catch (Exception ignored) {
					LOGGER.info("[Dungeon Secrets] No minepoints.json found (expected for first run)");
				}

				// NEW: Load routes.json (SecretRoutes format)
				try {
					BufferedReader routesReader = Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("hunchclient", "dungeons/routes.json"));
					loadJson(routesReader, routesJson);
					routesReader.close();
					LOGGER.info("[Dungeon Secrets] Loaded {} secret routes", routesJson.size());
				} catch (Exception e) {
					LOGGER.warn("[Dungeon Secrets] No routes.json found - secret routes will not be available", e);
				}

				LOGGER.debug("[Dungeon Secrets] Loaded dungeon secret waypoints json");
			} catch (Exception e) {
				LOGGER.error("[Dungeon Secrets] Failed to load dungeon secret waypoints json", e);
			}
		}));

		// Load custom waypoints
		dungeonFutures.add(CompletableFuture.runAsync(() -> {
			try (BufferedReader customWaypointsReader = Files.newBufferedReader(CUSTOM_WAYPOINTS_DIR)) {
				com.google.gson.Gson gson = new com.google.gson.Gson();
				gson.fromJson(customWaypointsReader, JsonObject.class).asMap().forEach((room, waypointsJsonElement) -> {
					// Parse custom waypoints
					// TODO: Implement proper codec parsing if needed
				});
				LOGGER.debug("[Dungeon Secrets] Loaded custom dungeon secret waypoints");
			} catch (NoSuchFileException ignored) {
			} catch (Exception e) {
				LOGGER.error("[Dungeon Secrets] Failed to load custom dungeon secret waypoints", e);
			}
		}));

		// NEW: Load custom minepoints
		dungeonFutures.add(CompletableFuture.runAsync(() -> {
			try (BufferedReader customMinepointsReader = Files.newBufferedReader(CUSTOM_MINEPOINTS_DIR)) {
				JsonObject root = HunchClient.GSON.fromJson(customMinepointsReader, JsonObject.class);
				if (root != null) {
					customMinepoints.clear();
					int totalLoaded = 0;
					for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
						String room = entry.getKey();
						JsonElement value = entry.getValue();
						if (!value.isJsonArray()) continue;
						JsonArray array = value.getAsJsonArray();
						int roomCount = 0;
						for (JsonElement element : array) {
							if (!element.isJsonObject()) continue;
							JsonObject pointObj = element.getAsJsonObject();
							int index = pointObj.has("index") ? pointObj.get("index").getAsInt() : customMinepointIndex(room);
							String name = pointObj.has("name") ? pointObj.get("name").getAsString() : ("Custom " + index);
							BlockPos relativePos = new BlockPos(
									pointObj.get("x").getAsInt(),
									pointObj.get("y").getAsInt(),
									pointObj.get("z").getAsInt()
							);
							// Load direction from JSON, default to NW for backwards compatibility
							Room.Direction direction = Room.Direction.NW;
							if (pointObj.has("direction")) {
								String dirStr = pointObj.get("direction").getAsString();
								direction = switch (dirStr) {
									case "NW" -> Room.Direction.NW;
									case "NE" -> Room.Direction.NE;
									case "SE" -> Room.Direction.SE;
									case "SW" -> Room.Direction.SW;
									default -> Room.Direction.NW;
								};
							}
							customMinepoints.put(room, relativePos, new CustomMinePoint(index, relativePos, name, direction));
							roomCount++;
							totalLoaded++;
						}
						LOGGER.info("[Dungeon Secrets] Loaded {} custom minepoints for room '{}'", roomCount, room);
					}
					LOGGER.info("[Dungeon Secrets] Loaded {} total custom minepoints from {} rooms", totalLoaded, root.size());
				}
			} catch (NoSuchFileException ignored) {
				LOGGER.info("[Dungeon Secrets] No custom minepoints file found (first time setup)");
			} catch (Exception e) {
				LOGGER.error("[Dungeon Secrets] Failed to load custom minepoints", e);
			}
		}));

		// NEW: Load boss minepoints (absolute coordinates)
		dungeonFutures.add(CompletableFuture.runAsync(DungeonManager::loadBossMinepointsFromDisk));

		roomsLoaded = CompletableFuture.allOf(dungeonFutures.toArray(CompletableFuture[]::new)).thenRun(() ->
				LOGGER.info("[Dungeon Secrets] Loaded dungeon secrets for {} dungeon(s), {} room shapes, {} rooms, and {} custom secret waypoints total in {} ms",
						ROOMS_DATA.size(),
						ROOMS_DATA.values().stream().mapToInt(Map::size).sum(),
						ROOMS_DATA.values().stream().map(Map::values).flatMap(Collection::stream).mapToInt(Map::size).sum(),
						customWaypoints.size(),
						System.currentTimeMillis() - startTime))
				.exceptionally(e -> {
					LOGGER.error("[Dungeon Secrets] Failed to load dungeon secrets", e);
					return null;
				});
		LOGGER.info("[Dungeon Secrets] Started loading dungeon secrets in (blocked main thread for) {} ms", System.currentTimeMillis() - startTime);
	}

	public static void saveCustomWaypoints(Minecraft client) {
		com.google.gson.Gson gson = new com.google.gson.Gson();
		try (BufferedWriter writer = Files.newBufferedWriter(CUSTOM_WAYPOINTS_DIR)) {
			JsonObject customWaypointsJsonObject = new JsonObject();
			customWaypoints.rowMap().forEach((room, waypoints) -> {
				// TODO: Implement proper codec encoding
			});
			gson.toJson(customWaypointsJsonObject, writer);
			LOGGER.info("[Dungeon Secrets] Saved custom dungeon secret waypoints");
		} catch (Exception e) {
			LOGGER.error("[Dungeon Secrets] Failed to save custom dungeon secret waypoints", e);
		}

		// NEW: Save custom minepoints
		try {
			Files.createDirectories(CUSTOM_MINEPOINTS_DIR.getParent());
		} catch (IOException e) {
			LOGGER.error("[Dungeon Secrets] Failed to create directories for custom minepoints", e);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(CUSTOM_MINEPOINTS_DIR)) {
			JsonObject customMinepointsJsonObject = new JsonObject();
			customMinepoints.rowMap().forEach((room, minepoints) -> {
				if (minepoints.isEmpty()) {
					return;
				}
				JsonArray array = new JsonArray();
				for (CustomMinePoint customMinePoint : minepoints.values()) {
					JsonObject obj = new JsonObject();
					obj.addProperty("index", customMinePoint.index());
					obj.addProperty("name", customMinePoint.name());
					obj.addProperty("x", customMinePoint.relativePos().getX());
					obj.addProperty("y", customMinePoint.relativePos().getY());
					obj.addProperty("z", customMinePoint.relativePos().getZ());
					// Save direction as string (NW, NE, SE, SW)
					String dirStr = switch (customMinePoint.direction()) {
						case NW -> "NW";
						case NE -> "NE";
						case SE -> "SE";
						case SW -> "SW";
					};
					obj.addProperty("direction", dirStr);
					obj.addProperty("custom", true);
					array.add(obj);
				}
				customMinepointsJsonObject.add(room, array);
			});
			HunchClient.GSON.toJson(customMinepointsJsonObject, writer);
			LOGGER.info("[Dungeon Secrets] Saved custom minepoints");
		} catch (Exception e) {
			LOGGER.error("[Dungeon Secrets] Failed to save custom minepoints", e);
		}

		// NEW: Save boss minepoints
		try {
			Files.createDirectories(BOSS_MINEPOINTS_FILE.getParent());
		} catch (IOException e) {
			LOGGER.error("[Dungeon Secrets] Failed to create directories for boss minepoints", e);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(BOSS_MINEPOINTS_FILE)) {
			JsonArray array = new JsonArray();
			for (MinePoint minePoint : bossMinepoints) {
				JsonObject obj = new JsonObject();
				obj.addProperty("name", minePoint.getName());
				obj.addProperty("x", minePoint.pos.getX());
				obj.addProperty("y", minePoint.pos.getY());
				obj.addProperty("z", minePoint.pos.getZ());
				obj.addProperty("custom", true);
				array.add(obj);
			}
			HunchClient.GSON.toJson(array, writer);
			LOGGER.info("[Dungeon Secrets] Saved boss minepoints");
		} catch (Exception e) {
			LOGGER.error("[Dungeon Secrets] Failed to save boss minepoints", e);
		}
	}

	private static void loadBossMinepointsFromDisk() {
		if (BOSS_MINEPOINTS_FILE == null) {
			return;
		}
		try (BufferedReader bossReader = Files.newBufferedReader(BOSS_MINEPOINTS_FILE)) {
			JsonArray array = HunchClient.GSON.fromJson(bossReader, JsonArray.class);
			if (array != null) {
				bossMinepoints.clear();
				int index = 1;
				for (JsonElement element : array) {
					if (!element.isJsonObject()) continue;
					JsonObject obj = element.getAsJsonObject();
					BlockPos pos = new BlockPos(
							obj.get("x").getAsInt(),
							obj.get("y").getAsInt(),
							obj.get("z").getAsInt()
					);
					String name = obj.has("name") ? obj.get("name").getAsString() : ("Boss Mine " + index);
					MinePoint minePoint = new MinePoint(index++, pos, name, pos, true);
					bossMinepoints.add(minePoint);
				}
			}
			LOGGER.debug("[Dungeon Secrets] Loaded boss minepoints");
		} catch (NoSuchFileException ignored) {
		} catch (Exception e) {
			LOGGER.error("[Dungeon Secrets] Failed to load boss minepoints", e);
		}
	}

	private static int[] readRoom(Resource resource) throws RuntimeException {
		try (ObjectInputStream in = new ObjectInputStream(new InflaterInputStream(resource.open()))) {
			return (int[]) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static int customMinepointIndex(String room) {
		return 1000 + customMinepoints.row(room).size();
	}

	private static void loadJson(BufferedReader reader, Map<String, JsonElement> map) {
		com.google.gson.Gson gson = new com.google.gson.Gson();
		gson.fromJson(reader, JsonObject.class).asMap().forEach((room, jsonElement) ->
				map.put(room.toLowerCase(Locale.ENGLISH).replaceAll(" ", "-"), jsonElement));
	}

	public static Vec3 getMortArmorStandPos() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return null;

		for (var entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand armorStand) {
				Component name = armorStand.getCustomName();
				if (name != null && name.getString().contains("Mort")) {
					return new Vec3(armorStand.getX(), armorStand.getY(), armorStand.getZ());
				}
			}
		}
		return null;
	}

	/**
	 * Injects a fake dungeon map clientside when the item in slot 9 is not a filled map (e.g., feather when holding bow).
	 * This ensures the map is always available for room detection and rendering.
	 * Inspired by Skytils' approach.
	 *
	 * IMPORTANT: Only injects the map AFTER waypoints have been found (currentRoom is matched)
	 * to prevent interference with the map scanner during initial room detection.
	 */
	public static void injectFakeMapIfNeeded(Minecraft client) {
		if (client.player == null) {
			return;
		}

		// Check if player is holding a bow
		ItemStack mainHand = client.player.getMainHandItem();
		ItemStack offHand = client.player.getOffhandItem();
		boolean holdingBow = (mainHand.getItem() instanceof net.minecraft.world.item.BowItem) ||
		                     (offHand.getItem() instanceof net.minecraft.world.item.BowItem);

		// Clean up if not in dungeon or not holding bow
		if (!DungeonUtils.isInDungeon() || !holdingBow) {
			// Restore original item if we had injected
			if (hasInjectedFakeMap && client.player != null && originalStack != null) {
				client.player.getInventory().setItem(8, originalStack);
			}
			originalStack = null;
			fakeMapStack = null;
			hasInjectedFakeMap = false;
			return;
		}

		// FIX: Injiziere sobald wir eine gecachte Map-ID haben
		// Die alte Bedingung (currentRoom.isMatched()) verursachte ein Deadlock:
		// - Room kann ohne Map-Daten nicht matchen
		// - Map-Daten können ohne Injection nicht gelesen werden wenn Bogen gehalten wird
		// Jetzt: Injiziere sobald Map-ID einmal gesehen wurde (gecached)
		if (cachedMapIdComponent == null) {
			// Keine Map-ID gecached - können noch nicht injecten
			return;
		}

		ItemStack slot8 = client.player.getInventory().getItem(8);

		// If it's already a real filled map, don't override it
		if (slot8.is(Items.FILLED_MAP)) {
			hasInjectedFakeMap = false;
			return;
		}

		// Not a filled map - inject our fake map EVERY tick
		if (fakeMapStack == null) {
			fakeMapStack = new ItemStack(Items.FILLED_MAP);
			fakeMapStack.set(DataComponents.MAP_ID, cachedMapIdComponent);
		}

		// ALWAYS set the fake map every tick while holding bow (but only after waypoints are found)
		client.player.getInventory().setItem(8, fakeMapStack);
		hasInjectedFakeMap = true;
	}

	private static void update() {
		boolean inDungeonNow = DungeonUtils.isInDungeon();

		// CRITICAL FIX: Detect dungeon exit and reset DungeonManager
		// This fixes SecretRoutes not loading in subsequent dungeons
		if (wasInDungeonLastTick && !inDungeonNow) {
			LOGGER.info("[Dungeon Secrets] Exited dungeon, resetting DungeonManager...");
			reset();
		}
		wasInDungeonLastTick = inDungeonNow;

		if (!inDungeonNow) {
			return;
		}

		// Check if we just entered boss fight
		if (isInBoss()) {
			// Clear current room when entering boss so waypoints don't remain
			if (currentRoom != null) {
				LOGGER.info("[Dungeon Secrets] Entering boss fight, clearing current room: {}",
					currentRoom.getName() != null ? currentRoom.getName() : "unknown");
				currentRoom = null;
			}
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		// FIX: Aktiv nach Map-ID suchen sobald im Dungeon
		// Dies verhindert das Deadlock wenn Spieler von Anfang an Bogen hält
		ensureMapIdCached(client);

		if (physicalEntrancePos == null) {
			Vec3 mortPos = getMortArmorStandPos();
			if (mortPos == null) {
				LOGGER.warn("[Dungeon Secrets] Failed to find Mort armor stand, retrying...");
				return;
			}
			physicalEntrancePos = DungeonMapUtils.getPhysicalRoomPos(mortPos);
			currentRoom = newRoom(Room.Type.ENTRANCE, physicalEntrancePos);

			// Set the entrance direction based on Mort's position (he always faces the player)
			if (client.player != null) {
				currentRoom.setDirectionFromMort(mortPos, new Vec3(client.player.getX(), client.player.getY(), client.player.getZ()));
				LOGGER.info("[Dungeon Secrets] Created ENTRANCE room at physical pos {}, Mort pos was {}, direction: {}",
					physicalEntrancePos, mortPos, currentRoom.getDirection());
			} else {
				LOGGER.info("[Dungeon Secrets] Created ENTRANCE room at physical pos {}, Mort pos was {}", physicalEntrancePos, mortPos);
			}
		}

		MapItemSavedData map = getMapState(client);
		if (map == null) {
			return;
		}
		if (mapEntrancePos == null || mapRoomSize == 0) {
			ObjectIntPair<Vector2ic> mapEntrancePosAndSize = DungeonMapUtils.getMapEntrancePosAndRoomSize(map);
			if (mapEntrancePosAndSize == null) {
				return;
			}
			mapEntrancePos = mapEntrancePosAndSize.left();
			mapRoomSize = mapEntrancePosAndSize.rightInt();
			LOGGER.info("[Dungeon Secrets] Started dungeon with map room size {}, map entrance pos {}, player pos {}, and physical entrance pos {}",
					mapRoomSize, mapEntrancePos, new Vec3(client.player.getX(), client.player.getY(), client.player.getZ()), physicalEntrancePos);
		}

		// DISABLED: AllRoomScanner (ILLEGAL MODE - shows all grid positions)
		// We use noamm Physical Scanner instead which actually scans chunks!
		// if (!scannerInitialized && mapEntrancePos != null && mapRoomSize > 0 && physicalEntrancePos != null) {
		// 	allRoomScanner = new AllRoomScanner();
		// 	allRoomScanner.scanMap(map, mapEntrancePos, mapRoomSize, physicalEntrancePos)
		// 		.thenRun(() -> LOGGER.info("[Dungeon Secrets] AllRoomScanner completed: {}", allRoomScanner.getStats()));
		// 	scannerInitialized = true;
		// }

		// NEW: Initialize noamm system once map data is available
		if (noammSystem == null && mapRoomSize > 0) {
			noammSystem = new dev.hunchclient.module.impl.dungeons.NoammDungeonSystem();
			noammSystem.initialize(mapRoomSize);
			LOGGER.info("[Dungeon Secrets] NoammDungeonSystem initialized!");
		}

		// Update noamm system every tick
		if (noammSystem != null && noammSystem.isInitialized()) {
			noammSystem.update(map);
		}

		getBloodRushDoorPos(map);

		// Always update current room based on player position
		Vector2ic physicalPos = DungeonMapUtils.getPhysicalRoomPos(new Vec3(client.player.getX(), client.player.getY(), client.player.getZ()));
		Vector2ic mapPos = DungeonMapUtils.getMapPosFromPhysical(physicalEntrancePos, mapEntrancePos, mapRoomSize, physicalPos);
		Room room = rooms.get(physicalPos);
		if (room == null) {
			Room.Type type = DungeonMapUtils.getRoomType(map, mapPos);
			if (type == null || type == Room.Type.UNKNOWN) {
				LOGGER.debug("[Dungeon Secrets] Unknown room type at physical pos {} map pos {}", physicalPos, mapPos);
				return;
			}
			LOGGER.debug("[Dungeon Secrets] Creating new room type {} at physical pos {} map pos {}", type, physicalPos, mapPos);
			switch (type) {
				case ENTRANCE, PUZZLE, TRAP, MINIBOSS, FAIRY, BLOOD -> room = newRoom(type, physicalPos);
				case ROOM -> room = newRoom(type, DungeonMapUtils.getPhysicalPosFromMap(mapEntrancePos, mapRoomSize, physicalEntrancePos,
						DungeonMapUtils.getRoomSegments(map, mapPos, mapRoomSize, type.color)));
				case UNKNOWN -> {}  // Do nothing for unknown room type
			}
		}

		// Update current room whenever we find a valid room at player position
		if (room != null) {
			if (currentRoom != room) {
				LOGGER.info("[Dungeon Secrets] Room changed from {} to {}",
					currentRoom != null ? currentRoom.getName() : "null",
					room.getName());
				currentRoom = room;
			}
		}

		// Mark all secrets as found if checkmark is green
		if (currentRoom != null && currentRoom.getType() != Room.Type.ENTRANCE && currentRoom.isMatched() && !currentRoom.greenChecked &&
				getRoomCheckmarkColour(client, map, currentRoom) == DungeonMapUtils.GREEN_COLOR) {
			currentRoom.markAllSecrets(true);
			currentRoom.greenChecked = true;
		}

		if (currentRoom != null) {
			currentRoom.tick(client);
		}
	}

	@Nullable
	private static Room newRoom(Room.Type type, Vector2ic... physicalPositions) {
		try {
			Room newRoom = new Room(type, physicalPositions);
			for (Vector2ic physicalPos : physicalPositions) {
				rooms.put(physicalPos, newRoom);
			}
			return newRoom;
		} catch (IllegalArgumentException e) {
			LOGGER.error("[Dungeon Secrets] Failed to create room", e);
		}
		return null;
	}

	public static void extractRendering(RenderContext context) {
		if (shouldProcess() && currentRoom != null) {
			currentRoom.extractRendering(context);
		}

		// Render blood door highlight
		if (bloodRushDoorBox != null && !bloodOpened) {
			float[] colorComponents = hasKey ? GREEN_COLOR_COMPONENTS : RED_COLOR_COMPONENTS;
			context.submitFilledBox(bloodRushDoorBox, colorComponents, 0.5f, true);
		}

		// NEW: Render boss minepoints
		if (isInBoss()) {
			for (MinePoint minepoint : bossMinepoints) {
				if (minepoint.shouldRender()) {
					minepoint.extractRendering(context);
				}
			}
		}

		// NOTE: EnhancedDungeonMap rendering is now done via HUD in the module, not here!
	}

	private static boolean onChatMessage(Component text, boolean overlay) {
		if (!shouldProcess()) {
			return true;
		}

		String message = text.getString();

		if (isCurrentRoomMatched()) {
			currentRoom.onChatMessage(message);
		}

		// Process key found messages for door highlight
		if (!bloodOpened) {
			if (BLOOD_DOOR_OPENED.equals(message)) {
				bloodOpened = true;
			}
			if (KEY_FOUND.matcher(message).matches()) {
				hasKey = true;
			}
			if (WITHER_DOOR_OPENED.matcher(message).matches()) {
				hasKey = false;
			}
		}

		// Detect boss entry
		var newBoss = DungeonBoss.fromMessage(message);
		if (!isInBoss() && newBoss.isInBoss()) {
			LOGGER.info("[Dungeon Secrets] Boss detected from message: {}, clearing rooms", newBoss);
			// Clear current room to stop waypoint rendering
			currentRoom = null;
			reset();
			boss = newBoss;
		}

		return true;
	}

	private static InteractionResult onUseBlock(Level world, BlockHitResult hitResult) {
		if (isCurrentRoomMatched()) {
			currentRoom.onUseBlock(world, hitResult.getBlockPos());
		}
		return InteractionResult.PASS;
	}

	public static void onItemPickup(ItemEntity itemEntity) {
		Room room = getRoomAtPhysical(new Vec3(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ()));
		if (isRoomMatched(room)) {
			room.onItemPickup(itemEntity);
		}
	}

	public static void onBatRemoved(AmbientCreature bat) {
		Room room = getRoomAtPhysical(new Vec3(bat.getX(), bat.getY(), bat.getZ()));
		if (isRoomMatched(room)) {
			room.onBatRemoved(bat);
		}
	}

	public static void onBatDeathSound(Vec3 soundPos) {
		Room room = getRoomAtPhysical(soundPos);
		if (isRoomMatched(room)) {
			room.onBatDeathSound(soundPos);
		}
	}

	public static boolean markSecrets(int secretIndex, boolean found) {
		if (isCurrentRoomMatched()) {
			return currentRoom.markSecrets(secretIndex, found);
		}
		return false;
	}

	@Nullable
	private static Room getRoomAtPhysical(Vec3 pos) {
		return rooms.get(DungeonMapUtils.getPhysicalRoomPos(pos));
	}

	@Nullable
	private static Room getRoomAtPhysical(Vec3i pos) {
		return rooms.get(DungeonMapUtils.getPhysicalRoomPos(pos));
	}

	/**
	 * Gets the MapIdComponent from the item stack in slot 9.
	 * Caches the component to prevent issues when holding a bow (item becomes feather).
	 * The cache is updated whenever a valid FILLED_MAP is present.
	 */
	private static MapId getMapIdComponent(ItemStack stack) {
		if (stack.is(Items.FILLED_MAP) && stack.has(DataComponents.MAP_ID)) {
			MapId mapIdComponent = stack.get(DataComponents.MAP_ID);
			cachedMapIdComponent = mapIdComponent;
			return mapIdComponent;
		}
		// Return cached value if item is not a map (e.g., feather when holding bow)
		return cachedMapIdComponent != null ? cachedMapIdComponent : DEFAULT_MAP_ID_COMPONENT;
	}

	@Nullable
	private static MapItemSavedData getMapState(Minecraft client) {
		ItemStack mapStack = client.player.getInventory().getNonEquipmentItems().get(8);

		// Proaktives Caching: Map-ID cachen sobald sie verfügbar ist
		// Dies verhindert das Deadlock-Problem wenn Bogen gehalten wird
		if (mapStack.is(Items.FILLED_MAP) && mapStack.has(DataComponents.MAP_ID)) {
			cachedMapIdComponent = mapStack.get(DataComponents.MAP_ID);
		}

		MapId mapId = getMapIdComponent(mapStack);
		return MapItem.getSavedData(mapId, client.level);
	}

	/**
	 * Sucht aktiv nach der Map-ID in der Hotbar und cached sie.
	 * Dies verhindert das Problem dass die Map-ID nie gecached wird
	 * wenn der Spieler von Anfang an einen Bogen hält.
	 */
	private static void ensureMapIdCached(Minecraft client) {
		if (cachedMapIdComponent != null) return;
		if (client.player == null) return;

		// Suche Map in Hotbar (Slot 0-8)
		for (int i = 0; i <= 8; i++) {
			ItemStack stack = client.player.getInventory().getItem(i);
			if (stack.is(Items.FILLED_MAP) && stack.has(DataComponents.MAP_ID)) {
				cachedMapIdComponent = stack.get(DataComponents.MAP_ID);
				LOGGER.info("[DungeonManager] Cached map ID from slot {}", i);
				return;
			}
		}
	}

	public static boolean isClearingDungeon() {
		return physicalEntrancePos != null && mapEntrancePos != null && mapRoomSize != 0;
	}

	public static boolean isCurrentRoomMatched() {
		return isRoomMatched(currentRoom);
	}

	@Contract("null -> false")
	private static boolean isRoomMatched(@Nullable Room room) {
		return shouldProcess() && room != null && room.isMatched();
	}

	private static boolean shouldProcess() {
		return DungeonUtils.isInDungeon();
	}

	private static void reset() {
		mapEntrancePos = null;
		mapRoomSize = 0;
		physicalEntrancePos = null;
		rooms.clear();
		currentRoom = null;
		boss = DungeonBoss.NONE;
		bloodRushDoorBox = null;
		bloodOpened = false;
		hasKey = false;
		bossMinepoints.clear(); // NEW
		loadBossMinepointsFromDisk(); // NEW: ensure boss config persists across worlds
		cachedMapIdComponent = null; // Reset cache to detect new dungeon map
		fakeMapStack = null; // Reset fake map
		originalStack = null; // Reset original stack
		hasInjectedFakeMap = false; // Reset injection state
		wasInDungeonLastTick = false; // Reset dungeon state tracker

		// NEW: Reset AllRoomScanner
		if (allRoomScanner != null) {
			allRoomScanner.reset();
		}
		allRoomScanner = null;
		scannerInitialized = false;

		// NEW: Reset noamm system
		if (noammSystem != null) {
			noammSystem.reset();
		}
		noammSystem = null;
	}

	private static void getBloodRushDoorPos(@NotNull MapItemSavedData map) {
		if (mapEntrancePos == null || mapRoomSize == 0) {
			return;
		}

		Vector2i nWMostRoom = getMapPosForNWMostRoom(mapEntrancePos, mapRoomSize);

		// Check horizontal doors
		for (int x = nWMostRoom.x + mapRoomSize / 2; x < 128; x += mapRoomSize + 4) {
			for (int y = nWMostRoom.y + mapRoomSize; y < 128; y += mapRoomSize + 4) {
				byte color = getColor(map, x, y);
				if (color == 119 || color == 18) {
					Vector2ic doorPos = getPhysicalPosFromMap(mapEntrancePos, mapRoomSize, physicalEntrancePos, new Vector2i(x - mapRoomSize / 2, y - mapRoomSize));
					bloodRushDoorBox = new AABB(doorPos.x() + 14, 69, doorPos.y() + 30, doorPos.x() + 17, 73, doorPos.y() + 33);
					return;
				}
			}
		}

		// Check vertical doors
		for (int x = nWMostRoom.x + mapRoomSize; x < 128; x += mapRoomSize + 4) {
			for (int y = nWMostRoom.y + mapRoomSize / 2; y < 128; y += mapRoomSize + 4) {
				byte color = getColor(map, x, y);
				if (color == 119 || color == 18) {
					Vector2ic doorPos = getPhysicalPosFromMap(mapEntrancePos, mapRoomSize, physicalEntrancePos, new Vector2i(x - mapRoomSize, y - mapRoomSize / 2));
					bloodRushDoorBox = new AABB(doorPos.x() + 30, 69, doorPos.y() + 14, doorPos.x() + 33, 73, doorPos.y() + 17);
					return;
				}
			}
		}
	}

	private static int getRoomCheckmarkColour(Minecraft client, MapItemSavedData mapState, Room room) {
		int halfRoomSize = mapRoomSize / 2;

		for (Vector2ic segmentPhysicalPos : room.segments) {
			Vector2ic topLeftCorner = DungeonMapUtils.getMapPosFromPhysical(physicalEntrancePos, mapEntrancePos, mapRoomSize, segmentPhysicalPos);
			Vector2ic middle = topLeftCorner.add(halfRoomSize, halfRoomSize, new Vector2i());

			for (int offset = 0; offset < halfRoomSize; offset++) {
				int colour = DungeonMapUtils.getColor(mapState, new Vector2i(middle.x(), middle.y() + offset));
				if (colour == DungeonMapUtils.WHITE_COLOR || colour == DungeonMapUtils.GREEN_COLOR) return colour;
			}
		}

		return -1;
	}

	// Placeholder for DungeonBoss enum
	public enum DungeonBoss {
		NONE(false), THE_WATCHER(true), BONZO(true), SCARF(true), PROFESSOR(true), THORN(true), LIVID(true), SADAN(true), NECRON(true);

		private final boolean isInBoss;

		DungeonBoss(boolean isInBoss) {
			this.isInBoss = isInBoss;
		}

		public boolean isInBoss() {
			return isInBoss;
		}

		public static DungeonBoss fromMessage(String message) {
			// TODO: Implement boss detection from chat messages
			return NONE;
		}
	}
}
