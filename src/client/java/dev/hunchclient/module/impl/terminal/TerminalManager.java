package dev.hunchclient.module.impl.terminal;

import dev.hunchclient.module.impl.misc.F7SimModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

/**
 * Manages F7 terminal positions and interactions
 * Handles spawning invisible "terminal points" and opening terminals on interaction
 */
public class TerminalManager {
    private static final double INTERACTION_RANGE = 6.5;
    private static final double INTERACTION_RADIUS = 3.0;
    private static final double LOOK_DOT_THRESHOLD = 0.6;

    private final List<TerminalPosition> terminals = new ArrayList<>();
    private int completedCount = 0;
    private boolean allTerminalsCompleted = false;
    private Runnable onTerminalCompletedCallback = null;

    public TerminalManager() {
        // Empty constructor - terminals will be added via addTerminal()
    }

    /**
     * Set callback to be called when any terminal is completed
     */
    public void setOnTerminalCompletedCallback(Runnable callback) {
        this.onTerminalCompletedCallback = callback;
    }

    /**
     * Add a terminal position
     */
    public void addTerminal(TerminalPosition terminal) {
        terminals.add(terminal);
    }

    /**
     * Add a terminal position from coordinates
     */
    public void addTerminal(double x, double y, double z, TerminalType type) {
        terminals.add(new TerminalPosition(x, y, z, type));
    }

    /**
     * Clear all terminals
     */
    public void clearTerminals() {
        terminals.clear();
        completedCount = 0;
        allTerminalsCompleted = false;
    }

    /**
     * Reset all terminals (mark as not completed)
     */
    public void resetTerminals() {
        for (TerminalPosition terminal : terminals) {
            terminal.reset();
        }
        completedCount = 0;
        allTerminalsCompleted = false;

        // Notify callback to update armor stands
        if (onTerminalCompletedCallback != null) {
            onTerminalCompletedCallback.run();
        }
    }

    /**
     * Check if player is looking at a terminal and return it
     */
    public TerminalPosition getLookedAtTerminal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }

        Vec3 playerPos = mc.player.getEyePosition();

        // Find the closest terminal within interaction range that the player is looking at
        TerminalPosition closest = null;
        double closestDistance = INTERACTION_RANGE;

        for (TerminalPosition terminal : terminals) {
            if (terminal.isCompleted()) {
                continue; // Skip completed terminals
            }

            Vec3 terminalPos = terminal.getPosition();
            double distance = playerPos.distanceTo(terminalPos);

            // Check if terminal is within range
            if (distance <= INTERACTION_RANGE) {
                // Check if player is looking in the general direction
                Vec3 lookVec = mc.player.getViewVector(1.0f);
                Vec3 toTerminal = terminalPos.subtract(playerPos).normalize();
                double dotProduct = lookVec.dot(toTerminal);

                // If looking towards terminal (dot product > 0.8 means within ~36 degrees)
                if (dotProduct > LOOK_DOT_THRESHOLD && distance < closestDistance) {
                    // Check if within interaction radius
                    double distanceToLookRay = getDistanceToLookRay(playerPos, lookVec, terminalPos);
                    if (distanceToLookRay <= INTERACTION_RADIUS) {
                        closest = terminal;
                        closestDistance = distance;
                    }
                }
            }
        }

        return closest;
    }

    /**
     * Calculate the shortest distance from a point to a ray
     */
    private double getDistanceToLookRay(Vec3 rayOrigin, Vec3 rayDirection, Vec3 point) {
        Vec3 toPoint = point.subtract(rayOrigin);
        double projection = toPoint.dot(rayDirection);
        Vec3 closestPoint = rayOrigin.add(rayDirection.scale(projection));
        return point.distanceTo(closestPoint);
    }

    /**
     * Open a terminal GUI with optional ping simulation
     */
    public void openTerminal(TerminalPosition terminal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Inventory playerInventory = mc.player.getInventory();
        TermSimGUI gui = createTerminalGUI(terminal.getType(), playerInventory);

        if (gui != null) {
            gui.initializeLayout();

            // Set ping simulation on the GUI (for click delays)
            int pingDelay = getPingDelay();
            gui.setSimulatedPing(pingDelay);

            // Set completion callback
            gui.setOnCompleteCallback(() -> {
                terminal.setCompleted(true);
                completedCount++;

                // Notify callback (e.g., update armor stand nametags)
                if (onTerminalCompletedCallback != null) {
                    onTerminalCompletedCallback.run();
                }

                // Check if all terminals are completed
                if (completedCount >= terminals.size()) {
                    allTerminalsCompleted = true;
                    onAllTerminalsCompleted();
                }
            });

            // Open the GUI immediately (ping delay is handled in click processing)
            mc.setScreen(gui);

            // Send fake slot update packets for TerminalSolver compatibility
            // Delay slightly to ensure TerminalHandler is subscribed to EventBus
            new Thread(() -> {
                try {
                    Thread.sleep(50); // 50ms delay to let handler initialize
                    Minecraft.getInstance().execute(() -> sendFakeSlotUpdates(gui));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    /**
     * Send fake ScreenHandlerSlotUpdateS2CPacket for each slot in the GUI
     * This allows TerminalSolver to detect items in F7Sim terminal GUIs
     */
    private void sendFakeSlotUpdates(TermSimGUI gui) {
        try {
            // Get the EventBus from HunchModClient
            meteordevelopment.orbit.IEventBus eventBus = dev.hunchclient.HunchModClient.EVENT_BUS;

            if (eventBus == null) {
                return;
            }

            // Get the inventory from the GUI
            net.minecraft.world.SimpleContainer inventory = gui.inventory;
            if (inventory == null) {
                return;
            }

            // Send slot update packet for each slot
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack stack = inventory.getItem(slot);
                // Create fake packet for every slot (even empty ones, to clear them)
                net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet =
                    new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                        0,     // syncId (doesn't matter for client-side)
                        0,     // revision (doesn't matter)
                        slot,  // slot index
                        stack != null ? stack : net.minecraft.world.item.ItemStack.EMPTY  // item stack
                    );

                // Post packet event
                dev.hunchclient.event.PacketEvent.Receive event =
                    dev.hunchclient.event.PacketEvent.Receive.of(packet);
                eventBus.post(event);
            }

            // IMPORTANT: Terminal handlers trigger on the LAST slot
            // Send the last slot again to ensure solution is calculated
            int lastSlot = inventory.getContainerSize() - 1;
            if (lastSlot >= 0) {
                net.minecraft.world.item.ItemStack lastStack = inventory.getItem(lastSlot);
                net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket triggerPacket =
                    new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                        0, 0, lastSlot,
                        lastStack != null ? lastStack : net.minecraft.world.item.ItemStack.EMPTY
                    );
                dev.hunchclient.event.PacketEvent.Receive triggerEvent =
                    dev.hunchclient.event.PacketEvent.Receive.of(triggerPacket);
                eventBus.post(triggerEvent);
            }
        } catch (Exception e) {
            // Silently fail if TerminalSolver is not enabled
            e.printStackTrace();
        }
    }

    /**
     * Get the ping delay from F7Sim module
     */
    private int getPingDelay() {
        try {
            dev.hunchclient.module.ModuleManager manager = dev.hunchclient.module.ModuleManager.getInstance();
            F7SimModule f7Sim = manager.getModule(F7SimModule.class);
            if (f7Sim != null && f7Sim.isEnabled()) {
                return f7Sim.getTerminalPingMs();
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Create a terminal GUI based on type.
     * Supports all six Skyblock terminal types.
     */
    private TermSimGUI createTerminalGUI(TerminalType type, Inventory playerInventory) {
        switch (type) {
            case PANES:
                return new PanesSim(playerInventory);
            case NUMBERS:
                return new NumbersSim(playerInventory);
            case RUBIX:
                return new RubixSim(playerInventory);
            case MELODY:
                return new MelodySim(playerInventory);
            case STARTS_WITH:
                return new StartsWithSim(playerInventory);
            case SELECT:
                return new SelectAllSim(playerInventory);
            default:
                // Fallback to PANES if unknown type
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Terminal] " + type + " not yet implemented, using PANES instead"), false);
                }
                return new PanesSim(playerInventory);
        }
    }

    /**
     * Called when all terminals are completed
     */
    private void onAllTerminalsCompleted() {
        // Reset after 2 seconds
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                resetTerminals();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Get the list of terminals
     */
    public List<TerminalPosition> getTerminals() {
        return terminals;
    }

    /**
     * Get the number of completed terminals
     */
    public int getCompletedCount() {
        return completedCount;
    }

    /**
     * Get the total number of terminals
     */
    public int getTotalCount() {
        return terminals.size();
    }

    /**
     * Check if all terminals are completed
     */
    public boolean areAllTerminalsCompleted() {
        return allTerminalsCompleted;
    }
}
