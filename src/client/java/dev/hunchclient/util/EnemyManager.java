package dev.hunchclient.util;

import com.google.gson.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;

/**
 * Manages the enemy/target list for the client
 */
public class EnemyManager {

    private static EnemyManager instance;
    private final List<String> enemies = new CopyOnWriteArrayList<>();
    private final Minecraft mc = Minecraft.getInstance();

    private EnemyManager() {
        loadEnemies();
    }

    public static EnemyManager getInstance() {
        if (instance == null) {
            instance = new EnemyManager();
        }
        return instance;
    }

    /**
     * Add a player to the enemy list
     */
    public void addEnemy(String playerName) {
        if (!enemies.contains(playerName.toLowerCase())) {
            enemies.add(playerName.toLowerCase());
            saveEnemies();
        }
    }

    /**
     * Remove a player from the enemy list
     */
    public boolean removeEnemy(String playerName) {
        boolean removed = enemies.remove(playerName.toLowerCase());
        if (removed) {
            saveEnemies();
        }
        return removed;
    }

    /**
     * Check if a player is an enemy
     */
    public boolean isEnemy(String playerName) {
        return enemies.contains(playerName.toLowerCase());
    }

    /**
     * Get all enemies
     */
    public List<String> getEnemies() {
        return new ArrayList<>(enemies);
    }

    /**
     * Clear all enemies
     */
    public void clearEnemies() {
        enemies.clear();
        saveEnemies();
    }

    /**
     * Get enemy count
     */
    public int getEnemyCount() {
        return enemies.size();
    }

    /**
     * Save enemies to file
     */
    private void saveEnemies() {
        File file = new File(mc.gameDirectory, "hunchclient/enemies.json");
        file.getParentFile().mkdirs();

        JsonObject json = new JsonObject();
        JsonArray enemyArray = new JsonArray();

        for (String enemy : enemies) {
            enemyArray.add(enemy);
        }

        json.add("enemies", enemyArray);

        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load enemies from file
     */
    private void loadEnemies() {
        File file = new File(mc.gameDirectory, "hunchclient/enemies.json");
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray enemyArray = json.getAsJsonArray("enemies");

            enemies.clear();
            for (JsonElement element : enemyArray) {
                enemies.add(element.getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}