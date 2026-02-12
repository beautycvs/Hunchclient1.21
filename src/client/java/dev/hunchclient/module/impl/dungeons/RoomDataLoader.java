package dev.hunchclient.module.impl.dungeons;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hunchclient.module.impl.dungeons.map.RoomData;
import dev.hunchclient.module.impl.dungeons.map.RoomType;
import dev.hunchclient.module.impl.dungeons.scanner.ScanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Loads room database from Skytils rooms.json
 * 1:1 Port from noamm's WebUtils.fetchJson() for room data
 */
public class RoomDataLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomDataLoader.class);

    private static final String ROOMS_JSON_URL =
            "https://raw.githubusercontent.com/Skytils/SkytilsMod/refs/heads/1.x/src/main/resources/assets/catlas/rooms.json";

    private static final Gson GSON = new Gson();

    /**
     * Load room data asynchronously from Skytils repository
     */
    public static CompletableFuture<Void> loadRoomDataAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[RoomDataLoader] Loading room database from Skytils...");
                long startTime = System.currentTimeMillis();

                String jsonContent = fetchUrl(ROOMS_JSON_URL);
                if (jsonContent == null) {
                    LOGGER.error("[RoomDataLoader] Failed to fetch rooms.json");
                    return;
                }

                List<RoomData> roomDataList = parseRoomData(jsonContent);
                ScanUtils.getRoomList().clear();
                ScanUtils.getRoomList().addAll(roomDataList);

                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.info("[RoomDataLoader] ✓ Loaded {} rooms in {} ms", roomDataList.size(), elapsed);

                // Log first few rooms for debugging
                if (!roomDataList.isEmpty()) {
                    for (int i = 0; i < Math.min(3, roomDataList.size()); i++) {
                        RoomData room = roomDataList.get(i);
                        LOGGER.info("[RoomDataLoader]   Example room {}: {} (type={}, cores={}, secrets={})",
                            i+1, room.getName(), room.getType(), room.getCores().size(), room.getSecrets());
                    }
                }

            } catch (Exception e) {
                LOGGER.error("[RoomDataLoader] Error loading room data", e);
            }
        });
    }

    /**
     * Fetch URL content as string
     */
    private static String fetchUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "HunchClient/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                LOGGER.error("[RoomDataLoader] HTTP error: {}", responseCode);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            reader.close();
            connection.disconnect();

            return content.toString();

        } catch (Exception e) {
            LOGGER.error("[RoomDataLoader] Error fetching URL: {}", urlString, e);
            return null;
        }
    }

    /**
     * Parse rooms.json into RoomData list
     *
     * JSON Format:
     * [
     *   {
     *     "name": "Room Name",
     *     "type": "normal",
     *     "cores": [123456, 789012],
     *     "crypts": 1,
     *     "secrets": 5,
     *     "trappedChests": 0
     *   },
     *   ...
     * ]
     */
    private static List<RoomData> parseRoomData(String jsonContent) {
        List<RoomData> roomDataList = new ArrayList<>();

        try {
            JsonArray jsonArray = GSON.fromJson(jsonContent, JsonArray.class);

            for (JsonElement element : jsonArray) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject obj = element.getAsJsonObject();

                String name = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
                String typeStr = obj.has("type") ? obj.get("type").getAsString() : "normal";
                int crypts = obj.has("crypts") ? obj.get("crypts").getAsInt() : 0;
                int secrets = obj.has("secrets") ? obj.get("secrets").getAsInt() : 0;
                int trappedChests = obj.has("trappedChests") ? obj.get("trappedChests").getAsInt() : 0;

                // Parse cores array
                List<Integer> cores = new ArrayList<>();
                if (obj.has("cores") && obj.get("cores").isJsonArray()) {
                    JsonArray coresArray = obj.getAsJsonArray("cores");
                    for (JsonElement coreElement : coresArray) {
                        cores.add(coreElement.getAsInt());
                    }
                }

                // Map type string to RoomType enum
                RoomType roomType = parseRoomType(typeStr);

                RoomData roomData = new RoomData(name, roomType, cores, crypts, secrets, trappedChests);
                roomDataList.add(roomData);
            }

        } catch (Exception e) {
            LOGGER.error("[RoomDataLoader] Error parsing room data JSON", e);
        }

        return roomDataList;
    }

    /**
     * Parse room type string to RoomType enum
     */
    private static RoomType parseRoomType(String typeStr) {
        return switch (typeStr.toLowerCase()) {
            case "blood" -> RoomType.BLOOD;
            case "champion", "miniboss" -> RoomType.CHAMPION;
            case "entrance" -> RoomType.ENTRANCE;
            case "fairy" -> RoomType.FAIRY;
            case "puzzle" -> RoomType.PUZZLE;
            case "rare" -> RoomType.RARE;
            case "trap" -> RoomType.TRAP;
            default -> RoomType.NORMAL;
        };
    }
}
