package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.event.BlockChangeEvent;
import dev.hunchclient.event.PacketEvent;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.module.impl.misc.F7SimModule;
import dev.hunchclient.util.Executor;
import com.google.gson.JsonObject;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.hunchclient.module.IAutoScreenshot;

/**
 * AutoSS - automatically clicks buttons in the correct sequence for the "Device" secret in Skyblock Dungeons.
 *
 * @Author Kaze.0707
 */
public class AutoSSModule extends Module implements ConfigurableModule, SettingsProvider, IAutoScreenshot {

    // Settings
    private int delay = 200;              // Delay between clicks (ms)
    private boolean forceDevice = false;   // Force think we're at device
    private int autoStartDelay = 50;       // Delay between autostart clicks (default 1 tick)
    private boolean smoothRotate = false;  // Smooth rotation 
    private int rotationSpeed = 200;       // Rotation speed (ms)
    private boolean dontCheck = false;     // Faster SS mode
    private boolean requireButtonVisible = false; // Require button to be visible (1.8.9 mode)
    private boolean spawnClientButtons = true; // Spawn buttons clientside for visibility (Hypixel fix)
    private int buttonReadyTimeoutMs = 1500; // Wait time for start button to become clickable

    // State variables
    private long lastClickAdded = System.currentTimeMillis();
    private boolean next = false;
    private int progress = 0;
    private boolean doneFirst = false;
    private boolean doingSS = false;
    private boolean clicked = false;
    private List<BlockPos> clicks = new ArrayList<>();
    private long wtflip = System.currentTimeMillis();
    private Vec3 clickedButton = null;
    private List<Vec3> allButtons = new ArrayList<>();
    private long deviceCompletedAt = 0L; // Timestamp for hiding HUD numbers after completion
    private long lastPollTime = 0L; // Last time we polled for sea lanterns
    private static final long POLL_INTERVAL = 50; // Poll every 50ms (20 times per second)
    private boolean clientButtonsSpawned = false; // Track if we spawned client-side buttons

    // Fixed positions for this specific secret
    private static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);
    private static final int BUTTON_READY_POLL_MS = 50;
    private static final boolean DEBUG = false; // Turn off spam logs

    private final Random random = new Random();
    private Executor ssLoopExecutor;
    private boolean isRotating = false;
    private long rotationStartTime = 0L;
    private int rotationDurationMs = 0;
    private float rotationStartYaw = 0f;
    private float rotationStartPitch = 0f;
    private float rotationTargetYaw = 0f;
    private float rotationTargetPitch = 0f;
    private Level lastWorld = null;

    // Human-like rotation noise (persistent between frames for smoothness)
    private float lastNoiseYaw = 0f;
    private float lastNoisePitch = 0f;
    private long rotationSeed = 0L;

    // Orbit EventBus for packet events
    private final IEventBus eventBus;
    private boolean orbitSubscribed = false;

    public AutoSSModule(IEventBus eventBus) {
        super("AutoSS", "Automatic Solver for first dev", Category.DUNGEONS, RiskLevel.VERY_RISKY);
        this.eventBus = eventBus;

        // Register block change listener (custom EventBus)
        dev.hunchclient.event.EventBus.getInstance().registerBlockChangeListener(this::onBlockChange);

        // Start SS loop
        initSSLoop();

        // Register world events
        // Register block interaction callback
        UseBlockCallback.EVENT.register(this::onBlockUse);
    }

    @Override
    protected void onEnable() {
        subscribeOrbitEvents();
        // Register for EVERY RENDER FRAME (unlimited FPS = smooth rotation!)
        dev.hunchclient.render.WorldRenderExtractionCallback.EVENT.register(this::onRenderFrame);
        // Register HUD rendering for 2D numbers with projection!
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(this::renderHudNumbers);
        reset();
    }

    /**
     * Called EVERY RENDER FRAME for ultra-smooth rotation at unlimited FPS!
     */
    private void onRenderFrame(PrimitiveCollector collector) {
        if (!isEnabled()) return;

        // Update rotation EVERY FRAME (not every 10ms!)
        if (isRotating) {
            updateSmoothRotation();
        }

        // Render button overlays
        renderButtonOverlays(collector);
    }

    @Override
    protected void onDisable() {
        // No explicit unregister - guard with isEnabled() in render callback
        unsubscribeOrbitEvents();
        stopRotation();
        reset();
    }

    private void subscribeOrbitEvents() {
        if (!orbitSubscribed) {
            eventBus.subscribe(this);
            orbitSubscribed = true;
        }
    }

    private void unsubscribeOrbitEvents() {
        if (orbitSubscribed) {
            eventBus.unsubscribe(this);
            orbitSubscribed = false;
        }
    }

    /**
     * Renders button numbers and last clicked button using PrimitiveCollector
     */
    private void renderButtonOverlays(PrimitiveCollector collector) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Only render if close to device
        if (getDistanceSqToCenter(mc.player.blockPosition(), START_BUTTON) > 1600) return;

        // Check if enough time passed since last click to hide clickedButton
        if (System.currentTimeMillis() - lastClickAdded > delay) {
            clickedButton = null;
        }

        // Render last clicked button as emerald green filled box
        if (clickedButton != null) {
            AABB box = new AABB(
                clickedButton.x + 0.875, clickedButton.y + 0.375, clickedButton.z + 0.3125,
                clickedButton.x + 1.0, clickedButton.y + 0.625, clickedButton.z + 0.6875
            );

            // Emerald green (#50C878)
            float[] emeraldGreen = {0.31f, 0.78f, 0.47f};
            collector.submitFilledBox(box, emeraldGreen, 0.8f, true);
        }

        if (!allButtons.isEmpty()) {
            for (int i = 0; i < allButtons.size(); i++) {
                Vec3 buttonPos = allButtons.get(i); // This is now button position (X=110)

                // Determine button state and color
                boolean isClicked = i < progress;  // Already clicked
                boolean isPending = i >= progress; // Still pending

                // Render box around button
                AABB box = new AABB(
                    buttonPos.x + 0.8, buttonPos.y + 0.3, buttonPos.z + 0.25,
                    buttonPos.x + 1.05, buttonPos.y + 0.7, buttonPos.z + 0.75
                );

                if (isClicked) {
                    // Already clicked - Amethyst purple (#9966CC)
                    float[] amethyst = {0.6f, 0.4f, 0.8f};
                    collector.submitFilledBox(box, amethyst, 0.6f, true);
                } else if (isPending) {
                    // Pending - Ruby red (#E0115F)
                    float[] ruby = {0.88f, 0.07f, 0.37f};
                    collector.submitFilledBox(box, ruby, 0.6f, true);
                }
            }
        }
    }

    /**
     * HUD rendering with 2D worldToScreen projection!
     */
    private void renderHudNumbers(net.minecraft.client.gui.GuiGraphics drawContext, net.minecraft.client.DeltaTracker tickCounter) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;

        // Hide numbers shortly after a complete solve to reduce clutter
        if (deviceCompletedAt > 0L) {
            long elapsed = System.currentTimeMillis() - deviceCompletedAt;
            if (elapsed >= 5000L) {
                return;
            }
        }

        // Only render if close to device
        if (getDistanceSqToCenter(mc.player.blockPosition(), START_BUTTON) > 1600) return;

        Camera camera = mc.gameRenderer.getMainCamera();

        var textRenderer = mc.font;

        for (int i = 0; i < allButtons.size(); i++) {
            Vec3 buttonPos = allButtons.get(i);

            // World position AT THE EXACT BUTTON CENTER (matches box rendering!)
            Vec3 worldPos = new Vec3(
                buttonPos.x + 0.925,  // Exact center of box X (0.8 to 1.05 → center = 0.925)
                buttonPos.y + 0.5,    // Exact center of box Y (0.3 to 0.7 → center = 0.5)
                buttonPos.z + 0.5     // Exact center of box Z (0.25 to 0.75 → center = 0.5)
            );

            // Project 3D world position to 2D screen coordinates
            Vec3 screenPos = worldToScreen(worldPos, camera, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());

            // Skip if behind camera or out of view
            if (screenPos == null) continue;

            String numberText = String.valueOf(i + 1);

            // Determine color
            int color;
            if (i < progress) {
                color = 0xFF9966CC; // Amethyst (clicked) with alpha
            } else {
                color = 0xFFE0115F; // Ruby (pending) with alpha
            }

            // Draw text centered on screen position
            // In 1.21.8+, Matrix3x2fStack API changed - use simpler approach
            float scale = 2.0f;
            int textWidth = textRenderer.width(numberText);

            // Calculate position (centered, scaled)
            int x = (int)(screenPos.x - (textWidth * scale) / 2);
            int y = (int)screenPos.y;

            // Draw black background for readability
            drawContext.fill(
                x - 2, y - 2,
                x + (int)(textWidth * scale) + 2, y + (int)(textRenderer.lineHeight * scale) + 2,
                0x80000000
            );

            // Draw scaled text using DrawContext
            // Note: In 1.21.8 we draw at absolute positions without matrix manipulation
            drawContext.drawString(
                textRenderer,
                numberText,
                x,
                y,
                color,
                true
            );
        }
    }

    /**
     * Convert 3D world position to 2D screen coordinates
     * Uses CORRECT Minecraft projection (like nametag rendering!)
     * Returns null if position is behind camera
     */
    private Vec3 worldToScreen(Vec3 worldPos, Camera camera, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();

        // Get position relative to camera
        Vec3 cameraPos = camera.getPosition();
        double x = worldPos.x - cameraPos.x;
        double y = worldPos.y - cameraPos.y;
        double z = worldPos.z - cameraPos.z;

        // Get camera rotation (yaw and pitch)
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();

        // Convert to radians
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // Rotate by yaw (around Y axis)
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double rotX = x * cosYaw + z * sinYaw;
        double rotZ = z * cosYaw - x * sinYaw;

        // Rotate by pitch (around X axis) - FIXED SIGNS!
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double rotY = y * cosPitch + rotZ * sinPitch;  // FIXED: was MINUS, now PLUS!
        double viewZ = rotZ * cosPitch - y * sinPitch;  // FIXED: was PLUS, now MINUS!

        // Check if behind camera (viewZ should be negative for things in front in Minecraft's coordinate system)
        if (viewZ <= 0) return null;

        // Project to screen space
        double fov = mc.options.fov().get();
        double fovRad = Math.toRadians(fov);
        double scaleFactor = screenHeight / (2.0 * Math.tan(fovRad / 2.0));

        // Calculate screen position
        double screenX = screenWidth / 2.0 - (rotX / viewZ) * scaleFactor;
        double screenY = screenHeight / 2.0 - (rotY / viewZ) * scaleFactor;

        return new Vec3(screenX, screenY, 0);
    }

    /**
     * Resets all state variables
     */
    public void reset() {
        allButtons.clear();
        clicks.clear();
        next = false;
        progress = 0;
        doneFirst = false;
        doingSS = false;
        clicked = false;
        clickedButton = null;
        wtflip = System.currentTimeMillis();
        deviceCompletedAt = 0L;
        clientButtonsSpawned = false; // Reset client button spawn state
        if (isEnabled()) {
            debugMessage("Reset!");
        }
    }

    /**
     * Starts the SS sequence
     */
    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!forceDevice && !isInSkyblock()) return;

        allButtons.clear();

        // Check distance to device
        if (getDistanceSqToCenter(mc.player.blockPosition(), START_BUTTON) > 25) return;

        if (!clicked) {
            debugMessage("Starting SS");
            debugMessage(String.valueOf(System.currentTimeMillis()));
            reset();
            clicked = true;
            doingSS = true;

            if (smoothRotate) {
                float[] rotation = calculateRotation(
                    START_BUTTON.getX() + 0.875,
                    START_BUTTON.getY() + 0.5,
                    START_BUTTON.getZ() + 0.5
                );
                scheduleRotation(rotation[0], rotation[1], rotationSpeed);
            }

            // EXACT ORIGINAL CGA LOGIC (Lines 102-114)
            // Fast Mode: Skip 2 times before actual start (to skip initial flashes)
            // Normal Mode: Just 1 click to start
            new Thread(() -> {
                try {
                    if (!waitForButtonReady(START_BUTTON, buttonReadyTimeoutMs)) {
                        debugMessage("§cStart button not ready within " + buttonReadyTimeoutMs + "ms, aborting auto-start.");
                        doingSS = false;
                        clicked = false;
                        return;
                    }

                    if (dontCheck) {
                        // Fast mode skip: Click start button 2 times to skip initial flashes
                        for (int i = 0; i < 2; i++) {
                            reset();
                            clickButton(START_BUTTON.getX(), START_BUTTON.getY(), START_BUTTON.getZ());
                            int upperBound = Math.max(autoStartDelay + 1, (autoStartDelay * 1136) / 1000);
                            int sleepTime = upperBound > autoStartDelay ? random.nextInt(autoStartDelay, upperBound) : autoStartDelay;
                            if (sleepTime > 0) {
                                Thread.sleep(sleepTime);
                            }
                        }
                    }
                    // After skipping (or without skip), do the actual start
                    doingSS = true;
                    clickButton(START_BUTTON.getX(), START_BUTTON.getY(), START_BUTTON.getZ());
                } catch (Exception e) {
                    modMessage("§cAutoSS Error: " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * Main SS loop - runs every 10ms
     */
    private void initSSLoop() {
        ssLoopExecutor = new Executor(10, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            // World change detection - reset state on world change
            if (lastWorld != mc.level) {
                lastWorld = mc.level;
                reset();
            }

            if (!this.isEnabled()) return;
            if (!forceDevice && !isInSkyblock()) return;

            // Spawn client-side buttons if toggle enabled (always show buttons for Hypixel fix)
            if (spawnClientButtons && !clientButtonsSpawned) {
                if (getDistanceSqToCenter(mc.player.blockPosition(), START_BUTTON) <= 400) { // Within 20 blocks
                    spawnClientSideButtons(mc);
                    clientButtonsSpawned = true;
                }
            }

            // Check if enough time passed since last click
            if (System.currentTimeMillis() - lastClickAdded + 1 < delay) return;

            // Check if we're in range
            if (getDistanceSqToCenter(mc.player.blockPosition(), START_BUTTON) > 25) return;

            // HYPIXEL 1.21 FIX: Poll for sea lanterns since BlockChangeEvent doesn't fire!
            long now = System.currentTimeMillis();
            if (doingSS && now - lastPollTime >= POLL_INTERVAL) {
                lastPollTime = now;
                pollForSeaLanterns(mc);
            }

            // Detect if "Device" armor stand is nearby (1.21 compatible)
            boolean device = doingSS; // If we're already doing SS, device is active!

            // Only need to check for armor stand if we haven't started yet
            if (!device && !forceDevice) {
                // PERFORMANCE: Use getEntitiesByClass to only get ArmorStands instead of all entities
                List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                    ArmorStand.class,
                    mc.player.getBoundingBox().inflate(6), // Only search within 6 blocks
                    armorStand -> armorStand.distanceTo(mc.player) < 6
                );

                for (ArmorStand armorStand : armorStands) {
                    // Try multiple ways to get the name (1.21 compatibility)
                    Component displayName = armorStand.getDisplayName();
                    Component customName = armorStand.getCustomName();
                    String name = "";

                    if (customName != null) {
                        name = customName.getString();
                    } else if (displayName != null) {
                        name = displayName.getString();
                    }

                    if (name.contains("Device")) {
                        device = true;
                        break;
                    }
                }
            }

            if (forceDevice) device = true;

            if (!device) {
                if (DEBUG) debugMessage("No device armor stand found, skipping.");
                clicked = false;
                return;
            }

            // NOTE: No auto-start here! In original CGA, start() is ONLY called from:
            // 1. onChatMessage("Who dares...") - Chat event
            // 2. onKeyBind() - Manual keybind
            // 3. onBlockUse(START_BUTTON) - Right-click on start button
            // The button detection was removed to match original behavior

            // EXACT ORIGINAL CGA LOGIC (Zeile 156-172 from AutoSS.kt)
            Block detect = mc.level.getBlockState(new BlockPos(110, 123, 92)).getBlock();

            if (DEBUG && doingSS && clicks.size() > 0) {
                debugMessage("§eLoop check: detect=" + detect +
                           ", doingSS=" + doingSS +
                           ", clicks=" + clicks.size() +
                           ", progress=" + progress +
                           ", doneFirst=" + doneFirst);
            }

            if ((detect == Blocks.STONE_BUTTON || (dontCheck && doneFirst)) && doingSS) {
                if (!doneFirst && clicks.size() == 3) {
                    clicks.remove(0);
                    allButtons.remove(0);
                    debugMessage("§6Removed one of 3 initial clicks");
                }
                doneFirst = true;

                if (progress < clicks.size()) {
                    BlockPos next = clicks.get(progress);
                    Block blockAtNext = mc.level.getBlockState(next).getBlock();

                    if (DEBUG) {
                        debugMessage("§eChecking button " + (progress+1) + "/" + clicks.size() +
                                   " at " + next + ", block=" + blockAtNext);
                    }

                    if (blockAtNext == Blocks.STONE_BUTTON) {
                        debugMessage("§a§lBUTTON FOUND! Clicking " + (progress+1) + "/" + clicks.size());

                        if (smoothRotate) {
                            float[] rotation = calculateRotation(
                                next.getX() + 0.875,
                                next.getY() + 0.5,
                                next.getZ() + 0.5
                            );
                            scheduleRotation(rotation[0], rotation[1], rotationSpeed);
                        }
                        clickButton(next.getX(), next.getY(), next.getZ());
                        progress++;

                        if (progress >= clicks.size() && !clicks.isEmpty()) {
                            deviceCompletedAt = System.currentTimeMillis();
                            debugMessage("§a§l§nDEVICE COMPLETED!");
                        }
                    } else if (DEBUG) {
                        debugMessage("§cButton not visible yet, waiting...");
                    }
                }
            } else if (DEBUG && doingSS && clicks.size() > 0) {
                debugMessage("§cDetection block check failed: detect=" + detect + ", required=STONE_BUTTON");
            }
        });
        ssLoopExecutor.register();
    }

    /**
     * Spawn client-side buttons for visibility (Hypixel fix - buttons not visible until clicked)
     * These are ONLY visual and don't send packets to server!
     */
    private void spawnClientSideButtons(Minecraft mc) {
        if (mc.level == null) return;

        mc.execute(() -> {
            if (mc.level == null) {
                return;
            }

            debugMessage("§aSpawning client-side buttons at X=110");
            int count = 0;

            for (int y = 120; y <= 123; y++) {
                for (int z = 92; z <= 95; z++) {
                    BlockPos buttonPos = new BlockPos(110, y, z);

                    BlockPos obsidianPos = new BlockPos(111, y, z);
                    BlockState obsidianState = mc.level.getBlockState(obsidianPos);
                    if (!obsidianState.is(Blocks.OBSIDIAN)) {
                        mc.level.setBlockAndUpdate(obsidianPos, Blocks.OBSIDIAN.defaultBlockState());
                    }

                    BlockState current = mc.level.getBlockState(buttonPos);
                    if (!current.is(Blocks.STONE_BUTTON)) {
                        BlockState buttonState = Blocks.STONE_BUTTON.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
                        mc.level.setBlockAndUpdate(buttonPos, buttonState);
                        count++;
                    }
                }
            }

            debugMessage("§aSpawned " + count + " client-side buttons");
        });
    }

    /**
     * HYPIXEL 1.21 FIX: Poll for sea lanterns instead of relying on BlockChangeEvent
     * Hypixel doesn't send block change packets for sea lanterns in 1.21
     */
    private void pollForSeaLanterns(Minecraft mc) {
        // Check all 16 positions in the 4x4 grid (x=111, y=120-123, z=92-95)
        for (int y = 120; y <= 123; y++) {
            for (int z = 92; z <= 95; z++) {
                BlockPos gridPos = new BlockPos(111, y, z);
                Block block = mc.level.getBlockState(gridPos).getBlock();

                if (block == Blocks.SEA_LANTERN) {
                    // Sea lantern at X=111, button at X=110 (one block west)
                    BlockPos buttonPos = new BlockPos(110, y, z);

                    // Check if we already registered this position
                    if (!clicks.contains(buttonPos)) {
                        debugMessage("§a§lSEA LANTERN " + (clicks.size() + 1) + "/5 §7(y=" + y + ", z=" + z + ")");
                        progress = 0;
                        clicks.add(buttonPos);
                        allButtons.add(new Vec3(buttonPos.getX(), buttonPos.getY(), buttonPos.getZ()));
                        deviceCompletedAt = 0L;
                    }
                }
            }
        }
    }

    /**
     * Block change event handler
     */
    private void onBlockChange(BlockChangeEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!this.isEnabled()) return;

        BlockPos pos = event.getPos();

        // LOG ALL CHANGES near the device when doingSS is active!
        if (DEBUG && doingSS) {
            double distSq = mc.player.blockPosition().distSqr(pos);
            if (distSq < 100) { // Within 10 blocks
                debugMessage("§7BlockChange nearby: " + pos + " -> " + event.getUpdate().getBlock());
            }
        }

        // Check if this is in the SEA LANTERN area (x=111, y=120-123, z=92-95)
        if (pos.getX() == 111 && pos.getY() >= 120 && pos.getY() <= 123 &&
            pos.getZ() >= 92 && pos.getZ() <= 95) {

            // Sea lantern at X=111, button at X=110 (one block west)
            BlockPos buttonPos = new BlockPos(110, pos.getY(), pos.getZ());

            if (DEBUG) {
                debugMessage("§eBlock changed at device area: " + pos + ", block=" + event.getUpdate().getBlock());
            }

            // Sea lantern indicates correct button
            if (event.getUpdate().getBlock() == Blocks.SEA_LANTERN) {
                if (clicks.size() == 2) {
                    if (clicks.get(0).equals(buttonPos) && !doneFirst) {
                        doneFirst = true;
                        clicks.remove(0);
                        allButtons.remove(0);
                        debugMessage("§6Removed first duplicate click");
                    }
                }

                if (!clicks.contains(buttonPos)) {
                    debugMessage("§a§lSEA LANTERN " + (clicks.size() + 1) + "/5 §7(y=" + pos.getY() + ", z=" + pos.getZ() + ")");
                    progress = 0;
                    clicks.add(buttonPos);
                    // Store BUTTON position (X=110), not block position (X=111)!
                    allButtons.add(new Vec3(buttonPos.getX(), buttonPos.getY(), buttonPos.getZ()));
                    deviceCompletedAt = 0L;
                }
            }
        }
    }

    /**
     * Packet receive handler - listens for chat messages
     * Catches "Who dares trespass into my domain" to auto-start
     */
    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        // Listen for GameMessageS2CPacket (chat messages)
        if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket packet) {
            if (!packet.overlay()) { // Not actionbar/title
                net.minecraft.network.chat.Component content = packet.content();
                if (content != null) {
                    String message = content.getString();
                    onChatMessage(message);
                }
            }
        }
    }

    /**
     * Chat message handler
     * EXACT COPY from catgirlroutes AutoSS.kt (lines 119-128)
     * PUBLIC so ChatHudMixin can call it
     */
    public void onChatMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (getDistanceSqToCenter(mc.player.blockPosition(), START_BUTTON) > 25) return;

        // Log Device message (for debug timing)
        if (msg.contains("Device")) {
            debugMessage(String.valueOf(System.currentTimeMillis()));
        }

        // Auto-start on "Who dares" message (EXACT from catgirlroutes line 125)
        if (!msg.contains("Who dares trespass into my domain")) return;
        debugMessage("§a§l[AutoSS] Who dares detected - Starting SS!");
        start();
    }

    /**
     * Block interaction hook
     */
    private InteractionResult onBlockUse(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!world.isClientSide()) return InteractionResult.PASS;
        if (!isEnabled()) return InteractionResult.PASS;

        Minecraft mc = Minecraft.getInstance();
        if (player == null || player != mc.player) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        if (!START_BUTTON.equals(pos)) return InteractionResult.PASS;

        if (System.currentTimeMillis() - wtflip < 1000) {
            return InteractionResult.PASS;
        }

        wtflip = System.currentTimeMillis();
        clicked = false;
        stopRotation();
        reset();

        // Start F7Sim Device Simulator if in singleplayer
        if (mc.isLocalServer()) {
            F7SimModule f7sim = dev.hunchclient.module.ModuleManager.getInstance().getModule(dev.hunchclient.module.impl.misc.F7SimModule.class);
            if (f7sim != null && f7sim.isEnabled()) {
                debugMessage("§aStarting Device Simulator...");
                f7sim.setDeviceSimEnabled(true); // Force enable
                f7sim.startDeviceSimulation();
            }
        }

        start();
        return InteractionResult.PASS;
    }

    /**
     * Starts a smooth rotation towards the target yaw/pitch
     * Runs every tick in ssLoop for perfect sync with game
     */
    private void scheduleRotation(float targetYaw, float targetPitch, int durationMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        rotationTargetYaw = wrapYaw(targetYaw);
        rotationTargetPitch = Mth.clamp(targetPitch, -90.0f, 90.0f);
        rotationStartYaw = mc.player.getYRot();
        rotationStartPitch = mc.player.getXRot();
        rotationDurationMs = Math.max(1, durationMs);
        rotationStartTime = System.currentTimeMillis();

        // Reset noise for new rotation (unique seed for variation)
        lastNoiseYaw = 0f;
        lastNoisePitch = 0f;
        rotationSeed = System.nanoTime();

        if (durationMs <= 0) {
            mc.player.setYRot(rotationTargetYaw);
            mc.player.setXRot(rotationTargetPitch);
            isRotating = false;
            return;
        }

        isRotating = true;
    }

    /**
     * Updates the ongoing smooth rotation with human-like imperfections
     * Ultra-smooth for unlimited FPS - no hard jumps!
     */
    private void updateSmoothRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stopRotation();
            return;
        }

        float progress = (float)(System.currentTimeMillis() - rotationStartTime) / rotationDurationMs;
        progress = Mth.clamp(progress, 0.0f, 1.0f);

        // Apply easing curve for smooth movement (Ease-In-Out-Cubic)
        float eased = easeInOutCubic(progress);

        // Smooth Perlin-like noise (interpolate between frames, no hard jumps!)
        float targetNoiseYaw = smoothNoise(progress, rotationSeed, 0) * 0.12f;
        float targetNoisePitch = smoothNoise(progress, rotationSeed, 100) * 0.08f;

        // Lerp noise smoothly (critical for high FPS smoothness!)
        lastNoiseYaw = Mth.lerp(0.15f, lastNoiseYaw, targetNoiseYaw);
        lastNoisePitch = Mth.lerp(0.15f, lastNoisePitch, targetNoisePitch);

        // Reduce noise near start/end for precision
        float noiseFactor = (float)(Math.sin(progress * Math.PI)); // 0 at start/end, 1 at middle
        float noiseYaw = lastNoiseYaw * noiseFactor;
        float noisePitch = lastNoisePitch * noiseFactor;

        float newYaw = lerpAngle(eased, rotationStartYaw, rotationTargetYaw) + noiseYaw;
        float newPitch = Mth.lerp(eased, rotationStartPitch, rotationTargetPitch) + noisePitch;

        // Micro-settling at the very end (human hand stabilizes)
        if (progress > 0.92f) {
            float settle = (1.0f - progress) * 8; // Decreases to 0
            float settleNoise = (float)(Math.sin(System.currentTimeMillis() * 0.05) * 0.05 * settle);
            newYaw += settleNoise;
            newPitch += settleNoise * 0.6f;
        }

        mc.player.setYRot(newYaw);
        mc.player.setXRot(newPitch);

        if (progress >= 1.0f - 1e-3f) {
            // Final precise snap (human settles on target)
            mc.player.setYRot(rotationTargetYaw);
            mc.player.setXRot(rotationTargetPitch);
            isRotating = false;
        }
    }

    /**
     * Smooth noise function (like Perlin but simpler)
     * Returns smooth value between -1 and 1
     */
    private float smoothNoise(float x, long seed, int offset) {
        // Multiple octaves of smooth sin waves
        float n1 = (float)Math.sin((x + seed * 0.001 + offset) * 3.14159 * 2);
        float n2 = (float)Math.sin((x + seed * 0.001 + offset) * 3.14159 * 5.2) * 0.5f;
        float n3 = (float)Math.sin((x + seed * 0.001 + offset) * 3.14159 * 8.7) * 0.25f;
        return (n1 + n2 + n3) / 1.75f; // Normalize
    }

    /**
     * Ease-in-out cubic for natural acceleration/deceleration
     */
    private float easeInOutCubic(float x) {
        return x < 0.5f
            ? 4 * x * x * x
            : 1 - (float)Math.pow(-2 * x + 2, 3) / 2;
    }

    private float lerpAngle(float progress, float start, float end) {
        float delta = Mth.wrapDegrees(end - start);
        return start + delta * progress;
    }

    private float wrapYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }

    private float[] calculateRotation(double targetX, double targetY, double targetZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return new float[]{0f, 0f};
        }

        Vec3 eyePos = mc.player.getEyePosition();
        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[]{yaw, pitch};
    }

    private void stopRotation() {
        isRotating = false;
    }

    private boolean isInSkyblock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) return false;

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective != null) {
            String display = objective.getDisplayName().getString();
            if (display != null) {
                String normalized = stripFormatting(display);
                String lower = normalized.toLowerCase();
                if (lower.contains("skyblock") || lower.contains("dungeon") || lower.contains("catacombs")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean skipNext = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (c == '\u00A7') {
                skipNext = true;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * Waits until the given button position turns into a stone button or timeout is reached.
     */
    private boolean waitForButtonReady(BlockPos pos, int timeoutMs) {
        if (timeoutMs <= 0) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (mc.level.getBlockState(pos).getBlock() == Blocks.STONE_BUTTON) {
                return true;
            }
            try {
                Thread.sleep(BUTTON_READY_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return mc.level.getBlockState(pos).getBlock() == Blocks.STONE_BUTTON;
    }

    /**
     * Clicks a button at the given position by sending a packet
     */
    private void clickButton(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        BlockPos pos = new BlockPos(x, y, z);
        if (getDistanceSqToCenter(mc.player.blockPosition(), pos) > 25) return;

        debugMessage("Clicked at: x: " + x + ", y: " + y + ", z: " + z + ". Time: " + System.currentTimeMillis());
        clickedButton = new Vec3(x, y, z);
        lastClickAdded = System.currentTimeMillis();

        // Create block hit result for the button click
        BlockHitResult hitResult = new BlockHitResult(
            new Vec3(x + 0.875, y + 0.5, z + 0.5),
            Direction.EAST,
            pos,
            false
        );

        // Send player interact block packet (right-click on block)
        ServerboundUseItemOnPacket packet = new ServerboundUseItemOnPacket(
            InteractionHand.MAIN_HAND,
            hitResult,
            0 // sequence number
        );

        mc.getConnection().send(packet);
    }

    /**
     * Calculates squared distance from player position to center of block
     */
    private double getDistanceSqToCenter(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private void debugMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§7[AutoSS Debug] §f" + msg), false);
        }
    }

    private void modMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6[AutoSS] " + msg), false);
        }
    }

    // Getters and setters for GUI
    public int getDelay() { return delay; }
    public void setDelay(int delay) { this.delay = delay; }

    public int getAutoStartDelay() { return autoStartDelay; }
    public void setAutoStartDelay(int delay) { this.autoStartDelay = delay; }

    public boolean isForceDevice() { return forceDevice; }
    public void setForceDevice(boolean force) { this.forceDevice = force; }

    public boolean isSmoothRotate() { return smoothRotate; }
    public void setSmoothRotate(boolean smooth) { this.smoothRotate = smooth; }

    public int getRotationSpeed() { return rotationSpeed; }
    public void setRotationSpeed(int speed) { this.rotationSpeed = speed; }

    public boolean isDontCheck() { return dontCheck; }
    public void setDontCheck(boolean check) { this.dontCheck = check; }

    public boolean isRequireButtonVisible() { return requireButtonVisible; }
    public void setRequireButtonVisible(boolean require) { this.requireButtonVisible = require; }

    public boolean isSpawnClientButtons() { return spawnClientButtons; }
    public void setSpawnClientButtons(boolean spawn) { this.spawnClientButtons = spawn; }

    // Config persistence
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("delay", delay);
        config.addProperty("forceDevice", forceDevice);
        config.addProperty("autoStartDelay", autoStartDelay);
        config.addProperty("smoothRotate", smoothRotate);
        config.addProperty("rotationSpeed", rotationSpeed);
        config.addProperty("dontCheck", dontCheck);
        config.addProperty("requireButtonVisible", requireButtonVisible);
        config.addProperty("spawnClientButtons", spawnClientButtons);
        config.addProperty("buttonReadyTimeoutMs", buttonReadyTimeoutMs);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("delay")) {
            delay = config.get("delay").getAsInt();
        }
        if (config.has("forceDevice")) {
            forceDevice = config.get("forceDevice").getAsBoolean();
        }
        if (config.has("autoStartDelay")) {
            autoStartDelay = config.get("autoStartDelay").getAsInt();
        }
        if (config.has("smoothRotate")) {
            smoothRotate = config.get("smoothRotate").getAsBoolean();
        }
        if (config.has("rotationSpeed")) {
            rotationSpeed = config.get("rotationSpeed").getAsInt();
        }
        if (config.has("dontCheck")) {
            dontCheck = config.get("dontCheck").getAsBoolean();
        }
        if (config.has("requireButtonVisible")) {
            requireButtonVisible = config.get("requireButtonVisible").getAsBoolean();
        }
        if (config.has("spawnClientButtons")) {
            spawnClientButtons = config.get("spawnClientButtons").getAsBoolean();
        }
        if (config.has("buttonReadyTimeoutMs")) {
            buttonReadyTimeoutMs = config.get("buttonReadyTimeoutMs").getAsInt();
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Click Delay
        settings.add(new SliderSetting(
            "Click Delay",
            "Delay between button clicks",
            "autoss_delay",
            0f, 1000f,
            () -> (float) delay,
            val -> delay = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms"));

        // Auto Start Delay
        settings.add(new SliderSetting(
            "Auto Start Delay",
            "Delay before starting sequence",
            "autoss_autostart_delay",
            0f, 500f,
            () -> (float) autoStartDelay,
            val -> autoStartDelay = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms"));

        settings.add(new SliderSetting(
            "Button Ready Timeout",
            "Maximum wait before the start button is clicked (0 = no wait)",
            "autoss_button_ready_timeout",
            0f, 5000f,
            () -> (float) buttonReadyTimeoutMs,
            val -> buttonReadyTimeoutMs = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms"));

        // Smooth Rotation
        settings.add(new CheckboxSetting(
            "Smooth Rotation",
            "Enable smooth head rotation to buttons",
            "autoss_smooth_rotate",
            () -> smoothRotate,
            val -> smoothRotate = val
        ));

        // Rotation Speed (only visible when smooth rotation is enabled)
        settings.add(new SliderSetting(
            "Rotation Speed",
            "Duration of rotation animation",
            "autoss_rotation_speed",
            0f, 1000f,
            () -> (float) rotationSpeed,
            val -> rotationSpeed = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> smoothRotate));

        // Fast Mode (Don't Check)
        settings.add(new CheckboxSetting(
            "Fast Mode",
            "Skip button visibility checks (faster)",
            "autoss_fast_mode",
            () -> dontCheck,
            val -> dontCheck = val
        ));

        // Spawn Client Buttons
        settings.add(new CheckboxSetting(
            "Show Buttons",
            "Spawn client-side buttons for visibility (Hypixel fix)",
            "autoss_spawn_buttons",
            () -> spawnClientButtons,
            val -> spawnClientButtons = val
        ));

        // Force Device (debug)
        settings.add(new CheckboxSetting(
            "Force Device",
            "Force device detection (debug mode)",
            "autoss_force_device",
            () -> forceDevice,
            val -> forceDevice = val
        ));

        return settings;
    }
}
