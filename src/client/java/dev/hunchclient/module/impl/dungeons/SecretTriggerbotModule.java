package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;
import dev.hunchclient.HunchClient;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Secret Triggerbot - SAFE version that only clicks when you're looking directly at secrets
 *
 * Features:
 * - Only clicks when crosshair is 100% on the secret (no auto-aim!)
 * - Range check (max 6.2 blocks)
 * - Only clicks real secrets (validates chest/skull UUIDs)
 * - CGA-style tick safety (max 1 action per tick)
 *
 * MUCH SAFER than full auto-aura because:
 * - No scanning all blocks
 * - No auto-aim
 * - User must manually aim at the secret
 * - Only triggers when already looking at it
 */
public class SecretTriggerbotModule extends Module implements ConfigurableModule, SettingsProvider {
    private final Minecraft mc = Minecraft.getInstance();

    // Settings
    private boolean debugMessages = false;
    private double maxRange = 6.2; // Max range for triggering (same as CGA)
    private double maxSkullRange = 4.7; // Max range for skulls (same as CGA)
    private boolean onlyInDungeons = true;

    // CGA-style safety: Track actions per tick
    private boolean actionThisTick = false;
    private int ticksSinceKeyPress = 0; // Track ticks since we pressed the key (0 = not pressed)

    // Cooldown tracking (like CGA)
    private final Map<BlockPos, Long> blockCooldowns = new HashMap<>();
    private final Set<BlockPos> clickedBlocks = new HashSet<>();
    private static final long CLICK_COOLDOWN_MS = 500L; // 500ms cooldown per block

    // Track dungeon state for auto-clear
    private boolean wasInDungeon = false;


    // Secret skull UUIDs (from CGA code)
    private static final String SECRET_SKULL_UUID = "e0f3e929-869e-3dca-9504-54c666ee6f23"; // Normal secret skull
    private static final String REDSTONE_KEY_UUID = "fed95410-aba1-39df-9b95-1d4f361eb66e"; // Redstone key skull

    public SecretTriggerbotModule() {
        super("SecretTriggerbot", "Auto-clicks secrets when you look at them", Category.DUNGEONS, RiskLevel.VERY_RISKY);
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTickEnd);
        debugMessage("§a[SecretTriggerbot] Enabled! Look at secrets to auto-click them.");
    }

    @Override
    protected void onDisable() {
        blockCooldowns.clear();
        clickedBlocks.clear();
        actionThisTick = false;

        // Release key if it was pressed
        if (ticksSinceKeyPress > 0 && mc.options != null) {
            mc.options.keyUse.setDown(false);
            ticksSinceKeyPress = 0;
        }

        debugMessage("§c[SecretTriggerbot] Disabled!");
    }

    /**
     * START of tick: Reset action guard, increment key press counter
     */
    private void onTickStart(Minecraft client) {
        if (!isEnabled()) return;

        // Increment counter if key is pressed (so we know how long it's been held)
        if (ticksSinceKeyPress > 0) {
            ticksSinceKeyPress++;
        }

        actionThisTick = false; // CGA-style: Reset action guard at tick start
    }

    /**
     * END of tick: Check what player is looking at and auto-click if it's a secret
     */
    private void onTickEnd(Minecraft client) {
        if (!isEnabled()) return;
        if (client.player == null || client.level == null) return;

        // Release key after it's been held for a full tick (tick 2+)
        // Flow: Tick N end: press (counter=1) -> Tick N+1 start: counter=2 -> Tick N+1 end: release
        if (ticksSinceKeyPress >= 2 && mc.options != null) {
            mc.options.keyUse.setDown(false);
            ticksSinceKeyPress = 0;
        }

        // Auto-clear when leaving dungeons (like CGA's world load clear)
        boolean inDungeon = dev.hunchclient.util.DungeonUtils.isInDungeon();
        if (wasInDungeon && !inDungeon) {
            // Just left dungeon - clear clicked blocks
            clearClickedBlocks();
        }
        wasInDungeon = inDungeon;

        // Check dungeon requirement
        if (onlyInDungeons && !inDungeon) {
            return;
        }

        // CGA-style: Max 1 action per tick
        if (actionThisTick) {
            return;
        }

        // Get what the player is looking at (raycast from camera)
        HitResult hitResult = client.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return; // Not looking at a block
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos targetPos = blockHitResult.getBlockPos();
        BlockState targetState = client.level.getBlockState(targetPos);

        // Calculate distance
        Vec3 playerEyes = client.player.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        double distance = playerEyes.distanceTo(targetCenter);

        // Check if it's a secret and in range
        if (targetState.is(Blocks.CHEST) || targetState.is(Blocks.TRAPPED_CHEST)) {
            // Chest secret
            if (distance > maxRange) {
                return; // Too far
            }

            // Check cooldown
            if (!canClickBlock(targetPos)) {
                return;
            }

            // VANILLA: Just press USE KEY - Minecraft handles everything!
            triggerUseKey(targetPos);
            debugMessage(String.format("§a[SecretTriggerbot] Triggered CHEST at distance %.2f", distance));

        } else if (targetState.is(Blocks.PLAYER_HEAD) || targetState.is(Blocks.PLAYER_WALL_HEAD)) {
            // Skull secret - validate UUID
            if (distance > maxSkullRange) {
                return; // Too far
            }

            // Check if it's a real secret skull
            if (!isSecretSkull(client.level.getBlockEntity(targetPos))) {
                return; // Not a secret skull
            }

            // Check cooldown
            if (!canClickBlock(targetPos)) {
                return;
            }

            // VANILLA: Just press USE KEY - Minecraft handles everything!
            triggerUseKey(targetPos);
            debugMessage(String.format("§a[SecretTriggerbot] Triggered SKULL at distance %.2f", distance));
        }
    }

    /**
     * Check if a block can be clicked (cooldown check)
     */
    private boolean canClickBlock(BlockPos pos) {
        // Check if already clicked (permanent block)
        if (clickedBlocks.contains(pos)) {
            return false;
        }

        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastClick = blockCooldowns.get(pos);
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN_MS) {
            return false; // Still on cooldown
        }

        return true;
    }

    /**
     * Check if a skull is a secret skull (by UUID)
     * 1.21.10 API: SkullBlockEntity.getOwner() returns ProfileComponent
     */
    private boolean isSecretSkull(BlockEntity blockEntity) {
        if (!(blockEntity instanceof SkullBlockEntity skullEntity)) {
            return false;
        }

        // Get skull profile (1.21.10: returns ProfileComponent)
        ResolvableProfile profileComponent = skullEntity.getOwnerProfile();
        if (profileComponent == null) {
            return false;
        }

        // Extract GameProfile from ProfileComponent
        var gameProfile = profileComponent.partialProfile();
        if (gameProfile == null || gameProfile.id() == null) {
            return false;
        }

        // 1.21.10: GameProfile is a record, use id() accessor
        String uuid = gameProfile.id().toString();

        // Check if it's a secret skull or redstone key
        return SECRET_SKULL_UUID.equals(uuid) || REDSTONE_KEY_UUID.equals(uuid);
    }

    /**
     * Trigger USE KEY - PURE VANILLA WAY
     * Presses the use key for exactly ONE tick, then releases it
     * Minecraft handles: raycasting, packet sending, everything!
     */
    private void triggerUseKey(BlockPos pos) {
        if (mc.options == null) return;

        // Mark this tick as having an action
        actionThisTick = true;

        // VANILLA: Press the use key for this tick!
        // Minecraft will:
        // 1. Raycast to find what we're looking at
        // 2. Create the correct BlockHitResult
        // 3. Send PlayerInteractBlockC2SPacket with correct sequence
        // 4. Handle everything exactly like a manual click
        mc.options.keyUse.setDown(true);
        ticksSinceKeyPress = 1; // Start counter, will be released after full tick

        // Mark as clicked (permanent tracking)
        clickedBlocks.add(pos);
        blockCooldowns.put(pos, System.currentTimeMillis());
    }

    private void debugMessage(String msg) {
        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    /**
     * Clear clicked blocks (e.g., on world change or dungeon restart)
     * Can be called manually via .secretreset command
     */
    public void clearClickedBlocks() {
        clickedBlocks.clear();
        blockCooldowns.clear();
        HunchClient.LOGGER.info("[SecretTriggerbot] Cleared {} clicked blocks", clickedBlocks.size());
    }

    // === CONFIG ===

    public boolean isDebugMessages() { return debugMessages; }
    public void setDebugMessages(boolean enabled) { this.debugMessages = enabled; }

    public double getMaxRange() { return maxRange; }
    public void setMaxRange(double range) {
        this.maxRange = Math.max(2.0, Math.min(6.5, range));
    }

    public double getMaxSkullRange() { return maxSkullRange; }
    public void setMaxSkullRange(double range) {
        this.maxSkullRange = Math.max(2.0, Math.min(4.7, range));
    }

    public boolean isOnlyInDungeons() { return onlyInDungeons; }
    public void setOnlyInDungeons(boolean enabled) { this.onlyInDungeons = enabled; }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("debugMessages", debugMessages);
        data.addProperty("maxRange", maxRange);
        data.addProperty("maxSkullRange", maxSkullRange);
        data.addProperty("onlyInDungeons", onlyInDungeons);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("debugMessages")) setDebugMessages(data.get("debugMessages").getAsBoolean());
        if (data.has("maxRange")) setMaxRange(data.get("maxRange").getAsDouble());
        if (data.has("maxSkullRange")) setMaxSkullRange(data.get("maxSkullRange").getAsDouble());
        if (data.has("onlyInDungeons")) setOnlyInDungeons(data.get("onlyInDungeons").getAsBoolean());
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Debug Messages",
            "Show debug messages when clicking secrets",
            "secrettriggerbot_debug",
            () -> debugMessages,
            val -> debugMessages = val
        ));

        settings.add(new CheckboxSetting(
            "Only in Dungeons",
            "Only work when in dungeons",
            "secrettriggerbot_dungeons_only",
            () -> onlyInDungeons,
            val -> onlyInDungeons = val
        ));

        settings.add(new SliderSetting(
            "Max Range (Chests)",
            "Maximum range for clicking chests",
            "secrettriggerbot_range",
            2.0f, 6.5f,
            () -> (float) maxRange,
            val -> maxRange = val
        ).withDecimals(1).withSuffix(" blocks"));

        settings.add(new SliderSetting(
            "Max Range (Skulls)",
            "Maximum range for clicking skulls",
            "secrettriggerbot_skull_range",
            2.0f, 4.7f,
            () -> (float) maxSkullRange,
            val -> maxSkullRange = val
        ).withDecimals(1).withSuffix(" blocks"));

        return settings;
    }

    @Override
    public void onTick() {
        // Unused - we use ClientTickEvents instead
    }
}
