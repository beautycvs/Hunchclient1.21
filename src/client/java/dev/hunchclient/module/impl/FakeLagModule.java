package dev.hunchclient.module.impl;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.network.PacketQueueManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FakeLag Module - Simulates network lag like Clumsy.
 *
 * VERY RISKY: This delays all outgoing packets, which WILL trigger anti-cheat.
 *
 * Features:
 * - Delay(ms): Configurable delay in milliseconds (like Clumsy)
 * - All outgoing packets are delayed by the specified amount
 * - After the delay, packets are automatically sent
 *
 * This is 1:1 like Clumsy's "Lag" function!
 */
public class FakeLagModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final Minecraft MC = Minecraft.getInstance();
    private static FakeLagModule instance;

    // Settings - just like Clumsy!
    private int delayMs = 200; // Default 200ms like Clumsy
    private boolean showNotifications = true;

    public FakeLagModule() {
        super("FakeLag", "Delay packets like Clumsy", Category.MOVEMENT, RiskLevel.VERY_RISKY);
        instance = this;
    }

    public static FakeLagModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        if (MC.player == null) {
            this.enabled = false;
            return;
        }

        // Enable lag mode in PacketQueueManager
        PacketQueueManager.getInstance().enableLagMode(delayMs);

        if (showNotifications) {
            MC.player.displayClientMessage(
                Component.literal("\u00A7c[FakeLag] \u00A7fEnabled - " + delayMs + "ms delay"),
                true
            );
        }
    }

    @Override
    protected void onDisable() {
        // Disable lag mode
        PacketQueueManager.getInstance().disableLagMode();

        if (showNotifications && MC.player != null) {
            MC.player.displayClientMessage(
                Component.literal("\u00A7a[FakeLag] \u00A7fDisabled"),
                true
            );
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        // Update delay in case slider was changed
        PacketQueueManager.getInstance().setLagDelayMs(delayMs);
    }

    // Getters
    public int getDelayMs() { return delayMs; }
    public boolean isShowNotifications() { return showNotifications; }

    // Setters
    public void setDelayMs(int v) { this.delayMs = v; }
    public void setShowNotifications(boolean v) { this.showNotifications = v; }

    // ConfigurableModule
    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("delayMs", delayMs);
        data.addProperty("showNotifications", showNotifications);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("delayMs")) delayMs = data.get("delayMs").getAsInt();
        if (data.has("showNotifications")) showNotifications = data.get("showNotifications").getAsBoolean();
    }

    // SettingsProvider
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new SliderSetting(
            "Delay (ms)",
            "Packet delay in milliseconds (like Clumsy)",
            "fakelag_delay",
            0, 2000, // 0-2000ms range
            () -> (float) delayMs,
            v -> delayMs = v.intValue()
        ));

        settings.add(new CheckboxSetting(
            "Show Notifications",
            "Show status messages",
            "fakelag_notifications",
            () -> showNotifications,
            v -> showNotifications = v
        ));

        return settings;
    }
}
