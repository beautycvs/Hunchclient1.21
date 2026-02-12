package dev.hunchclient.module.impl.misc;

import dev.hunchclient.event.TerminalInteractionHandler;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.EtherwarpModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.module.impl.terminal.TerminalManager;
import dev.hunchclient.module.impl.terminal.TerminalType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * F7 Sim - simulates F7 dungeon speed (500), lava bounce, and a device simulator for testing AutoSS.
 * ONLY WORKS IN SINGLEPLAYER - DISABLED IN MULTIPLAYER.
 *
 * @Author Kaze.0707
 */
public class F7SimModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IF7Sim {

    private int playerSpeed = 500; // Default 500 speed like F7
    private boolean speedSimulationEnabled = true;
    private boolean lavaBounceSimulation = true;
    private boolean etherwarpSimulation = true; // Allow shovel-based etherwarp in sim
    private long lastEtherwarpSimTime = 0L;
    private static final double MAX_ETHERWARP_RANGE = 61.0;
    private static final long ETHERWARP_COOLDOWN_MS = 400L;
    private boolean bonzoSimulation = true;
    private int bonzoPingMs = 15;
    private int bonzoExtraDelayMs = 15;
    private long lastBonzoShotTime = 0L;
    private final List<BonzoProjectile> bonzoProjectiles = new ArrayList<>();

    // Random instance for terminal type randomization
    private final java.util.Random random = new java.util.Random();

    // Terminator Bow Simulation (Hypixel F7)
    private boolean terminatorMode = false; // Toggle for Terminator bow (3 arrows + instant shot)
    private long lastBowShotTime = 0L;
    private long terminatorFireDelayMs = 200L; // Rapid-fire delay (800ms = 0.8s)
    private boolean isHoldingRightClick = false;

    // Shortbow Simulation (Clean instant-shot, perfekt für F7 Sim)
    private boolean shortbowMode = true; // Default: true für F7 Sim Training
    private static final float SHORTBOW_VELOCITY = 3.15f;  // ~Vollaufzug
    private static final float SHORTBOW_INACCURACY = 0.0f; // exakt
    private static final int SHORTBOW_COOLDOWN_TICKS = 10; // 0.5s (10 ticks)

    // Terminal Simulator (F7 Phase System)
    private boolean terminalSimEnabled = true;
    private final TerminalManager terminalManager = new TerminalManager();
    private dev.hunchclient.module.impl.terminal.ClientArmorStandManager armorStandManager;
    private boolean awaitingArmorStandSpawn = false;
    private int terminalPingMs = 0; // 0-250ms ping simulation for terminal GUIs

    // Block Respawn System
    private boolean blockRespawnEnabled = true;
    private int blockRespawnDelayMs = 10000; // Default 10 seconds

    // Clientside Block Placing (desynced from server for parkour practice)
    private boolean clientsideBlockPlacingEnabled = false; // Default: disabled
    private final java.util.PriorityQueue<RespawningBlock> scheduledRespawns =
        new java.util.PriorityQueue<>(java.util.Comparator.comparingLong(block -> block.respawnTime));
    private final java.util.Deque<RespawningBlock> respawnQueue = new java.util.ArrayDeque<>();
    private final java.util.Set<BlockPos> trackedRespawnPositions = new java.util.HashSet<>();

    // Ghost Block System (Hypixel-style: blocks exist for ping delay then disappear)
    private int ghostBlockPingMs = 100; // Default 100ms ping delay (adjustable)
    private static class GhostBlock {
        BlockPos pos;
        BlockState originalState; // State to restore (e.g. lava)
        long removeTime; // Timestamp when block should be removed

        GhostBlock(BlockPos pos, BlockState originalState, long removeTime) {
            this.pos = pos;
            this.originalState = originalState;
            this.removeTime = removeTime;
        }
    }
    private final java.util.List<GhostBlock> ghostBlocks = new java.util.ArrayList<>();
    private final java.util.Set<BlockPos> pendingGhostBlocks = new java.util.HashSet<>(); // Track positions being placed

    private long lastRespawnUpdateTime = System.currentTimeMillis();
    private long respawnAccumulatorMs = 0L;
    private static final long RESPAWN_INTERVAL_MS = 100L; // Respawn every 100ms
    private static final int BLOCKS_PER_INTERVAL = 999; // Respawn ALL queued blocks at once for clean effect

    private static final int MAX_PICKAXE_CHARGES = 20;
    private static final long CHARGE_INTERVAL_MS = 500L;
    private static final int CHARGE_PER_INTERVAL = 2;
    private int currentPickaxeCharges = MAX_PICKAXE_CHARGES;
    private long lastChargeUpdateTime = System.currentTimeMillis();
    private long chargeAccumulatorMs = 0L;

    // Phase System - Sequential terminal phases
    private int currentPhase = 1; // Start with phase 1
    private static final int MAX_PHASES = 4;
    private int phase1StartIndex = 0;  // Terminals 0-3
    private int phase2StartIndex = 4;  // Terminals 4-8
    private int phase3StartIndex = 9;  // Terminals 9-12
    private int phase4StartIndex = 13; // Terminals 13-16
    private int totalTerminals = 17;

    // Device Simulator settings (Simon Says style)
    private boolean deviceSimEnabled = true; // Auto-enabled
    private boolean deviceRunning = false;
    private List<BlockPos> fullSequence = new ArrayList<>(); // Complete 5-block sequence
    private int currentRound = 0; // Current round (1-5)
    private int flashIndex = 0; // Current flash in round
    private long lastFlashTime = 0;
    private int flashDelay = 200; // Delay between flashes (fast!)
    private boolean waitingForPlayer = false;
    private int flashDuration = 50; // How long sea lantern stays (ultra-fast, 1 tick)
    private boolean buttonsVisible = false;

    // Ivor Fohr Simulator (4th Device / Panes Terminal)
    private boolean ivorFohrRunning = false;
    private long ivorFohrStartTime = 0L;
    private long ivorFohrCountdownEnd = 0L;
    private boolean ivorFohrCountdownActive = false;
    private long ivorFohrLastSwitchTime = 0L;
    private int ivorFohrSwitchDelay = 800; // Switch block every 800ms
    private final List<Integer> ivorFohrEmeraldIndices = new ArrayList<>(); // Which blocks are emerald
    private final List<Integer> ivorFohrDoneIndices = new ArrayList<>(); // Which blocks are done (terracotta)

    // Ivor Fohr grid (3x3, from ICant4Module)
    private static final int[][] IVOR_FOHR_BLOCKS = {
        {68, 130, 50}, {66, 130, 50}, {64, 130, 50},
        {68, 128, 50}, {66, 128, 50}, {64, 128, 50},
        {68, 126, 50}, {66, 126, 50}, {64, 126, 50}
    };

    // Device grid positions
    private static final int GRID_X = 111; // Obsidian blocks (sea lantern flashes here)
    private static final int BUTTON_X = 110; // Buttons WEST of obsidian (player clicks here)
    private static final int GRID_Y_MIN = 120;
    private static final int GRID_Y_MAX = 123;
    private static final int GRID_Z_MIN = 92;
    private static final int GRID_Z_MAX = 95;
    private static final BlockPos START_BUTTON_POS = new BlockPos(110, 121, 91); // Start button (AutoSS detection!)

    public F7SimModule() {
        super("F7Sim", "Simulates 500 speed and lava bounce in singleplayer", Category.MISC, false);
        UseBlockCallback.EVENT.register(this::onSimulatedBlockUse);
        UseItemCallback.EVENT.register(this::onSimulatedItemUse);
        this.armorStandManager = new dev.hunchclient.module.impl.terminal.ClientArmorStandManager(terminalManager);
    }

    @Override
    protected void onEnable() {
        // Reset mining/respawn state every enable
        currentPickaxeCharges = MAX_PICKAXE_CHARGES;
        chargeAccumulatorMs = 0L;
        lastChargeUpdateTime = System.currentTimeMillis();
        respawnAccumulatorMs = 0L;
        lastRespawnUpdateTime = System.currentTimeMillis();
        scheduledRespawns.clear();
        respawnQueue.clear();
        trackedRespawnPositions.clear();

        Minecraft mc = Minecraft.getInstance();

        // CRITICAL: NEVER work in multiplayer! Disable immediately!
        if (mc != null && !mc.isLocalServer()) {
            debugMessage("\u00A7c\u00A7l[F7Sim] ERROR: F7Sim ONLY works in Singleplayer! Disabling...");
            setEnabled(false);
            return;
        }

        // Initialize terminals if terminal sim is enabled
        if (terminalSimEnabled) {
            // FORCE cleanup any leftover armor stands from previous sessions BEFORE initializing
            debugMessage("§e[F7Sim] Cleaning up old terminal armor stands...");
            armorStandManager.forceCleanupAllTerminalStands();

            initializeTerminals();
            TerminalInteractionHandler.register(terminalManager, armorStandManager);
            awaitingArmorStandSpawn = true;
        }
    }

    /**
     * Initialize F7 terminal positions with sequential phase system
     * Only Phase 1 terminals are active at start
     * Next phases unlock when previous phase is completed
     *
     * UPDATED: Terminal types are now randomized - not always same type at same position!
     */
    private void initializeTerminals() {
        terminalManager.clearTerminals();
        currentPhase = 1; // Reset to phase 1

        // Set callback to check phase completion and unlock next phase
        terminalManager.setOnTerminalCompletedCallback(() -> {
            armorStandManager.updateArmorStands();
            checkPhaseCompletion();
        });

        // F7 Phase 1 Terminals (4 terminals) - ACTIVE AT START
        // Randomize terminal types for variety
        List<TerminalType> phase1Types = getRandomTerminalTypes(4);
        terminalManager.addTerminal(109, 113, 73, phase1Types.get(0));
        terminalManager.addTerminal(109, 119, 79, phase1Types.get(1));
        terminalManager.addTerminal(91, 112, 92, phase1Types.get(2));
        terminalManager.addTerminal(91, 122, 101, phase1Types.get(3));

        debugMessage("\u00A7a\u00A7l[Terminal Sim] Phase 1/4 active! Complete all terminals to unlock Phase 2.");
        // Note: Armor stands are spawned lazily in onTick() once world is loaded
    }

    /**
     * Get randomized terminal types (can have duplicates like in real F7)
     * @param count How many terminal types to generate
     * @return List of random terminal types
     */
    private List<TerminalType> getRandomTerminalTypes(int count) {
        List<TerminalType> types = new ArrayList<>();
        TerminalType[] allTypes = TerminalType.values();

        for (int i = 0; i < count; i++) {
            types.add(allTypes[random.nextInt(allTypes.length)]);
        }

        return types;
    }

    /**
     * Check if current phase is completed and unlock next phase
     */
    private void checkPhaseCompletion() {
        int completed = terminalManager.getCompletedCount();
        int total = terminalManager.getTotalCount();

        // Check if all terminals in current phase are completed
        if (completed >= total && currentPhase < MAX_PHASES) {
            unlockNextPhase();
        }
    }

    /**
     * Unlock the next phase by adding its terminals
     * Terminal types are randomized for variety
     */
    private void unlockNextPhase() {
        currentPhase++;

        if (currentPhase == 2) {
            // Add Phase 2 terminals (5 terminals) - RANDOMIZED
            List<TerminalType> phase2Types = getRandomTerminalTypes(5);
            terminalManager.addTerminal(68, 109, 123, phase2Types.get(0));
            terminalManager.addTerminal(59, 120, 124, phase2Types.get(1));
            terminalManager.addTerminal(47, 109, 123, phase2Types.get(2));
            terminalManager.addTerminal(40, 124, 124, phase2Types.get(3));
            terminalManager.addTerminal(39, 108, 141, phase2Types.get(4));
            debugMessage("\u00A76\u00A7l[Terminal Sim] Phase 2/4 UNLOCKED! 5 new terminals added.");
        } else if (currentPhase == 3) {
            // Add Phase 3 terminals (4 terminals) - RANDOMIZED
            List<TerminalType> phase3Types = getRandomTerminalTypes(4);
            terminalManager.addTerminal(-1, 109, 112, phase3Types.get(0));
            terminalManager.addTerminal(-1, 119, 93, phase3Types.get(1));
            terminalManager.addTerminal(17, 123, 93, phase3Types.get(2));
            terminalManager.addTerminal(-1, 109, 77, phase3Types.get(3));
            debugMessage("\u00A76\u00A7l[Terminal Sim] Phase 3/4 UNLOCKED! 4 new terminals added.");
        } else if (currentPhase == 4) {
            // Add Phase 4 terminals (4 terminals) - RANDOMIZED
            List<TerminalType> phase4Types = getRandomTerminalTypes(4);
            terminalManager.addTerminal(41, 109, 31, phase4Types.get(0));
            terminalManager.addTerminal(44, 121, 31, phase4Types.get(1));
            terminalManager.addTerminal(67, 109, 31, phase4Types.get(2));
            terminalManager.addTerminal(72, 115, 46, phase4Types.get(3));
            debugMessage("\u00A76\u00A7l[Terminal Sim] Phase 4/4 UNLOCKED! Final 4 terminals added.");
        }

        // Spawn armor stands for new terminals
        armorStandManager.spawnArmorStands();
    }

    @Override
    protected void onDisable() {
        // Reset player speed to normal
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            AttributeInstance speedAttr = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.1); // Default walking speed
            }
            if (mc.player.getAbilities() != null) {
                mc.player.getAbilities().setWalkingSpeed(0.1f);
            }
        }
        cleanupAllState();
    }

    /**
     * Called on world unload/disconnect to force cleanup all state
     * This ensures terminals and armor stands are properly cleaned up
     * even if the module wasn't disabled through normal means
     */
    public void onWorldUnload() {
        System.out.println("[F7Sim] World unload - forcing cleanup of all terminals and armor stands");
        cleanupAllState();
    }

    /**
     * Internal cleanup method - clears all simulation state
     */
    private void cleanupAllState() {
        lastEtherwarpSimTime = 0L;
        bonzoProjectiles.clear();
        lastBonzoShotTime = 0L;

        // Clear armor stands before clearing terminals
        armorStandManager.clearArmorStands();
        awaitingArmorStandSpawn = false;

        // Clear terminals
        terminalManager.clearTerminals();
        TerminalInteractionHandler.unregister();

        // Clear queued respawns/mining state
        scheduledRespawns.clear();
        respawnQueue.clear();
        trackedRespawnPositions.clear();
        respawnAccumulatorMs = 0L;
        chargeAccumulatorMs = 0L;

        // Reset device simulator
        deviceRunning = false;
        fullSequence.clear();

        // Reset Ivor Fohr
        ivorFohrRunning = false;
        ivorFohrEmeraldIndices.clear();
        ivorFohrDoneIndices.clear();

        // Reset phase
        currentPhase = 1;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // CRITICAL: Only work in singleplayer - NEVER in multiplayer!
        if (!mc.isLocalServer()) {
            if (isEnabled()) {
                debugMessage("\u00A7c\u00A7lWARNING: F7Sim ONLY works in Singleplayer! Disabling...");
                setEnabled(false);
            }
            return;
        }

        if (terminalSimEnabled && awaitingArmorStandSpawn) {
            boolean isReady = mc.level != null && mc.player != null && mc.isLocalServer();
            if (isReady) {
                awaitingArmorStandSpawn = false;
                armorStandManager.spawnArmorStands();
            }
        }

        // Spawn armor stands if terminals exist but armor stands don't (lazy init when world is loaded)
        if (terminalSimEnabled && terminalManager.getTotalCount() > 0 && !armorStandManager.hasArmorStands()) {
            armorStandManager.spawnArmorStands();
        }

        // Set movement speed to simulate F7 speed
        AttributeInstance speedAttr = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            if (speedSimulationEnabled) {
                speedAttr.setBaseValue(playerSpeed / 1000.0);
            } else {
                speedAttr.setBaseValue(0.1);
            }
        }

        if (mc.player.getAbilities() != null) {
            mc.player.getAbilities().setWalkingSpeed(speedSimulationEnabled ? (float) (playerSpeed / 1000.0) : 0.1f);
        }

        // Lava bounce simulation (like in F7)
        if (lavaBounceSimulation) {
            BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            boolean isInLava = mc.player.isInLava();

            // Check if player is on a rail (player is IN the rail block, not above it)
            BlockState currentState = mc.level.getBlockState(playerPos);
            boolean isOnRail = currentState.is(Blocks.RAIL) || currentState.is(Blocks.POWERED_RAIL) ||
                               currentState.is(Blocks.DETECTOR_RAIL) || currentState.is(Blocks.ACTIVATOR_RAIL);

            // Check if player is standing on a chest (chest is below player)
            BlockPos belowPos = playerPos.below();
            BlockState belowState = mc.level.getBlockState(belowPos);
            boolean isOnChest = belowState.is(Blocks.CHEST) || belowState.is(Blocks.TRAPPED_CHEST);

            // Also check if standing on a ghost chest (clientside placed chest)
            boolean isOnGhostChest = isGhostChestAtPosition(belowPos, mc);

            // Check if player is close to ground
            // Chests are only 0.875 blocks tall, so we need a more lenient check
            double distanceToGround = mc.player.getY() - Math.floor(mc.player.getY());
            double groundThreshold = (isOnChest || isOnGhostChest) ? 0.25 : 0.1; // More lenient for chests

            // Debug output every second
            if (mc.player.tickCount % 20 == 0 && (isOnChest || isOnGhostChest)) {
                debugMessage("§7Chest Debug: isOnChest=" + isOnChest + " isOnGhostChest=" + isOnGhostChest +
                           " distToGround=" + String.format("%.3f", distanceToGround) +
                           " threshold=" + groundThreshold +
                           " belowBlock=" + belowState.getBlock().getName().getString());
            }

            if ((isInLava || isOnRail || isOnChest || isOnGhostChest) && distanceToGround < groundThreshold) {
                double bounceVelocity;
                if (isOnRail || isOnChest || isOnGhostChest) {
                    // High bounce on rail, chest or ghost chest
                    bounceVelocity = 5.0;
                } else {
                    // Normal lava bounce
                    bounceVelocity = 3.5;
                }
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, bounceVelocity, mc.player.getDeltaMovement().z);
            }
        }

        // Device Simulator (Simon Says style)
        if (deviceSimEnabled && deviceRunning && !waitingForPlayer) {
            long now = System.currentTimeMillis();
            if (now - lastFlashTime >= flashDelay) {
                showNextFlash(mc);
            }
        }

        // Ivor Fohr Simulator (4th Device / Panes Terminal)
        updateIvorFohrSimulation(mc);

        updateRespawnQueue(mc);
        updatePickaxeCharge();
        updateTerminatorRapidFire(mc);

        updateBonzoProjectiles(mc, mc.player);

        // Ghost Block System (Hypixel-style ping delay)
        updateGhostBlocks(mc);
    }

    /**
     * Update rapid-fire for Terminator mode.
     * Shoots continuously while right-click is held.
     * NOTE: Disabled in Shortbow mode (uses cooldown instead)
     */
    private void updateTerminatorRapidFire(Minecraft mc) {
        if (!terminatorMode || shortbowMode || !isEnabled() || mc.player == null) {
            isHoldingRightClick = false;
            return;
        }

        // Check if player is holding a bow and right-click is pressed
        ItemStack heldItem = mc.player.getMainHandItem();
        boolean holdingBow = heldItem != null && !heldItem.isEmpty() && heldItem.is(net.minecraft.world.item.Items.BOW);

        if (!holdingBow || !mc.options.keyUse.isDown()) {
            isHoldingRightClick = false;
            return;
        }

        // If right-click is held, try to shoot (respects cooldown)
        if (isHoldingRightClick) {
            shootBowInstantly(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    private void updateRespawnQueue(Minecraft mc) {
        if (!isEnabled() || !blockRespawnEnabled) {
            return;
        }

        long now = System.currentTimeMillis();

        while (!scheduledRespawns.isEmpty() && scheduledRespawns.peek().shouldRespawn(now)) {
            RespawningBlock ready = scheduledRespawns.poll();
            if (ready != null) {
                respawnQueue.add(ready);
            }
        }

        long elapsed = Math.max(0L, now - lastRespawnUpdateTime);
        lastRespawnUpdateTime = now;

        if (respawnQueue.isEmpty()) {
            if (scheduledRespawns.isEmpty()) {
                respawnAccumulatorMs = 0L;
            }
            return;
        }

        respawnAccumulatorMs += elapsed;

        if (!(mc.level instanceof ClientLevel clientWorld)) {
            return;
        }

        while (respawnAccumulatorMs >= RESPAWN_INTERVAL_MS && !respawnQueue.isEmpty()) {
            respawnAccumulatorMs -= RESPAWN_INTERVAL_MS;
            // Respawn multiple blocks at once for smooth, fast effect
            for (int i = 0; i < BLOCKS_PER_INTERVAL && !respawnQueue.isEmpty(); i++) {
                RespawningBlock block = respawnQueue.poll();
                if (block != null) {
                    trackedRespawnPositions.remove(block.pos);
                    respawnBlock(mc, clientWorld, block);
                }
            }
        }
    }

    private void updatePickaxeCharge() {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - lastChargeUpdateTime);
        lastChargeUpdateTime = now;

        if (currentPickaxeCharges >= MAX_PICKAXE_CHARGES) {
            chargeAccumulatorMs = 0L;
            return;
        }

        chargeAccumulatorMs += elapsed;
        while (chargeAccumulatorMs >= CHARGE_INTERVAL_MS && currentPickaxeCharges < MAX_PICKAXE_CHARGES) {
            chargeAccumulatorMs -= CHARGE_INTERVAL_MS;
            currentPickaxeCharges = Math.min(MAX_PICKAXE_CHARGES, currentPickaxeCharges + CHARGE_PER_INTERVAL);
        }
    }

    public boolean hasAvailablePickaxeCharge() {
        if (!isEnabled()) {
            return true;
        }
        return currentPickaxeCharges > 0;
    }

    private boolean consumePickaxeCharge() {
        if (!isEnabled()) {
            return true;
        }
        if (currentPickaxeCharges <= 0) {
            return false;
        }
        currentPickaxeCharges--;
        return true;
    }

    public int getCurrentPickaxeCharges() {
        return currentPickaxeCharges;
    }
    // Device Simulator Methods (Simon Says)
    public void startDeviceSimulation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            debugMessage("\u00A7cERROR: World is null!");
            return;
        }
        if (!mc.isLocalServer()) {
            debugMessage("\u00A7cDevice Sim only works in Singleplayer!");
            return;
        }

        // Force enable device sim when starting (AutoSS compatibility)
        if (!deviceSimEnabled) {
            debugMessage("\u00A7e[F7Sim] Enabling Device Sim for AutoSS...");
            deviceSimEnabled = true;
        }

        debugMessage("\u00A7a\u00A7l[F7Sim] Starting Simon Says Device!");
        debugMessage("\u00A7edeviceSimEnabled: " + deviceSimEnabled);
        fullSequence.clear();
        currentRound = 1;
        flashIndex = 0;
        waitingForPlayer = false;

        // Buttons spawn AFTER flash, not at start (like on Hypixel)
        setDeviceButtonsVisible(mc, false);

        // Generate 5 random unique blocks
        List<BlockPos> allPositions = new ArrayList<>();
        for (int y = GRID_Y_MIN; y <= GRID_Y_MAX; y++) {
            for (int z = GRID_Z_MIN; z <= GRID_Z_MAX; z++) {
                allPositions.add(new BlockPos(GRID_X, y, z));
            }
        }

        for (int i = 0; i < 5; i++) {
            BlockPos pos = allPositions.remove(random.nextInt(allPositions.size()));
            fullSequence.add(pos);
        }

        deviceRunning = true;
        lastFlashTime = System.currentTimeMillis();
        debugMessage("\u00A7eRound 1/5 - Watch carefully!");
    }

    public void stopDeviceSimulation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Reset all blocks to obsidian
        for (int y = GRID_Y_MIN; y <= GRID_Y_MAX; y++) {
            for (int z = GRID_Z_MIN; z <= GRID_Z_MAX; z++) {
                BlockPos pos = new BlockPos(GRID_X, y, z);
                mc.level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
            }
        }
        setDeviceButtonsVisible(mc, false);

        deviceRunning = false;
        fullSequence.clear();
        currentRound = 0;
        flashIndex = 0;
        waitingForPlayer = false;
        debugMessage("\u00A7cDevice Simulation stopped");
    }

    /**
     * Shows next flash in current round (Simon Says cumulative)
     */
    private void showNextFlash(Minecraft mc) {
        // How many blocks to show this round (cumulative!)
        int blocksThisRound = currentRound;

        if (flashIndex >= blocksThisRound) {
            // Done showing this round - wait for player
            waitingForPlayer = true;
            // Spawn buttons AFTER all flashes (like on Hypixel!)
            setDeviceButtonsVisible(mc, true);
            debugMessage("\u00A7bYour turn! Click the buttons in order.");
            // AutoSS would take over here in real use
            // For testing, auto-advance to next round after 3 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Minecraft client = Minecraft.getInstance();
                    client.execute(this::nextRound);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return;
        }

        BlockPos pos = fullSequence.get(flashIndex);

        // Flash to sea lantern
        mc.level.setBlockAndUpdate(pos, Blocks.SEA_LANTERN.defaultBlockState());
        debugMessage("\u00A7e" + (flashIndex + 1) + "/" + blocksThisRound);

        // Reset after flashDuration (ultra-fast)
        new Thread(() -> {
            try {
                Thread.sleep(flashDuration);
                Minecraft client = Minecraft.getInstance();
                if (client.level != null && client.isLocalServer()) {
                    client.execute(() -> {
                        client.level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                    });
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        flashIndex++;
        lastFlashTime = System.currentTimeMillis();
    }

    private void setDeviceButtonsVisible(Minecraft mc, boolean visible) {
        if (buttonsVisible == visible) {
            debugMessage("\u00A77Buttons already " + (visible ? "visible" : "hidden") + ", skipping");
            return;
        }

        debugMessage("\u00A7aSet buttons visible=" + visible);
        buttonsVisible = visible;
        if (mc.level == null) {
            debugMessage("\u00A7cWorld is null!");
            return;
        }

        int count = 0;
        for (int y = GRID_Y_MIN; y <= GRID_Y_MAX; y++) {
            for (int z = GRID_Z_MIN; z <= GRID_Z_MAX; z++) {
                BlockPos buttonPos = new BlockPos(BUTTON_X, y, z);

                if (visible) {
                    // Button at X=110, facing WEST (towards player), attached to block at X=111 (EAST)
                    BlockState buttonState = Blocks.STONE_BUTTON.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
                    mc.level.setBlockAndUpdate(buttonPos, buttonState);
                    count++;
                } else {
                    // Remove buttons
                    BlockState existing = mc.level.getBlockState(buttonPos);
                    if (existing.is(BlockTags.BUTTONS)) {
                        mc.level.setBlockAndUpdate(buttonPos, Blocks.AIR.defaultBlockState());
                        count++;
                    }
                }
            }
        }
        debugMessage("\u00A7aProcessed " + count + " buttons");
    }

    /**
     * Advances to next round (after player successfully clicked)
     */
    private void nextRound() {
        if (currentRound >= 5) {
            debugMessage("\u00A7a\u00A7lSimon Says Complete! All 5 rounds done!");
            stopDeviceSimulation();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Remove buttons before next round (they respawn after flash)
        setDeviceButtonsVisible(mc, false);

        currentRound++;
        flashIndex = 0;
        waitingForPlayer = false;
        lastFlashTime = System.currentTimeMillis();
        debugMessage("\u00A7eRound " + currentRound + "/5 - Watch carefully!");
    }

    private InteractionResult onSimulatedBlockUse(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!world.isClientSide()) {
            return InteractionResult.PASS;
        }

        if (tryHandleEtherwarp(player, hand)) {
            return InteractionResult.SUCCESS;
        }

        if (tryHandleBonzo(player, hand)) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private InteractionResult onSimulatedItemUse(Player player, Level world, InteractionHand hand) {
        if (!world.isClientSide()) {
            return InteractionResult.PASS;
        }

        if (tryHandleEtherwarp(player, hand)) {
            return InteractionResult.SUCCESS;
        }

        if (tryHandleBonzo(player, hand)) {
            return InteractionResult.SUCCESS;
        }

        // Note: Bow instant shot is now handled by BowItemMixin, not here!

        return InteractionResult.PASS;
    }

    private boolean tryHandleEtherwarp(Player player, InteractionHand hand) {
        if (!shouldSimulateEtherwarp(player, hand)) {
            return false;
        }
        return attemptEtherwarp(player);
    }

    private boolean tryHandleBonzo(Player player, InteractionHand hand) {
        if (!bonzoSimulation || !isEnabled()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        if (player == null || player != mc.player) {
            return false;
        }
        if (!mc.isLocalServer()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!isBonzoStaff(stack)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastBonzoShotTime < 150L) { // Prevent duplicate triggers from same click
            return false;
        }

        lastBonzoShotTime = now;

        Vec3 origin = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f).normalize();
        scheduleBonzoProjectile(origin, lookVec);
        return true;
    }

    /**
     * Called when player attempts to use bow (from BowItemUseMixin).
     * Handles instant-shot and registers right-click state for rapid-fire.
     */
    public void onBowUseAttempt(Player player, InteractionHand hand) {
        if (!isEnabled() || !terminatorMode) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // Mark that right-click is being held
        isHoldingRightClick = true;

        // Try to shoot immediately
        shootBowInstantly(player, hand);
    }

    /**
     * PUBLIC: Shoot bow instantly (called from BowItemMixin or rapid-fire tick)
     * - Bow always shoots instantly (no charge time) in F7Sim
     * - If Terminator mode is enabled, shoots 3 arrows instead of 1
     */
    public void shootBowInstantly(Player player, InteractionHand hand) {
        if (!isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // Cooldown check
        long now = System.currentTimeMillis();
        long cooldown = terminatorMode ? terminatorFireDelayMs : 200L;

        if (now - lastBowShotTime < cooldown) {
            return; // Still on cooldown
        }

        lastBowShotTime = now;

        // Spawn arrows
        int arrowCount = terminatorMode ? 3 : 1;
        shootInstantArrows(mc, player, arrowCount);
    }

    /**
     * Shoot arrows instantly without charge time
     * @param arrowCount How many arrows to shoot (1 for normal, 3 for Terminator)
     */
    private void shootInstantArrows(Minecraft mc, Player player, int arrowCount) {
        Vec3 origin = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f).normalize();

        // Terminator shoots 3 arrows in a small spread pattern
        if (arrowCount == 3) {
            // Calculate perpendicular vectors for spread
            Vec3 right = lookVec.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = right.cross(lookVec).normalize();

            // Middle arrow (straight)
            spawnArrow(mc, origin, lookVec, 3.0);

            // Left arrow (slight offset)
            Vec3 leftDir = lookVec.add(right.scale(-0.05)).normalize();
            spawnArrow(mc, origin, leftDir, 3.0);

            // Right arrow (slight offset)
            Vec3 rightDir = lookVec.add(right.scale(0.05)).normalize();
            spawnArrow(mc, origin, rightDir, 3.0);

        } else {
            // Normal bow - 1 arrow
            spawnArrow(mc, origin, lookVec, 3.0);
        }

        // Play bow shoot sound
        mc.level.playSound(player, player.blockPosition(),
            net.minecraft.sounds.SoundEvents.ARROW_SHOOT,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0f, 1.0f / (mc.level.getRandom().nextFloat() * 0.4f + 1.2f) + 0.5f);
    }

    /**
     * Spawn a single arrow with velocity
     * In Singleplayer: Spawn server-side so arrows can trigger block hits
     * In Multiplayer: Would need different approach (but F7Sim only works in SP)
     */
    private void spawnArrow(Minecraft mc, Vec3 origin, Vec3 direction, double speed) {
        if (mc.level == null || mc.player == null) return;

        // In singleplayer, spawn on server world so arrows are "real"
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            ServerLevel serverWorld = mc.getSingleplayerServer().overworld();
            if (serverWorld != null) {
                // Create arrow entity using world and shooter
                net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                    net.minecraft.world.entity.EntityType.ARROW,
                    serverWorld
                );

                arrow.setPos(origin.x, origin.y, origin.z);
                arrow.setOwner(mc.player);
                arrow.setDeltaMovement(direction.x * speed, direction.y * speed, direction.z * speed);
                arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;

                serverWorld.addFreshEntity(arrow);
                return;
            }
        }

        // Fallback: Client-side spawn (won't trigger block hits properly)
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
            net.minecraft.world.entity.EntityType.ARROW,
            mc.level
        );

        arrow.setPos(origin.x, origin.y, origin.z);
        arrow.setOwner(mc.player);
        arrow.setDeltaMovement(direction.x * speed, direction.y * speed, direction.z * speed);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;

        mc.level.addFreshEntity(arrow);
    }

    private boolean shouldSimulateEtherwarp(Player player, InteractionHand hand) {
        if (!etherwarpSimulation || !isEnabled()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (player == null || player != mc.player) return false;
        if (!mc.isLocalServer()) return false;
        if (!player.isShiftKeyDown()) return false;

        ItemStack stack = player.getItemInHand(hand);
        return isDiamondShovel(stack);
    }

    private boolean isDiamondShovel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.DIAMOND_SHOVEL);
    }

    private boolean isBonzoStaff(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.BLAZE_ROD);
    }

    private boolean isBow(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.BOW);
    }

    private boolean attemptEtherwarp(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastEtherwarpSimTime < ETHERWARP_COOLDOWN_MS) {
            return false;
        }

        BlockPos targetBlock = EtherwarpModule.getLookingAtBlock();
        if (targetBlock == null) {
            return false;
        }

        Vec3 targetPos = new Vec3(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 1.0,
            targetBlock.getZ() + 0.5
        );

        double distanceSq = player.distanceToSqr(targetPos);
        if (distanceSq > MAX_ETHERWARP_RANGE * MAX_ETHERWARP_RANGE) {
            return false;
        }

        if (!EtherwarpModule.isBlockEtherwarpable(targetBlock)) {
            return false;
        }

        player.setPos(targetPos);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0.0f;
        lastEtherwarpSimTime = now;
        return true;
    }

    private void scheduleBonzoProjectile(Vec3 origin, Vec3 direction) {
        int delayMs = Math.max(0, bonzoPingMs + bonzoExtraDelayMs);
        Runnable spawnTask = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !mc.isLocalServer()) {
                return;
            }
            bonzoProjectiles.add(new BonzoProjectile(origin.add(direction.scale(0.5)), direction.normalize()));
        };

        if (delayMs <= 0) {
            Minecraft.getInstance().execute(spawnTask);
        } else {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(delayMs);
                    Minecraft.getInstance().execute(spawnTask);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "BonzoSimDelay");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void updateBonzoProjectiles(Minecraft mc, Player player) {
        if (!bonzoSimulation || bonzoProjectiles.isEmpty()) {
            return;
        }
        if (!(mc.level instanceof ClientLevel clientWorld) || player == null) {
            bonzoProjectiles.clear();
            return;
        }

        Iterator<BonzoProjectile> iterator = bonzoProjectiles.iterator();
        while (iterator.hasNext()) {
            BonzoProjectile projectile = iterator.next();
            if (!projectile.tick(clientWorld, player)) {
                iterator.remove();
            }
        }
    }

    public void toggleDeviceSim() {
        if (deviceRunning) {
            stopDeviceSimulation();
        } else {
            startDeviceSimulation();
        }
    }

    private void debugMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("\u00A7e[F7Sim] \u00A7f" + msg), false);
        }
    }

    // Getters/Setters for GUI
    public int getPlayerSpeed() {
        return playerSpeed;
    }

    public void setPlayerSpeed(int speed) {
        this.playerSpeed = Math.max(100, Math.min(500, speed));
    }

    public boolean isSpeedSimulationEnabled() {
        return speedSimulationEnabled;
    }

    public void setSpeedSimulationEnabled(boolean enabled) {
        this.speedSimulationEnabled = enabled;
        if (!enabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                AttributeInstance speedAttr = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.setBaseValue(0.1);
                }
                if (mc.player.getAbilities() != null) {
                    mc.player.getAbilities().setWalkingSpeed(0.1f);
                }
            }
        }
    }

    public boolean isLavaBounceEnabled() {
        return lavaBounceSimulation;
    }

    public void setLavaBounceEnabled(boolean enabled) {
        this.lavaBounceSimulation = enabled;
    }

    public boolean isDeviceSimEnabled() {
        return deviceSimEnabled;
    }

    public void setDeviceSimEnabled(boolean enabled) {
        this.deviceSimEnabled = enabled;
        if (!enabled && deviceRunning) {
            stopDeviceSimulation();
        }
    }

    public boolean isEtherwarpSimulationEnabled() {
        return etherwarpSimulation;
    }

    public void setEtherwarpSimulationEnabled(boolean enabled) {
        this.etherwarpSimulation = enabled;
        if (!enabled) {
            lastEtherwarpSimTime = 0L;
        }
    }

    public boolean isBonzoSimulationEnabled() {
        return bonzoSimulation;
    }

    public void setBonzoSimulationEnabled(boolean enabled) {
        this.bonzoSimulation = enabled;
        if (!enabled) {
            bonzoProjectiles.clear();
            lastBonzoShotTime = 0L;
        }
    }

    public int getBonzoPingMs() {
        return bonzoPingMs;
    }

    public void setBonzoPingMs(int pingMs) {
        this.bonzoPingMs = Math.max(0, pingMs);
    }

    public int getBonzoExtraDelayMs() {
        return bonzoExtraDelayMs;
    }

    public void setBonzoExtraDelayMs(int delayMs) {
        this.bonzoExtraDelayMs = Math.max(0, delayMs);
    }

    public boolean isTerminatorMode() {
        return terminatorMode;
    }

    public void setTerminatorMode(boolean enabled) {
        this.terminatorMode = enabled;
        if (!enabled) {
            lastBowShotTime = 0L;
            isHoldingRightClick = false;
        }
    }

    public long getTerminatorFireDelayMs() {
        return terminatorFireDelayMs;
    }

    public void setTerminatorFireDelayMs(long delayMs) {
        this.terminatorFireDelayMs = Math.max(50L, Math.min(5000L, delayMs)); // 50ms to 5s range
    }

    public boolean isTerminalSimEnabled() {
        return terminalSimEnabled;
    }

    public void setTerminalSimEnabled(boolean enabled) {
        if (this.terminalSimEnabled == enabled) {
            if (enabled) {
                awaitingArmorStandSpawn = true;
            }
            return;
        }

        this.terminalSimEnabled = enabled;
        if (!enabled) {
            awaitingArmorStandSpawn = false;
            armorStandManager.clearArmorStands();
            terminalManager.clearTerminals();
            TerminalInteractionHandler.unregister();
        } else if (isEnabled()) {
            initializeTerminals();
            TerminalInteractionHandler.register(terminalManager, armorStandManager);
            awaitingArmorStandSpawn = true;
        }
    }

    public TerminalManager getTerminalManager() {
        return terminalManager;
    }

    /**
     * Force cleanup all terminal armor stands.
     * Useful after crashes or restarts when old stands remain.
     */
    public void forceCleanupTerminalStands() {
        armorStandManager.forceCleanupAllTerminalStands();
        debugMessage("\u00A7aForced cleanup of all terminal armor stands!");
    }

    public dev.hunchclient.module.impl.terminal.ClientArmorStandManager getArmorStandManager() {
        return armorStandManager;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("playerSpeed", playerSpeed);
        data.addProperty("speedSimulationEnabled", speedSimulationEnabled);
        data.addProperty("lavaBounceSimulation", lavaBounceSimulation);
        data.addProperty("deviceSimEnabled", deviceSimEnabled);
        data.addProperty("etherwarpSimulation", etherwarpSimulation);
        data.addProperty("bonzoSimulation", bonzoSimulation);
        data.addProperty("bonzoPingMs", bonzoPingMs);
        data.addProperty("bonzoExtraDelayMs", bonzoExtraDelayMs);
        data.addProperty("terminatorMode", terminatorMode);
        data.addProperty("terminatorFireDelayMs", terminatorFireDelayMs);
        data.addProperty("terminalSimEnabled", terminalSimEnabled);
        data.addProperty("terminalPingMs", terminalPingMs);
        data.addProperty("blockRespawnEnabled", blockRespawnEnabled);
        data.addProperty("blockRespawnDelayMs", blockRespawnDelayMs);
        data.addProperty("clientsideBlockPlacingEnabled", clientsideBlockPlacingEnabled);
        data.addProperty("ghostBlockPingMs", ghostBlockPingMs);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("playerSpeed")) {
            setPlayerSpeed(data.get("playerSpeed").getAsInt());
        }
        if (data.has("speedSimulationEnabled")) {
            setSpeedSimulationEnabled(data.get("speedSimulationEnabled").getAsBoolean());
        }
        if (data.has("lavaBounceSimulation")) {
            setLavaBounceEnabled(data.get("lavaBounceSimulation").getAsBoolean());
        }
        if (data.has("deviceSimEnabled")) {
            setDeviceSimEnabled(data.get("deviceSimEnabled").getAsBoolean());
        }
        if (data.has("etherwarpSimulation")) {
            setEtherwarpSimulationEnabled(data.get("etherwarpSimulation").getAsBoolean());
        }
        if (data.has("bonzoSimulation")) {
            setBonzoSimulationEnabled(data.get("bonzoSimulation").getAsBoolean());
        }
        if (data.has("bonzoPingMs")) {
            setBonzoPingMs(data.get("bonzoPingMs").getAsInt());
        }
        if (data.has("bonzoExtraDelayMs")) {
            setBonzoExtraDelayMs(data.get("bonzoExtraDelayMs").getAsInt());
        }
        if (data.has("terminatorMode")) {
            setTerminatorMode(data.get("terminatorMode").getAsBoolean());
        }
        if (data.has("terminatorFireDelayMs")) {
            setTerminatorFireDelayMs(data.get("terminatorFireDelayMs").getAsLong());
        }
        if (data.has("terminalSimEnabled")) {
            setTerminalSimEnabled(data.get("terminalSimEnabled").getAsBoolean());
        }
        if (data.has("terminalPingMs")) {
            setTerminalPingMs(data.get("terminalPingMs").getAsInt());
        }
        if (data.has("blockRespawnEnabled")) {
            setBlockRespawnEnabled(data.get("blockRespawnEnabled").getAsBoolean());
        }
        if (data.has("blockRespawnDelayMs")) {
            setBlockRespawnDelayMs(data.get("blockRespawnDelayMs").getAsInt());
        }
        if (data.has("clientsideBlockPlacingEnabled")) {
            setClientsideBlockPlacingEnabled(data.get("clientsideBlockPlacingEnabled").getAsBoolean());
        }
        if (data.has("ghostBlockPingMs")) {
            setGhostBlockPingMs(data.get("ghostBlockPingMs").getAsInt());
        }
    }

    public boolean isDeviceRunning() {
        return deviceRunning;
    }

    // ===========================================
    //  IVOR FOHR SIMULATOR (4th Device / Panes Terminal)
    // ===========================================

    /**
     * Start Ivor Fohr simulation with 5-second countdown
     */
    public void startIvorFohrSimulation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.isLocalServer()) {
            debugMessage("§c[Ivor Fohr] Only works in Singleplayer!");
            return;
        }

        // Check if player is near 4th device position
        if (mc.player != null) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();

            // Must be on pressure plate (x=63-64, y=127, z=35-36)
            if (!(x > 63 && x < 64 && y >= 126.5 && y <= 127.5 && z > 35 && z < 36)) {
                debugMessage("§c[Ivor Fohr] You must be standing on the 4th Device pressure plate!");
                debugMessage("§7Position: x=63-64, y=127, z=35-36");
                return;
            }
        }

        debugMessage("§a§l[Ivor Fohr] Starting Panes Terminal Simulation!");
        debugMessage("§e5 second countdown...");

        ivorFohrRunning = true;
        ivorFohrCountdownActive = true;
        ivorFohrCountdownEnd = System.currentTimeMillis() + 5000L; // 5 seconds
        ivorFohrStartTime = 0L;
        ivorFohrEmeraldIndices.clear();
        ivorFohrDoneIndices.clear();

        // Spawn all 9 blocks as OBSIDIAN (SERVERSIDE in Singleplayer!)
        for (int i = 0; i < IVOR_FOHR_BLOCKS.length; i++) {
            BlockPos pos = new BlockPos(IVOR_FOHR_BLOCKS[i][0], IVOR_FOHR_BLOCKS[i][1], IVOR_FOHR_BLOCKS[i][2]);
            setBlockServerside(mc, pos, Blocks.OBSIDIAN.defaultBlockState());
        }
    }

    /**
     * Stop Ivor Fohr simulation and clean up
     */
    public void stopIvorFohrSimulation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        debugMessage("§c[Ivor Fohr] Simulation stopped - cleaning up...");

        // Remove all blocks (SERVERSIDE!)
        for (int[] block : IVOR_FOHR_BLOCKS) {
            BlockPos pos = new BlockPos(block[0], block[1], block[2]);
            setBlockServerside(mc, pos, Blocks.AIR.defaultBlockState());
        }

        ivorFohrRunning = false;
        ivorFohrCountdownActive = false;
        ivorFohrEmeraldIndices.clear();
        ivorFohrDoneIndices.clear();
    }

    /**
     * Update Ivor Fohr simulation (called from onTick)
     */
    private void updateIvorFohrSimulation(Minecraft mc) {
        if (!ivorFohrRunning || mc.level == null) return;

        long now = System.currentTimeMillis();

        // PHASE 1: Countdown
        if (ivorFohrCountdownActive) {
            long timeLeft = ivorFohrCountdownEnd - now;
            if (timeLeft <= 0) {
                ivorFohrCountdownActive = false;
                ivorFohrStartTime = now;
                debugMessage("§a[Ivor Fohr] GO! Shoot the emerald blocks!");

                // Spawn the FIRST emerald block
                spawnNextEmeraldBlock(mc);
            } else {
                // Show countdown every second
                int secondsLeft = (int) (timeLeft / 1000) + 1;
                if (timeLeft % 1000 < 50) { // Show once per second
                    debugMessage("§e" + secondsLeft + "...");
                }
            }
            return;
        }

        // Check win condition
        if (ivorFohrDoneIndices.size() >= 9) {
            debugMessage("§a§l[Ivor Fohr] COMPLETE! All 9 blocks shot!");
            debugMessage("§7Resetting in 3 seconds...");

            // Schedule reset
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    mc.execute(() -> {
                        stopIvorFohrSimulation();
                    });
                } catch (InterruptedException ignored) {}
            }).start();

            ivorFohrRunning = false; // Stop updating
        }

        // Auto-reset after 30 seconds of activity (increased from 10s)
        if (now - ivorFohrStartTime >= 30000L && ivorFohrStartTime > 0) {
            debugMessage("§c[Ivor Fohr] Time's up! Resetting...");
            stopIvorFohrSimulation();
        }
    }

    /**
     * Spawn next emerald block (called after arrow hit or at start)
     */
    private void spawnNextEmeraldBlock(Minecraft mc) {
        // Pick a random block that's not done and not already emerald
        java.util.List<Integer> availableIndices = new java.util.ArrayList<>();
        for (int i = 0; i < IVOR_FOHR_BLOCKS.length; i++) {
            if (!ivorFohrDoneIndices.contains(i) && !ivorFohrEmeraldIndices.contains(i)) {
                availableIndices.add(i);
            }
        }

        if (!availableIndices.isEmpty()) {
            // Switch random block to emerald
            int randomIndex = availableIndices.get(new java.util.Random().nextInt(availableIndices.size()));
            BlockPos pos = new BlockPos(IVOR_FOHR_BLOCKS[randomIndex][0], IVOR_FOHR_BLOCKS[randomIndex][1], IVOR_FOHR_BLOCKS[randomIndex][2]);
            setBlockServerside(mc, pos, Blocks.EMERALD_BLOCK.defaultBlockState());
            ivorFohrEmeraldIndices.add(randomIndex);
            debugMessage("§e[Ivor Fohr] New emerald block spawned! (" + (ivorFohrDoneIndices.size() + ivorFohrEmeraldIndices.size()) + "/9)");
        }
    }

    /**
     * Handle arrow hitting block (called when arrow hits)
     */
    public void onIvorFohrArrowHit(BlockPos hitPos) {
        if (!ivorFohrRunning || ivorFohrCountdownActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Find which block was hit
        for (int i = 0; i < IVOR_FOHR_BLOCKS.length; i++) {
            if (hitPos.getX() == IVOR_FOHR_BLOCKS[i][0] &&
                hitPos.getY() == IVOR_FOHR_BLOCKS[i][1] &&
                hitPos.getZ() == IVOR_FOHR_BLOCKS[i][2]) {

                // Check if it's an emerald block
                if (ivorFohrEmeraldIndices.contains(i) && !ivorFohrDoneIndices.contains(i)) {
                    // Hit emerald! Convert to terracotta
                    setBlockServerside(mc, hitPos, Blocks.GREEN_TERRACOTTA.defaultBlockState());
                    ivorFohrEmeraldIndices.remove(Integer.valueOf(i));
                    ivorFohrDoneIndices.add(i);

                    debugMessage("§a[Ivor Fohr] Hit! (" + (ivorFohrDoneIndices.size()) + "/9)");
                    mc.level.playSound(mc.player, hitPos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 2.0f);

                    // Spawn next emerald block (like real F7!)
                    if (ivorFohrDoneIndices.size() < 9) {
                        spawnNextEmeraldBlock(mc);
                    }
                }
                break;
            }
        }
    }

    public boolean isIvorFohrRunning() {
        return ivorFohrRunning;
    }

    /**
     * Set block SERVERSIDE in Singleplayer (so BlockChange events trigger!)
     */
    private void setBlockServerside(Minecraft mc, BlockPos pos, BlockState state) {
        if (mc.getSingleplayerServer() != null && mc.getSingleplayerServer().overworld() != null) {
            // Set on server world (will sync to client automatically)
            mc.getSingleplayerServer().overworld().setBlock(pos, state, Block.UPDATE_ALL);
        } else {
            // Fallback: set clientside only
            mc.level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    private static class BonzoProjectile {
        private Vec3 position;
        private final Vec3 velocity;
        private int lifeTicks = 0;
        private int explosionTicks = -1;

        private BonzoProjectile(Vec3 startPosition, Vec3 direction) {
            this.position = startPosition;
            this.velocity = direction.normalize();
        }

        private boolean tick(ClientLevel world, Player player) {
            if (explosionTicks >= 0) {
                explosionTicks++;
                spawnLingeringParticles(world);
                return explosionTicks < 20;
            }

            lifeTicks++;
            if (lifeTicks > 80) {
                return false;
            }

            Vec3 nextPos = position.add(velocity);
            ClipContext context = new ClipContext(position, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
            HitResult result = world.clip(context);
            if (result.getType() == HitResult.Type.BLOCK) {
                position = ((BlockHitResult) result).getLocation();
                explode(world, player);
                return true;
            }

            position = nextPos;
            world.addParticle(ParticleTypes.END_ROD, position.x, position.y, position.z, velocity.x * 0.05, velocity.y * 0.05, velocity.z * 0.05);
            return true;
        }

        private void explode(ClientLevel world, Player player) {
            explosionTicks = 0;
            world.playSound(player, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.0f);
            for (int i = 0; i < 12; i++) {
                double ox = (world.random.nextDouble() - 0.5) * 1.5;
                double oy = (world.random.nextDouble() - 0.5) * 1.0;
                double oz = (world.random.nextDouble() - 0.5) * 1.5;
                world.addParticle(ParticleTypes.POOF, position.x + ox, position.y + oy, position.z + oz, 0, 0.02, 0);
            }
            applyKnockback(player);
        }

        private void spawnLingeringParticles(ClientLevel world) {
            world.addParticle(ParticleTypes.SMOKE, position.x, position.y, position.z, 0, 0.01, 0);
        }

        private void applyKnockback(Player player) {
            double distanceSq = player.distanceToSqr(position);
            if (distanceSq > 9.0) {
                return;
            }
            Vec3 delta = new Vec3(player.getX(), player.getY(), player.getZ()).subtract(position);
            double horizontal = Math.hypot(delta.x, delta.z);
            if (horizontal < 1e-4) {
                return;
            }
            Vec3 push = new Vec3(delta.x / horizontal, 0, delta.z / horizontal).scale(1.5);
            player.setDeltaMovement(push.x, 0.5, push.z);
            player.fallDistance = 0.0f;
        }
    }

    /**
     * Represents a block that will respawn after a delay
     */
    private static class RespawningBlock {
        private final BlockPos pos;
        private final BlockState state;
        private final long respawnTime;

        private RespawningBlock(BlockPos pos, BlockState state, long respawnTime) {
            this.pos = pos;
            this.state = state;
            this.respawnTime = respawnTime;
        }

        private boolean shouldRespawn(long currentTime) {
            return currentTime >= respawnTime;
        }

        private void respawn(ClientLevel world) {
            // Set block state with flags 3 (update neighbors + send to client)
            world.setBlock(pos, state, 3);
        }
    }

    // Getters/Setters for new features
    public int getTerminalPingMs() {
        return terminalPingMs;
    }

    public void setTerminalPingMs(int pingMs) {
        this.terminalPingMs = Math.max(0, Math.min(250, pingMs));
    }

    public boolean isBlockRespawnEnabled() {
        return blockRespawnEnabled;
    }

    public void setBlockRespawnEnabled(boolean enabled) {
        this.blockRespawnEnabled = enabled;
        if (!enabled) {
            scheduledRespawns.clear();
            respawnQueue.clear();
            trackedRespawnPositions.clear();
            respawnAccumulatorMs = 0L;
        }
    }

    public int getBlockRespawnDelayMs() {
        return blockRespawnDelayMs;
    }

    public void setBlockRespawnDelayMs(int delayMs) {
        this.blockRespawnDelayMs = Math.max(100, Math.min(10000, delayMs));
    }

    public boolean isClientsideBlockPlacingEnabled() {
        return clientsideBlockPlacingEnabled;
    }

    public void setClientsideBlockPlacingEnabled(boolean enabled) {
        this.clientsideBlockPlacingEnabled = enabled;
    }

    /**
     * Mark a position as pending ghost block placement
     * This prevents serverside placement
     */
    public void markGhostBlockPending(BlockPos pos) {
        pendingGhostBlocks.add(pos.immutable());
    }

    /**
     * Check if a position is being placed as a ghost block
     */
    public boolean isGhostBlockPosition(BlockPos pos) {
        return pendingGhostBlocks.contains(pos);
    }

    /**
     * Check if there's currently a ghost block at this position (for lava bounce detection)
     */
    private boolean isGhostBlockAtPosition(BlockPos pos) {
        // Check if this position has an active ghost block
        for (GhostBlock ghost : ghostBlocks) {
            if (ghost.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there's currently a ghost chest at this position (for chest bounce detection)
     */
    private boolean isGhostChestAtPosition(BlockPos pos, Minecraft mc) {
        if (mc.level == null) return false;

        // First check if there's a clientside placed chest
        BlockState currentState = mc.level.getBlockState(pos);
        if (currentState.is(Blocks.CHEST) || currentState.is(Blocks.TRAPPED_CHEST)) {
            // Check if this is a ghost block (will disappear soon) or permanent
            // Ghost blocks exist in ghostBlocks list OR pending list
            for (GhostBlock ghost : ghostBlocks) {
                if (ghost.pos.equals(pos)) {
                    return true; // It's a ghost chest
                }
            }
            // Also check pending ghost blocks - they're being placed right now
            if (pendingGhostBlocks.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Schedule a ghost block for removal after ping delay (Hypixel simulation)
     */
    public void scheduleGhostBlockRemoval(BlockPos pos, BlockState originalState) {
        long removeTime = System.currentTimeMillis() + ghostBlockPingMs;
        ghostBlocks.add(new GhostBlock(pos, originalState, removeTime));
        // Remove from pending set once scheduled
        pendingGhostBlocks.remove(pos);
    }

    /**
     * Update ghost blocks - remove blocks after ping delay and restore original state
     */
    private void updateGhostBlocks(Minecraft mc) {
        if (ghostBlocks.isEmpty() || mc.level == null) return;

        long now = System.currentTimeMillis();
        ghostBlocks.removeIf(ghost -> {
            if (now >= ghost.removeTime) {
                // Restore original state (e.g. lava) in ClientWorld chunk only
                if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel clientWorld) {
                    net.minecraft.world.level.chunk.LevelChunk chunk = clientWorld.getChunkAt(ghost.pos);
                    if (chunk != null) {
                        BlockState currentGhostState = chunk.getBlockState(ghost.pos);
                        // Set original state back directly in chunk
                        chunk.setBlockState(ghost.pos, ghost.originalState, 0);
                        // Mark for re-render
                        clientWorld.setBlocksDirty(ghost.pos, currentGhostState, ghost.originalState);
                    }
                }
                return true; // Remove from list
            }
            return false; // Keep in list
        });
    }

    public int getGhostBlockPingMs() {
        return ghostBlockPingMs;
    }

    public void setGhostBlockPingMs(int pingMs) {
        this.ghostBlockPingMs = Math.max(0, Math.min(500, pingMs)); // 0-500ms range
    }

    /**
     * Called from ClientPlayerInteractionManagerMixin when a block is broken
     * Schedules the block to respawn after the configured delay
     */
    public void onBlockBroken(BlockPos pos, BlockState state) {
        if (!blockRespawnEnabled || !isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isLocalServer() || mc.player == null) {
            return;
        }

        // Only respawn if player is in creative mode and holding a pickaxe
        if (!mc.player.getAbilities().instabuild) {
            return;
        }

        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.isEmpty() || !isPickaxe(mainHand)) {
            return;
        }

        // Check if this position is already scheduled to respawn
        if (!trackedRespawnPositions.add(pos)) {
            return; // Already tracked
        }

        if (!consumePickaxeCharge()) {
            trackedRespawnPositions.remove(pos);
            return;
        }

        // Schedule block respawn
        long respawnTime = System.currentTimeMillis() + blockRespawnDelayMs;
        scheduledRespawns.add(new RespawningBlock(pos, state, respawnTime));
    }

    /**
     * Called from BossBlockMiner or other modules when a block is packet-mined
     * (without using attackBlock/breakBlock - pure packet mining)
     * Schedules the block to respawn after the configured delay
     */
    public void onPacketBlockMined(BlockPos pos, BlockState state) {
        if (!blockRespawnEnabled || !isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isLocalServer() || mc.player == null) {
            return;
        }

        // Check if this position is already scheduled to respawn
        if (!trackedRespawnPositions.add(pos)) {
            return; // Already tracked
        }

        // Schedule block respawn (no pickaxe charge consumption for packet mining)
        long respawnTime = System.currentTimeMillis() + blockRespawnDelayMs;
        scheduledRespawns.add(new RespawningBlock(pos, state, respawnTime));
    }

    private void respawnBlock(Minecraft mc, ClientLevel clientWorld, RespawningBlock block) {
        // Always restore client copy
        clientWorld.setBlock(block.pos, block.state, Block.UPDATE_ALL);

        // Play block place sound for satisfying audio feedback
        if (mc.player != null) {
            // Get the sound from the block's sound group (same sound as when placing)
            net.minecraft.sounds.SoundEvent placeSound = block.state.getSoundType().getPlaceSound();
            clientWorld.playSound(
                mc.player,
                block.pos,
                placeSound,
                SoundSource.BLOCKS,
                0.5f, // Volume (0.5 = quieter, not overwhelming)
                1.0f  // Pitch (1.0 = normal)
            );

            // Add particle effect for visual feedback (like block placing)
            clientWorld.addParticle(
                ParticleTypes.POOF,
                block.pos.getX() + 0.5,
                block.pos.getY() + 0.5,
                block.pos.getZ() + 0.5,
                0.0, 0.05, 0.0 // Small upward velocity
            );
        }

        // Only schedule server updates in singleplayer (F7 Sim never runs in multiplayer)
        if (mc.getSingleplayerServer() != null && mc.isLocalServer()) {
            ServerLevel serverWorld = mc.getSingleplayerServer().getLevel(clientWorld.dimension());
            if (serverWorld != null) {
                serverWorld.setBlock(block.pos, block.state, Block.UPDATE_ALL);
            }
        }
    }

    private boolean isPickaxe(ItemStack stack) {
        return stack.is(Items.WOODEN_PICKAXE) ||
               stack.is(Items.STONE_PICKAXE) ||
               stack.is(Items.IRON_PICKAXE) ||
               stack.is(Items.GOLDEN_PICKAXE) ||
               stack.is(Items.DIAMOND_PICKAXE) ||
               stack.is(Items.NETHERITE_PICKAXE);
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Player Speed
        settings.add(new SliderSetting(
            "Player Speed",
            "Simulated movement speed (F7 default: 500)",
            "f7sim_speed",
            100f, 1000f,
            () -> (float) playerSpeed,
            val -> playerSpeed = (int) val.floatValue()
        ).withDecimals(0));

        // Speed Simulation
        settings.add(new CheckboxSetting(
            "Speed Simulation",
            "Enable speed simulation",
            "f7sim_speed_enabled",
            () -> speedSimulationEnabled,
            val -> speedSimulationEnabled = val
        ));

        // Lava Bounce
        settings.add(new CheckboxSetting(
            "Lava Bounce",
            "Simulate lava bouncing mechanics",
            "f7sim_lava_bounce",
            () -> lavaBounceSimulation,
            val -> lavaBounceSimulation = val
        ));

        // Device Simulator
        settings.add(new CheckboxSetting(
            "Device Simulator",
            "Simulate first device puzzle",
            "f7sim_device",
            () -> deviceSimEnabled,
            val -> deviceSimEnabled = val
        ));

        // Etherwarp Simulation
        settings.add(new CheckboxSetting(
            "Etherwarp Simulation",
            "Allow etherwarp in simulation",
            "f7sim_etherwarp",
            () -> etherwarpSimulation,
            val -> etherwarpSimulation = val
        ));

        // Bonzo Simulation
        settings.add(new CheckboxSetting(
            "Bonzo Simulation",
            "Simulate Bonzo Staff mechanics",
            "f7sim_bonzo",
            () -> bonzoSimulation,
            val -> bonzoSimulation = val
        ));

        // Bonzo Ping (only visible when Bonzo simulation is enabled)
        settings.add(new SliderSetting(
            "Bonzo Ping",
            "Ping delay for Bonzo Staff",
            "f7sim_bonzo_ping",
            0f, 200f,
            () -> (float) bonzoPingMs,
            val -> bonzoPingMs = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> bonzoSimulation));

        // Bonzo Extra Delay (only visible when Bonzo simulation is enabled)
        settings.add(new SliderSetting(
            "Bonzo Extra Delay",
            "Additional delay for Bonzo Staff",
            "f7sim_bonzo_extra_delay",
            0f, 200f,
            () -> (float) bonzoExtraDelayMs,
            val -> bonzoExtraDelayMs = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> bonzoSimulation));

        // Terminator Mode
        settings.add(new CheckboxSetting(
            "Terminator Mode",
            "Enable Terminator bow (3 arrows + instant shot)",
            "f7sim_terminator",
            () -> terminatorMode,
            val -> terminatorMode = val
        ));

        // Terminator Fire Delay (only visible when Terminator mode is enabled)
        settings.add(new SliderSetting(
            "Terminator Fire Delay",
            "Delay between terminator shots",
            "f7sim_terminator_delay",
            50f, 5000f,
            () -> (float) terminatorFireDelayMs,
            val -> terminatorFireDelayMs = (long) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> terminatorMode));

        // Terminal Simulation
        settings.add(new CheckboxSetting(
            "Terminal Simulation",
            "Enable terminal GUI simulation",
            "f7sim_terminal",
            () -> terminalSimEnabled,
            val -> terminalSimEnabled = val
        ));

        // Terminal Ping (only visible when Terminal simulation is enabled)
        settings.add(new SliderSetting(
            "Terminal Ping",
            "Ping delay for terminal GUIs",
            "f7sim_terminal_ping",
            0f, 250f,
            () -> (float) terminalPingMs,
            val -> terminalPingMs = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> terminalSimEnabled));

        // Block Respawn
        settings.add(new CheckboxSetting(
            "Block Respawn",
            "Enable automatic block respawn",
            "f7sim_block_respawn",
            () -> blockRespawnEnabled,
            val -> blockRespawnEnabled = val
        ));

        // Block Respawn Delay (only visible when Block respawn is enabled)
        settings.add(new SliderSetting(
            "Block Respawn Delay",
            "Delay before blocks respawn",
            "f7sim_block_respawn_delay",
            1000f, 30000f,
            () -> (float) blockRespawnDelayMs,
            val -> blockRespawnDelayMs = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> blockRespawnEnabled));

        // Clientside Block Placing
        settings.add(new CheckboxSetting(
            "Clientside Block Placing",
            "Place blocks clientside only (desynced) for parkour practice",
            "f7sim_clientside_placing",
            () -> clientsideBlockPlacingEnabled,
            val -> clientsideBlockPlacingEnabled = val
        ));

        // Ghost Block Ping (only visible when Clientside Block Placing is enabled)
        settings.add(new SliderSetting(
            "Ghost Block Ping",
            "Ping delay before ghost blocks disappear (Hypixel simulation)",
            "f7sim_ghost_block_ping",
            0f, 500f,
            () -> (float) ghostBlockPingMs,
            val -> ghostBlockPingMs = (int) val.floatValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> clientsideBlockPlacingEnabled));

        return settings;
    }
}
