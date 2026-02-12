package dev.hunchclient.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple tick-based scheduler - adapted from Skyblocker
 */
public class Scheduler {
    public static final Scheduler INSTANCE = new Scheduler();

    private final List<ScheduledTask> tasks = new ArrayList<>();
    private final List<CyclicTask> cyclicTasks = new ArrayList<>();
    private int currentTick = 0;

    public void tick() {
        currentTick++;
        tasks.removeIf(task -> {
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                task.runnable.run();
                return true;
            }
            return false;
        });

        for (CyclicTask task : cyclicTasks) {
            task.ticksUntilRun--;
            if (task.ticksUntilRun <= 0) {
                task.runnable.run();
                task.ticksUntilRun = task.intervalTicks;
            }
        }
    }

    public void schedule(Runnable runnable, int ticks) {
        tasks.add(new ScheduledTask(runnable, ticks));
    }

    public void scheduleCyclic(Runnable runnable, int intervalTicks) {
        cyclicTasks.add(new CyclicTask(runnable, intervalTicks));
    }

    private static class ScheduledTask {
        final Runnable runnable;
        int ticksRemaining;

        ScheduledTask(Runnable runnable, int ticks) {
            this.runnable = runnable;
            this.ticksRemaining = ticks;
        }
    }

    private static class CyclicTask {
        final Runnable runnable;
        final int intervalTicks;
        int ticksUntilRun;

        CyclicTask(Runnable runnable, int intervalTicks) {
            this.runnable = runnable;
            this.intervalTicks = Math.max(1, intervalTicks);
            this.ticksUntilRun = this.intervalTicks;
        }
    }
}
