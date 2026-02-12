package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.event.PacketEvent;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
// render imports via specific classes
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SS Helper - Complete 1:1 Port from ChatTriggers SimonSays Module
 *
 * Original by LegendaryJG and Bloom
 *
 * CRITICAL: This module follows the EXACT activation flow from the original:
 * 1. Chat "[BOSS] Storm: I should have known that I stood no chance." enables armor stand scanning
 * 2. When "Inactive" armor stand found → activates ALL SS features
 * 3. Features run until completed == 7
 */
public class SSHelperModule extends Module implements ConfigurableModule, SettingsProvider {

    /**
     * Debug event for tracking timing issues
     */
    private static class DebugEvent {
        final String type;
        final long timestamp;
        final String details;

        DebugEvent(String type, String details) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.details = details;
        }

        long getTimeSince() {
            return System.currentTimeMillis() - timestamp;
        }
    }

    // ===== SETTINGS =====
    private boolean blockWrongClicks = true;
    private boolean blockDuringLag = true;
    private boolean playSounds = true;
    private boolean showMessages = true;
    private boolean showP3Countdown = true;
    private boolean showCompletionTime = true;
    private boolean enableSSAlert = true;
    private boolean playSoundOnBreak = true;
    private boolean sendChatOnBreak = false;
    private boolean sendChatOnRestart = false;
    private boolean enableDebugMode = false;
    private boolean showDebugHUD = false;
    private List<ModuleSetting> settings;

    // ===== STATE FLAGS (1:1 from ChatTriggers) =====
    private boolean p3Active = false; // Main state - tracks if SS is running
    private boolean p3ArmorStandScanActive = false; // Scanning for "Inactive" armor stands
    private boolean lastButtonsExisted = false;
    private boolean allowClick = true;
    private boolean skipOver = false;
    private int buttonsShown = 0;

    // ===== SS ALERT STATE =====
    private boolean hasBroken = false;
    private boolean allowBreak = false;
    private boolean monitoringActive = false;
    private int alertTicks = 0;
    private String alertMessage = "";
    private boolean showingAlert = false;
    private long alertStartTime = 0;

    // ===== TIMING =====
    private long startTime = 0;
    private long ssCompletedTime = 0;

    // ===== P3 COUNTDOWN =====
    private boolean p3CountdownActive = false;
    private double p3CountdownDuration = 5.0; // Total duration
    private long p3CountdownStartTime = 0; // When countdown started

    // ===== DEBUG TIMING TRACKING =====
    private long lastPacketTime = 0;
    private long lastButtonClickTime = 0;
    private long lastSeaLanternDetectTime = 0;
    private long lastBlockRemoveTime = 0;
    private int totalPacketsReceived = 0;
    private int totalButtonsClicked = 0;
    private int totalButtonsDetected = 0;
    private int blockedWrongClicks = 0;
    private int blockedLagClicks = 0;
    private final List<DebugEvent> debugEvents = new ArrayList<>();

    // ===== DATA STRUCTURES =====
    private final LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();

    // ===== POSITIONS (1:1 from ChatTriggers) =====
    private static final int START_X = 111;
    private static final int START_Y = 120;
    private static final int START_Z = 92;
    private static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);

    // ===== RENDERING =====
    private static final double BUTTON_WIDTH = 0.4;
    private static final double BUTTON_HEIGHT = 0.26;

    // ===== CHAT PATTERN (1:1 from ChatTriggers) =====
    private static final Pattern DEVICE_PATTERN = Pattern.compile(
        "(\\w+) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)(?: \\(([\\d.]+)s \\| ([\\d.]+)s\\))?"
    );
    private static final Pattern STORM_PATTERN = Pattern.compile(
        "\\[BOSS\\] Storm: I should have known that I stood no chance\\."
    );

    public SSHelperModule() {
        super("SSHelper", "1:1 CT SimonSays port - button highlighting & click protection", Category.DUNGEONS, false);
        initializeSettings();
        UseBlockCallback.EVENT.register(this::onBlockInteract);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void initializeSettings() {
        settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Block Wrong Clicks",
            "Blocks clicking wrong buttons",
            "blockWrongClicks",
            () -> blockWrongClicks,
            (value) -> blockWrongClicks = value
        ));

        settings.add(new CheckboxSetting(
            "Block During Lag",
            "Blocks double-clicks during server lag",
            "blockDuringLag",
            () -> blockDuringLag,
            (value) -> blockDuringLag = value
        ));

        settings.add(new CheckboxSetting(
            "Play Sounds",
            "Play sounds when blocking clicks",
            "playSounds",
            () -> playSounds,
            (value) -> playSounds = value
        ));

        settings.add(new CheckboxSetting(
            "Show Messages",
            "Show chat messages when blocking clicks",
            "showMessages",
            () -> showMessages,
            (value) -> showMessages = value
        ));

        settings.add(new CheckboxSetting(
            "P3 Countdown",
            "Show 5 second countdown when P3 starts",
            "showP3Countdown",
            () -> showP3Countdown,
            (value) -> showP3Countdown = value
        ));

        settings.add(new CheckboxSetting(
            "Completion Time",
            "Show SS completion time",
            "showCompletionTime",
            () -> showCompletionTime,
            (value) -> showCompletionTime = value
        ));

        settings.add(new CheckboxSetting(
            "Enable SS Alert",
            "Shows alert when SS breaks or restarts",
            "enableSSAlert",
            () -> enableSSAlert,
            (value) -> enableSSAlert = value
        ));

        settings.add(new CheckboxSetting(
            "Play Sound on Break",
            "Play sound when SS breaks",
            "playSoundOnBreak",
            () -> playSoundOnBreak,
            (value) -> playSoundOnBreak = value
        ));

        settings.add(new CheckboxSetting(
            "Chat on Break",
            "Send chat message when SS breaks",
            "sendChatOnBreak",
            () -> sendChatOnBreak,
            (value) -> sendChatOnBreak = value
        ));

        settings.add(new CheckboxSetting(
            "Chat on Restart",
            "Send chat message when SS restarts",
            "sendChatOnRestart",
            () -> sendChatOnRestart,
            (value) -> sendChatOnRestart = value
        ));

        settings.add(new CheckboxSetting(
            "Enable Debug Mode",
            "Enables detailed timing logs in console",
            "enableDebugMode",
            () -> enableDebugMode,
            (value) -> enableDebugMode = value
        ));

        settings.add(new CheckboxSetting(
            "Show Debug HUD",
            "Shows live timing information on screen",
            "showDebugHUD",
            () -> showDebugHUD,
            (value) -> showDebugHUD = value
        ));
    }

    @Override
    public List<ModuleSetting> getSettings() {
        return settings;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("blockWrongClicks", blockWrongClicks);
        config.addProperty("blockDuringLag", blockDuringLag);
        config.addProperty("playSounds", playSounds);
        config.addProperty("showMessages", showMessages);
        config.addProperty("showP3Countdown", showP3Countdown);
        config.addProperty("showCompletionTime", showCompletionTime);
        config.addProperty("enableSSAlert", enableSSAlert);
        config.addProperty("playSoundOnBreak", playSoundOnBreak);
        config.addProperty("sendChatOnBreak", sendChatOnBreak);
        config.addProperty("sendChatOnRestart", sendChatOnRestart);
        config.addProperty("enableDebugMode", enableDebugMode);
        config.addProperty("showDebugHUD", showDebugHUD);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("blockWrongClicks")) blockWrongClicks = data.get("blockWrongClicks").getAsBoolean();
        if (data.has("blockDuringLag")) blockDuringLag = data.get("blockDuringLag").getAsBoolean();
        if (data.has("playSounds")) playSounds = data.get("playSounds").getAsBoolean();
        if (data.has("showMessages")) showMessages = data.get("showMessages").getAsBoolean();
        if (data.has("showP3Countdown")) showP3Countdown = data.get("showP3Countdown").getAsBoolean();
        if (data.has("showCompletionTime")) showCompletionTime = data.get("showCompletionTime").getAsBoolean();
        if (data.has("enableSSAlert")) enableSSAlert = data.get("enableSSAlert").getAsBoolean();
        if (data.has("playSoundOnBreak")) playSoundOnBreak = data.get("playSoundOnBreak").getAsBoolean();
        if (data.has("sendChatOnBreak")) sendChatOnBreak = data.get("sendChatOnBreak").getAsBoolean();
        if (data.has("sendChatOnRestart")) sendChatOnRestart = data.get("sendChatOnRestart").getAsBoolean();
        if (data.has("enableDebugMode")) enableDebugMode = data.get("enableDebugMode").getAsBoolean();
        if (data.has("showDebugHUD")) showDebugHUD = data.get("showDebugHUD").getAsBoolean();
    }

    @Override
    protected void onEnable() {
        dev.hunchclient.render.WorldRenderExtractionCallback.EVENT.register(this::renderWorld);
        HudRenderCallback.EVENT.register(this::renderHud);
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    /**
     * Reset all state (called on disable or world unload)
     */
    private void reset() {
        p3Active = false;
        p3ArmorStandScanActive = false;
        lastButtonsExisted = false;
        allowClick = true;
        skipOver = false;
        buttonsShown = 0;
        startTime = 0;
        ssCompletedTime = 0;
        p3CountdownActive = false;
        p3CountdownStartTime = 0;
        p3CountdownDuration = 5.0;
        blocks.clear();

        // SS Alert reset
        hasBroken = false;
        allowBreak = false;
        monitoringActive = false;
        alertTicks = 0;
        alertMessage = "";
        showingAlert = false;
        alertStartTime = 0;

        // Debug reset
        lastPacketTime = 0;
        lastButtonClickTime = 0;
        lastSeaLanternDetectTime = 0;
        lastBlockRemoveTime = 0;
        totalPacketsReceived = 0;
        totalButtonsClicked = 0;
        totalButtonsDetected = 0;
        blockedWrongClicks = 0;
        blockedLagClicks = 0;
        debugEvents.clear();
    }

    /**
     * Add a debug event (only if debug mode enabled)
     */
    private void logDebug(String type, String details) {
        if (!enableDebugMode) return;

        DebugEvent event = new DebugEvent(type, details);
        debugEvents.add(event);

        // Keep only last 100 events
        if (debugEvents.size() > 100) {
            debugEvents.remove(0);
        }

        // Console log
        System.out.println(String.format("[SSHelper DEBUG] [%s] %s", type, details));
    }

    /**
     * CRITICAL: Packet Handler for lag detection & P3 countdown
     * Called on EVERY packet (1:1 from ChatTriggers)
     */
    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        // All features only work in dungeons
        if (!dev.hunchclient.util.DungeonUtils.isInDungeon()) return;

        // Reset allowClick on every packet (lag detection)
        if (p3Active) {
            long timeSinceLastPacket = lastPacketTime > 0 ? (System.currentTimeMillis() - lastPacketTime) : 0;
            lastPacketTime = System.currentTimeMillis();
            totalPacketsReceived++;

            boolean wasAllowClick = allowClick;
            allowClick = true;

            if (enableDebugMode) {
                logDebug("PACKET", String.format("Received packet #%d | Gap: %dms | AllowClick: %s -> true",
                    totalPacketsReceived, timeSinceLastPacket, wasAllowClick));
            }
        }
    }

    /**
     * Main tick - runs button scanning when p3Active
     */
    public void onClientTick(Minecraft mc) {
        if (!isEnabled()) return;
        if (mc.level == null || mc.player == null) return;

        // IMPORTANT: Alert timer runs ALWAYS (even outside dungeons for debug commands)
        if (showingAlert) {
            long elapsed = System.currentTimeMillis() - alertStartTime;
            if (elapsed > 3000) { // Show for 3 seconds
                showingAlert = false;
                alertMessage = "";
            }
        }

        // All other features only work in dungeons
        if (!dev.hunchclient.util.DungeonUtils.isInDungeon()) return;

        // Scan for "Inactive" armor stands when enabled
        if (p3ArmorStandScanActive && !p3Active) {
            scanForInactiveArmorStands(mc);
        }

        // Track buttons when p3 is active
        if (p3Active) {
            trackButtons(mc);
        }

        // SS Alert monitoring
        if (enableSSAlert && monitoringActive) {
            checkSSStatus(mc);
        }
    }

    /**
     * Scan for "Inactive" armor stands to detect P3 start
     * (1:1 from ChatTriggers renderEntity hook)
     */
    private void scanForInactiveArmorStands(Minecraft mc) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand)) continue;

            String name = entity.getName().getString();
            String stripped = name.replaceAll("§.", ""); // Remove formatting

            if (stripped.equals("Inactive") ||
                stripped.equals("Inactive Terminal") ||
                stripped.equals("Not Activated")) {

                // Found it! Start P3
                p3Started();
                break;
            }
        }
    }

    /**
     * CRITICAL: Called when P3 starts (1:1 from ChatTriggers p3juststarted())
     * This activates ALL SS features
     */
    private void p3Started() {
        System.out.println("[SSHelper] P3 Started! Activating all features...");

        p3ArmorStandScanActive = false; // Stop scanning
        p3Active = true; // Enable all features

        // Reset all state (1:1 from ChatTriggers)
        startTime = System.currentTimeMillis();
        allowClick = true;
        ssCompletedTime = 0;
        lastButtonsExisted = false;
        buttonsShown = 0;
        skipOver = false;
        blocks.clear();

        // Debug reset
        lastPacketTime = 0;
        lastButtonClickTime = 0;
        lastSeaLanternDetectTime = 0;
        lastBlockRemoveTime = 0;
        totalPacketsReceived = 0;
        totalButtonsClicked = 0;
        totalButtonsDetected = 0;
        blockedWrongClicks = 0;
        blockedLagClicks = 0;
        debugEvents.clear();

        logDebug("P3_START", "P3 Started! All features activated. Timer: 0ms");
    }

    /**
     * Track button sequence (1:1 from ChatTriggers clientTicksForSS)
     */
    private void trackButtons(Minecraft mc) {
        int x0 = START_X;
        int y0 = START_Y;
        int z0 = START_Z;

        // Check if buttons exist (button at START_X-1, START_Y, START_Z)
        boolean buttonsExist = mc.level.getBlockState(new BlockPos(x0 - 1, y0, z0))
            .is(Blocks.STONE_BUTTON);

        // Detect new SS start
        if (buttonsExist && !lastButtonsExisted) {
            boolean allObi = true;

            // Check if all obsidian blocks are present (1:1 from ChatTriggers)
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = 0; dz <= 3; dz++) {
                    BlockPos pos = new BlockPos(x0, y0 + dy, z0 + dz);
                    if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                        allObi = false;
                        break;
                    }
                }
                if (!allObi) break;
            }

            if (allObi) {
                lastButtonsExisted = true;
                skipOver = true;
            }
        }

        // Detect SS end (1:1 from ChatTriggers)
        // IMPORTANT: Only reset lastButtonsExisted and blocks, NOT buttonsShown or skipOver!
        if (!buttonsExist && lastButtonsExisted) {
            lastButtonsExisted = false;
            blocks.clear();
        }

        // Scan for sea lantern blocks (ID 169 in 1.8.9 = SEA_LANTERN in 1.21)
        // CRITICAL: dy = 0; dy <= 3 (4 ROWS, not 3!)
        for (int dy = 0; dy <= 3; dy++) {
            for (int dz = 0; dz <= 3; dz++) {
                BlockPos pos = new BlockPos(x0, y0 + dy, z0 + dz);

                // Check for sea lantern (this shows which button to press)
                if (mc.level.getBlockState(pos).is(Blocks.SEA_LANTERN) && !blocks.contains(pos)) {
                    long timeSinceLastDetect = lastSeaLanternDetectTime > 0 ?
                        (System.currentTimeMillis() - lastSeaLanternDetectTime) : 0;
                    lastSeaLanternDetectTime = System.currentTimeMillis();
                    totalButtonsDetected++;

                    blocks.add(pos);

                    logDebug("SEA_LANTERN", String.format("Detected button #%d at %s | Gap: %dms | Queue size: %d | skipOver: %s | buttonsShown: %d",
                        totalButtonsDetected, pos, timeSinceLastDetect, blocks.size(), skipOver, buttonsShown));

                    // CRITICAL: skipOver prevents deleteFirst for initial buttons (1:1 from ChatTriggers)
                    // When skipOver = true (perfect SS with all obsidian), queue management is DISABLED
                    // skipOver is NEVER reset during SS - it stays true for the entire session
                    if (!skipOver) {
                        buttonsShown++;
                        // Remove old buttons based on pattern (1:1 from ChatTriggers)
                        int toRemove = 0;
                        if (buttonsShown == 3) toRemove = 1;
                        else if (buttonsShown == 5 || buttonsShown == 6) toRemove = 1;
                        else if (buttonsShown == 8 || buttonsShown == 9) toRemove = 1;
                        else if (buttonsShown > 10) toRemove = 1;

                        if (toRemove > 0) {
                            long removeTime = System.currentTimeMillis();
                            deleteFirst(blocks, toRemove);
                            logDebug("QUEUE_REMOVE", String.format("Removed %d old button(s) (buttonsShown=%d) | Queue size: %d -> %d | Time: %dms",
                                toRemove, buttonsShown, blocks.size() + toRemove, blocks.size(), System.currentTimeMillis() - removeTime));
                            lastBlockRemoveTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }
    }

    /**
     * Delete first N elements from set (1:1 from ChatTriggers)
     */
    private void deleteFirst(LinkedHashSet<BlockPos> set, int n) {
        List<BlockPos> toRemove = new ArrayList<>();
        int count = 0;
        for (BlockPos pos : set) {
            if (count >= n) break;
            toRemove.add(pos);
            count++;
        }
        set.removeAll(toRemove);
    }

    /**
     * Block interaction callback (1:1 from ChatTriggers mouseevent)
     */
    private InteractionResult onBlockInteract(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!isEnabled() || !world.isClientSide()) return InteractionResult.PASS;
        if (!p3Active) return InteractionResult.PASS; // Only work when P3 is active

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) return InteractionResult.PASS;

        BlockPos clickedPos = hitResult.getBlockPos();

        // Check if clicking a button
        if (!world.getBlockState(clickedPos).is(Blocks.STONE_BUTTON)) return InteractionResult.PASS;

        // If it's the start button (110, 121, 91), reset
        if (clickedPos.equals(START_BUTTON)) {
            blocks.clear();
            return InteractionResult.PASS;
        }

        // Get corresponding obsidian position (button is at x, obsidian is at x+1)
        BlockPos obsidianPos = new BlockPos(clickedPos.getX() + 1, clickedPos.getY(), clickedPos.getZ());

        // BLOCK WRONG CLICKS (1:1 from ChatTriggers)
        if (blockWrongClicks && !blocks.isEmpty()) {
            BlockPos firstButton = blocks.iterator().next();

            // Block if: NOT the first button AND is obsidian AND not sneaking
            if (!obsidianPos.equals(firstButton) && !player.isShiftKeyDown()) {
                if (world.getBlockState(obsidianPos).is(Blocks.OBSIDIAN)) {
                    blockedWrongClicks++;
                    long timeSinceLastClick = lastButtonClickTime > 0 ? (System.currentTimeMillis() - lastButtonClickTime) : 0;
                    logDebug("BLOCK_WRONG", String.format("Blocked wrong button click! Clicked: %s | Expected: %s | Gap: %dms | Total blocked: %d",
                        obsidianPos, firstButton, timeSinceLastClick, blockedWrongClicks));

                    if (playSounds) {
                        // 1:1 from ChatTriggers: "random.successful_hit" with volume 10, pitch 0
                        world.playSound(player, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK,
                            net.minecraft.sounds.SoundSource.MASTER, 10.0f, 0.0f);
                    }
                    if (showMessages) {
                        mc.player.displayClientMessage(Component.literal("§3[§bSSHelper§3]§r §cBlocking click because the wrong button was clicked"), false);
                    }
                    return InteractionResult.FAIL;
                }
            }
        }

        // BLOCK DURING LAG (1:1 from ChatTriggers)
        if (blockDuringLag) {
            // Check if NOT emerald block (ID 133 in 1.8.9)
            if (!world.getBlockState(obsidianPos).is(Blocks.EMERALD_BLOCK)) {
                if (allowClick) {
                    // First click after packet is OK
                    allowClick = false;
                    long timeSinceLastClick = lastButtonClickTime > 0 ? (System.currentTimeMillis() - lastButtonClickTime) : 0;
                    long timeSinceLastPacket = lastPacketTime > 0 ? (System.currentTimeMillis() - lastPacketTime) : 0;
                    logDebug("LAG_CHECK", String.format("First click allowed | Gap since last click: %dms | Gap since last packet: %dms",
                        timeSinceLastClick, timeSinceLastPacket));
                } else {
                    // Second click without new packet = LAG, BLOCK IT!
                    blockedLagClicks++;
                    long timeSinceLastClick = lastButtonClickTime > 0 ? (System.currentTimeMillis() - lastButtonClickTime) : 0;
                    long timeSinceLastPacket = lastPacketTime > 0 ? (System.currentTimeMillis() - lastPacketTime) : 0;
                    logDebug("BLOCK_LAG", String.format("Blocked lag click! Gap since last click: %dms | Gap since last packet: %dms | Total blocked: %d",
                        timeSinceLastClick, timeSinceLastPacket, blockedLagClicks));

                    if (playSounds) {
                        // 1:1 from ChatTriggers: "random.successful_hit" with volume 10, pitch 0
                        world.playSound(player, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK,
                            net.minecraft.sounds.SoundSource.MASTER, 10.0f, 0.0f);
                    }
                    if (showMessages) {
                        mc.player.displayClientMessage(Component.literal("§3[§bSSHelper§3]§r §cBlocking click because of server lag"), false);
                    }
                    return InteractionResult.FAIL;
                }
            }
        }

        // Remove clicked button from sequence (1:1 from ChatTriggers playerInteract)
        boolean wasRemoved = blocks.remove(obsidianPos);
        long timeSinceLastClick = lastButtonClickTime > 0 ? (System.currentTimeMillis() - lastButtonClickTime) : 0;
        lastButtonClickTime = System.currentTimeMillis();
        totalButtonsClicked++;

        logDebug("BUTTON_CLICK", String.format("Button clicked #%d at %s | Removed: %s | Gap: %dms | Queue size: %d | allowClick: %s",
            totalButtonsClicked, obsidianPos, wasRemoved, timeSinceLastClick, blocks.size(), allowClick));

        return InteractionResult.PASS;
    }

    /**
     * Called when a chat message is received
     * (1:1 from ChatTriggers chat triggers)
     */
    public void onChatMessage(Component message) {
        if (!isEnabled()) return;

        String text = message.getString();

        // SS Alert: Check for "Who dares trespass" (activates monitoring)
        if (enableSSAlert && text.contains("Who dares trespass")) {
            System.out.println("[SSHelper] Activating SS Alert monitoring...");
            monitoringActive = true;
            hasBroken = false;
            allowBreak = false;
            alertTicks = 12; // Initialize tick countdown
            return;
        }

        // Check for Storm P3 start (activates armor stand scanning + P3 countdown)
        Matcher stormMatcher = STORM_PATTERN.matcher(text);
        if (stormMatcher.find()) {
            System.out.println("[SSHelper] Storm chat detected! Starting armor stand scan...");

            // Start armor stand scanning
            p3ArmorStandScanActive = true;

            // Start P3 countdown
            if (showP3Countdown) {
                p3CountdownActive = true;
                p3CountdownDuration = 5.0;
                p3CountdownStartTime = System.currentTimeMillis();
            }
            return;
        }

        // Check for device completion (1:1 from ChatTriggers termLeverDeviceChat)
        Matcher deviceMatcher = DEVICE_PATTERN.matcher(text);
        if (deviceMatcher.find()) {
            String playerName = deviceMatcher.group(1);
            String object = deviceMatcher.group(3); // terminal, device, or lever
            int completed = Integer.parseInt(deviceMatcher.group(4));
            int total = Integer.parseInt(deviceMatcher.group(5));

            System.out.println("[SSHelper] Device pattern matched! object=" + object + " completed=" + completed + "/" + total);

            // If device completed, record time
            if (object.equals("device")) {
                ssCompletedTime = System.currentTimeMillis();

                // Check if player is in SS area (1:1 from ChatTriggers simonSaysOverDetection)
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double px = mc.player.getX();
                    double py = mc.player.getY();
                    double pz = mc.player.getZ();

                    // Check position: x > 103, y between 106-135, z between 84-103
                    if (px > 103 && py < 135 && py > 106 && pz < 103 && pz > 84) {
                        showSSCompletion();
                    }
                }
            }

            // If all devices done (7/7), stop P3 and SS Alert monitoring
            if (completed == 7) {
                System.out.println("[SSHelper] All devices completed! Deactivating P3...");
                p3Active = false;
                blocks.clear();
                monitoringActive = false; // Stop SS Alert monitoring
            }
        }
    }

    /**
     * Show SS completion time (1:1 from ChatTriggers ssJustFinished)
     */
    private void showSSCompletion() {
        if (!showCompletionTime) return;
        if (startTime == 0 || ssCompletedTime == 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double duration = (ssCompletedTime - startTime) / 1000.0;
            mc.player.displayClientMessage(
                Component.literal(String.format("§3[§bSSHelper§3]§r §eSS Took §6%.3fs!", duration)),
                false
            );
        }
    }

    /**
     * SS Alert: Check obsidian blocks to detect breaks and restarts
     * (1:1 from SSAlert ChatTriggers module)
     *
     * CORRECT LOGIC (from original SSAlert):
     * 1. Every tick: decrement alertTicks counter
     * 2. Check if ALL 16 blocks are obsidian
     *    - If ANY block is NOT obsidian → SS is running → reset alertTicks = 12, handle restart
     * 3. If ALL blocks are obsidian → SS might be broke
     *    - Wait for alertTicks countdown AND allowBreak flag
     *    - Check if ALL 16 button positions are AIR
     *    - If all AIR → SS BROKE!
     */
    private void checkSSStatus(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        // Decrement tick counter
        if (alertTicks > 0) {
            alertTicks--;
        }

        int x0 = START_X;
        int y0 = START_Y;
        int z0 = START_Z;

        // STEP 1: Check if ALL blocks are obsidian
        boolean allObsidian = true;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dz = 0; dz <= 3; dz++) {
                BlockPos pos = new BlockPos(x0, y0 + dy, z0 + dz);
                if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                    allObsidian = false;
                    break;
                }
            }
            if (!allObsidian) break;
        }

        // If NOT all obsidian → SS is actively running
        if (!allObsidian) {
            alertTicks = 12;  // Reset countdown (1:1 from original)
            allowBreak = true;

            // Handle restart (if it was previously broken)
            if (hasBroken) {
                System.out.println("[SSHelper] SS RESTART!");
                hasBroken = false;
                alertMessage = "§a§l§nSS RESTART!";
                showingAlert = true;
                alertStartTime = System.currentTimeMillis();

                if (sendChatOnRestart && mc.player != null) {
                    mc.player.connection.sendChat("SS Started Again!");
                }
            }
            return;
        }

        // STEP 2: ALL blocks are obsidian - wait for tick countdown
        if (alertTicks > 0 || !allowBreak) {
            return;
        }

        // STEP 3: Check if ALL button positions are AIR (no buttons)
        // Button positions: (x0-1, y0+dy, z0+dz) where x0=111 → button x=110
        for (int dy = 0; dy <= 3; dy++) {
            for (int dz = 0; dz <= 3; dz++) {
                BlockPos buttonPos = new BlockPos(x0 - 1, y0 + dy, z0 + dz);
                if (!mc.level.getBlockState(buttonPos).isAir()) {
                    // Button still exists, SS not broke yet
                    return;
                }
            }
        }

        // STEP 4: ALL obsidian + no buttons + ticks expired = SS BROKE!
        allowBreak = false;
        hasBroken = true;

        System.out.println("[SSHelper] SS BROKE!");
        alertMessage = "§c§l§nSS BROKE!";
        showingAlert = true;
        alertStartTime = System.currentTimeMillis();

        if (playSoundOnBreak) {
            mc.level.playSound(mc.player, mc.player.blockPosition(),
                SoundEvents.ANVIL_LAND,
                net.minecraft.sounds.SoundSource.MASTER, 5.0f, 0.0f);
        }

        if (sendChatOnBreak && mc.player != null) {
            mc.player.connection.sendChat("SS Broke!");
        }
    }

    /**
     * Render HUD elements (P3 countdown + SS Alerts + Debug HUD)
     */
    public void renderHud(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Draw Debug HUD (top-left corner)
        if (showDebugHUD && p3Active) {
            renderDebugHUD(context, mc);
        }

        // Draw P3 countdown in center of screen (1:1 from ChatTriggers)
        if (p3CountdownActive) {
            // Calculate remaining time based on elapsed time
            double elapsed = (System.currentTimeMillis() - p3CountdownStartTime) / 1000.0;
            double remaining = p3CountdownDuration - elapsed;

            // Stop countdown if time is up
            if (remaining <= 0) {
                p3CountdownActive = false;
                return;
            }

            String countdownText = String.format("§b%.2f", remaining);
            int textWidth = mc.font.width(countdownText);

            context.drawString(
                mc.font,
                countdownText,
                screenWidth / 2 - textWidth / 2,
                screenHeight / 2 - 50,
                0xFFFFFF,
                true
            );
        }

        // Draw SS Alert notification (Now Playing style HUD)
        if (showingAlert && !alertMessage.isEmpty()) {
            // Strip formatting codes ONLY for color detection
            String cleanText = alertMessage.replaceAll("§.", "");

            // HUD dimensions (like Now Playing)
            int hudWidth = 280;
            int hudHeight = 70;
            int hudX = screenWidth / 2 - hudWidth / 2; // Centered
            int hudY = screenHeight / 2 - hudHeight / 2 - 50; // Above center

            // Determine color based on message
            int accentColor = cleanText.contains("BROKE") ? 0xFFFF0000 : 0xFF00FF00; // Red or Green
            int backgroundColor = 0xCC000000; // Semi-transparent black

            // Draw background
            context.fill(hudX, hudY, hudX + hudWidth, hudY + hudHeight, backgroundColor);

            // Draw border (Now Playing style)
            int borderThickness = 2;
            context.fill(hudX, hudY, hudX + hudWidth, hudY + borderThickness, accentColor); // Top
            context.fill(hudX, hudY + hudHeight - borderThickness, hudX + hudWidth, hudY + hudHeight, accentColor); // Bottom
            context.fill(hudX, hudY, hudX + borderThickness, hudY + hudHeight, accentColor); // Left
            context.fill(hudX + hudWidth - borderThickness, hudY, hudX + hudWidth, hudY + hudHeight, accentColor); // Right

            // Calculate text size and position (using clean text for width calculation)
            int textY = hudY + 15;
            int textWidth = mc.font.width(cleanText);
            int textX = hudX + hudWidth / 2 - textWidth / 2; // Center text

            // Simple & clean: just ONE shadow (bottom-right) + colored text
            // Shadow (black, 1px offset)
            context.drawString(
                mc.font,
                alertMessage,
                textX + 1,
                textY + 1,
                0xFF000000,  // Black shadow
                false
            );

            // Main text (with §c§l§n formatting!)
            context.drawString(
                mc.font,
                alertMessage,
                textX,
                textY,
                0xFFFFFFFF,
                false
            );

            // Draw subtitle with time remaining
            long elapsed = System.currentTimeMillis() - alertStartTime;
            long remaining = Math.max(0, 3000 - elapsed);
            double secondsRemaining = remaining / 1000.0;
            String subtitle = String.format("§7Closing in %.1fs", secondsRemaining);
            int subtitleWidth = mc.font.width(subtitle);
            int subtitleX = hudX + hudWidth / 2 - subtitleWidth / 2;
            int subtitleY = textY + mc.font.lineHeight + 12;

            context.drawString(
                mc.font,
                subtitle,
                subtitleX,
                subtitleY,
                0xFFAAAAAA,
                true
            );

            // Draw accent bar at bottom (animated) - FIX: prevent negative width
            float progress = Math.max(0.0f, 1.0f - (elapsed / 3000.0f));
            int barWidth = (int)(hudWidth * progress);
            int barHeight = 3;
            context.fill(
                hudX,
                hudY + hudHeight - barHeight,
                hudX + barWidth,
                hudY + hudHeight,
                accentColor
            );
        }
    }

    /**
     * Render Debug HUD showing live timing information
     */
    private void renderDebugHUD(GuiGraphics context, Minecraft mc) {
        int x = 5;
        int y = 5;
        int lineHeight = mc.font.lineHeight + 2;
        int currentLine = 0;

        // Background
        int hudWidth = 300;
        int hudHeight = 200;
        context.fill(x - 2, y - 2, x + hudWidth, y + hudHeight, 0xCC000000);

        // Title
        context.drawString(mc.font, "§6§lSSHelper Debug", x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        currentLine++; // Blank line

        // Elapsed time since P3 start
        long elapsedTime = startTime > 0 ? (System.currentTimeMillis() - startTime) : 0;
        context.drawString(mc.font, String.format("§eElapsed: §f%.2fs", elapsedTime / 1000.0),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);

        // Current state
        context.drawString(mc.font, String.format("§eQueue Size: §f%d", blocks.size()),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§eButtons Shown: §f%d", buttonsShown),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§eSkip Over: §f%s", skipOver),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§eAllow Click: §f%s", allowClick),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);

        currentLine++; // Blank line

        // Statistics
        context.drawString(mc.font, "§a§lStatistics:", x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§aPackets: §f%d", totalPacketsReceived),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§aDetected: §f%d", totalButtonsDetected),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§aClicked: §f%d", totalButtonsClicked),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§cBlocked (Wrong): §f%d", blockedWrongClicks),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§cBlocked (Lag): §f%d", blockedLagClicks),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);

        currentLine++; // Blank line

        // Last event timings
        long timeSincePacket = lastPacketTime > 0 ? (System.currentTimeMillis() - lastPacketTime) : 0;
        long timeSinceDetect = lastSeaLanternDetectTime > 0 ? (System.currentTimeMillis() - lastSeaLanternDetectTime) : 0;
        long timeSinceClick = lastButtonClickTime > 0 ? (System.currentTimeMillis() - lastButtonClickTime) : 0;

        context.drawString(mc.font, "§b§lLast Event:", x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§bPacket: §f%dms ago", timeSincePacket),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§bDetect: §f%dms ago", timeSinceDetect),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
        context.drawString(mc.font, String.format("§bClick: §f%dms ago", timeSinceClick),
            x, y + (currentLine++ * lineHeight), 0xFFFFFF, true);
    }

    // ===== DEBUG METHODS =====

    /**
     * Debug: Manually trigger an alert (for testing)
     */
    public void debugShowAlert(String message, int durationMs) {
        alertMessage = message;
        showingAlert = true;
        alertStartTime = System.currentTimeMillis() - (3000 - durationMs);
        System.out.println("[SSHelper Debug] Showing alert: " + message);
    }

    /**
     * Debug: Manually start P3 countdown (for testing)
     */
    public void debugStartCountdown(double seconds) {
        p3CountdownActive = true;
        p3CountdownDuration = seconds;
        p3CountdownStartTime = System.currentTimeMillis();
        System.out.println("[SSHelper Debug] Starting countdown: " + seconds + "s");
    }

    /**
     * Debug: Toggle debug mode
     */
    public void toggleDebugMode() {
        enableDebugMode = !enableDebugMode;
        System.out.println("[SSHelper Debug] Debug mode: " + enableDebugMode);
    }

    /**
     * Debug: Toggle debug HUD
     */
    public void toggleDebugHUD() {
        showDebugHUD = !showDebugHUD;
        System.out.println("[SSHelper Debug] Debug HUD: " + showDebugHUD);
    }

    /**
     * Debug: Get debug mode state
     */
    public boolean isDebugModeEnabled() {
        return enableDebugMode;
    }

    /**
     * Debug: Get debug HUD state
     */
    public boolean isDebugHUDEnabled() {
        return showDebugHUD;
    }

    /**
     * Render world elements (button highlighting)
     * (1:1 from ChatTriggers renderWorldSSHighlight, using PrimitiveCollector)
     */
    private void renderWorld(PrimitiveCollector collector) {
        if (!isEnabled()) return;
        if (!p3Active) return;
        if (blocks.isEmpty()) return;

        List<BlockPos> blocksList = new ArrayList<>(blocks);
        for (int i = 0; i < blocksList.size(); i++) {
            BlockPos pos = blocksList.get(i);

            // Color based on position (1:1 from ChatTriggers)
            float[] color;
            if (i == 0) {
                // First button: GREEN
                color = new float[]{0.0f, 1.0f, 0.0f};
            } else if (i == 1) {
                // Second button: YELLOW
                color = new float[]{1.0f, 1.0f, 0.0f};
            } else {
                // Rest: RED
                color = new float[]{1.0f, 0.0f, 0.0f};
            }

            // Draw inner ESP box (1:1 from ChatTriggers)
            double x = pos.getX() + 0.05;
            double y = pos.getY() + 0.5 - BUTTON_HEIGHT / 2 + 0.001;
            double z = pos.getZ() + 0.5;
            double width = BUTTON_WIDTH;
            double height = BUTTON_HEIGHT;

            AABB box = new AABB(x, y, z, x + width, y + height, z + width);
            collector.submitFilledBox(box, color, 0.7f, true);
        }
    }
}
