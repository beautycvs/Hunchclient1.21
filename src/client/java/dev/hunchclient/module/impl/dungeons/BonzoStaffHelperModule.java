package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Bonzo Staff Helper Module
 *
 * S-Taps before explosion to catch the boost even at 500 speed
 *
 * WATCHDOG SAFE: YES (mimics player input)
 */
public class BonzoStaffHelperModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final Minecraft MC = Minecraft.getInstance();

    // Configuration
    private int explosionDelay = 8;     // Ticks until explosion (adjust for ping)
    private int sTapDuration = 4;       // How long to S-Tap
    private boolean adaptiveTiming = true;  // Auto-learn optimal timing
    private boolean experimentalMode = false;  // EXPERIMENTAL: Set velocity to 0 until explosion

    // State tracking
    private static boolean shouldPressS = false;
    private static boolean shouldCancelVelocity = false;
    private boolean wasRightClicking = false;
    private int ticksSinceClick = 0;
    private boolean waitingForExplosion = false;

    // Velocity tracking for adaptive learning
    private double velocityBeforeStaff = 0.0;
    private double maxVelocityAfterStaff = 0.0;
    private int ticksAtMaxVelocity = 0;

    // Adaptive learning data
    private int successfulBoosts = 0;
    private int totalAttempts = 0;
    private double averageBoostStrength = 0.0;

    public BonzoStaffHelperModule() {
        super("BonzoStaffHelper", "S-Tap before Bonzo Staff explosion to catch boost", Category.DUNGEONS, RiskLevel.RISKY);
    }

    @Override
    protected void onEnable() {
        shouldPressS = false;
        waitingForExplosion = false;
        wasRightClicking = false;
    }

    @Override
    protected void onDisable() {
        shouldPressS = false;
        waitingForExplosion = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (MC.player == null) return;

        boolean isRightClicking = MC.options.keyUse.isDown();
        boolean holdingStaff = isHoldingBonzoStaff();

        // Detect staff use
        if (isRightClicking && !wasRightClicking && holdingStaff) {
            // Staff RIGHT-CLICKED - start tracking immediately
            waitingForExplosion = true;
            ticksSinceClick = 0;
            shouldPressS = false;
            totalAttempts++;

            // Capture velocity before boost
            velocityBeforeStaff = MC.player.getDeltaMovement().horizontalDistance();
            maxVelocityAfterStaff = velocityBeforeStaff;
            ticksAtMaxVelocity = 0;

            HunchClient.LOGGER.info("═══════════════════════════════════════════════════");
            HunchClient.LOGGER.info("[BonzoStaff] STAFF CLICKED - Attempt #{}", totalAttempts);
            HunchClient.LOGGER.info("[BonzoStaff] Initial velocity: {}", String.format("%.3f", velocityBeforeStaff));
            HunchClient.LOGGER.info("[BonzoStaff] Explosion delay: {} ticks", explosionDelay);
            if (experimentalMode) {
                HunchClient.LOGGER.info("[BonzoStaff] Mode: EXPERIMENTAL (Velocity Cancel OnGround)");
            } else {
                HunchClient.LOGGER.info("[BonzoStaff] S-Tap duration: {} ticks", sTapDuration);
            }
            HunchClient.LOGGER.info("═══════════════════════════════════════════════════");
        }

        wasRightClicking = isRightClicking;

        // Handle explosion timing
        if (waitingForExplosion) {
            ticksSinceClick++;

            // Track velocity changes
            double currentVelocity = MC.player.getDeltaMovement().horizontalDistance();
            if (currentVelocity > maxVelocityAfterStaff) {
                maxVelocityAfterStaff = currentVelocity;
                ticksAtMaxVelocity = ticksSinceClick;
            }

            // EXPERIMENTAL MODE: Cancel velocity (ONLY when on ground!)
            // Note: User is responsible for having mana - we don't check!
            if (experimentalMode && ticksSinceClick <= explosionDelay) {
                shouldCancelVelocity = true;

                // Only cancel horizontal velocity when ON GROUND
                if (MC.player.onGround()) {
                    MC.player.setDeltaMovement(0, MC.player.getDeltaMovement().y, 0);
                    HunchClient.LOGGER.info("[BonzoStaff] Tick {}: Velocity = {} → CANCELLED (OnGround)",
                        ticksSinceClick,
                        String.format("%.3f", currentVelocity)
                    );
                } else {
                    // In air - don't touch velocity
                    HunchClient.LOGGER.info("[BonzoStaff] Tick {}: Velocity = {} (InAir - not cancelled)",
                        ticksSinceClick,
                        String.format("%.3f", currentVelocity)
                    );
                }
            } else {
                shouldCancelVelocity = false;

                // Debug: Log velocity every tick
                HunchClient.LOGGER.info("[BonzoStaff] Tick {}: Velocity = {}, S-Tap = {}",
                    ticksSinceClick,
                    String.format("%.3f", currentVelocity),
                    shouldPressS ? "YES" : "NO"
                );

                // Start S-Tapping just before explosion hits (ONLY in non-experimental mode)
                int startSTapAt = explosionDelay - sTapDuration;
                if (ticksSinceClick >= startSTapAt && ticksSinceClick < explosionDelay) {
                    shouldPressS = true;
                    if (ticksSinceClick == startSTapAt) {
                        HunchClient.LOGGER.info("[BonzoStaff] ⚡ STARTING S-TAP at tick {} (duration: {} ticks)", ticksSinceClick, sTapDuration);
                    }
                } else {
                    shouldPressS = false;
                }
            }

            // Explosion should have hit by now, analyze results
            if (ticksSinceClick >= explosionDelay + 5) {
                waitingForExplosion = false;
                shouldPressS = false;

                // Calculate boost success
                double boostGain = maxVelocityAfterStaff - velocityBeforeStaff;
                boolean wasSuccessful = boostGain > 0.5; // Threshold for "good" boost

                if (wasSuccessful) {
                    successfulBoosts++;
                    averageBoostStrength = (averageBoostStrength * (successfulBoosts - 1) + boostGain) / successfulBoosts;
                }

                HunchClient.LOGGER.info("═══════════════════════════════════════════════════");
                HunchClient.LOGGER.info("[BonzoStaff] 📊 BOOST ANALYSIS:");
                HunchClient.LOGGER.info("[BonzoStaff] Before velocity: {}", String.format("%.3f", velocityBeforeStaff));
                HunchClient.LOGGER.info("[BonzoStaff] Max velocity:    {}", String.format("%.3f", maxVelocityAfterStaff));
                HunchClient.LOGGER.info("[BonzoStaff] Boost gain:      {} (at tick {})", String.format("%.3f", boostGain), ticksAtMaxVelocity);
                HunchClient.LOGGER.info("[BonzoStaff] Success:         {}", wasSuccessful ? "✓ YES" : "✗ NO");
                HunchClient.LOGGER.info("[BonzoStaff] Success rate:    {}/{} ({} %)",
                    successfulBoosts, totalAttempts,
                    String.format("%.1f", (successfulBoosts * 100.0 / totalAttempts)));
                HunchClient.LOGGER.info("[BonzoStaff] Avg boost:       {}", String.format("%.3f", averageBoostStrength));

                // Adaptive timing suggestion
                if (adaptiveTiming && totalAttempts >= 3) {
                    if (ticksAtMaxVelocity < explosionDelay - 1) {
                        HunchClient.LOGGER.info("[BonzoStaff] 💡 SUGGESTION: Explosion came at tick {}. Try delay = {}",
                            ticksAtMaxVelocity, ticksAtMaxVelocity + 1);
                    } else if (boostGain < 0.3) {
                        HunchClient.LOGGER.info("[BonzoStaff] 💡 SUGGESTION: Weak boost. Try increasing S-Tap duration");
                    } else {
                        HunchClient.LOGGER.info("[BonzoStaff] ✓ Settings look good!");
                    }
                }

                HunchClient.LOGGER.info("═══════════════════════════════════════════════════");
            }
        }
    }

    // Called from mixin to check if we should press S
    public static boolean shouldPressBackward() {
        return shouldPressS;
    }

    // Called from mixin to check if we should cancel velocity
    public static boolean shouldCancelVelocity() {
        return shouldCancelVelocity;
    }

    private boolean isHoldingBonzoStaff() {
        if (MC.player == null) return false;

        ItemStack mainHand = MC.player.getMainHandItem();
        ItemStack offHand = MC.player.getOffhandItem();

        return isBonzoStaffStack(mainHand) || isBonzoStaffStack(offHand);
    }

    private boolean isBonzoStaffStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        try {
            // Check item type (must be blaze rod)
            String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
            if (!itemName.contains("blaze_rod")) return false;

            // Check NBT for Skyblock ID
            CompoundTag attributes = getExtraAttributes(stack);
            if (attributes != null && attributes.contains("id")) {
                String id = attributes.getString("id").orElse("");
                if (id.equals("BONZO_STAFF")) {
                    return true;
                }
            }

            // Fallback: Check display name for "Bonzo" and "Staff"
            String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (displayName.contains("bonzo") && displayName.contains("staff")) {
                return true;
            }

            // For testing: Accept any blaze rod
            return true;
        } catch (Exception e) {
            return false;
        }
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

    // Configuration methods
    public int getExplosionDelay() {
        return explosionDelay;
    }

    public void setExplosionDelay(int ticks) {
        this.explosionDelay = Math.max(1, Math.min(20, ticks));
    }

    public int getSTapDuration() {
        return sTapDuration;
    }

    public void setSTapDuration(int ticks) {
        this.sTapDuration = Math.max(1, Math.min(10, ticks));
    }

    public boolean isAdaptiveTiming() {
        return adaptiveTiming;
    }

    public void setAdaptiveTiming(boolean enabled) {
        this.adaptiveTiming = enabled;
    }

    public boolean isExperimentalMode() {
        return experimentalMode;
    }

    public void setExperimentalMode(boolean enabled) {
        this.experimentalMode = enabled;
        if (enabled) {
            HunchClient.LOGGER.warn("[BonzoStaff] ⚠️ EXPERIMENTAL MODE ENABLED - Sets velocity to 0!");
        }
    }

    public void resetStats() {
        totalAttempts = 0;
        successfulBoosts = 0;
        averageBoostStrength = 0.0;
        HunchClient.LOGGER.info("[BonzoStaff] Stats reset!");
    }

    public String getStatsString() {
        if (totalAttempts == 0) return "No data yet";
        return String.format("%d/%d (%.1f%%) - Avg: %.2f",
            successfulBoosts, totalAttempts,
            (successfulBoosts * 100.0 / totalAttempts),
            averageBoostStrength);
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("explosionDelay", explosionDelay);
        config.addProperty("sTapDuration", sTapDuration);
        config.addProperty("adaptiveTiming", adaptiveTiming);
        config.addProperty("experimentalMode", experimentalMode);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;

        if (data.has("explosionDelay")) {
            setExplosionDelay(data.get("explosionDelay").getAsInt());
        }
        if (data.has("sTapDuration")) {
            setSTapDuration(data.get("sTapDuration").getAsInt());
        }
        if (data.has("adaptiveTiming")) {
            setAdaptiveTiming(data.get("adaptiveTiming").getAsBoolean());
        }
        if (data.has("experimentalMode")) {
            setExperimentalMode(data.get("experimentalMode").getAsBoolean());
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // Explosion Delay
        settings.add(new SliderSetting(
            "Explosion Delay",
            "Ticks until explosion (adjust for ping)",
            "bonzo_explosion_delay",
            1f, 20f,
            () -> (float) explosionDelay,
            val -> explosionDelay = (int) val.floatValue()
        ).withDecimals(0).withSuffix(" ticks"));

        // S-Tap Duration (only visible when NOT in experimental mode)
        settings.add(new SliderSetting(
            "S-Tap Duration",
            "How long to press S before explosion",
            "bonzo_stap_duration",
            1f, 10f,
            () -> (float) sTapDuration,
            val -> sTapDuration = (int) val.floatValue()
        ).withDecimals(0).withSuffix(" ticks").setVisible(() -> !experimentalMode));

        // Adaptive Timing
        settings.add(new CheckboxSetting(
            "Adaptive Timing",
            "Auto-learn optimal timing from attempts",
            "bonzo_adaptive_timing",
            () -> adaptiveTiming,
            val -> adaptiveTiming = val
        ));

        // Experimental Mode
        settings.add(new CheckboxSetting(
            "Experimental Mode",
            "Cancel velocity until explosion (requires mana)",
            "bonzo_experimental_mode",
            () -> experimentalMode,
            val -> {
                experimentalMode = val;
                if (val) {
                    HunchClient.LOGGER.warn("[BonzoStaff] ⚠️ EXPERIMENTAL MODE ENABLED");
                }
            }
        ));

        return settings;
    }
}
