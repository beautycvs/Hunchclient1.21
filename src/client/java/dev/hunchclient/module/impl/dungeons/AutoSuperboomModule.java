package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import java.util.Locale;

/**
 * AutoSuperboom Module
 *
 * Automatically swaps to Superboom/Infinityboom TNT when left-clicking on specific dungeon blocks:
 * - Cracked Stone Bricks (Weak Walls)
 * - Smooth Stone Slabs (Crypt Lids)
 * - Stone Brick Stairs (Crypts)
 *
 * ONLY WORKS IN DUNGEONS/CATACOMBS!
 *
 * WATCHDOG SAFE: ✅ YES
 * - Only slot switch, NO automatic right-click (you click manually!)
 * - Timing delays and randomization
 * - Mimics normal player behavior
 * - No unusual packets sent
 */
public class AutoSuperboomModule extends Module implements ConfigurableModule, SettingsProvider {

    private final Minecraft mc = Minecraft.getInstance();

    // Sequence state
    private boolean sequenceActive = false;
    private int lastSuperboomTick = 0;
    private int originalSlot = -1;
    private int currentTick = 0;

    // CGA-style swap guard: Only allow 1 swap per tick
    private boolean recentlySwapped = false;
    private int lastSwapTick = -1;

    // Scheduled actions (tick-based)
    private int slotSwitchScheduledTick = -1;
    private int targetSlot = -1;
    private int returnSwitchScheduledTick = -1;

    // Boss detection (disable AutoSuperboom in boss)
    private boolean inBoss = false;

    // Timing configuration (TICK-BASED like CGA)
    // 1 tick = 50ms, increased delay for safety
    private int switchDelayTicks = 4;     // 4 ticks after swap (200ms, safer)
    private int returnDelayTicks = 8;     // 8 ticks before switching back (400ms)
    private boolean adaptivePing = false;  // Disabled for safety (tick-based is enough)

    // Block detection toggles
    private boolean requireTargetBlock = true; // If false, triggers on ANY block (for testing)
    private boolean detectCrackedBricks = true; // Weak walls
    private boolean detectSlabs = true; // Crypt lids
    private boolean detectStairs = true; // Crypt steps

    // Testing mode - allow normal TNT
    private boolean allowNormalTNT = true; // Default: true for testing
    private boolean debugMessages = false; // Debug output

    public AutoSuperboomModule() {
        super("AutoSuperboom", "Auto-swap to Superboom TNT in Dungeons only", Category.DUNGEONS, RiskLevel.RISKY);
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        // Reset boss status when enabling module (in case it got stuck)
        inBoss = false;
        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Boss status reset on enable"), false);
        }
    }

    @Override
    protected void onDisable() {
        // Restore slot if needed
        try {
            if (originalSlot != -1 && mc.player != null) {
                restoreOriginalSlot();
            }
        } catch (Exception e) {
            // Ignore
        }
        sequenceActive = false;
        originalSlot = -1;
    }

    private void onTick(Minecraft client) {
        if (!isEnabled()) return;
        if (client.player == null || client.level == null) return;

        currentTick++;

        // CGA-style: Reset swap guard at the END of each tick
        if (lastSwapTick < currentTick) {
            recentlySwapped = false;
        }

        // Check boss status via scoreboard every 20 ticks (1 second)
        if (currentTick % 20 == 0) {
            updateBossStatus();
        }

        // Execute scheduled actions (tick-based like ChestAura)
        if (slotSwitchScheduledTick >= 0 && currentTick >= slotSwitchScheduledTick) {
            executeSwitchToSlot(targetSlot);
            slotSwitchScheduledTick = -1;
        }

        if (returnSwitchScheduledTick >= 0 && currentTick >= returnSwitchScheduledTick) {
            executeRestoreSlot();
            returnSwitchScheduledTick = -1;
        }
    }

  private boolean isInDungeon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) return false;

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return false;

        // Collect all scoreboard lines + title into one string
        StringBuilder allText = new StringBuilder();

        // Add title
        String display = objective.getDisplayName().getString();
        if (display != null) {
            allText.append(display).append(" ");
        }

        // Add all scoreboard lines (get the actual displayed text, not just the owner)
        try {
            var scores = scoreboard.listPlayerScores(objective);
            for (var score : scores) {
                // Get the team for this entry to get the formatted display name
                String ownerName = score.owner();
                var team = scoreboard.getPlayersTeam(ownerName);

                if (team != null) {
                    // Get the full formatted text: prefix + name + suffix
                    String prefix = team.getPlayerPrefix().getString();
                    String suffix = team.getPlayerSuffix().getString();
                    String lineText = prefix + ownerName + suffix;
                    allText.append(lineText).append(" ");
                } else {
                    // No team, just add the owner name
                    allText.append(ownerName).append(" ");
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        String fullText = stripFormatting(allText.toString());
        String lower = fullText.toLowerCase();

        // Debug: Show scoreboard text when debug is enabled (ALWAYS show when called)
        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Scoreboard: '" + fullText + "'"), false);
        }

        // EXCLUDE Dungeon Hub!
        if (lower.contains("hub")) {
            return false;
        }

        // Check for dungeon keywords
        if (lower.contains("catacombs") || lower.contains("the catacombs")) {
            return true;
        }

        // Check for entrance/floors: "(E)", "(F1)", etc.
        if (lower.contains("(e)") || lower.contains("entrance")) {
            return true;
        }

        // Check for floors F1-F7 and M1-M7
        for (int i = 1; i <= 7; i++) {
            if (lower.contains("(f" + i + ")") || lower.contains("floor " + i) || lower.contains("f" + i)) {
                return true;
            }
            if (lower.contains("(m" + i + ")") || lower.contains("master " + i) || lower.contains("m" + i)) {
                return true;
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
    }    /**
     * Called from mixin when left-click is detected
     * @return true if AutoSuperboom was triggered (blocks other modules), false otherwise
     */
    public boolean onLeftClick() {
        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7[AutoSuperboom] onLeftClick triggered"), false);
        }

        if (!isEnabled()) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Module not enabled"), false);
            }
            return false;
        }

        // Don't trigger if player is holding Dungeonbreaker (BossBlockMiner has priority)
        if (mc.player != null) {
            ItemStack heldItem = mc.player.getMainHandItem();
            if (heldItem != null && heldItem.getHoverName().getString().contains("Dungeonbreaker")) {
                if (debugMessages) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Skipping - player holding Dungeonbreaker"), false);
                }
                return false;
            }
        }

        if (!isInDungeon()) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Not in dungeon"), false);
            }
            return false;
        }

        // Check if in boss (disable AutoSuperboom in boss)
        if (inBoss) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Disabled in boss"), false);
            }
            return false;
        }

        if (sequenceActive) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Sequence already active"), false);
            }
            return false;
        }

        // Check cooldown (prevent spam) - TICK-BASED
        if (currentTick - lastSuperboomTick < 10) {  // 10 ticks = 500ms cooldown
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Cooldown active"), false);
            }
            return false;
        }

        BlockPos targetBlock = getLookingAtBlock();
        if (targetBlock == null) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] No block targeted"), false);
            }
            return false;
        }

        // Block check (can be disabled for testing on anti-cheat servers)
        if (requireTargetBlock && !isTargetBlock(targetBlock)) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Not a target block"), false);
            }
            return false;
        }

        if (debugMessages && !requireTargetBlock && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Block check DISABLED - triggering on ANY block!"), false);
        }

        // Find Superboom in hotbar
        int superboomSlot = findSuperboomInHotbar();
        if (superboomSlot == -1) {
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] No TNT found in hotbar"), false);
            }
            return false;
        }

        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Starting sequence! Slot: " + superboomSlot), false);
        }

        // Start superboom sequence - TICK-BASED
        lastSuperboomTick = currentTick;
        runSuperboomSequence(superboomSlot);

        // Return true: AutoSuperboom wurde getriggert, andere Module sollen NICHT ausgeführt werden!
        return true;
    }

    private void runSuperboomSequence(int superboomSlot) {
        if (sequenceActive) return;
        if (mc.player == null) return;

        sequenceActive = true;
        originalSlot = mc.player.getInventory().getSelectedSlot();

        // CGA-style: Check if already holding TNT
        if (originalSlot == superboomSlot) {
            // Already holding it - no swap needed, just notify and finish
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Already holding TNT - no swap needed!"), false);
            }
            sequenceActive = false;  // No sequence needed
            return;
        }

        // Need to swap to TNT
        targetSlot = superboomSlot;
        int jitter = mc.level != null ? mc.level.getRandom().nextInt(2) : 0;

        // Schedule swap for next tick (minimum 1 tick delay)
        slotSwitchScheduledTick = currentTick + 1;

        // Schedule return swap after delay (you right-click manually!)
        returnSwitchScheduledTick = currentTick + 1 + switchDelayTicks + returnDelayTicks + jitter;

        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[AutoSuperboom] Swap scheduled tick " + slotSwitchScheduledTick + ", return tick " + returnSwitchScheduledTick), false);
        }
    }

    private void executeSwitchToSlot(int slot) {
        if (mc.player == null) return;

        if (debugMessages) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Executing swap to slot " + slot + " (tick " + currentTick + ")"), false);
        }

        switchToSlot(slot);
    }

    private void executeRestoreSlot() {
        if (mc.player == null) return;

        if (debugMessages) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Restoring to slot " + originalSlot + " (tick " + currentTick + ")"), false);
        }

        restoreOriginalSlot();
        sequenceActive = false;
    }

    /**
     * Switch to the specified hotbar slot (CGA-style: client-side only, with swap guard)
     */
    private void switchToSlot(int slot) {
        if (mc.player == null) return;
        if (slot < 0 || slot > 8) return;

        // CGA-style swap guard: Max 1 swap per tick (same as ChestAura2)
        if (recentlySwapped) {
            if (debugMessages) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Swap blocked: recentlySwapped (max 1 per tick)"), false);
            }
            return;
        }

        // CLIENT-SIDE ONLY - NO PACKET! (vanilla client sends packet automatically)
        mc.player.getInventory().setSelectedSlot(slot);

        // Mark as swapped for this tick
        recentlySwapped = true;
        lastSwapTick = currentTick;

        if (debugMessages) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Swapped to slot " + slot + " (client-side, no packet)"), false);
        }
    }

    /**
     * Restore the original hotbar slot (CGA-style)
     */
    private void restoreOriginalSlot() {
        if (mc.player == null) return;
        if (originalSlot < 0 || originalSlot > 8) return;

        // CGA-style swap guard: Max 1 swap per tick
        if (recentlySwapped) {
            if (debugMessages) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Restore blocked: recentlySwapped (max 1 per tick)"), false);
            }
            // Reschedule for next tick
            returnSwitchScheduledTick = currentTick + 1;
            return;
        }

        // CLIENT-SIDE ONLY - NO PACKET! (vanilla client sends packet automatically)
        mc.player.getInventory().setSelectedSlot(originalSlot);

        // Mark as swapped for this tick
        recentlySwapped = true;
        lastSwapTick = currentTick;

        if (debugMessages) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Restored to slot " + originalSlot + " (client-side, no packet)"), false);
        }

        originalSlot = -1;
    }

    /**
     * Find Superboom/Infinityboom TNT in hotbar
     * Returns slot number (0-8) or -1 if not found
     */
    private int findSuperboomInHotbar() {
        if (mc.player == null) return -1;

        Inventory inventory = mc.player.getInventory();

        // Search hotbar (slots 0-8)
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isSuperboomStack(stack)) {
                return slot;
            }
        }

        return -1; // Not found
    }

    /**
     * Check if ItemStack is Superboom, Infinityboom, or normal TNT (for testing)
     */
    private boolean isSuperboomStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        try {
            // Check item type (must be TNT)
            String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
            if (!itemName.contains("tnt")) return false;

            // Check NBT for Skyblock ID (Superboom/Infinityboom)
            CompoundTag attributes = getExtraAttributes(stack);
            if (attributes != null && attributes.contains("id")) {
                String id = attributes.getString("id").orElse("");

                // Check for Superboom/Infinityboom
                if (id.equals("SUPERBOOM_TNT") || id.equals("INFINITYBOOM_TNT")) {
                    return true;
                }

                // Check for normal TNT (if testing mode enabled)
                if (allowNormalTNT && id.equals("TNT")) {
                    return true;
                }
            }

            // Fallback: Check display name
            String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);

            // Always accept Superboom/Infinityboom
            if (displayName.contains("superboom") || displayName.contains("infinityboom")) {
                return true;
            }

            // Accept normal TNT only if testing mode enabled
            if (allowNormalTNT && displayName.contains("tnt")) {
                return true;
            }

        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    /**
     * Get the block the player is looking at
     */
    private BlockPos getLookingAtBlock() {
        if (mc.player == null || mc.level == null) return null;

        double range = 5.0; // Standard block reach distance
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 traceEnd = eyePos.add(lookVec.scale(range));

        BlockHitResult result = mc.level.clip(new ClipContext(
            eyePos,
            traceEnd,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            mc.player
        ));

        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = result.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Check if block at position is a target block (Cracked Bricks, Stone Bricks, or Slabs)
     */
    private boolean isTargetBlock(BlockPos pos) {
        if (mc.level == null || pos == null) return false;

        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        // Check against enabled block types
        // NOTE: Normal STONE_BRICKS are NEVER superboomable!
        if (block == Blocks.CRACKED_STONE_BRICKS && detectCrackedBricks) {
            return true;
        }
        // Crypt lids are SMOOTH_STONE_SLAB, not STONE_BRICK_SLAB!
        if (block == Blocks.SMOOTH_STONE_SLAB && detectSlabs) {
            return true;
        }
        if (block == Blocks.STONE_BRICK_STAIRS && detectStairs) {
            return true;
        }

        return false;
    }

    /**
     * Extract ExtraAttributes from ItemStack NBT
     */
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
                    if (nbt.contains("id")) {
                        return nbt;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Update boss status by checking scoreboard
     */
    private void updateBossStatus() {
        if (mc.level == null) {
            inBoss = false;
            return;
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) {
            inBoss = false;
            return;
        }

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            inBoss = false;
            return;
        }

        // Check all scoreboard lines for boss indicators
        boolean wasBoss = inBoss;
        inBoss = false;

        try {
            var scores = scoreboard.listPlayerScores(objective);
            for (var score : scores) {
                String ownerName = score.owner();
                var team = scoreboard.getPlayersTeam(ownerName);

                String lineText = "";
                if (team != null) {
                    String prefix = team.getPlayerPrefix().getString();
                    String suffix = team.getPlayerSuffix().getString();
                    lineText = prefix + ownerName + suffix;
                } else {
                    lineText = ownerName;
                }

                String stripped = stripFormatting(lineText).toLowerCase();

                // Check for boss room indicators
                if (stripped.contains("sadan") ||
                    stripped.contains("maxor") ||
                    stripped.contains("storm") ||
                    stripped.contains("goldor") ||
                    stripped.contains("necron") ||
                    stripped.contains("wither king") ||
                    stripped.contains("the watcher") ||
                    (stripped.contains("boss") && (stripped.contains("room") || stripped.contains("fight")))) {
                    inBoss = true;
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Debug message when boss status changes
        if (debugMessages && wasBoss != inBoss && mc.player != null) {
            if (inBoss) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Boss detected via scoreboard - DISABLED"), false);
            } else {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Left boss - ENABLED"), false);
            }
        }
    }

    /**
     * Called when chat message received (for boss detection)
     */
    public void onChatMessage(String message) {
        // Detect boss entry (same messages as AlignAura)
        if (message.contains("[BOSS]") && (
            message.contains("Maxor") ||
            message.contains("Storm") ||
            message.contains("Goldor") ||
            message.contains("Necron") ||
            message.contains("Wither King") ||
            message.contains("Sadan")
        )) {
            inBoss = true;
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[AutoSuperboom] Boss detected via chat - DISABLED"), false);
            }
        }

        // Detect boss exit
        if (message.contains("The Core entrance is opening!") ||
            message.contains("PUZZLE FAIL!") ||
            message.contains("PUZZLE COMPLETE!") ||
            message.contains("defeated") ||
            message.contains("EXTRA STATS")) {
            inBoss = false;
            if (debugMessages && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[AutoSuperboom] Boss ended via chat - ENABLED"), false);
            }
        }
    }

    /**
     * Called on world unload
     */
    public void onWorldUnload() {
        inBoss = false;
        sequenceActive = false;
        originalSlot = -1;
        if (debugMessages && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7[AutoSuperboom] World unloaded - reset state"), false);
        }
    }

    // Configuration getters and setters

    public int getSwitchDelayTicks() {
        return switchDelayTicks;
    }

    public void setSwitchDelayTicks(int ticks) {
        // MINIMUM 3 ticks delay after swap for safety!
        this.switchDelayTicks = Math.max(3, Math.min(15, ticks));
    }

    public int getReturnDelayTicks() {
        return returnDelayTicks;
    }

    public void setReturnDelayTicks(int ticks) {
        // Minimum 5 ticks before switching back
        this.returnDelayTicks = Math.max(5, Math.min(25, ticks));
    }

    public boolean isAdaptivePing() {
        return adaptivePing;
    }

    public void setAdaptivePing(boolean enabled) {
        this.adaptivePing = enabled;
    }

    public boolean isRequireTargetBlock() {
        return requireTargetBlock;
    }

    public void setRequireTargetBlock(boolean enabled) {
        this.requireTargetBlock = enabled;
    }

    public boolean isDetectCrackedBricks() {
        return detectCrackedBricks;
    }

    public void setDetectCrackedBricks(boolean enabled) {
        this.detectCrackedBricks = enabled;
    }

    public boolean isDetectSlabs() {
        return detectSlabs;
    }

    public void setDetectSlabs(boolean enabled) {
        this.detectSlabs = enabled;
    }

    public boolean isDetectStairs() {
        return detectStairs;
    }

    public void setDetectStairs(boolean enabled) {
        this.detectStairs = enabled;
    }

    public boolean isAllowNormalTNT() {
        return allowNormalTNT;
    }

    public void setAllowNormalTNT(boolean enabled) {
        this.allowNormalTNT = enabled;
    }

    public boolean isDebugMessages() {
        return debugMessages;
    }

    public void setDebugMessages(boolean enabled) {
        this.debugMessages = enabled;
    }

    // Config persistence
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("switchDelayTicks", switchDelayTicks);
        config.addProperty("returnDelayTicks", returnDelayTicks);
        config.addProperty("adaptivePing", adaptivePing);
        config.addProperty("requireTargetBlock", requireTargetBlock);
        config.addProperty("detectCrackedBricks", detectCrackedBricks);
        config.addProperty("detectSlabs", detectSlabs);
        config.addProperty("detectStairs", detectStairs);
        config.addProperty("allowNormalTNT", allowNormalTNT);
        config.addProperty("debugMessages", debugMessages);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("switchDelayTicks")) {
            setSwitchDelayTicks(config.get("switchDelayTicks").getAsInt());
        }
        if (config.has("returnDelayTicks")) {
            setReturnDelayTicks(config.get("returnDelayTicks").getAsInt());
        }
        if (config.has("adaptivePing")) {
            adaptivePing = config.get("adaptivePing").getAsBoolean();
        }
        if (config.has("requireTargetBlock")) {
            requireTargetBlock = config.get("requireTargetBlock").getAsBoolean();
        }
        if (config.has("detectCrackedBricks")) {
            detectCrackedBricks = config.get("detectCrackedBricks").getAsBoolean();
        }
        if (config.has("detectSlabs")) {
            detectSlabs = config.get("detectSlabs").getAsBoolean();
        }
        if (config.has("detectStairs")) {
            detectStairs = config.get("detectStairs").getAsBoolean();
        }
        if (config.has("allowNormalTNT")) {
            allowNormalTNT = config.get("allowNormalTNT").getAsBoolean();
        }
        if (config.has("debugMessages")) {
            debugMessages = config.get("debugMessages").getAsBoolean();
        }
    }

    // SettingsProvider implementation
    @Override
    public java.util.List<ModuleSetting> getSettings() {
        java.util.List<ModuleSetting> settings = new java.util.ArrayList<>();

        // Switch Delay
        settings.add(new SliderSetting(
            "Switch Delay",
            "Ticks to wait after switching slot (3-15 ticks, 150-750ms)",
            "autosuperboom_switch_delay",
            3f, 15f,
            () -> (float) switchDelayTicks,
            val -> setSwitchDelayTicks((int) val.floatValue())
        ).withDecimals(0).withSuffix(" ticks"));

        // Return Delay
        settings.add(new SliderSetting(
            "Return Delay",
            "Ticks before switching back (5-25 ticks, 250ms-1.25s)",
            "autosuperboom_return_delay",
            5f, 25f,
            () -> (float) returnDelayTicks,
            val -> setReturnDelayTicks((int) val.floatValue())
        ).withDecimals(0).withSuffix(" ticks"));

        // Require Target Block (Testing toggle)
        settings.add(new CheckboxSetting(
            "Require Target Block",
            "Only trigger on dungeon blocks. DISABLE for anti-cheat testing!",
            "autosuperboom_require_target",
            () -> requireTargetBlock,
            val -> requireTargetBlock = val
        ));

        // Detect Cracked Bricks
        settings.add(new CheckboxSetting(
            "Cracked Bricks",
            "Detect cracked stone bricks (weak walls)",
            "autosuperboom_cracked_bricks",
            () -> detectCrackedBricks,
            val -> detectCrackedBricks = val
        ));

        // Detect Slabs
        settings.add(new CheckboxSetting(
            "Smooth Stone Slabs",
            "Detect smooth stone slabs (crypt lids)",
            "autosuperboom_slabs",
            () -> detectSlabs,
            val -> detectSlabs = val
        ));

        // Detect Stairs
        settings.add(new CheckboxSetting(
            "Stone Stairs",
            "Detect stone brick stairs (crypts)",
            "autosuperboom_stairs",
            () -> detectStairs,
            val -> detectStairs = val
        ));

        // Allow Normal TNT
        settings.add(new CheckboxSetting(
            "Allow Normal TNT",
            "Allow normal TNT (for testing)",
            "autosuperboom_normal_tnt",
            () -> allowNormalTNT,
            val -> allowNormalTNT = val
        ));

        // Debug Messages
        settings.add(new CheckboxSetting(
            "Debug Messages",
            "Show debug messages in chat",
            "autosuperboom_debug",
            () -> debugMessages,
            val -> debugMessages = val
        ));

        return settings;
    }
}
