package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;
import dev.hunchclient.hud.HudEditorManager;
import dev.hunchclient.hud.elements.SkeetDungeonMapHudElement;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.impl.dungeons.devmap.DevMapState;
import dev.hunchclient.module.impl.dungeons.map.SkeetDungeonMapRenderer;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


public class SkeetDungeonMapModule extends Module implements ConfigurableModule, SettingsProvider {

    private static SkeetDungeonMapModule INSTANCE;

    // HUD Element
    private SkeetDungeonMapHudElement hudElement;

    // Settings (applied to HUD element's renderer)
    private boolean showCheckmarks = true;
    private boolean showPlayerNames = true;
    private boolean showRoomNames = false;
    private boolean funnyMode = true; // Show all rooms including undiscovered
    private float roomSizePercent = 0.8f;
    private float doorSizePercent = 0.4f;
    private float markerScale = 1.0f;
    private float nameScale = 1.0f;

    // HUD element position/scale (saved separately)
    private float hudX = 10;
    private float hudY = 10;
    private float hudScale = 1.0f;

    public SkeetDungeonMapModule() {
        super("SkeetDungeonMap", "Skeet-styled 2D dungeon map with RGB glow border (use HUD Editor to position)", Category.DUNGEONS, false);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        // Initialize DevMap state
        DevMapState.getInstance().init();

        // Create and register HUD element with saved position/scale
        hudElement = new SkeetDungeonMapHudElement(hudX, hudY, hudScale);
        applySettings();
        HudEditorManager.getInstance().registerElement(hudElement);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§d[HunchClient] §aSkeet Dungeon Map enabled! Use HUD Editor to reposition."), false);
        }
    }

    @Override
    protected void onDisable() {
        // Unregister HUD element
        if (hudElement != null) {
            HudEditorManager.getInstance().unregisterElement(hudElement);
            hudElement = null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§d[HunchClient] §cSkeet Dungeon Map disabled"), false);
        }
    }

    /**
     * Apply current settings to renderer
     */
    private void applySettings() {
        if (hudElement == null) return;

        SkeetDungeonMapRenderer renderer = hudElement.getRenderer();
        if (renderer == null) return;

        renderer.setRoomSizePercent(roomSizePercent);
        renderer.setDoorSizePercent(doorSizePercent);
        renderer.setRenderCheckmarks(showCheckmarks);
        renderer.setRenderPlayerNames(showPlayerNames);
        renderer.setRenderRoomNames(showRoomNames);
        renderer.setRenderUnknownRooms(funnyMode);
        renderer.setMarkerScale(markerScale);
        renderer.setNameScale(nameScale);
    }

    /**
     * Force map redraw
     */
    public void invalidateMap() {
        if (hudElement != null && hudElement.getRenderer() != null) {
            hudElement.getRenderer().invalidate();
        }
    }

    public static SkeetDungeonMapModule getInstance() {
        return INSTANCE;
    }

    // ==================== ConfigurableModule Implementation ====================

    @Override
    public JsonObject saveConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("showCheckmarks", showCheckmarks);
        json.addProperty("showPlayerNames", showPlayerNames);
        json.addProperty("showRoomNames", showRoomNames);
        json.addProperty("funnyMode", funnyMode);
        json.addProperty("roomSizePercent", roomSizePercent);
        json.addProperty("doorSizePercent", doorSizePercent);
        json.addProperty("markerScale", markerScale);
        json.addProperty("nameScale", nameScale);

        // Save HUD element position/scale
        if (hudElement != null) {
            json.addProperty("hudX", hudElement.getX());
            json.addProperty("hudY", hudElement.getY());
            json.addProperty("hudScale", hudElement.getScale());
        } else {
            json.addProperty("hudX", hudX);
            json.addProperty("hudY", hudY);
            json.addProperty("hudScale", hudScale);
        }
        return json;
    }

    @Override
    public void loadConfig(JsonObject json) {
        if (json == null) return;

        if (json.has("showCheckmarks")) showCheckmarks = json.get("showCheckmarks").getAsBoolean();
        if (json.has("showPlayerNames")) showPlayerNames = json.get("showPlayerNames").getAsBoolean();
        if (json.has("showRoomNames")) showRoomNames = json.get("showRoomNames").getAsBoolean();
        if (json.has("funnyMode")) funnyMode = json.get("funnyMode").getAsBoolean();
        if (json.has("roomSizePercent")) roomSizePercent = json.get("roomSizePercent").getAsFloat();
        if (json.has("doorSizePercent")) doorSizePercent = json.get("doorSizePercent").getAsFloat();
        if (json.has("markerScale")) markerScale = json.get("markerScale").getAsFloat();
        if (json.has("nameScale")) nameScale = json.get("nameScale").getAsFloat();

        // Load HUD element position/scale
        if (json.has("hudX")) hudX = json.get("hudX").getAsFloat();
        if (json.has("hudY")) hudY = json.get("hudY").getAsFloat();
        if (json.has("hudScale")) hudScale = json.get("hudScale").getAsFloat();

        // Apply to existing HUD element if present
        if (hudElement != null) {
            hudElement.setX(hudX);
            hudElement.setY(hudY);
            hudElement.setScale(hudScale);
        }

        applySettings();
    }

    // ==================== SettingsProvider Implementation ====================

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Show Checkmarks",
            "Display checkmarks on cleared rooms",
            "skeet_dungeon_map_checkmarks",
            () -> showCheckmarks,
            value -> {
                showCheckmarks = value;
                applySettings();
            }
        ));

        settings.add(new CheckboxSetting(
            "Show Player Names",
            "Display player names on the map",
            "skeet_dungeon_map_names",
            () -> showPlayerNames,
            value -> {
                showPlayerNames = value;
                applySettings();
            }
        ));

        settings.add(new CheckboxSetting(
            "Show Room Names",
            "Display room names on the map",
            "skeet_dungeon_map_room_names",
            () -> showRoomNames,
            value -> {
                showRoomNames = value;
                applySettings();
            }
        ));

        settings.add(new CheckboxSetting(
            "Funny Mode",
            "Show all rooms including undiscovered (disable to only see explored rooms)",
            "skeet_dungeon_map_funny_mode",
            () -> funnyMode,
            value -> {
                funnyMode = value;
                applySettings();
            }
        ));

        settings.add(new SliderSetting(
            "Room Size",
            "Size of rooms relative to tile (0.5-1.0)",
            "skeet_dungeon_map_room_size",
            0.5f, 1.0f,
            () -> roomSizePercent,
            value -> {
                roomSizePercent = value;
                applySettings();
            }
        ));

        settings.add(new SliderSetting(
            "Door Size",
            "Size of doors relative to tile (0.2-0.6)",
            "skeet_dungeon_map_door_size",
            0.2f, 0.6f,
            () -> doorSizePercent,
            value -> {
                doorSizePercent = value;
                applySettings();
            }
        ));

        settings.add(new SliderSetting(
            "Marker Scale",
            "Scale of player markers",
            "skeet_dungeon_map_marker_scale",
            0.5f, 2.0f,
            () -> markerScale,
            value -> {
                markerScale = value;
                applySettings();
            }
        ));

        settings.add(new SliderSetting(
            "Name Scale",
            "Scale of player names",
            "skeet_dungeon_map_name_scale",
            0.5f, 2.0f,
            () -> nameScale,
            value -> {
                nameScale = value;
                applySettings();
            }
        ));

        return settings;
    }
}
