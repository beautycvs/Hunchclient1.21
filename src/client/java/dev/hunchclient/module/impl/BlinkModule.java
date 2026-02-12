package dev.hunchclient.module.impl;

import com.google.gson.JsonObject;
import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.QueuePacketEvent;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.network.PacketQueueManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blink Module - Queues outgoing packets to create a "teleport" effect.
 *
 * VERY RISKY: This module delays packets to the server, which can trigger anti-cheat.
 *
 * Features:
 * - Dummy player: Shows a fake player at your original position
 * - Ambush mode: Automatically blinks when you attack
 * - Auto-reset: Automatically flushes/resets after X packets
 * - Auto-disable: Disables after reset
 *
 * Based on LiquidBounce's Blink module.
 */
public class BlinkModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final Minecraft MC = Minecraft.getInstance();
    private static BlinkModule instance;

    // Settings
    private boolean showDummy = true;
    private boolean ambushMode = false;
    private boolean autoReset = false;
    private int resetAfter = 100;
    private boolean autoDisable = true;
    private boolean showNotifications = true;

    // State
    private RemotePlayer dummyPlayer = null;

    public BlinkModule() {
        super("Blink", "Queue packets to teleport", Category.MOVEMENT, RiskLevel.VERY_RISKY);
        instance = this;
    }

    public static BlinkModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        if (MC.player == null || MC.level == null) {
            this.enabled = false;
            return;
        }

        // Enable packet queuing
        PacketQueueManager.getInstance().enableQueuing();

        // Register event handler
        HunchModClient.EVENT_BUS.subscribe(this);

        // Spawn dummy player at current position
        if (showDummy) {
            spawnDummy();
        }

        if (showNotifications) {
            MC.player.displayClientMessage(
                Component.literal("\u00A7c[Blink] \u00A7fEnabled - Packets are being queued!"),
                true
            );
        }
    }

    @Override
    protected void onDisable() {
        // Unregister event handler
        HunchModClient.EVENT_BUS.unsubscribe(this);

        // Flush all queued packets
        PacketQueueManager.getInstance().flush();

        // Disable packet queuing
        PacketQueueManager.getInstance().disableQueuing();

        // Remove dummy player
        removeDummy();

        if (showNotifications && MC.player != null) {
            MC.player.displayClientMessage(
                Component.literal("\u00A7a[Blink] \u00A7fDisabled - Packets sent!"),
                true
            );
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || MC.player == null) return;

        // Auto-reset check
        if (autoReset) {
            int queueSize = PacketQueueManager.getInstance().getQueueSize();
            if (queueSize >= resetAfter) {
                performReset();
            }
        }

        // Update dummy position display in action bar
        if (showNotifications) {
            int queueSize = PacketQueueManager.getInstance().getQueueSize();
            MC.player.displayClientMessage(
                Component.literal("\u00A7c[Blink] \u00A7fQueued: \u00A7e" + queueSize + " \u00A7fpackets"),
                true
            );
        }
    }

    /**
     * Handle QueuePacketEvent - decide whether to queue packets.
     */
    @EventHandler
    public void onQueuePacket(QueuePacketEvent event) {
        // Queue all outgoing packets
        event.setAction(QueuePacketEvent.Action.QUEUE);

        // Ambush mode: disable blink when attacking
        if (ambushMode && event.getPacket() instanceof ServerboundInteractPacket) {
            // Schedule disable for next tick to ensure attack packet is included
            MC.execute(() -> setEnabled(false));
        }
    }

    /**
     * Perform auto-reset action.
     */
    private void performReset() {
        // Flush all packets (blink to current position)
        PacketQueueManager.getInstance().flush();

        // Update dummy to new position
        if (showDummy && dummyPlayer != null && MC.player != null) {
            dummyPlayer.copyPosition(MC.player);
        }

        if (showNotifications && MC.player != null) {
            MC.player.displayClientMessage(
                Component.literal("\u00A7e[Blink] \u00A7fAuto-reset triggered"),
                true
            );
        }

        // Auto-disable if enabled
        if (autoDisable) {
            setEnabled(false);
        }
    }

    /**
     * Cancel all queued packets (don't blink, stay at dummy position).
     */
    public void cancelBlink() {
        PacketQueueManager.getInstance().cancel();

        // Teleport player back to dummy position visually
        Vec3 dummyPos = PacketQueueManager.getInstance().getFirstPosition();
        if (dummyPos != null && MC.player != null) {
            // Note: This only affects client-side. Server still thinks we're at original pos.
            MC.player.setPos(dummyPos.x, dummyPos.y, dummyPos.z);
        }

        if (showNotifications && MC.player != null) {
            MC.player.displayClientMessage(
                Component.literal("\u00A7c[Blink] \u00A7fCancelled - Packets discarded!"),
                true
            );
        }

        setEnabled(false);
    }

    /**
     * Spawn a dummy player at the current position.
     */
    private void spawnDummy() {
        if (MC.player == null || MC.level == null) return;

        ClientLevel level = MC.level;

        // Create fake player with player's game profile
        dummyPlayer = new RemotePlayer(level, MC.player.getGameProfile());
        dummyPlayer.setUUID(UUID.randomUUID()); // Different UUID to avoid conflicts
        dummyPlayer.copyPosition(MC.player);
        dummyPlayer.setYHeadRot(MC.player.getYHeadRot());
        dummyPlayer.yRotO = MC.player.yRotO;
        dummyPlayer.xRotO = MC.player.xRotO;

        // Add to world
        level.addEntity(dummyPlayer);
    }

    /**
     * Remove the dummy player from the world.
     */
    private void removeDummy() {
        if (dummyPlayer != null && MC.level != null) {
            MC.level.removeEntity(dummyPlayer.getId(), Entity.RemovalReason.DISCARDED);
            dummyPlayer = null;
        }
    }

    // Getters
    public boolean isShowDummy() { return showDummy; }
    public boolean isAmbushMode() { return ambushMode; }
    public boolean isAutoReset() { return autoReset; }
    public int getResetAfter() { return resetAfter; }
    public boolean isAutoDisable() { return autoDisable; }
    public boolean isShowNotifications() { return showNotifications; }
    public RemotePlayer getDummyPlayer() { return dummyPlayer; }

    // Setters
    public void setShowDummy(boolean v) { this.showDummy = v; }
    public void setAmbushMode(boolean v) { this.ambushMode = v; }
    public void setAutoReset(boolean v) { this.autoReset = v; }
    public void setResetAfter(int v) { this.resetAfter = v; }
    public void setAutoDisable(boolean v) { this.autoDisable = v; }
    public void setShowNotifications(boolean v) { this.showNotifications = v; }

    // ConfigurableModule
    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("showDummy", showDummy);
        data.addProperty("ambushMode", ambushMode);
        data.addProperty("autoReset", autoReset);
        data.addProperty("resetAfter", resetAfter);
        data.addProperty("autoDisable", autoDisable);
        data.addProperty("showNotifications", showNotifications);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("showDummy")) showDummy = data.get("showDummy").getAsBoolean();
        if (data.has("ambushMode")) ambushMode = data.get("ambushMode").getAsBoolean();
        if (data.has("autoReset")) autoReset = data.get("autoReset").getAsBoolean();
        if (data.has("resetAfter")) resetAfter = data.get("resetAfter").getAsInt();
        if (data.has("autoDisable")) autoDisable = data.get("autoDisable").getAsBoolean();
        if (data.has("showNotifications")) showNotifications = data.get("showNotifications").getAsBoolean();
    }

    // SettingsProvider
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Show Dummy",
            "Display a fake player at your original position",
            "blink_dummy",
            () -> showDummy,
            v -> showDummy = v
        ));

        settings.add(new CheckboxSetting(
            "Ambush Mode",
            "Automatically disable blink when you attack",
            "blink_ambush",
            () -> ambushMode,
            v -> ambushMode = v
        ));

        settings.add(new CheckboxSetting(
            "Auto Reset",
            "Automatically flush packets after reaching limit",
            "blink_autoreset",
            () -> autoReset,
            v -> autoReset = v
        ));

        settings.add(new SliderSetting(
            "Reset After",
            "Number of packets before auto-reset",
            "blink_resetafter",
            10, 500,
            () -> (float) resetAfter,
            v -> resetAfter = v.intValue()
        ));

        settings.add(new CheckboxSetting(
            "Auto Disable",
            "Disable module after reset",
            "blink_autodisable",
            () -> autoDisable,
            v -> autoDisable = v
        ));

        settings.add(new CheckboxSetting(
            "Show Notifications",
            "Show status messages in action bar",
            "blink_notifications",
            () -> showNotifications,
            v -> showNotifications = v
        ));

        return settings;
    }
}
