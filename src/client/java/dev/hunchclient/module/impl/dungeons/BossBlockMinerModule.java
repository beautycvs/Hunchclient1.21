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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
public class BossBlockMinerModule extends Module implements ConfigurableModule, SettingsProvider {
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
    private int lastRoomCheckTick = 0;
    private static final int ROOM_CHECK_INTERVAL = 2; // Check room every 2 ticks (100ms) for FAST boss detection
    private boolean notifiedRoomReady = false; // Track if we already notified about room detection ready

    // ========== SETTINGS ==========
    private boolean autoMode = false; // false = keybind mode, true = auto proximity mode
    private boolean autoReloadBlocks = true; // Auto-reload blocks when entering a known room
    private double autoMineRange = 4.5;
    private double autoSwapRange = 5.5; // Swap to pickaxe earlier (mineRange + 1.0)
    private boolean requireDungeonBreaker = false; // Default: false (works with any pickaxe)
    private boolean renderESP = true;
    private int espColorUnmined = 0x8000FF00; // Green with alpha
    private int espColorMined = 0x80808080; // Gray with alpha
    private boolean debugMessages = false;
    private boolean autoRespawn = true; // Auto-reset mined blocks after respawn time
    private int respawnDelaySeconds = 15; // 10s spawn + 5s buffer
    private boolean instantMining = true; // RDBT-style instant mining (nuke every tick without waiting)
    private boolean delayBetweenBlocks = false; // Add 1 tick delay between blocks (only for safe mode)

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

    public BossBlockMinerModule() {
        super("BossBlockMiner", "Auto-mines marked blocks with vanilla mining (CGA-safe)", Category.DUNGEONS, false);

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
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Enabled! Mode: " + (autoMode ? "AUTO" : "KEYBIND") + " | Marked blocks: " + markedBlocks.size()), false);
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

        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] Disabled!"), false);
        }
    }

    // ========== TICK HANDLER ==========

    private void tickHandler(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // DungeonManager is ticked globally in HunchModClient via Scheduler

        // Check for room detection notifications (always, even when disabled)
        checkRoomDetectionNotification();

        // Early exit if module is disabled
        if (!isEnabled()) return;

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
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] No blocks marked!"), false);
            }
            return;
        }

        // Just build queue, don't start mining yet
        buildMiningQueue();

        if (miningQueue.isEmpty()) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] No unmined blocks in range!"), false);
            }
            return;
        }

        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Ready! " + miningQueue.size() + " blocks queued. Move closer to start."), false);
        }
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
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Pre-swapping (distance: " + String.format("%.2f", dist) + ")"), false);
                }
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
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Starting to mine!"), false);
            }
        } else if (dbSlot != -1) {
            // Need to swap
            swapToSlot(dbSlot);
            miningScheduledTick = currentTick + 3;
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Swapped and mining!"), false);
            }
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
            debugMessage("§a[BossBlockMiner] Restored to original slot");
        }

        cancelSequence();
    }

    // ========== AUTO MODE (like ChestAura2) ==========

    private void handleAutoMode(Minecraft client) {
        // Don't start new sequence if one is active
        if (sequenceActive) {
            if (MC.player != null && currentTick % 100 == 0) {
                MC.player.displayClientMessage(Component.literal("§7[BossBlockMiner] Sequence already active, waiting..."), false);
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
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] Queue peek returned null!"), false);
            }
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
            if (MC.player != null && currentTick % 100 == 0) {
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] No Dungeonbreaker found! Disable 'Require Dungeonbreaker' setting to use any item."), false);
            }
            return; // No dungeonbreaker and it's required
        }

        // If dungeonbreaker not required, try to find any pickaxe
        if (dbSlot == -1 && !requireDungeonBreaker) {
            dbSlot = findAnyPickaxeInHotbar();
            if (dbSlot != -1 && MC.player != null && currentTick % 100 == 0) {
                MC.player.displayClientMessage(Component.literal("§7[BossBlockMiner] Using pickaxe in slot " + dbSlot), false);
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
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Pre-swapping to pickaxe (distance: " + String.format("%.2f", dist) + ")"), false);
                }
            }
            return;
        }

        // In mine range - start mining
        if (dist <= autoMineRange) {
            // Start auto sequence
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Starting auto sequence! Distance: " + String.format("%.2f", dist)), false);
            }
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
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Already holding pickaxe, mining in 3 ticks!"), false);
            }
        } else if (dbSlot != -1) {
            // Swap immediately, then schedule mining after 3 ticks
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Auto-mode: Swapping to Dungeonbreaker (slot " + dbSlot + ")!"), false);
            }
            swapToSlot(dbSlot);

            miningScheduledTick = currentTick + 3; // 3 ticks for item sync
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Mining scheduled for tick " + (currentTick + 3)), false);
            }
        } else {
            // No dungeonbreaker required
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] No dungeonbreaker required, mining now!"), false);
            }
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

        if (!nearby.isEmpty() && MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Queued " + nearby.size() + " blocks for mining"), false);
        }
    }

    private void startMiningImmediate() {
        miningScheduledTick = currentTick;
        sequenceActive = true;
        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Mining scheduled IMMEDIATELY for tick " + currentTick), false);
        }
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
                if (debugMessages) {
                    MC.player.displayClientMessage(Component.literal("§e[InstantMine] No blocks mined for " + ticksSinceLastMine + " ticks, finishing"), false);
                }
                finishMining();
                return;
            }

            // Still within threshold - keep looking (player might be moving)
            if (debugMessages && ticksSinceLastMine % 10 == 0) {
                MC.player.displayClientMessage(Component.literal("§e[InstantMine] No blocks in range, waiting... (" + ticksSinceLastMine + "/" + NO_MINING_THRESHOLD_TICKS + " ticks)"), false);
            }
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

        if (debugMessages) {
            MC.player.displayClientMessage(Component.literal("§e[InstantMine] Nuked: " + closestBlock.toShortString() + " (" + String.format("%.1f", closestDistance) + "m)"), false);
        }
    }

    private void executeSafeMining() {
        // Safe mode: wait for block to break before mining next one

        // Check if currently mining a block
        if (currentlyMining != null) {
            BlockState state = MC.level.getBlockState(currentlyMining);

            if (state.isAir()) {
                // Block broken!
                if (debugMessages) {
                    MC.player.displayClientMessage(Component.literal("§a[SafeMine] Block broken: " + currentlyMining.toShortString()), false);
                }

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
                    if (debugMessages) {
                        MC.player.displayClientMessage(Component.literal("§c[SafeMine] Timeout: " + currentlyMining.toShortString()), false);
                    }
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

            if (debugMessages) {
                MC.player.displayClientMessage(Component.literal("§e[SafeMine] Mining: " + nextBlock.toShortString()), false);
            }
        }
    }

    private void finishMining() {
        waitingForMoreBlocksTick = -1;
        lastBlockMinedTick = -1;
        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Mining complete!"), false);
        }

        currentlyMining = null;
        currentlyMiningState = null;
        miningStartTick = -1;
        miningScheduledTick = -1;
        miningQueue.clear();

        if (originalSlot != -1) {
            swapBackScheduledTick = currentTick + 3;
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Swapping back in 3 ticks..."), false);
            }
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
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Block respawned: " + pos.toShortString()), false);
            }
        }
    }

    /**
     * Chat message handler for instant boss detection
     * Called from ClientPlayNetworkHandlerMixin when chat messages arrive
     * Uses EXACT same detection as AlignAura (specific boss entry message only)
     */
    public void onChatMessage(String message) {
        if (message == null) return;

        // F7 Boss Entry Detection - EXACT wie AlignAura
        // Goldor P3: "Who dares trespass into my domain?"
        if (message.contains("[BOSS] Goldor: Who dares trespass into my domain?")) {
            if (debugMessages && MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Goldor P3 detected! Force-loading boss config..."), false);
            }

            // Force update DungeonUtils cache immediately
            dev.hunchclient.util.DungeonUtils.updateCache();

            // Force reload blocks immediately (don't wait for tick check)
            loadMinepointsFromDungeonManager();

            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Boss config loaded! " + markedBlocks.size() + " blocks ready"), false);
            }
        }

        // Exit-Logic nicht nötig - Map-Detection (DungeonUtils) erkennt automatisch wenn wir zurück in Räumen sind
    }

    /**
     * Check room detection status and notify user when ready
     * Called every tick, even when module is disabled
     */
    private void checkRoomDetectionNotification() {
        Room currentRoom = null;
        if (MC.player != null) {
            currentRoom = DungeonManager.getMatchedRoomAt(MC.player.blockPosition());
        }
        if (currentRoom == null) {
            currentRoom = DungeonManager.getCurrentRoom();
        }

        // Determine if we're currently in a valid room that is matched
        boolean currentHasRoom = (currentRoom != null && currentRoom.isMatched());

        // Notify user when room detection becomes ready (first time only per room)
        if (currentHasRoom && !notifiedRoomReady && editMode) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] ✓ Room detected: " + currentRoom.getName() +
                    " (direction: " + currentRoom.getDirection() + ")"), false);
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Ready to mark MinePoints!"), false);
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
        Room currentRoom = DungeonManager.getCurrentRoom();

        // Determine if we're currently in a valid room that is matched
        boolean currentHasRoom = (currentRoom != null && currentRoom.isMatched());

        // Detect context switches
        boolean switchedToBoss = lastHadRoom && !currentHasRoom;
        boolean switchedToRoom = !lastHadRoom && currentHasRoom;
        boolean roomToRoom = lastHadRoom && currentHasRoom && lastRoom != null &&
                            !lastRoom.getName().equals(currentRoom.getName());

        boolean needsReload = switchedToBoss || switchedToRoom || roomToRoom;

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
            }

            if (debugMessages && MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Transition: " + transition), false);
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
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Loaded " + markedBlocks.size() +
                    " minepoints for " + context), false);
            }
        }

        // Always update tracking (even if no reload needed)
        lastHadRoom = currentHasRoom;
        lastRoom = currentRoom;
    }

    /**
     * NEW: Load minepoints from DungeonManager
     * Converts MinePoints to BlockPos for mining
     */
    public void loadMinepointsFromDungeonManager() {
        markedBlocks.clear();

        Room currentRoom = null;
        if (MC.player != null) {
            currentRoom = DungeonManager.getMatchedRoomAt(MC.player.blockPosition());
        }
        if (currentRoom == null) {
            currentRoom = DungeonManager.getCurrentRoom();
        }

        if (currentRoom != null && currentRoom.isMatched()) {
            // Load room minepoints
            for (MinePoint minepoint : currentRoom.getMinePoints()) {
                markedBlocks.add(minepoint.pos);
            }
        } else if (DungeonUtils.isInBossFight()) {
            // Load boss minepoints (absolute coordinates)
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
            debugMessage("§c[BossBlockMiner] Dungeonbreaker not found!");
            cancelSequence();
            return;
        }

        swapToSlot(dbSlot);
        debugMessage("§a[BossBlockMiner] Swapped to Dungeonbreaker (slot " + dbSlot + ")");
    }

    private void executeSwapBack() {
        if (originalSlot != -1) {
            restoreOriginalSlot();
            debugMessage("§a[BossBlockMiner] Swapped back to original slot");
        }
        cancelSequence();
    }

    // ========== CGA-STYLE SWAP FUNCTIONS ==========

    private void swapToSlot(int slot) {
        if (MC.player == null) return;
        if (slot < 0 || slot > 8) return;

        // CGA-style swap guard: max 1 per tick
        if (recentlySwapped) {
            debugMessage("§c[BossBlockMiner] Swap blocked (max 1 per tick)");
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
            debugMessage("§c[BossBlockMiner] Restore blocked (max 1 per tick)");
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
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Edit mode enabled - Right-click blocks to mark"), false);
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
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Auto mode: " + (autoMode ? "§aON" : "§cOFF")), false);
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
            } else if (DungeonUtils.isInBossFight()) {
                // Remove from boss minepoints
                DungeonManager.getBossMinepoints().removeIf(mp -> mp.pos.equals(pos));
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§c[MinePoint] Removed from Boss"), false);
                }
            }

            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] Block unmarked: " + pos.toShortString()), false);
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
            } else if (DungeonUtils.isInBossFight()) {
                // Boss minepoint (absolute coords)
                int index = DungeonManager.getBossMinepoints().size() + 1;
                MinePoint mp = new MinePoint(index, pos, "Boss Mine " + index, pos, true);
                DungeonManager.getBossMinepoints().add(mp);

                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[MinePoint] Added to Boss (absolute coords)"), false);
                }
            } else {
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§e[MinePoint] ⚠ Not in a matched room or boss!"), false);
                    MC.player.displayClientMessage(Component.literal("§e[MinePoint] Block added to local list, but won't persist."), false);
                }
            }

            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Block marked: " + pos.toShortString() +
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
                    // Edit mode: All blocks bright green
                    colorInt = 0xA000FF00; // Brighter green with more alpha
                } else if (isCurrentlyMining) {
                    // Currently mining: Yellow/Orange
                    colorInt = 0xA0FFAA00;
                } else if (mined) {
                    // Already mined: Gray
                    colorInt = espColorMined;
                } else {
                    // Not yet mined: Green
                    colorInt = espColorUnmined;
                }

                // Convert int color to RGBA float array
                float[] color = intToRGBA(colorInt);

                // Draw filled box around block using PrimitiveCollector
                AABB box = new AABB(pos).inflate(0.0025);
                collector.submitFilledBox(box, color, 1.0f, true);
            }
        } catch (Exception e) {
            // Log rendering errors for debugging
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] Rendering error: " + e.getMessage()), false);
            }
            e.printStackTrace();
        }
    }

    private float[] intToRGBA(int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
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
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Cleared all marked blocks"), false);
        }
    }

    public void resetMinedBlocks() {
        minedBlocksWithTime.clear();
        currentlyMining = null;
        miningQueue.clear();

        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Reset mined blocks status"), false);
        }
    }

    // ========== BLOCK FILE I/O ==========

    /**
     * Check if player is in a dungeon via scoreboard (like AutoSuperboom does)
     */
    private boolean isInDungeon() {
        return DungeonUtils.isInDungeon() || DungeonUtils.isInBossFight();
    }


    /**
     * Strip formatting codes from text
     */
    private String stripFormatting(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
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
        try {
            if (markedBlocks.isEmpty()) {
                if (debugMessages && MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] No minepoints to save"), false);
                }
                return;
            }

            Room currentRoom = null;
            if (MC.player != null) {
                currentRoom = DungeonManager.getMatchedRoomAt(MC.player.blockPosition());
            }
            if (currentRoom == null) {
                currentRoom = DungeonManager.getCurrentRoom();
            }

            // Check if we're in a dungeon but room detection isn't ready yet
            if (isInDungeon() && (currentRoom == null || !currentRoom.isMatched())) {
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] ⚠ Room detection not ready yet!"), false);
                    MC.player.displayClientMessage(Component.literal("§e[BossBlockMiner] Please wait for room to be matched..."), false);
                }
                return; // Don't save yet
            }

            if (currentRoom != null && currentRoom.isMatched()) {
                // Save to room custom minepoints
                String roomName = currentRoom.getName();
                dev.hunchclient.HunchClient.LOGGER.info("[BossBlockMiner] Saving {} minepoints for room: {}, direction: {}, physicalCorner: {}",
                    markedBlocks.size(), roomName, currentRoom.getDirection(), currentRoom.getPhysicalCornerPos());

                // Don't clear existing minepoints! Users build their configs over time.
                // Instead, just add new ones with unique indices based on what already exists.
                int baseIndex = 1000 + DungeonManager.getCustomMinepointCount(roomName);

                int index = 1;
                for (BlockPos pos : markedBlocks) {
                    BlockPos relativePos = currentRoom.actualToRelative(pos);
                    int uniqueIndex = baseIndex + index;
                    String pointName = uniqueIndex + " - Mine";
                    DungeonManager.addCustomMinepoint(roomName, uniqueIndex, relativePos, pointName, currentRoom.getDirection());
                    currentRoom.addMinePoint(uniqueIndex, relativePos, pointName);
                    index++;
                }

                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Saved " + markedBlocks.size() +
                        " minepoints (relative) for " + roomName + " (total: " +
                        DungeonManager.getCustomMinepointCount(roomName) + ")"), false);
                }
            } else if (DungeonUtils.isInBossFight()) {
                // Save to boss minepoints (absolute coords)
                int index = 1;
                DungeonManager.getBossMinepoints().clear();
                for (BlockPos pos : markedBlocks) {
                    MinePoint mp = new MinePoint(index, pos, "Boss Mine " + index, pos, true);
                    DungeonManager.getBossMinepoints().add(mp);
                    index++;
                }

                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Saved " + markedBlocks.size() +
                        " minepoints (absolute) for Boss"), false);
                }
            }
        } catch (Exception e) {
            if (MC.player != null) {
                MC.player.displayClientMessage(Component.literal("§c[BossBlockMiner] Failed to save minepoints: " + e.getMessage()), false);
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
        config.addProperty("espColorUnmined", espColorUnmined);
        config.addProperty("espColorMined", espColorMined);
        config.addProperty("debugMessages", debugMessages);
        config.addProperty("autoRespawn", autoRespawn);
        config.addProperty("respawnDelaySeconds", respawnDelaySeconds);
        config.addProperty("instantMining", instantMining);
        config.addProperty("delayBetweenBlocks", delayBetweenBlocks);

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
            if (config.has("espColorUnmined")) {
                espColorUnmined = config.get("espColorUnmined").getAsInt();
            }
            if (config.has("espColorMined")) {
                espColorMined = config.get("espColorMined").getAsInt();
            }
            if (config.has("debugMessages")) {
                debugMessages = config.get("debugMessages").getAsBoolean();
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
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Mode: " + (autoMode ? "AUTO" : "KEYBIND")), false);
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
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Auto-Reload: " + (val ? "§aON" : "§cOFF")), false);
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
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Require Dungeonbreaker: " + (val ? "§cON (only Dungeonbreaker)" : "§aOFF (any pickaxe)")), false);
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

        settings.add(new CheckboxSetting(
            "Auto-Respawn",
            "Auto-reset blocks after respawn time",
            "auto_respawn",
            () -> autoRespawn,
            val -> {
                autoRespawn = val;
                if (MC.player != null) {
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Auto-Respawn: " + (val ? "§aON" : "§cOFF")), false);
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
                    MC.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Instant Mining: " + (val ? "§aON (RDBT-style)" : "§eOFF (Safe mode)")), false);
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
            "Debug Messages",
            "Show debug messages in chat",
            "debug_messages",
            () -> debugMessages,
            val -> debugMessages = val
        ));

        return settings;
    }

    private void debugMessage(String message) {
        if (debugMessages && MC.player != null) {
            MC.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
