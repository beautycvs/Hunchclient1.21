package dev.hunchclient.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of color picker favorite colors and settings
 */
public class ColorPickerConfig {

    private static final String FAVORITES_KEY = "colorPickerFavorites";
    private static final String CONFIG_FILE_KEY = "colorPicker";

    /**
     * Save favorite colors to config
     */
    public static void saveFavoriteColors(List<int[]> favoriteColors) {
        try {
            JsonObject root = ConfigManagerUtil.loadOrCreateConfig();
            
            JsonArray favoritesArray = new JsonArray();
            for (int[] color : favoriteColors) {
                JsonObject colorObj = new JsonObject();
                colorObj.addProperty("r", color[0]);
                colorObj.addProperty("g", color[1]);
                colorObj.addProperty("b", color[2]);
                favoritesArray.add(colorObj);
            }
            
            JsonObject colorPickerObj = root.has(CONFIG_FILE_KEY) ? 
                root.getAsJsonObject(CONFIG_FILE_KEY) : new JsonObject();
            colorPickerObj.add(FAVORITES_KEY, favoritesArray);
            root.add(CONFIG_FILE_KEY, colorPickerObj);
            
            ConfigManagerUtil.saveConfig(root);
        } catch (Exception e) {
            System.err.println("Failed to save favorite colors: " + e.getMessage());
        }
    }

    /**
     * Load favorite colors from config
     */
    public static List<int[]> loadFavoriteColors() {
        List<int[]> favorites = new ArrayList<>();
        
        try {
            JsonObject root = ConfigManagerUtil.loadOrCreateConfig();
            
            if (root.has(CONFIG_FILE_KEY)) {
                JsonObject colorPickerObj = root.getAsJsonObject(CONFIG_FILE_KEY);
                if (colorPickerObj.has(FAVORITES_KEY)) {
                    JsonArray favoritesArray = colorPickerObj.getAsJsonArray(FAVORITES_KEY);
                    
                    for (JsonElement element : favoritesArray) {
                        if (element.isJsonObject()) {
                            JsonObject colorObj = element.getAsJsonObject();
                            if (colorObj.has("r") && colorObj.has("g") && colorObj.has("b")) {
                                int[] color = new int[]{
                                    colorObj.get("r").getAsInt(),
                                    colorObj.get("g").getAsInt(),
                                    colorObj.get("b").getAsInt()
                                };
                                favorites.add(color);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load favorite colors: " + e.getMessage());
        }
        
        return favorites;
    }
}

