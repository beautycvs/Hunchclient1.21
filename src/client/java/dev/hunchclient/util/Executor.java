package dev.hunchclient.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Timer/Executor system for running periodic tasks
 * Based on catgirlroutes Executor system
 */
public class Executor {
    private static final List<Executor> EXECUTORS = new ArrayList<>();

    private final long intervalMs;
    private final Runnable task;
    private long lastRun = 0;
    private boolean active = true;

    /**
     * Creates a new executor that runs every intervalMs milliseconds
     * @param intervalMs Interval in milliseconds
     * @param task Task to run
     */
    public Executor(long intervalMs, Runnable task) {
        this.intervalMs = intervalMs;
        this.task = task;
    }

    /**
     * Registers this executor to run
     */
    public void register() {
        if (!EXECUTORS.contains(this)) {
            EXECUTORS.add(this);
        }
    }

    /**
     * Unregisters this executor
     */
    public void unregister() {
        EXECUTORS.remove(this);
    }

    /**
     * Stops this executor
     */
    public void stop() {
        this.active = false;
    }

    /**
     * Ticks all registered executors - should be called every client tick
     */
    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long currentTime = System.currentTimeMillis();

        for (Executor executor : new ArrayList<>(EXECUTORS)) {
            if (!executor.active) {
                EXECUTORS.remove(executor);
                continue;
            }

            if (currentTime - executor.lastRun >= executor.intervalMs) {
                try {
                    executor.task.run();
                    executor.lastRun = currentTime;
                } catch (Exception e) {
                    System.err.println("Error in Executor: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Clears all executors (on world unload)
     */
    public static void clearAll() {
        EXECUTORS.clear();
    }

    public static void init() {
    }
}
