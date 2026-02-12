package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.module.impl.misc.F7SimModule;
import dev.hunchclient.module.impl.dungeons.secrets.DungeonManager;
import dev.hunchclient.module.impl.dungeons.secrets.Room;
import dev.hunchclient.module.impl.dungeons.secrets.MinePoint;
import dev.hunchclient.util.DungeonUtils;
import com.google.gson.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import dev.hunchclient.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.hunchclient.render.compat.VertexRenderer;
import java.util.*;

/**
 * DBE - DungeonBreaker Extension
 * Advanced block mining module with universal room detection
 *
 * FEATURES:
 * - Mark blocks in edit mode (right-click)
 * - Universal room detection: Scans for roof and corner to find room grid
 * - Automatic rotation detection (0°, 90°, 180°, 270°)
 * - Queued vanilla mining (one block at a time, with progress)
 * - Two modes: Keybind (manual) and Auto (proximity-based)
 * - CGA-SAFE: Never swap and mine on same tick, 3-tick delay after swap
 * - ESP rendering for marked blocks
 *
 * WATCHDOG SAFE: NO (automated mining)
 */
public class DungeonBreakerExtrasModule extends Module implements ConfigurableModule, SettingsProvider {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Set<String> DUNGEON_BREAKER_IDS = Set.of(
        "DUNGEON_STONE_PICKAXE",
        "DUNGEON_IRON_PICKAXE",
        "DUNGEON_GOLD_PICKAXE",
        "DUNGEON_DIAMOND_PICKAXE",
        "DUNGEON_BREAKER",
        "DUNGEONBREAKER"
    );

    // ========== CONFIG PATHS ==========
    private static final Path CONFIG_DIR = Paths.get("config", "hunchclient");

    // ========== EDIT MODE ==========
    private boolean editMode = false;
    private final List<BlockPos> markedBlocks = new ArrayList<>();
    private final Map<BlockPos, Long> minedBlocksWithTime = new HashMap<>();

    // ========== ROOM TRACKING (NEW: Using DungeonManager) ==========
    private Room lastRoom = null; // Track last seen room for auto-reload
    private boolean lastHadRoom = false; // Track if we were in a room last tick (for Boss ↔ Room switching)
    private boolean lastBossContext = false; // Track if we're operating without a matched room (boss config fallback)
    private boolean bossDetectedViaChat = false; // Force boss context when detection triggers
    private int lastRoomCheckTick = 0;
    private static final int ROOM_CHECK_INTERVAL = 20; // Check room every second (20 ticks)
    private boolean notifiedRoomReady = false; // Track if we already notified about room detection ready

    // ========== SETTINGS ==========
    private boolean autoMode = false; // false = keybind mode, true = auto proximity mode
    private boolean autoReloadBlocks = true; // Auto-reload blocks when entering a known room
    private double autoMineRange = 4.5;
    private double autoSwapRange = 5.5; // Swap to pickaxe earlier (mineRange + 1.0)
    private boolean requireDungeonBreaker = false; // Default: false (works with any pickaxe)
    private boolean renderESP = true;
    private int espColorUnmined = 0x8000FF00; // Green with alpha (ARGB)
    private int espColorMined = 0x80808080; // Gray with alpha (ARGB)
    private int espColorMining = 0xA0FFAA00; // Yellow/Orange for currently mining (ARGB)
    private ESPMode espMode = ESPMode.FILLED; // ESP rendering mode
    private boolean espThroughWalls = false; // Render ESP through walls (disable for Iris shader compatibility)
    private boolean autoRespawn = true; // Auto-reset mined blocks after respawn time
    private int respawnDelaySeconds = 15; // 10s spawn + 5s buffer
    private boolean instantMining = true; // RDBT-style instant mining (nuke every tick without waiting)
    private boolean delayBetweenBlocks = false; // Add 1 tick delay between blocks (only for safe mode)
    private boolean debugMode = false; // Show debug messages in chat

    // ========== MINING QUEUE & STATE ==========
    private final Queue<BlockPos> miningQueue = new LinkedList<>();
    private BlockPos currentlyMining = null;
    private BlockState currentlyMiningState = null; // Store state for F7Sim integration
    private int miningStartTick = -1;
    private static final int MAX_MINING_TICKS = 100; // Timeout for stuck mining

    // Threshold: Track last mined block tick for instant mode
    private int lastBlockMinedTick = -1;
    private static final int NO_MINING_THRESHOLD_TICKS = 20; // Swap back if no block mined for 1 second (20 ticks)

    // Threshold: Wait before swapping back to check for more blocks (safe mode only)
    private int waitingForMoreBlocksTick = -1;
    private static final int WAIT_FOR_MORE_BLOCKS_TICKS = 20; // Wait 1 second (20 ticks)

    // ========== CGA-STYLE SWAP TRACKING ==========
    private boolean recentlySwapped = false;
    private int lastSwapTick = -1;
    private int currentTick = 0;
    private int originalSlot = -1;
    private boolean wasKeyPressed = false;

    // ========== TICK-BASED SCHEDULING ==========
    private int swapToDungeonBreakerScheduledTick = -1;
    private int miningScheduledTick = -1;
    private int swapBackScheduledTick = -1;
    private boolean sequenceActive = false;

    // ========== KEYBIND ==========
    private static KeyMapping activationKey;

    public DungeonBreakerExtrasModule() {
        super("DungeonBreakerExtras", "Auto-mines marked blocks with vanilla mining (CGA-safe)", Category.DUNGEONS, RiskLevel.VERY_RISKY);

        // Don't load minepoints in constructor - race condition with async DungeonManager.load()
        // Minepoints will be loaded when entering a room via checkAndReloadRoom()

        // Register keybind
        if (activationKey == null) {
            activationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hunchclient.bossblockminer",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_B,
                dev.hunchclient.HunchModClient.KEYBIND_CATEGORY
            ));
        }

        // Register event callbacks (ONCE in constructor, not in onEnable!)
        UseBlockCallback.EVENT.register(this::onBlockUse);
        ClientTickEvents.END_CLIENT_TICK.register(this::tickHandler);
        dev.hunchclient.render.WorldRenderExtractionCallback.EVENT.register(this::renderESP);
    }

    @Override
    protected void onEnable() {
        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Enabled! Mode: " + (autoMode ? "AUTO" : "KEYBIND") + " | Marked blocks: " + markedBlocks.size()), false);
        }
    }

    @Override
    protected void onDisable() {
        // DON'T stop room scanner - it runs always in background!

        // Restore slot if still swapped
        if (originalSlot != -1) {
            restoreOriginalSlot();
        }
        cancelSequence();

        bossDetectedViaChat = false;
        lastBossContext = false;
        lastHadRoom = false;

        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§c[DungeonBreakerExtras] Disabled!"), false);
        }
    }

    // ========== TICK HANDLER ==========

    private void tickHandler(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // DungeonManager is ticked globally in HunchModClient via Scheduler

        // Check for room detection notifications (always, even when disabled)
        checkRoomDetectionNotification();

        // F7 helper: force boss config once we detect any boss phase
        updateBossDetectionFromPhase();

        // Early exit if module is disabled
        if (!isEnabled()) return;

        // Don't mine while in GUI
        if (MC.screen != null) {
            cancelSequence();
            return;
        }

        currentTick++;

        // CGA-style: Reset swap guard at tick end
        if (lastSwapTick < currentTick) {
            recentlySwapped = false;
        }

        // Auto-reload: Check if we entered a new room with saved blocks
        if (autoReloadBlocks && currentTick - lastRoomCheckTick >= ROOM_CHECK_INTERVAL) {
            lastRoomCheckTick = currentTick;
            checkAndReloadRoom();
        }

        // Auto-respawn: Check if any mined blocks should be reset
        if (autoRespawn && currentTick % 20 == 0) { // Check every second
            checkAndResetRespawnedBlocks();
        }

        // Don't mine in edit mode
        if (editMode) {
            cancelSequence();
            return;
        }

        // Execute scheduled actions
        if (swapToDungeonBreakerScheduledTick >= 0 && currentTick >= swapToDungeonBreakerScheduledTick) {
            executeSwapToDungeonBreaker();
            swapToDungeonBreakerScheduledTick = -1;
        }

        // Mining logic: instant mode runs every tick, safe mode only when scheduled
        if (instantMining) {
            // Instant mining: run EVERY tick while sequenceActive
            if (sequenceActive && miningScheduledTick >= 0) {
                executeMining();
            }
        } else {
            // Safe mode: only run when scheduled tick is reached
            if (miningScheduledTick >= 0 && currentTick >= miningScheduledTick) {
                executeMining();
            }
        }

        if (swapBackScheduledTick >= 0 && currentTick >= swapBackScheduledTick) {
            executeSwapBack();
            swapBackScheduledTick = -1;
        }

        // Main logic based on mode
        if (autoMode) {
            handleAutoMode(client);
        } else {
            handleKeybindMode(client);
        }
    }

    private void updateBossDetectionFromPhase() {
        if (bossDetectedViaChat) {
            return;
        }

        if (!DungeonUtils.isInDungeon()) {
            return;
        }

        DungeonUtils.F7Phase phase = DungeonUtils.getF7Phase();
        if (phase == DungeonUtils.F7Phase.UNKNOWN) {
            return;
        }

        bossDetectedViaChat = true;

        if (!autoReloadBlocks && !isEnabled()) {
            return;
        }

        checkAndReloadRoom();
    }

    // ========== KEYBIND MODE (like ChestAura) ==========

    private void handleKeybindMode(Minecraft client) {
        boolean keyPressed = activationKey != null && activationKey.isDown();

        // KEY JUST PRESSED: Build queue and prepare
        if (keyPressed && !wasKeyPressed) {
            onKeyPress();
        }
        // KEY JUST RELEASED: Stop mining and swap back
        else if (!keyPressed && wasKeyPressed) {
            onKeyRelease();
        }
        // KEY HELD: Check distance and pre-swap/mine when in range
        else if (keyPressed && wasKeyPressed && !sequenceActive) {
            handleKeybindHeld();
        }

        wasKeyPressed = keyPressed;
    }

    private void onKeyPress() {
        if (MC.player == null) return;
        if (markedBlocks.isEmpty()) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[DungeonBreakerExtras] No blocks marked!"), false);
            }
            return;
        }

        // Just build queue, don't start mining yet
        buildMiningQueue();

        if (miningQueue.isEmpty()) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[DungeonBreakerExtras] No unmined blocks in range!"), false);
            }
            return;
        }

        debug("Ready! " + miningQueue.size() + " blocks queued. Move closer to start.");
    }

    /**
     * Called every tick while keybind is held (but sequence not active)
     * Checks distance and starts mining when in range
     */
    private void handleKeybindHeld() {
        if (MC.player == null) return;

        // Rebuild queue to get fresh nearby blocks
        buildMiningQueue();

        if (miningQueue.isEmpty()) {
            return; // No blocks to mine
        }

        // Check distance to next block
        BlockPos nextBlock = miningQueue.peek();
        if (nextBlock == null) return;

        double dist = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ()).distanceTo(
            new Vec3(nextBlock.getX() + 0.5, nextBlock.getY() + 0.5, nextBlock.getZ() + 0.5)
        );

        // Find dungeonbreaker
        int dbSlot = findDungeonBreakerInHotbar();
        if (dbSlot == -1 && requireDungeonBreaker) {
            return; // No dungeonbreaker
        }

        if (dbSlot == -1 && !requireDungeonBreaker) {
            dbSlot = findAnyPickaxeInHotbar();
        }

        // Pre-swap when getting close (5.5 blocks)
        if (dist > autoMineRange && dist <= autoSwapRange) {
            int currentSlot = MC.player.getInventory().getSelectedSlot();
            if (dbSlot != -1 && currentSlot != dbSlot && originalSlot == -1) {
                originalSlot = currentSlot;
                swapToSlot(dbSlot);
                debug("Pre-swapping (distance: " + String.format("%.2f", dist) + ")");
            }
            return;
        }

        // Start mining when in range (4.5 blocks)
        if (dist <= autoMineRange) {
            startKeybindSequence(dbSlot);
        }
    }

    private void startKeybindSequence(int dbSlot) {
        if (MC.player == null) return;

        sequenceActive = true;
        int currentSlot = MC.player.getInventory().getSelectedSlot();

        // If we already pre-swapped, don't overwrite originalSlot
        if (originalSlot == -1) {
            originalSlot = currentSlot;
        }

        // Check if already holding pickaxe
        if (dbSlot != -1 && currentSlot == dbSlot) {
            // Already holding pickaxe - schedule mining after delay
            miningScheduledTick = currentTick + 3;
            debug("Starting to mine!");
        } else if (dbSlot != -1) {
            // Need to swap
            swapToSlot(dbSlot);
            miningScheduledTick = currentTick + 3;
            debug("Swapped and mining!");
        } else {
            // No pickaxe required
            startMiningImmediate();
        }
    }

    private void onKeyRelease() {
        // Stop mining
        stopMining();

        // Swap back to original slot
        if (originalSlot != -1) {
            restoreOriginalSlot();
        }

        cancelSequence();
    }

    // ========== AUTO MODE (like ChestAura2) ==========

    private void handleAutoMode(Minecraft client) {
        // Don't start new sequence if one is active
        if (sequenceActive) {
            if (currentTick % 100 == 0) {
                debug("Sequence already active, waiting...");
            }
            return;
        }

        // Only build queue if it's empty
        if (miningQueue.isEmpty()) {
            buildMiningQueue();

            if (miningQueue.isEmpty()) {
                return; // No blocks to mine
            }
        }

        // Check distance to next block
        BlockPos nextBlock = miningQueue.peek();
        if (nextBlock == null) {
            debug("Queue peek returned null!");
            return;
        }

        double dist = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ()).distanceTo(
            new Vec3(nextBlock.getX() + 0.5, nextBlock.getY() + 0.5, nextBlock.getZ() + 0.5)
        );

        // Too far even for swap prep
        if (dist > autoSwapRange) {
            return;
        }

        // Find dungeonbreaker (or any pickaxe if not required)
        int dbSlot = findDungeonBreakerInHotbar();

        // If no dungeonbreaker found and it's required, wait
        if (dbSlot == -1 && requireDungeonBreaker) {
            if (currentTick % 100 == 0) {
                debug("No Dungeonbreaker found! Disable 'Require Dungeonbreaker' setting to use any item.");
            }
            return; // No dungeonbreaker and it's required
        }

        // If dungeonbreaker not required, try to find any pickaxe
        if (dbSlot == -1 && !requireDungeonBreaker) {
            dbSlot = findAnyPickaxeInHotbar();
            if (dbSlot != -1 && currentTick % 100 == 0) {
                debug("Using pickaxe in slot " + dbSlot);
            }
        }

        // In swap range but not mine range - just swap and wait
        if (dist > autoMineRange && dist <= autoSwapRange) {
            // Already holding pickaxe?
            int currentSlot = MC.player.getInventory().getSelectedSlot();
            if (dbSlot != -1 && currentSlot == dbSlot) {
                return; // Already ready
            }

            // Need to swap
            if (dbSlot != -1 && originalSlot == -1) {
                originalSlot = currentSlot;
                swapToSlot(dbSlot);
                debug("Pre-swapping to pickaxe (distance: " + String.format("%.2f", dist) + ")");
            }
            return;
        }

        // In mine range - start mining
        if (dist <= autoMineRange) {
            // Start auto sequence
            debug("Starting auto sequence! Distance: " + String.format("%.2f", dist));
            startAutoSequence(dbSlot);
        }
    }

    private void startAutoSequence(int dbSlot) {
        if (MC.player == null) return;

        sequenceActive = true;
        int currentSlot = MC.player.getInventory().getSelectedSlot();

        // If we already pre-swapped, don't overwrite originalSlot
        if (originalSlot == -1) {
            originalSlot = currentSlot;
        }

        // Check if already holding dungeonbreaker
        if (dbSlot != -1 && currentSlot == dbSlot) {
            // Already holding pickaxe (might be from pre-swap)
            // Schedule mining after 3 ticks delay for safety
            miningScheduledTick = currentTick + 3;
            debug("Already holding pickaxe, mining in 3 ticks!");
        } else if (dbSlot != -1) {
            // Swap immediately, then schedule mining after 3 ticks
            debug("Auto-mode: Swapping to Dungeonbreaker (slot " + dbSlot + ")!");
            swapToSlot(dbSlot);

            miningScheduledTick = currentTick + 3; // 3 ticks for item sync
            debug("Mining scheduled for tick " + (currentTick + 3));
        } else {
            // No dungeonbreaker required
            debug("No dungeonbreaker required, mining now!");
            startMiningImmediate();
        }
    }

    // ========== MINING LOGIC ==========

    /**
     * Check if a block is directly reachable (no walls in between)
     * Uses raycast from player eyes to block center
     */
    private boolean isBlockReachable(BlockPos targetBlock) {
        if (MC.player == null || MC.level == null) return false;

        Vec3 eyePos = MC.player.getEyePosition();
        Vec3 blockCenter = new Vec3(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 0.5,
            targetBlock.getZ() + 0.5
        );

        // Raycast from player eyes to block center
        net.minecraft.world.phys.BlockHitResult hitResult = MC.level.clip(
            new net.minecraft.world.level.ClipContext(
                eyePos,
                blockCenter,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                MC.player
            )
        );

        // Check if we hit the target block directly (no walls in between)
        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos hitPos = hitResult.getBlockPos();
            return hitPos.equals(targetBlock);
        }

        return false;
    }

    private void buildMiningQueue() {
        miningQueue.clear();

        if (MC.player == null) return;

        Vec3 playerPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());

        // Find all unmined blocks within range that are directly reachable, sorted by distance
        List<BlockPos> nearby = new ArrayList<>();
        for (BlockPos pos : markedBlocks) {
            if (minedBlocksWithTime.containsKey(pos)) continue; // Skip mined blocks

            double dist = playerPos.distanceTo(
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
            );

            // Check range and line-of-sight
            if (dist <= autoMineRange && isBlockReachable(pos)) {
                nearby.add(pos);
            }
        }

        // Sort by distance
        nearby.sort((a, b) -> {
            double distA = playerPos.distanceTo(new Vec3(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5));
            double distB = playerPos.distanceTo(new Vec3(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5));
            return Double.compare(distA, distB);
        });

        miningQueue.addAll(nearby);

        if (!nearby.isEmpty()) {
            debug("Queued " + nearby.size() + " blocks for mining");
        }
    }

    private void startMiningImmediate() {
        miningScheduledTick = currentTick;
        sequenceActive = true;
        debug("Mining scheduled IMMEDIATELY for tick " + currentTick);
    }

    private void executeMining() {
        if (MC.player == null || MC.gameMode == null || MC.level == null) {
            return;
        }

        if (instantMining) {
            // ========== INSTANT MINING MODE (RDBT-style) ==========
            // Nuke 1 block per tick without waiting for it to break
            executeInstantMining();
        } else {
            // ========== SAFE MODE ==========
            // Wait until block breaks before mining next one
            executeSafeMining();
        }
    }

    private void executeInstantMining() {
        // RDBT-style: find closest block EVERY tick and nuke it
        // No queue building - just iterate over all marked blocks

        if (MC.player == null || MC.level == null) return;

        // Note: Auto-Respawn system handles cleanup of minedBlocksWithTime
        // Don't clean up here - respawnDelaySeconds controls when blocks become mineable again
        long currentTime = System.currentTimeMillis();

        // Find closest valid block
        BlockPos closestBlock = null;
        double closestDistance = Double.MAX_VALUE;
        Vec3 playerEyePos = MC.player.getEyePosition();

        for (BlockPos pos : markedBlocks) {
            // Skip if recently mined
            if (minedBlocksWithTime.containsKey(pos)) continue;

            // Check if block exists (not air)
            BlockState state = MC.level.getBlockState(pos);
            if (state.isAir()) continue;

            // Calculate distance
            double distance = playerEyePos.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

            // Skip if out of range (use autoMineRange setting)
            if (distance > autoMineRange) continue;

            // Check if this is the closest block
            if (distance < closestDistance) {
                closestBlock = pos;
                closestDistance = distance;
            }
        }

        // No block found in range
        if (closestBlock == null) {
            // Check if we've been mining recently
            if (lastBlockMinedTick == -1) {
                // Never mined anything yet - finish immediately
                finishMining();
                return;
            }

            // Check how long it's been since we last mined a block
            int ticksSinceLastMine = currentTick - lastBlockMinedTick;
            if (ticksSinceLastMine >= NO_MINING_THRESHOLD_TICKS) {
                // No blocks mined for 1 second - finish mining
                finishMining();
                return;
            }

            // Still within threshold - keep looking (player might be moving)
            return;
        }

        // Found a block - nuke it!
        Direction facing = getClosestFacing(closestBlock);
        MC.player.connection.send(
            new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                closestBlock,
                facing
            )
        );
        MC.player.swing(InteractionHand.MAIN_HAND, true);

        // Update last mined tick (we just mined a block)
        lastBlockMinedTick = currentTick;

        // Record as mined (prevents re-mining same block for 1 second)
        minedBlocksWithTime.put(closestBlock, currentTime);

        // Notify F7Sim for singleplayer
        BlockState state = MC.level.getBlockState(closestBlock);
        if (!state.isAir()) {
            F7SimModule f7Sim = ModuleManager.getInstance().getModule(F7SimModule.class);
            if (f7Sim != null && f7Sim.isEnabled()) {
                f7Sim.onPacketBlockMined(closestBlock, state);
            }
        }

    }

    private void executeSafeMining() {
        // Safe mode: wait for block to break before mining next one

        // Check if currently mining a block
        if (currentlyMining != null) {
            BlockState state = MC.level.getBlockState(currentlyMining);

            if (state.isAir()) {
                // Block broken!
                // Notify F7Sim
                if (currentlyMiningState != null) {
                    F7SimModule f7Sim = ModuleManager.getInstance().getModule(F7SimModule.class);
                    if (f7Sim != null && f7Sim.isEnabled()) {
                        f7Sim.onPacketBlockMined(currentlyMining, currentlyMiningState);
                    }
                }

                minedBlocksWithTime.put(currentlyMining, System.currentTimeMillis());
                currentlyMining = null;
                currentlyMiningState = null;
                miningStartTick = -1;

                // Delay if enabled
                if (delayBetweenBlocks) {
                    return;
                }
            } else {
                // Check timeout
                if (currentTick - miningStartTick > MAX_MINING_TICKS) {
                    currentlyMining = null;
                    currentlyMiningState = null;
                    miningStartTick = -1;
                } else {
                    return; // Still mining
                }
            }
        }

        // Find next block to mine
        while (currentlyMining == null) {
            BlockPos nextBlock = miningQueue.poll();

            if (nextBlock == null) {
                // Check for more blocks
                if (waitingForMoreBlocksTick == -1) {
                    waitingForMoreBlocksTick = currentTick;
                    return;
                }

                if (currentTick - waitingForMoreBlocksTick < WAIT_FOR_MORE_BLOCKS_TICKS) {
                    buildMiningQueue();
                    if (!miningQueue.isEmpty()) {
                        waitingForMoreBlocksTick = -1;
                        return;
                    }
                    return;
                }

                finishMining();
                return;
            }

            waitingForMoreBlocksTick = -1;

            BlockState state = MC.level.getBlockState(nextBlock);
            if (state.isAir()) {
                minedBlocksWithTime.put(nextBlock, System.currentTimeMillis());
                continue;
            }

            // Raycast check is done in buildMiningQueue(), not here
            // Just mine the block - trust the queue

            // Start mining
            currentlyMining = nextBlock;
            currentlyMiningState = state;
            miningStartTick = currentTick;

            Direction facing = getClosestFacing(nextBlock);
            MC.player.connection.send(
                new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    nextBlock,
                    facing
                )
            );
            MC.player.swing(InteractionHand.MAIN_HAND, true);

        }
    }

    private void finishMining() {
        waitingForMoreBlocksTick = -1;
        lastBlockMinedTick = -1;
        debug("Mining complete!");

        currentlyMining = null;
        currentlyMiningState = null;
        miningStartTick = -1;
        miningScheduledTick = -1;
        miningQueue.clear();

        if (originalSlot != -1) {
            swapBackScheduledTick = currentTick + 3;
            debug("Swapping back in 3 ticks...");
        } else {
            sequenceActive = false;
        }
    }

    private void stopMining() {
        currentlyMining = null;
        currentlyMiningState = null;
        miningStartTick = -1;
        miningScheduledTick = -1;
        lastBlockMinedTick = -1;
        miningQueue.clear();
        sequenceActive = false;
    }

    // ========== AUTO-RESPAWN LOGIC ==========

    private void checkAndResetRespawnedBlocks() {
        if (minedBlocksWithTime.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        long respawnDelayMs = respawnDelaySeconds * 1000L;

        List<BlockPos> toReset = new ArrayList<>();

        for (Map.Entry<BlockPos, Long> entry : minedBlocksWithTime.entrySet()) {
            BlockPos pos = entry.getKey();
            long minedTime = entry.getValue();

            // Check if enough time has passed
            if (currentTime - minedTime >= respawnDelayMs) {
                toReset.add(pos);
            }
        }

        // Reset blocks
        for (BlockPos pos : toReset) {
            minedBlocksWithTime.remove(pos);
            debug("Block respawned: " + pos.toShortString());
        }
    }

    private Room resolveActiveRoom() {
        if (MC.player != null) {
            Room matched = DungeonManager.getMatchedRoomAt(MC.player.blockPosition());
            if (matched != null && matched.isMatched()) {
                return matched;
            }
        }
        Room current = DungeonManager.getCurrentRoom();
        if (current != null && current.isMatched()) {
            return current;
        }
        return null;
    }

    /**
     * Check room detection status and notify user when ready
     * Called every tick, even when module is disabled
     */
    private void checkRoomDetectionNotification() {
        Room currentRoom = resolveActiveRoom();
        boolean currentHasRoom = currentRoom != null;

        // Notify user when room detection becomes ready (first time only per room)
        if (currentHasRoom && !notifiedRoomReady && editMode) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] ✓ Room detected: " + currentRoom.getName() +
                    " (direction: " + currentRoom.getDirection() + ")"), false);
                MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Ready to mark MinePoints!"), false);
            }
            notifiedRoomReady = true;
        }

        // Reset notification flag when leaving room
        if (!currentHasRoom && notifiedRoomReady) {
            notifiedRoomReady = false;
        }
    }

    /**
     * Dynamic room/boss switching system
     * Detects changes between:
     * - Room → Room (normal room change)
     * - Room → Boss (entering boss fight)
     * - Boss → Room (leaving boss, entering room)
     * - Boss → Boss (staying in boss)
     */
    private void checkAndReloadRoom() {
        Room currentRoom = resolveActiveRoom();

        if (bossDetectedViaChat) {
            // Clear forced boss state if we actually have a room again or left the dungeon entirely
            if (currentRoom != null || !DungeonUtils.isInDungeon()) {
                bossDetectedViaChat = false;
            }
        }

        if (bossDetectedViaChat) {
            currentRoom = null;
        }

        boolean currentHasRoom = currentRoom != null;
        boolean bossContext = bossDetectedViaChat || !currentHasRoom;
        boolean bossContextChanged = bossContext != lastBossContext;

        // Detect context switches
        boolean switchedToBoss = lastHadRoom && !currentHasRoom;
        boolean switchedToRoom = !lastHadRoom && currentHasRoom;
        boolean roomToRoom = lastHadRoom && currentHasRoom && lastRoom != null &&
                            currentRoom != null && !lastRoom.getName().equals(currentRoom.getName());

        boolean needsReload = switchedToBoss || switchedToRoom || roomToRoom || bossContextChanged;

        if (needsReload) {
            String transition = "";
            if (switchedToBoss) {
                transition = "Room → Boss";
            } else if (switchedToRoom) {
                transition = "Boss → Room (" + currentRoom.getName() + ")";
                notifiedRoomReady = false; // Reset for new room
            } else if (roomToRoom) {
                transition = lastRoom.getName() + " → " + currentRoom.getName();
                notifiedRoomReady = false; // Reset for new room
            } else if (bossContextChanged) {
                transition = bossContext ? "No room detected → Boss config" : "Boss config → None";
            }

            // Removed auto-save: Only save in edit mode when placing/removing blocks

            // Update context tracking
            lastHadRoom = currentHasRoom;
            lastRoom = currentRoom;

            // NEW: Load minepoints from DungeonManager instead of files
            loadMinepointsFromDungeonManager();

            // Clear mining state
            minedBlocksWithTime.clear();
            miningQueue.clear();
            currentlyMining = null;

            if (MC.player != null) {
                String context = currentHasRoom ? currentRoom.getName() : "Boss";
                MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Loaded " + markedBlocks.size() +
                    " minepoints for " + context), false);
            }
        }

        // Always update tracking (even if no reload needed)
        lastHadRoom = currentHasRoom;
        lastRoom = currentRoom;
        lastBossContext = bossContext;

    }

    /**
     * NEW: Load minepoints from DungeonManager
     * Converts MinePoints to BlockPos for mining
     */
    public void loadMinepointsFromDungeonManager() {
        markedBlocks.clear();

        Room currentRoom = resolveActiveRoom();
        if (bossDetectedViaChat) {
            currentRoom = null;
        }

        if (currentRoom != null) {
            // Load room minepoints
            for (MinePoint minepoint : currentRoom.getMinePoints()) {
                markedBlocks.add(minepoint.pos);
            }
        } else {
            // Load boss minepoints (absolute coordinates) whenever no room is matched
            for (MinePoint minepoint : DungeonManager.getBossMinepoints()) {
                markedBlocks.add(minepoint.pos);
            }
        }
    }

    private void cancelSequence() {
        sequenceActive = false;
        swapToDungeonBreakerScheduledTick = -1;
        miningScheduledTick = -1;
        swapBackScheduledTick = -1;
        stopMining();
    }

    // ========== SCHEDULED ACTIONS ==========

    private void executeSwapToDungeonBreaker() {
        if (MC.player == null) return;

        int dbSlot = findDungeonBreakerInHotbar();
        if (dbSlot == -1) {
            cancelSequence();
            return;
        }

        swapToSlot(dbSlot);
    }

    private void executeSwapBack() {
        if (originalSlot != -1) {
            restoreOriginalSlot();
        }
        cancelSequence();
    }

    // ========== CGA-STYLE SWAP FUNCTIONS ==========

    private void swapToSlot(int slot) {
        if (MC.player == null) return;
        if (slot < 0 || slot > 8) return;

        // CGA-style swap guard: max 1 per tick
        if (recentlySwapped) {
            return;
        }

        // CLIENT-SIDE ONLY - NO PACKET!
        MC.player.getInventory().setSelectedSlot(slot);

        recentlySwapped = true;
        lastSwapTick = currentTick;
    }

    private void restoreOriginalSlot() {
        if (MC.player == null) return;
        if (originalSlot < 0 || originalSlot > 8) return;

        // CGA-style swap guard: max 1 per tick
        if (recentlySwapped) {
            return;
        }

        // CLIENT-SIDE ONLY - NO PACKET!
        MC.player.getInventory().setSelectedSlot(originalSlot);

        recentlySwapped = true;
        lastSwapTick = currentTick;
        originalSlot = -1;
    }

    // ========== EDIT MODE ==========

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        if (editMode && MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Edit mode enabled - Right-click blocks to mark"), false);
        }
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    public void setAutoMode(boolean autoMode) {
        if (this.autoMode == autoMode) {
            return;
        }
        this.autoMode = autoMode;
        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Auto mode: " + (autoMode ? "§aON" : "§cOFF")), false);
        }
    }

    /**
     * NEW: EditMode handler - Right-click to mark/unmark MinePoints
     * Saves directly to DungeonManager (room-relative or boss-absolute)
     */
    private InteractionResult onBlockUse(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!world.isClientSide()) return InteractionResult.PASS;
        if (!isEditMode()) return InteractionResult.PASS;
        if (player != MC.player) return InteractionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (state.isAir()) return InteractionResult.PASS;

        // NEW: Use getMatchedRoomAt() instead of getCurrentRoom() - more reliable for block marking
        Room currentRoom = DungeonManager.getMatchedRoomAt(pos);
        boolean inDungeon = isInDungeon();

        // Toggle block marking
        if (markedBlocks.contains(pos)) {
            // Unmark
            markedBlocks.remove(pos);
            minedBlocksWithTime.remove(pos);

            // Remove from DungeonManager
            if (currentRoom != null && currentRoom.isMatched()) {
                BlockPos relativePos = currentRoom.actualToRelative(pos);
                DungeonManager.removeCustomMinepoint(currentRoom.getName(), relativePos);
                currentRoom.removeMinePoint(relativePos);
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§c[MinePoint] Removed from room " + currentRoom.getName()), false);
                }
            } else {
                // Remove from boss minepoints
                DungeonManager.getBossMinepoints().removeIf(mp -> mp.pos.equals(pos));
                if (MC.player != null) {
                    String context = inDungeon ? "Boss" : "Boss (offline)";
                    MC.player.displayClientMessage(Component.literal("§c[MinePoint] Removed from " + context), false);
                }
            }

            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[DungeonBreakerExtras] Block unmarked: " + pos.toShortString()), false);
            }
        } else {
            // Mark
            markedBlocks.add(pos.immutable());

            // Add to DungeonManager
            if (currentRoom != null && currentRoom.isMatched()) {
                // Room minepoint (relative coords)
                BlockPos relativePos = currentRoom.actualToRelative(pos);
                int index = 1000 + DungeonManager.getCustomMinepointCount(currentRoom.getName()) + 1;
                String pointName = index + " - Mine";
                DungeonManager.addCustomMinepoint(currentRoom.getName(), index, relativePos, pointName, currentRoom.getDirection());
                currentRoom.addMinePoint(index, relativePos, pointName);

                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[MinePoint] Added to room " + currentRoom.getName() +
                        " (direction: " + currentRoom.getDirection() + ")"), false);
                    MC.player.displayClientMessage(Component.literal("§7Relative: " + relativePos.toShortString()), false);
                }
            } else {
                // Boss minepoint (absolute coords)
                int index = DungeonManager.getBossMinepoints().size() + 1;
                MinePoint mp = new MinePoint(index, pos, "Boss Mine " + index, pos, true);
                DungeonManager.getBossMinepoints().add(mp);

                if (MC.player != null) {
                    String context = inDungeon ? "Boss" : "Boss (offline)";
                    MC.player.displayClientMessage(Component.literal("§a[MinePoint] Added to " + context + " (absolute coords)"), false);
                }
            }

            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Block marked: " + pos.toShortString() +
                    " §7(Total: " + markedBlocks.size() + ")"), false);
            }
        }

        // IMPORTANT: Save minepoints to disk immediately after placing/removing
        DungeonManager.saveCustomWaypoints(MC);
        saveConfig(); // Save module settings

        return InteractionResult.SUCCESS;
    }

    // ========== DUNGEONBREAKER DETECTION ==========

    private int findDungeonBreakerInHotbar() {
        if (MC.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (isDungeonBreakerStack(stack)) {
                return i;
            }
        }

        return -1;
    }

    private int findAnyPickaxeInHotbar() {
        if (MC.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
                if (itemName.contains("pickaxe")) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean isDungeonBreakerStack(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check if it's a pickaxe
        String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
        if (!itemName.contains("pickaxe")) return false;

        // Check NBT for Skyblock ID
        CompoundTag attributes = getExtraAttributes(stack);
        if (attributes != null && attributes.contains("id")) {
            String id = attributes.getString("id").orElse("");
            if (DUNGEON_BREAKER_IDS.contains(id.toUpperCase())) {
                return true;
            }
        }

        // Fallback: display name check
        String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return displayName.contains("dungeonbreaker") || displayName.contains("dungeon breaker");
    }

    private CompoundTag getExtraAttributes(ItemStack stack) {
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                if (nbt != null) {
                    if (nbt.contains("ExtraAttributes")) {
                        return nbt.getCompound("ExtraAttributes").orElse(null);
                    }
                    if (nbt.contains("extra_attributes")) {
                        return nbt.getCompound("extra_attributes").orElse(null);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // ========== ESP RENDERING ==========

    private void renderESP(PrimitiveCollector collector) {
        // Don't render if no blocks marked or ESP disabled
        if (markedBlocks.isEmpty()) return;
        if (!renderESP) return;
        if (espMode == ESPMode.NONE) return;
        if (MC.player == null || MC.level == null) return;

        // Only render if module is enabled OR in edit mode
        if (!isEnabled() && !editMode) return;

        try {
            // In edit mode, show all blocks in bright green
            // When mining, show green for unmined, gray for mined
            for (BlockPos pos : markedBlocks) {
                boolean mined = minedBlocksWithTime.containsKey(pos);
                boolean isCurrentlyMining = pos.equals(currentlyMining);

                int colorInt;
                if (editMode) {
                    // Edit mode: All blocks use unmined color (brighter)
                    colorInt = espColorUnmined | 0xA0000000; // Ensure good alpha
                } else if (isCurrentlyMining) {
                    // Currently mining: Use mining color
                    colorInt = espColorMining;
                } else if (mined) {
                    // Already mined: Use mined color
                    colorInt = espColorMined;
                } else {
                    // Not yet mined: Use unmined color
                    colorInt = espColorUnmined;
                }

                // Convert int color to RGBA float array
                float[] color = intToRGBA(colorInt);

                // Draw box around block using configured ESP mode
                AABB box = new AABB(pos).inflate(0.0025);
                renderBoxWithESP(collector, box, color, espMode, 2.0f, espThroughWalls);
            }
        } catch (Exception e) {
            // Log rendering errors for debugging
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[DungeonBreakerExtras] Rendering error: " + e.getMessage()), false);
            }
            e.printStackTrace();
        }
    }

    /**
     * Render a box with the specified ESP mode
     */
    private void renderBoxWithESP(PrimitiveCollector collector, AABB box, float[] colorComponents, ESPMode mode, float lineWidth, boolean throughWalls) {
        // Extract alpha from color picker (colorComponents[3])
        float alpha = colorComponents[3];

        switch (mode) {
            case OUTLINE:
                collector.submitOutlinedBox(box, colorComponents, alpha, lineWidth, throughWalls);
                break;
            case FILLED:
                // Use alpha from color picker
                collector.submitFilledBox(box, colorComponents, alpha, throughWalls);
                break;
            case FILLED_OUTLINE:
                // Fill uses half the alpha, outline uses full alpha
                collector.submitFilledBox(box, colorComponents, alpha * 0.5f, throughWalls);
                collector.submitOutlinedBox(box, colorComponents, alpha, lineWidth, throughWalls);
                break;
            case GLOW:
                // Fake glow effect using multiple expanding layers with decreasing alpha
                float r = colorComponents[0];
                float g = colorComponents[1];
                float b = colorComponents[2];

                // Pulsing animation
                long time = System.currentTimeMillis();
                float pulse = 0.85f + 0.15f * (float) Math.sin(time / 300.0);

                // Inner core - bright, uses color picker alpha
                float[] coreColor = new float[]{
                    Math.min(1.0f, r * 1.2f),
                    Math.min(1.0f, g * 1.2f),
                    Math.min(1.0f, b * 1.2f),
                    alpha
                };
                collector.submitFilledBox(box, coreColor, alpha * 0.7f * pulse, throughWalls);
                collector.submitOutlinedBox(box, coreColor, alpha, lineWidth * 1.5f, throughWalls);

                // Glow layers - expanding outward, scaled by color picker alpha
                double[] expansions = {0.03, 0.07, 0.12, 0.18};
                float[] layerAlphaFactors = {0.5f, 0.3f, 0.15f, 0.07f};

                for (int i = 0; i < expansions.length; i++) {
                    AABB expandedBox = box.inflate(expansions[i]);
                    float layerAlpha = layerAlphaFactors[i] * pulse * alpha;
                    float[] layerColor = new float[]{r, g, b, layerAlpha};
                    collector.submitFilledBox(expandedBox, layerColor, layerAlpha, throughWalls);
                }
                break;
            case CHAMS:
                // Chams: Uses color picker alpha (typically higher for visibility)
                collector.submitFilledBox(box, colorComponents, alpha, throughWalls);
                break;
            case WIREFRAME:
                // Wireframe: Multiple thin outlines for mesh effect
                collector.submitOutlinedBox(box, colorComponents, alpha, 1.0f, throughWalls);
                collector.submitOutlinedBox(box.deflate(0.05), colorComponents, alpha * 0.5f, 0.5f, throughWalls);
                break;
            case NONE:
                // Don't render anything
                break;
        }
    }

    private float[] intToRGBA(int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }

    /**
     * Send a debug message to chat (only if debugMode is enabled)
     */
    private void debug(String message) {
        if (debugMode && MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§7[DBE Debug] " + message), false);
        }
    }

    // ========== BLOCK MANAGEMENT ==========

    public List<BlockPos> getMarkedBlocks() {
        return new ArrayList<>(markedBlocks);
    }

    public void clearMarkedBlocks() {
        markedBlocks.clear();
        minedBlocksWithTime.clear();
        currentlyMining = null;
        miningQueue.clear();

        // Save empty blocks to file
        saveBlocksToFile();
        saveConfig();

        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Cleared all marked blocks"), false);
        }
    }

    public void resetMinedBlocks() {
        minedBlocksWithTime.clear();
        currentlyMining = null;
        miningQueue.clear();

        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Reset mined blocks status"), false);
        }
    }

    // ========== BLOCK FILE I/O ==========

    /**
     * Check if the dungeon map scanner currently has a context (room or boss).
     */
    private boolean isInDungeon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }

        if (DungeonManager.isClearingDungeon() || DungeonManager.isInBoss()) {
            return true;
        }

        if (!DungeonUtils.isInDungeon()) {
            return false;
        }

        ItemStack slotEight = mc.player.getInventory().getItem(8);
        return !slotEight.isEmpty() && slotEight.is(Items.FILLED_MAP);
    }

    /**
     * Find the closest Direction (block face) to the player's eye position
     * Based on RDBT's closestEnumFacing function
     */
    private Direction getClosestFacing(BlockPos blockPos) {
        if (MC.player == null) return Direction.UP;

        Vec3 playerEyePos = MC.player.getEyePosition();
        double minDistance = Double.MAX_VALUE;
        Direction closestFace = Direction.UP;

        // Check all 6 faces
        for (Direction face : Direction.values()) {
            // Get offset for this face
            Vec3 offset = Vec3.atLowerCornerOf(face.getUnitVec3i());

            // Calculate center point of this face
            Vec3 faceCenter = new Vec3(
                blockPos.getX() + 0.5 + offset.x * 0.5,
                blockPos.getY() + 0.5 + offset.y * 0.5,
                blockPos.getZ() + 0.5 + offset.z * 0.5
            );

            // Calculate distance from player eyes to this face
            double distance = playerEyePos.distanceTo(faceCenter);

            if (distance < minDistance) {
                minDistance = distance;
                closestFace = face;
            }
        }

        return closestFace;
    }

    /**
     * NEW: Save minepoints to DungeonManager
     * - With Room: Save to customMinepoints
     * - Without Room (Boss): Save to bossMinepoints
     */
    private void saveBlocksToFile() {
        saveBlocksToFile(null, false);
    }

    private void saveBlocksToFile(Room preferredRoom, boolean forceBossSave) {
        try {
            if (markedBlocks.isEmpty()) {
                return;
            }

            Room currentRoom = preferredRoom;
            if (currentRoom != null && !currentRoom.isMatched()) {
                currentRoom = null;
            }
            if (currentRoom == null && !forceBossSave) {
                currentRoom = resolveActiveRoom();
            }
            Room savedRoom = currentRoom;
            boolean bossContext = false;

            boolean saved = false;

            // Check if we're in a dungeon but room detection isn't ready yet
            if (!forceBossSave && currentRoom == null && !isInDungeon()) {
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§e[DungeonBreakerExtras] ⚠ Not in a dungeon - minepoints not saved"), false);
                }
                return;
            }

            if (!forceBossSave && currentRoom != null) {
                // Save to room custom minepoints
                String roomName = currentRoom.getName();
                // Clear existing custom minepoints for this room
                List<BlockPos> existing = new ArrayList<>(DungeonManager.getCustomMinepoints(roomName).keySet());
                for (BlockPos relative : existing) {
                    DungeonManager.removeCustomMinepoint(roomName, relative);
                    currentRoom.removeMinePoint(relative);
                }

                int index = 1;

                for (BlockPos pos : markedBlocks) {
                    BlockPos relativePos = currentRoom.actualToRelative(pos);
                    int uniqueIndex = 1000 + index;
                    String pointName = uniqueIndex + " - Mine";
                    DungeonManager.addCustomMinepoint(roomName, uniqueIndex, relativePos, pointName, currentRoom.getDirection());
                    currentRoom.addMinePoint(uniqueIndex, relativePos, pointName);
                    index++;
                }

                saved = true;

                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Saved " + markedBlocks.size() +
                        " minepoints (relative) for " + roomName), false);
                }
            } else {
                // Save to boss minepoints (absolute coords)
                int index = 1;
                DungeonManager.getBossMinepoints().clear();
                for (BlockPos pos : markedBlocks) {
                    MinePoint mp = new MinePoint(index, pos, "Boss Mine " + index, pos, true);
                    DungeonManager.getBossMinepoints().add(mp);
                    index++;
                }

                saved = true;
                bossContext = true;

                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Saved " + markedBlocks.size() +
                        " minepoints (absolute) for Boss"), false);
                }
            }

            if (saved) {
                // Persist minepoints immediately so both room and boss configs survive restarts
                DungeonManager.saveCustomWaypoints(MC);
            }
        } catch (Exception e) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[DungeonBreakerExtras] Failed to save minepoints: " + e.getMessage()), false);
            }
            e.printStackTrace();
        }
    }

    /**
     * OLD: Load blocks from file - NOW REPLACED by loadMinepointsFromDungeonManager()
     * This method is kept for backwards compatibility but just calls the new method
     */
    private void loadBlocksFromFile() {
        loadMinepointsFromDungeonManager();
    }

    // ========== CONFIG ==========

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();

        // Save settings only (blocks are saved in separate file)
        config.addProperty("autoMode", autoMode);
        config.addProperty("autoReloadBlocks", autoReloadBlocks);
        config.addProperty("autoMineRange", autoMineRange);
        config.addProperty("requireDungeonBreaker", requireDungeonBreaker);
        config.addProperty("renderESP", renderESP);
        config.addProperty("espMode", espMode.ordinal());
        config.addProperty("espColorUnmined", espColorUnmined);
        config.addProperty("espColorMined", espColorMined);
        config.addProperty("espColorMining", espColorMining);
        config.addProperty("espThroughWalls", espThroughWalls);
        config.addProperty("autoRespawn", autoRespawn);
        config.addProperty("respawnDelaySeconds", respawnDelaySeconds);
        config.addProperty("instantMining", instantMining);
        config.addProperty("delayBetweenBlocks", delayBetweenBlocks);
        config.addProperty("debugMode", debugMode);

        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        try {
            // Migrate old marked blocks from settings.dat to separate file (backwards compatibility)
            if (config.has("markedBlocks")) {
                JsonArray blocksArray = config.getAsJsonArray("markedBlocks");
                if (blocksArray.size() > 0) {
                    markedBlocks.clear();
                    for (int i = 0; i < blocksArray.size(); i++) {
                        JsonObject blockObj = blocksArray.get(i).getAsJsonObject();
                        int x = blockObj.get("x").getAsInt();
                        int y = blockObj.get("y").getAsInt();
                        int z = blockObj.get("z").getAsInt();
                        markedBlocks.add(new BlockPos(x, y, z));
                    }
                    // Migrate to new file
                    saveBlocksToFile();
                }
            }

            // Load settings
            if (config.has("autoMode")) {
                autoMode = config.get("autoMode").getAsBoolean();
            }
            if (config.has("autoReloadBlocks")) {
                autoReloadBlocks = config.get("autoReloadBlocks").getAsBoolean();
            }
            if (config.has("autoMineRange")) {
                autoMineRange = config.get("autoMineRange").getAsDouble();
            }
            if (config.has("requireDungeonBreaker")) {
                requireDungeonBreaker = config.get("requireDungeonBreaker").getAsBoolean();
            }
            if (config.has("renderESP")) {
                renderESP = config.get("renderESP").getAsBoolean();
            }
            if (config.has("espMode")) {
                int modeIndex = config.get("espMode").getAsInt();
                ESPMode[] modes = ESPMode.values();
                if (modeIndex >= 0 && modeIndex < modes.length) {
                    espMode = modes[modeIndex];
                }
            }
            if (config.has("espColorUnmined")) {
                espColorUnmined = config.get("espColorUnmined").getAsInt();
            }
            if (config.has("espColorMined")) {
                espColorMined = config.get("espColorMined").getAsInt();
            }
            if (config.has("espColorMining")) {
                espColorMining = config.get("espColorMining").getAsInt();
            }
            if (config.has("espThroughWalls")) {
                espThroughWalls = config.get("espThroughWalls").getAsBoolean();
            }
            if (config.has("autoRespawn")) {
                autoRespawn = config.get("autoRespawn").getAsBoolean();
            }
            if (config.has("respawnDelaySeconds")) {
                respawnDelaySeconds = config.get("respawnDelaySeconds").getAsInt();
            }
            if (config.has("instantMining")) {
                instantMining = config.get("instantMining").getAsBoolean();
            }
            if (config.has("delayBetweenBlocks")) {
                delayBetweenBlocks = config.get("delayBetweenBlocks").getAsBoolean();
            }
            if (config.has("debugMode")) {
                debugMode = config.get("debugMode").getAsBoolean();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========== SETTINGS ==========

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Auto Mode",
            "Auto-mine when close (false = keybind mode)",
            "auto_mode",
            () -> autoMode,
            val -> {
                autoMode = val;
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Mode: " + (autoMode ? "AUTO" : "KEYBIND")), false);
                }
            }
        ));

        settings.add(new CheckboxSetting(
            "Auto-Reload Blocks",
            "Auto-reload saved blocks when entering a room with matching ID",
            "auto_reload",
            () -> autoReloadBlocks,
            val -> {
                autoReloadBlocks = val;
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Auto-Reload: " + (val ? "§aON" : "§cOFF")), false);
                }
            }
        ));

        settings.add(new SliderSetting(
            "Auto-Mine Range",
            "Maximum distance to auto-mine blocks",
            "automine_range",
            1.0f, 10.0f,
            () -> (float) autoMineRange,
            val -> autoMineRange = val
        ).withDecimals(1).withSuffix("m"));

        settings.add(new CheckboxSetting(
            "Require Dungeonbreaker",
            "Only use Dungeonbreaker (false = any pickaxe works)",
            "require_db",
            () -> requireDungeonBreaker,
            val -> {
                requireDungeonBreaker = val;
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Require Dungeonbreaker: " + (val ? "§cON (only Dungeonbreaker)" : "§aOFF (any pickaxe)")), false);
                }
            }
        ));

        settings.add(new CheckboxSetting(
            "Render ESP",
            "Show boxes around marked blocks",
            "render_esp",
            () -> renderESP,
            val -> renderESP = val
        ));

        settings.add(new DropdownSetting(
            "ESP Mode",
            "Rendering style for the block ESP",
            "esp_mode",
            ESPMode.getDisplayNames(),
            () -> espMode.ordinal(),
            val -> espMode = ESPMode.values()[val]
        ));

        settings.add(new ColorPickerSetting(
            "Unmined Color",
            "Color for blocks that haven't been mined yet",
            "esp_color_unmined",
            () -> espColorUnmined,
            val -> espColorUnmined = val
        ));

        settings.add(new ColorPickerSetting(
            "Mined Color",
            "Color for blocks that have been mined",
            "esp_color_mined",
            () -> espColorMined,
            val -> espColorMined = val
        ));

        settings.add(new ColorPickerSetting(
            "Mining Color",
            "Color for the block currently being mined",
            "esp_color_mining",
            () -> espColorMining,
            val -> espColorMining = val
        ));

        settings.add(new CheckboxSetting(
            "Through Walls",
            "Render ESP through walls (disable for Iris shader compatibility)",
            "esp_through_walls",
            () -> espThroughWalls,
            val -> espThroughWalls = val
        ));

        settings.add(new CheckboxSetting(
            "Auto-Respawn",
            "Auto-reset blocks after respawn time",
            "auto_respawn",
            () -> autoRespawn,
            val -> {
                autoRespawn = val;
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Auto-Respawn: " + (val ? "§aON" : "§cOFF")), false);
                }
            }
        ));

        settings.add(new SliderSetting(
            "Respawn Delay",
            "Time to wait before remining (10s spawn + buffer)",
            "respawn_delay",
            5f, 30f,
            () -> (float) respawnDelaySeconds,
            val -> respawnDelaySeconds = (int) val.floatValue()
        ).withSuffix("s"));

        settings.add(new CheckboxSetting(
            "Instant Mining",
            "RDBT-style: mine 1 block per tick without waiting (true = fast, false = safe)",
            "instant_mining",
            () -> instantMining,
            val -> {
                instantMining = val;
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[DungeonBreakerExtras] Instant Mining: " + (val ? "§aON (RDBT-style)" : "§eOFF (Safe mode)")), false);
                }
            }
        ));

        settings.add(new CheckboxSetting(
            "Delay Between Blocks",
            "Wait 1 tick between blocks (only for safe mode)",
            "delay_between_blocks",
            () -> delayBetweenBlocks,
            val -> delayBetweenBlocks = val
        ));

        settings.add(new CheckboxSetting(
            "Debug Mode",
            "Show debug messages in chat",
            "debug_mode",
            () -> debugMode,
            val -> debugMode = val
        ));

        return settings;
    }

    /**
     * ESP Rendering Modes - Different visual styles for block rendering
     */
    public enum ESPMode {
        OUTLINE("Outline", "Simple outline box"),
        FILLED("Filled", "Filled box with transparency"),
        FILLED_OUTLINE("Filled + Outline", "Filled box with outline (classic)"),
        GLOW("Glow", "Glowing outline effect"),
        CHAMS("Chams", "See-through solid rendering"),
        WIREFRAME("Wireframe", "Wireframe mesh style"),
        NONE("None", "No ESP rendering");

        private final String displayName;
        private final String description;

        ESPMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public static String[] getDisplayNames() {
            ESPMode[] modes = values();
            String[] names = new String[modes.length];
            for (int i = 0; i < modes.length; i++) {
                names[i] = modes[i].displayName;
            }
            return names;
        }
    }

}
