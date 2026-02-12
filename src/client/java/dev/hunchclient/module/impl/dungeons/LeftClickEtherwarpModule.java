package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Left Click Etherwarp Module
 *
 * WATCHDOG SAFE: ⚠️ MEDIUM RISK
 * - Sends packets (sneak + use)
 * - Uses timing delays to avoid detection
 * - Randomization for humanization
 */
public class LeftClickEtherwarpModule extends Module implements ConfigurableModule, SettingsProvider {

    private final Minecraft mc = Minecraft.getInstance();

    // Sequence state
    private boolean sequenceActive = false;
    private long lastEtherwarp = 0;

    // Timing configuration (adaptive based on ping)
    private int sneakDelay = 50; // ms to wait after sneak before right-click
    private int processingTime = 100; // ms to hold sneak after right-click
    private boolean adaptivePing = true;

    public LeftClickEtherwarpModule() {
        super("LeftClickEtherwarp", "Left-click to etherwarp", Category.DUNGEONS, RiskLevel.RISKY);
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    @Override
    protected void onDisable() {
        // Release all keys
        try {
            KeyMapping sneakKey = mc.options.keyShift;
            KeyMapping useKey = mc.options.keyUse;
            sneakKey.setDown(false);
            useKey.setDown(false);
        } catch (Exception e) {
            // Ignore
        }
        sequenceActive = false;
    }

    private void onTick(Minecraft client) {
        // Only run if module is enabled
        if (!isEnabled()) return;
    }

    /**
     * Called from mixin when left-click is detected
     */
    public void onLeftClick() {
        if (!isEnabled()) return;

        // Basic checks (but NOT canEtherwarp() because we WILL sneak ourselves!)
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // Don't etherwarp in GUI
        if (!EtherwarpModule.holdingEtherwarpItem()) return;
        if (sequenceActive) return;

        // Check cooldown (prevent spam)
        long now = System.currentTimeMillis();
        if (now - lastEtherwarp < 500) return; // 500ms cooldown

        // Get etherwarp result AS IF sneaking (because we will sneak before warping)
        EtherwarpModule.EtherPos etherResult = EtherwarpModule.getEtherwarpResultSneaking();
        if (etherResult.pos == null) return;

        // Only proceed if the block is actually warpable
        if (!etherResult.succeeded) return;

        // Start etherwarp sequence
        lastEtherwarp = now;
        runEtherwarpSequence();
    }

    private void runEtherwarpSequence() {
        if (sequenceActive) return;
        sequenceActive = true;

        KeyMapping sneakKey = mc.options.keyShift;
        KeyMapping useKey = mc.options.keyUse;

        if (sneakKey == null || useKey == null) {
            sequenceActive = false;
            return;
        }

        // Calculate delays (adaptive based on ping if enabled)
        final int calculatedSneakDelay;
        final int calculatedProcessingTime;

        if (adaptivePing) {
            int ping = getPing();
            // Adjust delays based on ping
            calculatedSneakDelay = Math.max(30, sneakDelay + (ping / 2)) + random.nextInt(10) - 5;
            calculatedProcessingTime = Math.max(50, processingTime + ping) + random.nextInt(20) - 10;
        } else {
            // Add randomization to avoid detection
            calculatedSneakDelay = sneakDelay + random.nextInt(10) - 5; // ±5ms
            calculatedProcessingTime = processingTime + random.nextInt(20) - 10; // ±10ms
        }

        // Step 1: Press sneak
        sneakKey.setDown(true);

        // Step 2: Wait for server to register sneak, then press use
        scheduleTask(calculatedSneakDelay, () -> {
            // Verify player is actually sneaking before right-clicking
            if (mc.player != null && !mc.player.isShiftKeyDown()) {
                // Not sneaking yet, wait a bit longer
                scheduleTask(20, () -> {
                    if (mc.player != null && mc.player.isShiftKeyDown()) {
                        executeRightClick(sneakKey, useKey, calculatedProcessingTime);
                    } else {
                        // Failed to sneak, abort
                        sneakKey.setDown(false);
                        sequenceActive = false;
                    }
                });
                return;
            }

            executeRightClick(sneakKey, useKey, calculatedProcessingTime);
        });
    }

    private void executeRightClick(KeyMapping sneakKey, KeyMapping useKey, int processingTime) {
        // Press and hold use key WHILE sneaking
        useKey.setDown(true);

        // Swing arm for visual feedback
        if (mc.player != null) {
            mc.player.swing(mc.player.getUsedItemHand());
        }

        // Step 3: Hold use key for at least 50ms (not just 5ms!)
        scheduleTask(50, () -> {
            useKey.setDown(false);

            // Step 4: Keep sneaking for processing time, then release
            scheduleTask(processingTime, () -> {
                sneakKey.setDown(false);
                sequenceActive = false;
            });
        });
    }

    /**
     * Schedule a task to run after a delay in milliseconds
     */
    private void scheduleTask(int delayMs, Runnable task) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                // Run on main thread
                mc.execute(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Get player ping (estimate)
     */
    private int getPing() {
        try {
            LocalPlayer player = mc.player;
            if (player != null && mc.getConnection() != null) {
                var playerListEntry = mc.getConnection().getPlayerInfo(player.getUUID());
                if (playerListEntry != null) {
                    return playerListEntry.getLatency();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 50; // Default fallback
    }

    // Config setters
    public void setSneakDelay(int delayMs) {
        this.sneakDelay = Math.max(10, Math.min(200, delayMs));
    }

    public void setProcessingTime(int timeMs) {
        this.processingTime = Math.max(20, Math.min(500, timeMs));
    }

    public void setAdaptivePing(boolean enabled) {
        this.adaptivePing = enabled;
    }

    public int getSneakDelay() {
        return sneakDelay;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    public boolean isAdaptivePing() {
        return adaptivePing;
    }

    // Config persistence
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("sneakDelay", sneakDelay);
        config.addProperty("processingTime", processingTime);
        config.addProperty("adaptivePing", adaptivePing);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("sneakDelay")) {
            sneakDelay = config.get("sneakDelay").getAsInt();
        }
        if (config.has("processingTime")) {
            processingTime = config.get("processingTime").getAsInt();
        }
        if (config.has("adaptivePing")) {
            adaptivePing = config.get("adaptivePing").getAsBoolean();
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Sneak Delay
        settings.add(new SliderSetting(
            "Sneak Delay",
            "Delay after sneak before right-click",
            "lcew_sneak_delay",
            10f, 200f,
            () -> (float) sneakDelay,
            val -> sneakDelay = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms"));

        // Processing Time
        settings.add(new SliderSetting(
            "Processing Time",
            "Time to hold sneak after right-click",
            "lcew_processing_time",
            20f, 500f,
            () -> (float) processingTime,
            val -> processingTime = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms"));

        // Adaptive Ping
        settings.add(new CheckboxSetting(
            "Adaptive Ping",
            "Adjust timing based on your ping",
            "lcew_adaptive_ping",
            () -> adaptivePing,
            val -> adaptivePing = val
        ));

        return settings;
    }
}
