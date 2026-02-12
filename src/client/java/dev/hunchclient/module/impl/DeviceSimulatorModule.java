package dev.hunchclient.module.impl;

import dev.hunchclient.module.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/**
 * Device Simulator - Simulates the SS Device for testing AutoSS
 * Changes obsidian blocks to sea lanterns in sequence
 */
public class DeviceSimulatorModule extends Module {

    // Grid configuration (same as AutoSS)
    private static final int GRID_X = 111; // Obsidian blocks
    private static final int GRID_Y_MIN = 120;
    private static final int GRID_Y_MAX = 123;
    private static final int GRID_Z_MIN = 92;
    private static final int GRID_Z_MAX = 95;

    // State
    private boolean isRunning = false;
    private List<BlockPos> sequence = new ArrayList<>();
    private int currentStep = 0;
    private long lastChangeTime = 0;
    private int delayMs = 1000; // 1 second between changes

    private final Random random = new Random();

    public DeviceSimulatorModule() {
        super("DeviceSimulator", "Simulates SS Device for testing", Category.MISC, true);
    }

    @Override
    protected void onEnable() {
        // Module enabled
    }

    @Override
    protected void onDisable() {
        stopSimulation();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Check if simulation is running
        if (isRunning) {
            long now = System.currentTimeMillis();
            if (now - lastChangeTime >= delayMs) {
                nextStep(mc);
            }
        }
    }

    /**
     * Starts the device simulation
     */
    public void startSimulation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        debugMessage("Starting Device Simulation...");

        // Generate random sequence
        sequence.clear();
        currentStep = 0;

        // Create list of all possible blocks in grid
        List<BlockPos> allPositions = new ArrayList<>();
        for (int y = GRID_Y_MIN; y <= GRID_Y_MAX; y++) {
            for (int z = GRID_Z_MIN; z <= GRID_Z_MAX; z++) {
                allPositions.add(new BlockPos(GRID_X, y, z));
            }
        }

        // Generate sequence of 5-8 random blocks
        int sequenceLength = 5 + random.nextInt(4); // 5-8 blocks
        for (int i = 0; i < sequenceLength; i++) {
            BlockPos pos = allPositions.get(random.nextInt(allPositions.size()));
            sequence.add(pos);
        }

        isRunning = true;
        lastChangeTime = System.currentTimeMillis();

        debugMessage("Sequence length: " + sequenceLength);
    }

    /**
     * Stops the simulation
     */
    public void stopSimulation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Reset all blocks to obsidian
        for (int y = GRID_Y_MIN; y <= GRID_Y_MAX; y++) {
            for (int z = GRID_Z_MIN; z <= GRID_Z_MAX; z++) {
                BlockPos pos = new BlockPos(GRID_X, y, z);
                mc.level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
            }
        }

        isRunning = false;
        sequence.clear();
        currentStep = 0;
        debugMessage("Simulation stopped");
    }

    /**
     * Execute next step in sequence
     */
    private void nextStep(Minecraft mc) {
        if (currentStep >= sequence.size()) {
            // Sequence complete
            debugMessage("Sequence complete!");
            stopSimulation();
            return;
        }

        BlockPos pos = sequence.get(currentStep);

        // Change to sea lantern
        mc.level.setBlockAndUpdate(pos, Blocks.SEA_LANTERN.defaultBlockState());
        debugMessage("Step " + (currentStep + 1) + ": Sea Lantern at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());

        // Schedule reset after 500ms
        new Thread(() -> {
            try {
                Thread.sleep(500);
                Minecraft client = Minecraft.getInstance();
                if (client.level != null) {
                    client.execute(() -> {
                        // Reset to obsidian
                        client.level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                    });
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        currentStep++;
        lastChangeTime = System.currentTimeMillis();
    }

    /**
     * Manually trigger a test sequence
     */
    public void triggerTest() {
        if (isRunning) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    private void debugMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§e[DeviceSim] §f" + msg), false);
        }
    }

    // Getters/Setters
    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delay) {
        this.delayMs = Math.max(500, Math.min(3000, delay));
    }

    public boolean isRunning() {
        return isRunning;
    }
}
