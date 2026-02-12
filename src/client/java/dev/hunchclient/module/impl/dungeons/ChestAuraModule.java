package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ChestAura - Simple keybind-based version
 * - Press keybind: Swap to chest (client-side, no packet)
 * - Hold keybind: Stay on chest, auto-place
 * - Release keybind: Swap back (client-side, no packet)
 *
 * CGA-COMPLIANT:
 * - Client-side slot swap only (no UpdateSelectedSlotC2SPacket)
 * - recentlySwapped guard (max 1 swap per tick)
 * - Reset at tick end
 * - Simple interactBlock() for placement (like old working version)
 */
public class ChestAuraModule extends Module implements ConfigurableModule, SettingsProvider {
    private final Minecraft mc = Minecraft.getInstance();

    // Settings
    private boolean debugMessages = false;
    private boolean allowSoulSand = false;
    private double maxPlacementDistance = 1.8;
    private double triggerDistance = 1.2;
    private int simulateAheadTicks = 8;
    private double minFallSpeed = 0.15;

    // State
    private long lastPlacementTime = 0L;
    private static final long PLACEMENT_COOLDOWN_MS = 1000L;

    // CGA-style swap tracking
    private boolean recentlySwapped = false;
    private int lastSwapTick = -1;
    private int currentTick = 0;
    private int originalSlot = -1;
    private boolean wasKeyPressed = false;

    private static KeyMapping activationKey;

    public ChestAuraModule() {
        super("ChestAura", "Auto-places chest for F7 lava high bounces", Category.DUNGEONS, RiskLevel.RISKY);
        if (activationKey == null) {
            activationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hunchclient.chestaura",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT,
                dev.hunchclient.HunchModClient.KEYBIND_CATEGORY
            ));
        }
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tickHandler);
        debugMessage("§a[ChestAura] Enabled! Press keybind to swap to chest and auto-place.");
    }

    @Override
    protected void onDisable() {
        // Restore slot if still swapped
        if (originalSlot != -1) {
            restoreOriginalSlot();
        }
        lastPlacementTime = 0L;
        wasKeyPressed = false;
        debugMessage("§c[ChestAura] Disabled!");
    }

    private void tickHandler(Minecraft client) {
        if (!isEnabled()) return;
        if (client.player == null || client.level == null) return;

        currentTick++;

        // CGA-style: Reset swap guard at tick end
        if (lastSwapTick < currentTick) {
            recentlySwapped = false;
        }

        boolean keyPressed = activationKey != null && activationKey.isDown();

        // KEY JUST PRESSED: Swap to chest
        if (keyPressed && !wasKeyPressed) {
            onKeyPress();
        }
        // KEY JUST RELEASED: Swap back
        else if (!keyPressed && wasKeyPressed) {
            onKeyRelease();
        }

        wasKeyPressed = keyPressed;

        // Auto-placement logic (runs while key is held)
        if (keyPressed) {
            tryPlacement(client);
        }
    }

    private void onKeyPress() {
        if (mc.player == null) return;

        int blockSlot = findPlaceableBlockInHotbar();
        if (blockSlot == -1) {
            String blockType = allowSoulSand ? "chest/soul sand" : "chest";
            debugMessage("§c[ChestAura] No " + blockType + " in hotbar!");
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();

        // CGA-style: Check if already holding block
        if (originalSlot == blockSlot) {
            debugMessage("§a[ChestAura] Already holding block!");
            originalSlot = -1; // No need to swap back
            return;
        }

        // CGA-style: Client-side swap (no packet!)
        swapToSlot(blockSlot);
        debugMessage("§a[ChestAura] Swapped to block (slot " + blockSlot + ")");
    }

    private void onKeyRelease() {
        if (originalSlot != -1) {
            restoreOriginalSlot();
            debugMessage("§a[ChestAura] Restored to original slot");
        }
    }

    private void tryPlacement(Minecraft client) {
        // Cooldown check
        long now = System.currentTimeMillis();
        if (now - lastPlacementTime < PLACEMENT_COOLDOWN_MS) {
            return;
        }

        // Must be falling
        Vec3 velocity = client.player.getDeltaMovement();
        double vy = velocity.y;
        if (vy >= -minFallSpeed) {
            return;
        }

        // Find surface below
        double playerX = client.player.getX();
        double playerY = client.player.getBoundingBox().minY;
        double playerZ = client.player.getZ();

        Target target = computeSurfaceTargetAt(playerX, playerY, playerZ);
        if (target == null) return;

        double distanceToSurface = playerY - target.surfaceTopY;

        // Dynamic distance based on fall speed (fixes early placement on low jumps)
        double dynamicMaxDistance = Math.max(0.8, Math.min(1.8, 0.8 + Math.abs(vy) * 2.0));
        if (distanceToSurface > dynamicMaxDistance) {
            return;
        }

        // Predict impact
        int ticksToImpact = predictTicksToContact(playerY, vy, target.surfaceTopY, simulateAheadTicks);

        if (ticksToImpact < 0) {
            if (distanceToSurface > triggerDistance) {
                return;
            }
        } else if (ticksToImpact > simulateAheadTicks) {
            return;
        }

        // PLACE NOW!
        placeChest(target.placePos);
        lastPlacementTime = now;

        if (debugMessages) {
            String surfaceType = target.isLavaSurface ? "§c[LAVA]" : "§7[SOLID]";
            debugMessage(String.format("§a[ChestAura] %s PLACED! tImpact=%d vy=%.2f dist=%.2f",
                surfaceType, ticksToImpact, vy, distanceToSurface));
        }
    }

    /**
     * SIMPLE placement (like old working version that didn't ban)
     * - NO extra swing (interactBlock does it automatically)
     * - NO complex BlockHitResult logic
     * - Just like the decompiled working version!
     */
    private void placeChest(BlockPos pos) {
        if (mc.level == null || mc.player == null || mc.gameMode == null) {
            return;
        }

        // Check block is replaceable
        BlockState currentState = mc.level.getBlockState(pos);
        if (!currentState.canBeReplaced() && !currentState.is(Blocks.LAVA)) {
            return;
        }

        // Check block in hand (chest or soul sand)
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();

        boolean hasValidBlock = mainHand.is(Items.CHEST) || offHand.is(Items.CHEST);
        if (allowSoulSand) {
            hasValidBlock = hasValidBlock || mainHand.is(Items.SOUL_SAND) || offHand.is(Items.SOUL_SAND);
        }

        if (!hasValidBlock) {
            String blockType = allowSoulSand ? "chest/soul sand" : "chest";
            debugMessage("§c[ChestAura] No " + blockType + " in hand!");
            return;
        }

        InteractionHand hand = (mainHand.is(Items.CHEST) || mainHand.is(Items.SOUL_SAND)) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;


        // SIMPLE: Just like old working version!
        // Click on block UNDER chest position (Direction.UP = top face)
        BlockPos targetBlock = pos.below();
        Vec3 hitPos = new Vec3(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 0.999,
            targetBlock.getZ() + 0.5
        );
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, targetBlock, false);

        // NO manual swing - interactBlock does it automatically!
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hitResult);

        if (debugMessages && result == InteractionResult.FAIL) {
            debugMessage("§c[ChestAura] Placement failed (result: " + result + ")");
        }
    }

    /**
     * CGA-STYLE: Client-side swap with guard
     */
    private void swapToSlot(int slot) {
        if (mc.player == null) return;
        if (slot < 0 || slot > 8) return;

        // CGA-style swap guard: max 1 per tick
        if (recentlySwapped) {
            debugMessage("§c[ChestAura] Swap blocked (max 1 per tick)");
            return;
        }

        // CLIENT-SIDE ONLY - NO PACKET!
        // Vanilla client sends UpdateSelectedSlotC2SPacket automatically when needed
        mc.player.getInventory().setSelectedSlot(slot);

        recentlySwapped = true;
        lastSwapTick = currentTick;
    }

    /**
     * CGA-STYLE: Restore original slot
     */
    private void restoreOriginalSlot() {
        if (mc.player == null) return;
        if (originalSlot < 0 || originalSlot > 8) return;

        // CGA-style swap guard: max 1 per tick
        if (recentlySwapped) {
            debugMessage("§c[ChestAura] Restore blocked (max 1 per tick)");
            // Will retry next tick automatically when key is released again
            return;
        }

        // CLIENT-SIDE ONLY - NO PACKET!
        mc.player.getInventory().setSelectedSlot(originalSlot);

        recentlySwapped = true;
        lastSwapTick = currentTick;
        originalSlot = -1;
    }

    // === HELPER METHODS (unchanged from old working version) ===

    private Target computeSurfaceTargetAt(double x, double y, double z) {
        BlockPos.MutableBlockPos checkPos = BlockPos.containing(x, y, z).mutable();

        for (int i = 0; i < 16; i++) {
            checkPos.move(Direction.DOWN);
            BlockState state = mc.level.getBlockState(checkPos);

            // LAVA: Find solid block under it
            if (state.is(Blocks.LAVA)) {
                BlockPos.MutableBlockPos solidPos = checkPos.mutable();
                for (int depth = 1; depth <= 6; depth++) {
                    solidPos.move(Direction.DOWN);
                    BlockState solidState = mc.level.getBlockState(solidPos);

                    if (!solidState.is(Blocks.LAVA) &&
                        !solidState.isAir() &&
                        !solidState.getCollisionShape(mc.level, solidPos).isEmpty() &&
                        solidState.isCollisionShapeFullBlock(mc.level, solidPos)) {

                        double chestTopY = checkPos.getY() + 0.875;
                        return new Target(checkPos.immutable(), chestTopY, true);
                    }
                }
                continue;
            }

            // SOLID BLOCK
            if (!state.isAir() &&
                !state.getCollisionShape(mc.level, checkPos).isEmpty() &&
                state.isCollisionShapeFullBlock(mc.level, checkPos)) {

                BlockPos abovePos = checkPos.above();
                BlockState aboveState = mc.level.getBlockState(abovePos);

                if (!aboveState.canBeReplaced() && !aboveState.is(Blocks.LAVA)) {
                    continue;
                }

                double chestTopY = checkPos.getY() + 1.0 + 0.875;
                return new Target(checkPos.above().immutable(), chestTopY, false);
            }
        }

        return null;
    }

    private int predictTicksToContact(double feetY, double vy, double surfaceTopY, int maxTicks) {
        double f = feetY;
        double v = vy;
        for (int k = 1; k <= maxTicks; k++) {
            v = (v - 0.08) * 0.98;
            f += v;
            if (f <= surfaceTopY + 1e-6) return k;
        }
        return -1;
    }

    private boolean hasChestInHand(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.is(Items.CHEST) || offHand.is(Items.CHEST);
    }

    /**
     * Finds chest or soul sand (if enabled) in hotbar
     */
    private int findPlaceableBlockInHotbar() {
        if (mc.player == null) return -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack != null) {
                if (stack.is(Items.CHEST)) {
                    return slot;
                }
                if (allowSoulSand && stack.is(Items.SOUL_SAND)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private int findChestInHotbar() {
        return findPlaceableBlockInHotbar();
    }

    private void debugMessage(String msg) {
        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    @Override
    public void onTick() {}

    // === CONFIG ===

    public boolean isDebugMessages() { return debugMessages; }
    public void setDebugMessages(boolean enabled) { this.debugMessages = enabled; }

    public boolean isAllowSoulSand() { return allowSoulSand; }
    public void setAllowSoulSand(boolean enabled) { this.allowSoulSand = enabled; }

    public double getMaxPlacementDistance() { return maxPlacementDistance; }
    public void setMaxPlacementDistance(double distance) {
        this.maxPlacementDistance = Math.max(2.0, Math.min(10.0, distance));
    }

    public double getTriggerDistance() { return triggerDistance; }
    public void setTriggerDistance(double distance) {
        this.triggerDistance = Math.max(0.5, Math.min(5.0, distance));
    }

    public int getSimulateAheadTicks() { return simulateAheadTicks; }
    public void setSimulateAheadTicks(int v) {
        this.simulateAheadTicks = Math.max(1, Math.min(12, v));
    }

    public double getMinFallSpeed() { return minFallSpeed; }
    public void setMinFallSpeed(double v) {
        this.minFallSpeed = Math.max(0.0, Math.min(0.5, v));
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("debugMessages", debugMessages);
        data.addProperty("allowSoulSand", allowSoulSand);
        data.addProperty("maxPlacementDistance", maxPlacementDistance);
        data.addProperty("triggerDistance", triggerDistance);
        data.addProperty("simulateAheadTicks", simulateAheadTicks);
        data.addProperty("minFallSpeed", minFallSpeed);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("debugMessages")) setDebugMessages(data.get("debugMessages").getAsBoolean());
        if (data.has("allowSoulSand")) setAllowSoulSand(data.get("allowSoulSand").getAsBoolean());
        if (data.has("maxPlacementDistance")) setMaxPlacementDistance(data.get("maxPlacementDistance").getAsDouble());
        if (data.has("triggerDistance")) setTriggerDistance(data.get("triggerDistance").getAsDouble());
        if (data.has("simulateAheadTicks")) setSimulateAheadTicks(data.get("simulateAheadTicks").getAsInt());
        if (data.has("minFallSpeed")) setMinFallSpeed(data.get("minFallSpeed").getAsDouble());
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Debug Messages
        settings.add(new CheckboxSetting(
            "Debug Messages",
            "Show debug messages in chat",
            "chestaura_debug",
            () -> debugMessages,
            val -> debugMessages = val
        ));

        // Allow Soul Sand
        settings.add(new CheckboxSetting(
            "Allow Soul Sand",
            "Also use soul sand for even higher bounces",
            "chestaura_soulsand",
            () -> allowSoulSand,
            val -> allowSoulSand = val
        ));

        // Trigger Distance
        settings.add(new SliderSetting(
            "Trigger Distance",
            "Distance from surface to trigger placement",
            "chestaura_trigger_distance",
            0.5f, 5.0f,
            () -> (float) triggerDistance,
            val -> triggerDistance = val
        ).withDecimals(1).withSuffix("m"));

        // Simulate Ahead Ticks
        settings.add(new SliderSetting(
            "Simulate Ahead",
            "Physics simulation lookahead ticks",
            "chestaura_simulate_ahead",
            1f, 12f,
            () -> (float) simulateAheadTicks,
            val -> simulateAheadTicks = (int) val.floatValue()
        ).withDecimals(0).withSuffix(" ticks"));

        // Min Fall Speed
        settings.add(new SliderSetting(
            "Min Fall Speed",
            "Minimum falling speed to activate",
            "chestaura_min_fall_speed",
            0f, 0.5f,
            () -> (float) minFallSpeed,
            val -> minFallSpeed = val
        ).withDecimals(2));

        return settings;
    }

    private static class Target {
        final BlockPos placePos;
        final double surfaceTopY;
        final boolean isLavaSurface;

        Target(BlockPos placePos, double surfaceTopY, boolean isLavaSurface) {
            this.placePos = placePos;
            this.surfaceTopY = surfaceTopY;
            this.isLavaSurface = isLavaSurface;
        }
    }
}
