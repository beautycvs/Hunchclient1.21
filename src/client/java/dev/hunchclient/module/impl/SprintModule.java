package dev.hunchclient.module.impl;

import dev.hunchclient.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Sprint Module - Auto Sprint
 *
 * WATCHDOG SAFE: ✅ YES (with proper implementation)
 * - Client-side sprint flag
 * - Natural behavior (respects hunger, damage, etc.)
 * - No packets sent
 * - Same as pressing Ctrl constantly
 */
public class SprintModule extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    public SprintModule() {
        super("Sprint", "Automatically sprint", Category.MOVEMENT, true);
    }

    @Override
    protected void onEnable() {
        // Auto-sprint aktiviert
    }

    @Override
    protected void onDisable() {
        // Stop sprinting when disabled
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Only sprint if:
        // - Player is moving forward
        // - Player has enough hunger
        // - Player is not sneaking
        // - Player is not using an item
        if (mc.player.zza > 0 &&
            !mc.player.isShiftKeyDown() &&
            !mc.player.isUsingItem() &&
            !mc.player.isInWater() &&
            mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
