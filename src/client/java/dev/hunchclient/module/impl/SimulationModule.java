package dev.hunchclient.module.impl;

import dev.hunchclient.module.EtherwarpModule;
import dev.hunchclient.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Simulation Module for Debug/Testing
 *
 * Features:
 * - Simulates high speed (like F7 500 speed)
 * - Simulates lava bounce
 * - Allows etherwarp simulation with diamond shovel
 * - Works in singleplayer/testing environments
 */
public class SimulationModule extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    // Settings
    private int playerSpeed = 500; // Speed value (default 500 like F7)
    private boolean lavaBounce = true; // Enable lava bounce
    private boolean etherwarpSimulation = true; // Allow diamond shovel etherwarp

    public SimulationModule() {
        super("Simulation", "Debug simulation for testing (speed, lava bounce, etherwarp)", Category.MISC, true);
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    @Override
    protected void onDisable() {
        // Reset player speed to normal
        if (mc.player != null) {
            AttributeInstance speedAttr = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.1); // Default Minecraft speed
            }
        }
    }

    private void onTick(Minecraft client) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;

        // Apply speed simulation
        applySpeedSimulation(player);

        // Apply lava bounce simulation
        if (lavaBounce) {
            applyLavaBounce(player);
        }
    }

    private void applySpeedSimulation(LocalPlayer player) {
        // Set player movement speed
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double targetSpeed = playerSpeed / 1000.0; // Convert to Minecraft speed units
            speedAttr.setBaseValue(targetSpeed);
        }

        // Also update abilities speed
        if (player.getAbilities() != null) {
            player.getAbilities().setWalkingSpeed((float) (playerSpeed / 1000.0));
        }
    }

    private void applyLavaBounce(LocalPlayer player) {
        BlockPos playerPos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        boolean isInLava = player.isInLava();
        boolean isOnRail = mc.level.getBlockState(playerPos).is(Blocks.RAIL) ||
                           mc.level.getBlockState(playerPos).is(Blocks.POWERED_RAIL) ||
                           mc.level.getBlockState(playerPos).is(Blocks.DETECTOR_RAIL) ||
                           mc.level.getBlockState(playerPos).is(Blocks.ACTIVATOR_RAIL);

        // Check if player is close to ground (within 0.1 blocks)
        double distanceToGround = player.getY() - Math.floor(player.getY());

        if ((isInLava || isOnRail) && distanceToGround < 0.1) {
            // Apply upward velocity (3.5 like in F7sim)
            player.setDeltaMovement(player.getDeltaMovement().x, 3.5, player.getDeltaMovement().z);
        }
    }

    /**
     * Check if player is holding a diamond shovel (for etherwarp simulation)
     */
    public boolean isHoldingDiamondShovel() {
        if (!etherwarpSimulation) return false;
        if (mc.player == null) return false;

        return mc.player.getMainHandItem().is(Items.DIAMOND_SHOVEL) ||
               mc.player.getOffhandItem().is(Items.DIAMOND_SHOVEL);
    }

    /**
     * Simulate etherwarp with diamond shovel
     * Called when player sneaks + right-clicks with diamond shovel
     */
    public boolean simulateEtherwarp() {
        if (!isEnabled()) return false;
        if (!etherwarpSimulation) return false;
        if (mc.player == null || mc.level == null) return false;
        if (!isHoldingDiamondShovel()) return false;
        if (!mc.player.isShiftKeyDown()) return false;

        // Get target block
        BlockPos targetBlock = EtherwarpModule.getLookingAtBlock();
        if (targetBlock == null) return false;

        // Check if block is etherwarpable
        if (!EtherwarpModule.isBlockEtherwarpable(targetBlock)) return false;

        // Teleport player to above the block
        Vec3 targetPos = new Vec3(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 1.0,
            targetBlock.getZ() + 0.5
        );

        mc.player.setPos(targetPos);
        mc.player.setDeltaMovement(0, 0, 0);

        return true;
    }

    // Getters and Setters
    public int getPlayerSpeed() {
        return playerSpeed;
    }

    public void setPlayerSpeed(int speed) {
        this.playerSpeed = Math.max(100, Math.min(500, speed));
    }

    public boolean isLavaBounce() {
        return lavaBounce;
    }

    public void setLavaBounce(boolean enabled) {
        this.lavaBounce = enabled;
    }

    public boolean isEtherwarpSimulation() {
        return etherwarpSimulation;
    }

    public void setEtherwarpSimulation(boolean enabled) {
        this.etherwarpSimulation = enabled;
    }
}
