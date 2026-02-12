package dev.hunchclient.module.impl;

import com.google.gson.JsonObject;
import dev.hunchclient.freecam.FreeCamera;
import dev.hunchclient.freecam.FreecamPosition;
import dev.hunchclient.freecam.FreecamState;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;

import java.util.ArrayList;
import java.util.List;

/**
 * Freecam Module
 *
 * WATCHDOG SAFE: Yes (client-side camera manipulation only)
 * - No packets sent to server
 * - Player remains stationary on server-side
 * - Only visual/camera changes on client
 *
 * Features:
 * - Free-floating camera independent of player position
 * - Configurable speed and no-clip mode
 * - Player control toggle for controlling player while in freecam
 */
public class FreecamModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final Minecraft MC = Minecraft.getInstance();
    private static FreecamModule instance;

    // Settings
    private double horizontalSpeed = 1.0;
    private double verticalSpeed = 1.0;
    private boolean noClip = true;
    private boolean showNotifications = true;

    // State
    private boolean freecamEnabled = false;
    private boolean playerControlEnabled = false;
    private FreeCamera freeCamera;
    private CameraType rememberedCameraType = null;

    public FreecamModule() {
        super("Freecam", "Free-floating camera mode", Category.MISC, RiskLevel.SAFE);
        instance = this;
    }

    public static FreecamModule getInstance() {
        return instance;
    }

    /**
     * Static helper for mixins - checks if freecam is active and player control is disabled.
     * Used by FreecamLocalPlayerMixin to block packets at the source.
     */
    public static boolean isActiveAndNotPlayerControl() {
        return instance != null && instance.freecamEnabled && !instance.playerControlEnabled;
    }

    @Override
    protected void onEnable() {
        if (MC.player == null || MC.level == null) {
            this.enabled = false;
            return;
        }

        enableFreecam();
    }

    @Override
    protected void onDisable() {
        if (freecamEnabled) {
            disableFreecam();
        }
    }

    private void enableFreecam() {
        MC.smartCull = false;

        // Save player position for fake AFK packets (anti-cheat bypass)
        FreecamState.savePosition(MC.player);

        // Remember current camera type
        rememberedCameraType = MC.options.getCameraType();
        if (MC.gameRenderer.getMainCamera().isDetached()) {
            MC.options.setCameraType(CameraType.FIRST_PERSON);
        }

        // Create and spawn the free camera
        freeCamera = new FreeCamera(-420, horizontalSpeed, verticalSpeed, noClip);
        freeCamera.copyPosition(MC.player);
        freeCamera.spawn();
        MC.setCameraEntity(freeCamera);

        freecamEnabled = true;

        if (showNotifications && MC.player != null) {
            MC.player.displayClientMessage(Component.literal("\u00A7a[Freecam] \u00A7fEnabled"), true);
        }
    }

    private void disableFreecam() {
        MC.smartCull = true;
        MC.setCameraEntity(MC.player);
        playerControlEnabled = false;

        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera.input = new ClientInput();
            freeCamera = null;
        }

        if (MC.player != null) {
            MC.player.input = new KeyboardInput(MC.options);
        }

        // Restore camera type
        if (rememberedCameraType != null) {
            MC.options.setCameraType(rememberedCameraType);
        }

        freecamEnabled = false;

        if (showNotifications && MC.player != null) {
            MC.player.displayClientMessage(Component.literal("\u00A7c[Freecam] \u00A7fDisabled"), true);
        }
    }

    @Override
    public void onTick() {
        if (!freecamEnabled || MC.player == null) {
            return;
        }

        // Prevent player from being controlled when freecam is enabled (unless player control is enabled)
        if (!playerControlEnabled) {
            // Freeze rotation to prevent BadPacketsR (rotation change detected but StatusOnly sent)
            FreecamState.freezeRotation(MC.player);

            if (MC.player.input instanceof KeyboardInput) {
                ClientInput input = new ClientInput();
                Input keyPresses = MC.player.input.keyPresses;
                input.keyPresses = new Input(
                        false,
                        false,
                        false,
                        false,
                        false,
                        keyPresses.shift(),
                        false
                );
                MC.player.input = input;
            }
        }
    }

    /**
     * Toggle player control mode.
     * When enabled, the player can move while in freecam.
     */
    public void switchControls() {
        if (!freecamEnabled || freeCamera == null) {
            return;
        }

        if (playerControlEnabled) {
            freeCamera.input = new KeyboardInput(MC.options);
        } else {
            MC.player.input = new KeyboardInput(MC.options);
            freeCamera.input = new ClientInput();
        }
        playerControlEnabled = !playerControlEnabled;

        if (showNotifications && MC.player != null) {
            String status = playerControlEnabled ? "\u00A7aEnabled" : "\u00A7cDisabled";
            MC.player.displayClientMessage(Component.literal("\u00A7e[Freecam] \u00A7fPlayer Control: " + status), true);
        }
    }

    /**
     * Move the freecam to a specific entity's position.
     */
    public void moveToEntity(Entity entity) {
        if (freeCamera == null) {
            return;
        }
        if (entity == null) {
            moveToPlayer();
            return;
        }
        freeCamera.copyPosition(entity);
    }

    /**
     * Move the freecam to a specific position.
     */
    public void moveToPosition(FreecamPosition position) {
        if (freeCamera == null) {
            return;
        }
        if (position == null) {
            moveToPlayer();
            return;
        }
        freeCamera.applyPosition(position);
    }

    /**
     * Move the freecam back to the player's position.
     */
    public void moveToPlayer() {
        if (freeCamera == null || MC.player == null) {
            return;
        }
        freeCamera.copyPosition(MC.player);
    }

    public FreeCamera getFreeCamera() {
        return freeCamera;
    }

    public boolean isFreecamEnabled() {
        return freecamEnabled;
    }

    public boolean isPlayerControlEnabled() {
        return playerControlEnabled;
    }

    // Getters for settings
    public double getHorizontalSpeed() {
        return horizontalSpeed;
    }

    public double getVerticalSpeed() {
        return verticalSpeed;
    }

    public boolean isNoClip() {
        return noClip;
    }

    public boolean isShowNotifications() {
        return showNotifications;
    }

    // Setters for settings
    public void setHorizontalSpeed(double speed) {
        this.horizontalSpeed = speed;
    }

    public void setVerticalSpeed(double speed) {
        this.verticalSpeed = speed;
    }

    public void setNoClip(boolean noClip) {
        this.noClip = noClip;
    }

    public void setShowNotifications(boolean show) {
        this.showNotifications = show;
    }

    // ConfigurableModule implementation
    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("horizontalSpeed", horizontalSpeed);
        data.addProperty("verticalSpeed", verticalSpeed);
        data.addProperty("noClip", noClip);
        data.addProperty("showNotifications", showNotifications);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("horizontalSpeed")) {
            horizontalSpeed = data.get("horizontalSpeed").getAsDouble();
        }
        if (data.has("verticalSpeed")) {
            verticalSpeed = data.get("verticalSpeed").getAsDouble();
        }
        if (data.has("noClip")) {
            noClip = data.get("noClip").getAsBoolean();
        }
        if (data.has("showNotifications")) {
            showNotifications = data.get("showNotifications").getAsBoolean();
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new SliderSetting(
            "Horizontal Speed",
            "Movement speed for left/right/forward/backward",
            "freecam_hspeed",
            0.1f,
            5.0f,
            () -> (float) horizontalSpeed,
            v -> horizontalSpeed = v
        ).withDecimals(1));

        settings.add(new SliderSetting(
            "Vertical Speed",
            "Movement speed for up/down",
            "freecam_vspeed",
            0.1f,
            5.0f,
            () -> (float) verticalSpeed,
            v -> verticalSpeed = v
        ).withDecimals(1));

        settings.add(new CheckboxSetting(
            "No Clip",
            "Pass through blocks",
            "freecam_noclip",
            () -> noClip,
            v -> noClip = v
        ));

        settings.add(new CheckboxSetting(
            "Show Notifications",
            "Show enable/disable messages",
            "freecam_notifications",
            () -> showNotifications,
            v -> showNotifications = v
        ));

        return settings;
    }
}
