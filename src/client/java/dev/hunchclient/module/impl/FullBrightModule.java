package dev.hunchclient.module.impl;

import dev.hunchclient.module.Module;
import net.minecraft.client.Minecraft;

/**
 * FullBright Module
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only (gamma value)
 * - No packets sent
 * - No server-side effects
 * - Undetectable
 */
public class FullBrightModule extends Module implements dev.hunchclient.bridge.module.IFullBright {

    private double previousGamma = 1.0;
    private final Minecraft mc = Minecraft.getInstance();

    public FullBrightModule() {
        super("FullBright", "See in the dark", Category.VISUALS, true);
    }

    @Override
    protected void onEnable() {
        if (mc.options != null) {
            // Save current gamma
            previousGamma = mc.options.gamma().get();

            // Set to max
            mc.options.gamma().set(100.0);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options != null) {
            // Restore previous gamma
            mc.options.gamma().set(previousGamma);
        }
    }

    @Override
    public void onTick() {
        if (mc.options != null) {
            // Keep gamma at max every tick
            mc.options.gamma().set(100.0);
        }
    }
}
