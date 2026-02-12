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
 * Manages the friend list for the client
 */
public class FriendManager {

    private static FriendManager instance;
    private final List<String> friends = new CopyOnWriteArrayList<>();
    private final Minecraft mc = Minecraft.getInstance();

    private FriendManager() {
        loadFriends();
    }

    public static FriendManager getInstance() {
        if (instance == null) {
            instance = new FriendManager();
        }
        return instance;
    }

    /**
     * Add a player to the friends list
     */
    public void addFriend(String playerName) {
        if (!friends.contains(playerName.toLowerCase())) {
            friends.add(playerName.toLowerCase());
            saveFriends();
        }
    }

    /**
     * Remove a player from the friends list
     */
    public boolean removeFriend(String playerName) {
        boolean removed = friends.remove(playerName.toLowerCase());
        if (removed) {
            saveFriends();
        }
        return removed;
    }

    /**
     * Check if a player is a friend
     */
    public boolean isFriend(String playerName) {
        return friends.contains(playerName.toLowerCase());
    }

    /**
     * Get all friends
     */
    public List<String> getFriends() {
        return new ArrayList<>(friends);
    }

    /**
     * Clear all friends
     */
    public void clearFriends() {
        friends.clear();
        saveFriends();
    }

    /**
     * Get friend count
     */
    public int getFriendCount() {
        return friends.size();
    }

    /**
     * Save friends to file
     */
    private void saveFriends() {
        File file = new File(mc.gameDirectory, "hunchclient/friends.json");
        file.getParentFile().mkdirs();

        JsonObject json = new JsonObject();
        JsonArray friendArray = new JsonArray();

        for (String friend : friends) {
            friendArray.add(friend);
        }

        json.add("friends", friendArray);

        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load friends from file
     */
    private void loadFriends() {
        File file = new File(mc.gameDirectory, "hunchclient/friends.json");
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray friendArray = json.getAsJsonArray("friends");

            friends.clear();
            for (JsonElement element : friendArray) {
                friends.add(element.getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}