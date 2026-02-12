package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.util.RaytraceUtils;
import dev.hunchclient.util.SwapManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
// block imports below
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Secret Aura - Port of CGA's SecretAura to 1.21.10
 * Automatically clicks secrets and levers in range (does NOT require looking at them)
 *
 * IMPORTANT: This uses custom raycasting and sends packets directly!
 * Much more aggressive than SecretTriggerbot.
 */
public class SecretAuraModule extends Module implements ConfigurableModule, SettingsProvider {
    private final Minecraft mc = Minecraft.getInstance();

    // Settings (from CGA)
    private double auraRange = 6.2;
    private double auraSkullRange = 4.7;
    private boolean swing = false;
    private boolean autoClose = false;
    private boolean onlyInDungeons = true;

    // Auto-Swap settings
    private boolean autoSwap = false;
    private int swapSlot = 1; // 1-9 (hotbar slot)
    private boolean swapBack = true;

    // Tracking (from CGA)
    private final List<BlockPos> blocksDone = new LinkedList<>();
    private final Map<BlockPos, Long> blocksCooldown = new HashMap<>();
    private boolean redstoneKey = false;

    // Tick safety
    private boolean actionThisTick = false;

    // SwapManager instance
    private final SwapManager swapManager = new SwapManager();

    // Swap timing (anti-cheat delays)
    private int ticksSinceSwap = 0;      // Ticks since we swapped to target slot
    private int ticksSinceClick = 0;     // Ticks since we clicked a secret
    private boolean waitingForClickDelay = false;  // Are we waiting to click after swap?
    private boolean waitingForSwapBackDelay = false; // Are we waiting to swap back after click?
    private boolean waitingForChestClose = false;  // Are we waiting for chest GUI to close?
    private boolean chestWasOpen = false;          // Was the chest GUI open at some point?

    private static final int SWAP_TO_CLICK_DELAY = 3;  // 3 ticks after swap before click
    private static final int CLICK_TO_SWAPBACK_DELAY = 5; // 5 ticks after click before swap back

    // Secret skull UUIDs (from CGA)
    private static final String SECRET_SKULL_UUID = "e0f3e929-869e-3dca-9504-54c666ee6f23";
    private static final String REDSTONE_KEY_UUID = "fed95410-aba1-39df-9b95-1d4f361eb66e";

    public SecretAuraModule() {
        super("SecretAura", "Auto-clicks secrets in range (CGA port)", Category.DUNGEONS, RiskLevel.VERY_RISKY);
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTickEnd);
        blocksDone.clear();
        blocksCooldown.clear();
        redstoneKey = false;
        swapManager.reset();
    }

    @Override
    protected void onDisable() {
        blocksDone.clear();
        blocksCooldown.clear();
        redstoneKey = false;
        actionThisTick = false;

        // Reset swap timing
        ticksSinceSwap = 0;
        ticksSinceClick = 0;
        waitingForClickDelay = false;
        waitingForSwapBackDelay = false;
        waitingForChestClose = false;
        chestWasOpen = false;

        // Restore original slot if swapped
        swapManager.forceRestore();
        swapManager.reset();
    }

    /**
     * START of tick: Reset action guard and update SwapManager
     */
    private void onTickStart(Minecraft client) {
        if (!isEnabled()) return;

        // Update SwapManager tick
        swapManager.tick();
        actionThisTick = false;

        // Increment delay counters
        if (waitingForClickDelay) ticksSinceSwap++;
        if (waitingForSwapBackDelay) ticksSinceClick++;

        if (client.player == null || client.level == null) return;

        // Check dungeon requirement
        if (onlyInDungeons && !dev.hunchclient.util.DungeonUtils.isInDungeon()) {
            return;
        }

        // CGA-style: Max 1 action per tick
        if (actionThisTick) {
            return;
        }

        // Get eye position
        Vec3 eyePos = client.player.getEyePosition();

        // Calculate scan box (CGA: BlockPos.getAllInBox)
        BlockPos scanMin = BlockPos.containing(
            eyePos.x - auraRange,
            eyePos.y - auraRange,
            eyePos.z - auraRange
        );
        BlockPos scanMax = BlockPos.containing(
            eyePos.x + auraRange,
            eyePos.y + auraRange,
            eyePos.z + auraRange
        );

        long currentTime = System.currentTimeMillis();

        // Scan all blocks in range (CGA: for (block in blocks))
        for (BlockPos blockPos : BlockPos.betweenClosed(scanMin, scanMax)) {
            // Skip if already done
            if (blocksDone.contains(blockPos)) continue;

            // Skip if on cooldown (CGA: 500ms cooldown)
            Long cooldown = blocksCooldown.get(blockPos);
            if (cooldown != null && cooldown + 500 > currentTime) continue;

            BlockState blockState = client.level.getBlockState(blockPos);
            Block block = blockState.getBlock();

            // === CHEST SECRETS ===
            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
                if (handleChestSecret(blockPos, eyePos, currentTime)) {
                    return; // CGA: return after one action per tick
                }
            }
            // === LEVER SECRETS ===
            else if (block == Blocks.LEVER) {
                if (handleLeverSecret(blockPos, blockState, eyePos, currentTime)) {
                    return;
                }
            }
            // === SKULL SECRETS ===
            else if (block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD) {
                if (handleSkullSecret(blockPos, blockState, eyePos, currentTime)) {
                    return;
                }
            }
            // === REDSTONE KEY SECRETS ===
            else if (block == Blocks.REDSTONE_BLOCK) {
                if (handleRedstoneKeySecret(blockPos, eyePos, currentTime)) {
                    return;
                }
            }
        }
    }

    /**
     * END of tick: Swap back to original slot if needed (with delay)
     */
    private void onTickEnd(Minecraft client) {
        if (!isEnabled()) return;
        if (client.player == null) return;

        // Track chest GUI state for proper swap-back timing
        if (waitingForChestClose) {
            if (client.screen != null) {
                // GUI is open - mark that we saw it
                chestWasOpen = true;
                return; // Don't swap while GUI is open
            } else if (chestWasOpen) {
                // GUI was open and now closed - chest interaction complete!
                waitingForChestClose = false;
                chestWasOpen = false;
                // Now we can proceed with swap-back logic
            } else {
                // Still waiting for GUI to appear
                return;
            }
        }

        // Don't swap back while a screen is open (unless autoClose is enabled)
        if (client.screen != null && !autoClose) {
            return; // Wait until screen is closed
        }

        // Check if we should swap back
        if (swapBack && swapManager.isSwapped() && swapManager.canSwap()) {
            // DON'T swap back if we're still waiting to click!
            if (waitingForClickDelay) {
                return; // Still waiting for swap-to-click delay, don't swap back yet
            }

            // If waiting for swap-back delay, check if enough ticks have passed
            if (waitingForSwapBackDelay) {
                if (ticksSinceClick >= CLICK_TO_SWAPBACK_DELAY) {
                    // Delay passed - swap back!
                    swapManager.restoreOriginalSlot();
                    waitingForSwapBackDelay = false;
                    ticksSinceClick = 0;
                }
                // Otherwise keep waiting
            }
            // If not waiting for any delay but still swapped, something went wrong - don't auto-restore
            // Let the module cycle handle it
        }
    }

    /**
     * Handle chest secrets (CGA lines 78-119)
     */
    private boolean handleChestSecret(BlockPos blockPos, Vec3 eyePos, long currentTime) {
        // CGA: Custom AABB for chest
        AABB aabb = new AABB(0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375);
        Vec3 centerPos = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.4375, blockPos.getZ() + 0.5);

        // CGA: Distance check
        if (eyePos.distanceTo(Vec3.atCenterOf(blockPos)) <= auraRange) {
            // CGA: collisionRayTrace
            BlockHitResult hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos);
            if (hitResult == null) return false;

            // AUTO-SWAP: Swap to desired slot before clicking
            if (!trySwapBeforeClick()) {
                return false; // Wait for next tick to swap
            }

            // CGA: Send C08PacketPlayerBlockPlacement
            sendBlockPlacementPacket(blockPos, hitResult);

            // CGA: Swing if enabled
            if (!mc.player.isShiftKeyDown() && swing) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            // If autoClose is OFF, wait for player to manually close chest before swapping back
            if (!autoClose && autoSwap) {
                waitingForChestClose = true;
                chestWasOpen = false;
            }

            blocksCooldown.put(blockPos, currentTime);
            actionThisTick = true;
            return true;
        }

        return false;
    }

    /**
     * Handle lever secrets (CGA lines 120-166)
     * TODO: Implement proper lever AABB handling
     */
    private boolean handleLeverSecret(BlockPos blockPos, BlockState blockState, Vec3 eyePos, long currentTime) {
        // Simplified: Use standard full-block AABB for now
        AABB aabb = new AABB(0.25, 0.2, 0.25, 0.75, 0.8, 0.75);
        Vec3 centerPos = Vec3.atCenterOf(blockPos);

        if (eyePos.distanceTo(centerPos) <= auraRange) {
            BlockHitResult hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos);
            if (hitResult == null) return false;

            // AUTO-SWAP: Swap to desired slot before clicking
            if (!trySwapBeforeClick()) {
                return false; // Wait for next tick to swap
            }

            sendBlockPlacementPacket(blockPos, hitResult);

            if (!mc.player.isShiftKeyDown() && swing) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            blocksCooldown.put(blockPos, currentTime);
            actionThisTick = true;
            return true;
        }

        return false;
    }

    /**
     * Handle skull secrets (CGA lines 167-224)
     */
    private boolean handleSkullSecret(BlockPos blockPos, BlockState blockState, Vec3 eyePos, long currentTime) {
        // CGA: Validate skull UUID
        BlockEntity blockEntity = mc.level.getBlockEntity(blockPos);
        if (!(blockEntity instanceof SkullBlockEntity skullEntity)) return false;

        ResolvableProfile profileComponent = skullEntity.getOwnerProfile();
        if (profileComponent == null) return false;

        var gameProfile = profileComponent.partialProfile();
        if (gameProfile == null || gameProfile.id() == null) return false;

        String uuid = gameProfile.id().toString();

        // CGA: Check if it's a secret skull or redstone key
        if (!SECRET_SKULL_UUID.equals(uuid)) {
            if (!REDSTONE_KEY_UUID.equals(uuid)) return false;

            // CGA: Redstone key special logic (lines 175-185)
            if (hasRedstoneBlockNearby(blockPos)) {
                redstoneKey = false;
                blocksDone.add(blockPos);
                return false;
            }
        }

        // CGA: Get skull facing and AABB (simplified for now)
        AABB aabb = new AABB(0.25, 0.0, 0.25, 0.75, 0.5, 0.75); // Default skull AABB

        Vec3 centerPos = Vec3.atLowerCornerOf(blockPos).add(
            (aabb.minX + aabb.maxX) / 2,
            (aabb.minY + aabb.maxY) / 2,
            (aabb.minZ + aabb.maxZ) / 2
        );

        if (eyePos.distanceTo(centerPos) <= auraSkullRange) {
            BlockHitResult hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos);
            if (hitResult == null) return false;

            // AUTO-SWAP: Swap to desired slot before clicking
            if (!trySwapBeforeClick()) {
                return false; // Wait for next tick to swap
            }

            sendBlockPlacementPacket(blockPos, hitResult);

            blocksCooldown.put(blockPos, currentTime);
            actionThisTick = true;
            return true;
        }

        return false;
    }

    /**
     * Handle redstone key secrets (CGA lines 225-269)
     */
    private boolean handleRedstoneKeySecret(BlockPos blockPos, Vec3 eyePos, long currentTime) {
        // CGA: Check if redstone key mode active
        if (!redstoneKey) return false;

        // CGA: Dungeon bounds check (lines 227)
        if (mc.player.getX() >= 0 || mc.player.getZ() >= 0 ||
            mc.player.getX() < -200 || mc.player.getZ() < -200) {
            return false;
        }

        // CGA: Check if skull is nearby (lines 229-238)
        if (hasSkullNearby(blockPos)) {
            redstoneKey = false;
            blocksDone.add(blockPos);
            return false;
        }

        AABB aabb = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        Vec3 centerPos = Vec3.atLowerCornerOf(blockPos).add(0.5, 0.5, 0.5);

        if (eyePos.distanceTo(Vec3.atCenterOf(blockPos)) <= auraRange) {
            BlockHitResult hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos);
            if (hitResult == null) return false;

            // CGA: Skip if hitting from below (line 248)
            if (hitResult.getDirection() == Direction.DOWN) return false;

            // AUTO-SWAP: Swap to desired slot before clicking
            if (!trySwapBeforeClick()) {
                return false; // Wait for next tick to swap
            }

            sendBlockPlacementPacket(blockPos, hitResult);

            blocksCooldown.put(blockPos, currentTime);
            actionThisTick = true;
            return true;
        }

        return false;
    }

    /**
     * Try to swap to desired slot before clicking secret
     * Returns true if ready to click (either already on correct slot or swap succeeded + delay passed)
     * Returns false if need to wait
     */
    private boolean trySwapBeforeClick() {
        // If auto-swap disabled, always ready
        if (!autoSwap) return true;

        // Don't swap/click while waiting for chest to close
        if (waitingForChestClose) {
            return false;
        }

        // Don't swap while a screen is open (unless autoClose is enabled)
        if (mc.screen != null && !autoClose) {
            return false; // Wait until screen is closed
        }

        // Convert 1-9 to 0-8 (hotbar slot index)
        int targetSlot = Math.max(0, Math.min(8, swapSlot - 1));

        // If already on correct slot and not waiting for swap delay, ready to click
        if (mc.player.getInventory().getSelectedSlot() == targetSlot) {
            // Check if we're waiting for the swap delay
            if (waitingForClickDelay) {
                if (ticksSinceSwap >= SWAP_TO_CLICK_DELAY) {
                    // Delay passed - ready to click!
                    waitingForClickDelay = false;
                    ticksSinceSwap = 0;
                    return true;
                } else {
                    // Still waiting for delay
                    return false;
                }
            }
            // Not waiting, already on correct slot
            return true;
        }

        // If already swapped but waiting for delay, continue waiting
        if (swapManager.isSwapped()) {
            return false;
        }

        // If can't swap this tick, wait
        if (!swapManager.canSwap()) return false;

        // Perform swap
        boolean swapped = swapManager.swapToSlot(targetSlot);
        if (swapped) {
            // Swapped - now start the delay counter
            waitingForClickDelay = true;
            ticksSinceSwap = 0;
            return false;
        } else {
            // Swap failed - but we might already be on target slot
            return mc.player.getInventory().getSelectedSlot() == targetSlot;
        }
    }

    /**
     * Send block placement packet (CGA: C08PacketPlayerBlockPlacement)
     * 1.21.10: Raw packet like AutoSS for consistency
     */
    private void sendBlockPlacementPacket(BlockPos blockPos, BlockHitResult hitResult) {
        if (mc.getConnection() == null || mc.player == null) return;

        try {
            // Raw packet like AutoSS and CGA - consistent behavior
            ServerboundUseItemOnPacket packet = new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hitResult,
                0
            );
            mc.getConnection().send(packet);

            // Start swap-back delay counter if auto-swap is enabled
            if (autoSwap && swapBack && swapManager.isSwapped()) {
                waitingForSwapBackDelay = true;
                ticksSinceClick = 0;
            }
        } catch (Exception e) {
            HunchClient.LOGGER.error("[SecretAura] Failed to interact: " + e.getMessage());
        }
    }

    // Lever and skull AABB helpers removed - using simplified AABBs directly in handlers

    // === AUTO CLOSE ===

    private static final Set<MenuType<?>> CHEST_TYPES = Set.of(
        MenuType.GENERIC_9x1,
        MenuType.GENERIC_9x2,
        MenuType.GENERIC_9x3,
        MenuType.GENERIC_9x4,
        MenuType.GENERIC_9x5,
        MenuType.GENERIC_9x6,
        MenuType.GENERIC_3x3
    );

    private static final Set<String> SECRET_CHEST_TITLES = Set.of(
        "chest",
        "large chest"
    );

    /**
     * Called from ClientPlayNetworkHandlerMixin when a screen is about to open.
     * If autoClose is enabled, closes secret chests immediately.
     *
     * @return true if the opening should be cancelled entirely.
     */
    public boolean handleOpenScreen(ClientboundOpenScreenPacket packet) {
        if (!isEnabled() || !autoClose) {
            return false;
        }

        // Check dungeon status
        if (onlyInDungeons && !dev.hunchclient.util.DungeonUtils.isInDungeon()) {
            return false;
        }

        // Check if it's a chest
        MenuType<?> type = packet.getType();
        if (type == null || !CHEST_TYPES.contains(type)) {
            return false;
        }

        // Check if it's a secret chest (title is "Chest" or "Large Chest")
        if (packet.getTitle() == null) {
            return false;
        }
        String normalized = dev.hunchclient.util.DungeonUtils.stripFormatting(
            packet.getTitle().getString()
        ).trim().toLowerCase(Locale.ROOT);

        if (!SECRET_CHEST_TITLES.contains(normalized)) {
            return false;
        }

        // Close the chest immediately!
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(packet.getContainerId()));
            return true;
        }

        return false;
    }

    /**
     * Check if redstone block is nearby (CGA lines 176-181)
     */
    private boolean hasRedstoneBlockNearby(BlockPos pos) {
        return mc.level.getBlockState(pos.below()).getBlock() == Blocks.REDSTONE_BLOCK ||
               mc.level.getBlockState(pos.north()).getBlock() == Blocks.REDSTONE_BLOCK ||
               mc.level.getBlockState(pos.south()).getBlock() == Blocks.REDSTONE_BLOCK ||
               mc.level.getBlockState(pos.west()).getBlock() == Blocks.REDSTONE_BLOCK ||
               mc.level.getBlockState(pos.east()).getBlock() == Blocks.REDSTONE_BLOCK;
    }

    /**
     * Check if skull is nearby (CGA lines 229-234)
     */
    private boolean hasSkullNearby(BlockPos pos) {
        Block up = mc.level.getBlockState(pos.above()).getBlock();
        Block north = mc.level.getBlockState(pos.north()).getBlock();
        Block south = mc.level.getBlockState(pos.south()).getBlock();
        Block west = mc.level.getBlockState(pos.west()).getBlock();
        Block east = mc.level.getBlockState(pos.east()).getBlock();

        return up == Blocks.PLAYER_HEAD || up == Blocks.PLAYER_WALL_HEAD ||
               north == Blocks.PLAYER_HEAD || north == Blocks.PLAYER_WALL_HEAD ||
               south == Blocks.PLAYER_HEAD || south == Blocks.PLAYER_WALL_HEAD ||
               west == Blocks.PLAYER_HEAD || west == Blocks.PLAYER_WALL_HEAD ||
               east == Blocks.PLAYER_HEAD || east == Blocks.PLAYER_WALL_HEAD;
    }

    /**
     * Clear tracked blocks (CGA: clearBlocks())
     */
    public void clearBlocks() {
        blocksDone.clear();
        blocksCooldown.clear();
        HunchClient.LOGGER.info("[SecretAura] Cleared blocks");
    }

    // === CONFIG ===

    public double getAuraRange() { return auraRange; }
    public void setAuraRange(double range) { this.auraRange = Math.max(2.1, Math.min(6.5, range)); }

    public double getAuraSkullRange() { return auraSkullRange; }
    public void setAuraSkullRange(double range) { this.auraSkullRange = Math.max(2.1, Math.min(4.7, range)); }

    public boolean isSwing() { return swing; }
    public void setSwing(boolean enabled) { this.swing = enabled; }

    public boolean isAutoClose() { return autoClose; }
    public void setAutoClose(boolean enabled) { this.autoClose = enabled; }

    public boolean isOnlyInDungeons() { return onlyInDungeons; }
    public void setOnlyInDungeons(boolean enabled) { this.onlyInDungeons = enabled; }

    public boolean isAutoSwap() { return autoSwap; }
    public void setAutoSwap(boolean enabled) { this.autoSwap = enabled; }

    public int getSwapSlot() { return swapSlot; }
    public void setSwapSlot(int slot) { this.swapSlot = Math.max(1, Math.min(9, slot)); }

    public boolean isSwapBack() { return swapBack; }
    public void setSwapBack(boolean enabled) { this.swapBack = enabled; }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("auraRange", auraRange);
        data.addProperty("auraSkullRange", auraSkullRange);
        data.addProperty("swing", swing);
        data.addProperty("autoClose", autoClose);
        data.addProperty("onlyInDungeons", onlyInDungeons);
        data.addProperty("autoSwap", autoSwap);
        data.addProperty("swapSlot", swapSlot);
        data.addProperty("swapBack", swapBack);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("auraRange")) setAuraRange(data.get("auraRange").getAsDouble());
        if (data.has("auraSkullRange")) setAuraSkullRange(data.get("auraSkullRange").getAsDouble());
        if (data.has("swing")) setSwing(data.get("swing").getAsBoolean());
        if (data.has("autoClose")) setAutoClose(data.get("autoClose").getAsBoolean());
        if (data.has("onlyInDungeons")) setOnlyInDungeons(data.get("onlyInDungeons").getAsBoolean());
        if (data.has("autoSwap")) setAutoSwap(data.get("autoSwap").getAsBoolean());
        if (data.has("swapSlot")) setSwapSlot(data.get("swapSlot").getAsInt());
        if (data.has("swapBack")) setSwapBack(data.get("swapBack").getAsBoolean());
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new SliderSetting(
            "Aura Range",
            "Maximum range for clicking secrets",
            "secretaura_range",
            2.1f, 6.5f,
            () -> (float) auraRange,
            val -> auraRange = val
        ).withDecimals(1).withSuffix(" blocks"));

        settings.add(new SliderSetting(
            "Skull Range",
            "Maximum range for clicking skulls",
            "secretaura_skull_range",
            2.1f, 4.7f,
            () -> (float) auraSkullRange,
            val -> auraSkullRange = val
        ).withDecimals(1).withSuffix(" blocks"));

        settings.add(new CheckboxSetting(
            "Swing Hand",
            "Swing hand when clicking secrets",
            "secretaura_swing",
            () -> swing,
            val -> swing = val
        ));

        settings.add(new CheckboxSetting(
            "Auto Close",
            "Auto close chest GUIs",
            "secretaura_autoclose",
            () -> autoClose,
            val -> autoClose = val
        ));

        settings.add(new CheckboxSetting(
            "Only in Dungeons",
            "Only work when in dungeons",
            "secretaura_dungeons_only",
            () -> onlyInDungeons,
            val -> onlyInDungeons = val
        ));

        // Auto-Swap Settings
        settings.add(new CheckboxSetting(
            "Auto Swap",
            "Swap to specific slot before clicking secrets",
            "secretaura_autoswap",
            () -> autoSwap,
            val -> autoSwap = val
        ));

        settings.add(new SliderSetting(
            "Swap Slot",
            "Hotbar slot to swap to (1-9)",
            "secretaura_swap_slot",
            1.0f, 9.0f,
            () -> (float) swapSlot,
            val -> swapSlot = Math.round(val)
        ).withDecimals(0).withSuffix(""));

        settings.add(new CheckboxSetting(
            "Swap Back",
            "Swap back to original slot after clicking",
            "secretaura_swapback",
            () -> swapBack,
            val -> swapBack = val
        ));

        return settings;
    }

    @Override
    public void onTick() {
        // Unused - we use ClientTickEvents instead
    }
}
