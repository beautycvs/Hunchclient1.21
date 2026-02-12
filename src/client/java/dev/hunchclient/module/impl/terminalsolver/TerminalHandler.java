package dev.hunchclient.module.impl.terminalsolver;

import dev.hunchclient.event.PacketEvent;
import dev.hunchclient.module.impl.TerminalSolverModule;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.orbit.IEventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.hunchclient.util.Utils;

/** Base class for all terminal handlers. */
public abstract class TerminalHandler {
    protected static final boolean DEBUG = false;
    protected static final Minecraft mc = Minecraft.getInstance();

    public final TerminalTypes type;
    public final List<Integer> solution = new CopyOnWriteArrayList<>();
    public final ItemStack[] items;
    public long timeOpened;
    public boolean isClicked = false;
    // Track if terminal was actually solved/skipped (to allow retries after failed early clicks)
    public boolean isSolved = false;

    // SA-style: Click queue
    private final List<QueuedClick> clickQueue = new CopyOnWriteArrayList<>();
    private int windowId = -1;

    // Track clicks sent but not yet confirmed (to prevent duplicate clicks)
    protected final Set<Integer> pendingClicks = new HashSet<>();

    // Track previous solution to detect if server accepted click
    private List<Integer> previousSolution = new ArrayList<>();

    // SA-style: setTimeout executor
    private static final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TerminalTimeout");
        t.setDaemon(true);
        return t;
    });

    // Track timeout tasks so we can cancel them (for Melody terminal)
    private volatile java.util.concurrent.ScheduledFuture<?> currentTimeout = null;

    // Reference to module settings
    private static TerminalSolverModule solverModule = null;

    protected final IEventBus eventBus;

    /**
     * Set the TerminalSolverModule reference for settings access
     */
    public static void setModule(TerminalSolverModule module) {
        solverModule = module;
        if (DEBUG) {
            System.out.println("[TermHandler] setModule called: solverModule=" + (module != null ? "SET" : "NULL") +
                ", queueDelayMin=" + (module != null ? module.queueDelayMin : "N/A") +
                ", queueDelayMax=" + (module != null ? module.queueDelayMax : "N/A"));
        }
    }

    /**
     * Get the solver module reference (for debugging)
     */
    public static TerminalSolverModule getModule() {
        return solverModule;
    }

    public TerminalHandler(TerminalTypes type, IEventBus eventBus) {
        this.type = type;
        this.items = new ItemStack[type.windowSize];
        this.timeOpened = System.currentTimeMillis();
        this.eventBus = eventBus;

        // Subscribe to events
        eventBus.subscribe(this);
    }

    // SA-style: count how many slots we have received (not slotsReceived counter)
    private int getFilledSlotCount() {
        int count = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) count++;
        }
        return count;
    }

    // Lock object for thread-safe slot updates
    private final Object solveLock = new Object();
    private volatile boolean solveInProgress = false;
    // Prevent multiple solves per refresh cycle (Netty + Render thread both receive packets)
    private volatile boolean hasSolvedThisRefresh = false;

    @EventHandler(priority = EventPriority.LOW)
    public void onPacketReceive(PacketEvent.Receive event) {
        // NOTE: OpenScreen is handled by TerminalSolverModule, NOT here!
        // TerminalSolverModule calls onTerminalRefresh() when needed.

        // === SA setSlotListener ===
        if (event.packet instanceof ClientboundContainerSetSlotPacket packet) {
            int slot = packet.getSlot();
            if (slot < 0 || slot >= type.windowSize) return;

            ItemStack receivedItem = packet.getItem();

            // Thread-safe: synchronize slot updates and solve trigger
            synchronized (solveLock) {
                // ALWAYS store item data (even after solve)
                items[slot] = receivedItem;

                // Melody terminal: ALWAYS re-solve on every slot update (lime pane moves continuously)
                if (type == TerminalTypes.MELODY) {
                    if (!solveInProgress) {
                        handleSlotUpdate(packet);
                    }
                    return;
                }

                // Skip solving if already solved this refresh cycle
                if (solveInProgress || hasSolvedThisRefresh) return;

                int filledCount = getFilledSlotCount();

                // SA: if (slots.length === windowSize)
                if (filledCount == type.windowSize) {
                    solveInProgress = true;
                    hasSolvedThisRefresh = true; // Only solve ONCE per refresh cycle
                    try {
                        if (DEBUG) {
                            String itemName = receivedItem != null && !receivedItem.isEmpty()
                                ? receivedItem.getHoverName().getString() : "(empty)";
                            System.out.println("[TermHandler] All " + type.windowSize + " slots filled (last: slot " + slot + " '" + itemName + "') - triggering solve!");
                        }

                        // SA: solve()
                        handleSlotUpdate(null);

                        // Record solve for debug dump
                        TerminalDebugger.getInstance().recordSolve(this, getSearchCriteria());

                        if (DEBUG) {
                            System.out.println("[TermHandler] After solve, solution = " + solution);
                        }

                        // Process queued clicks (high ping mode)
                        // This validates queue against solution and sends next click
                        processQueueAfterSolve();
                    } finally {
                        solveInProgress = false;
                    }
                }
            }
        }
    }

    /**
     * Process queued clicks after solve() - validate and send next click
     * Thread-safe: uses synchronized block to prevent race conditions
     */
    private void processQueueAfterSolve() {
        synchronized (clickQueue) {
            if (clickQueue.isEmpty()) {
                if (DEBUG) System.out.println("[TermHandler] processQueue: queue empty, nothing to do");
                return;
            }

            // Get delay settings once at the start
            int delayMin = solverModule != null ? solverModule.queueDelayMin : 1;
            int delayMax = solverModule != null ? solverModule.queueDelayMax : 10;

            if (DEBUG) {
                System.out.println("[TermHandler] processQueue: queue=" + clickQueue + ", solution=" + solution +
                    ", delayMin=" + delayMin + "ms, delayMax=" + delayMax + "ms (solverModule=" + (solverModule != null ? "set" : "NULL") + ")");
            }

            // Validate all queued clicks against current solution
            boolean allValid = true;
            QueuedClick invalidClick = null;
            
            // CRITICAL FIX for Numbers Terminal:
            // For order-dependent terminals, queued slots must match the BEGINNING of the solution
            // in the exact same order. Otherwise the queue is invalid.
            if (type == TerminalTypes.NUMBERS) {
                // Numbers terminal: Queue must match first N slots of solution in order
                if (clickQueue.size() > solution.size()) {
                    allValid = false;
                    if (DEBUG) {
                        System.out.println("[TermHandler] Numbers Queue INVALID: queue size " + clickQueue.size() + " > solution size " + solution.size());
                    }
                } else {
                    for (int i = 0; i < clickQueue.size(); i++) {
                        int queuedSlot = clickQueue.get(i).slot();
                        int expectedSlot = solution.get(i);
                        if (queuedSlot != expectedSlot) {
                            allValid = false;
                            invalidClick = clickQueue.get(i);
                            if (DEBUG) {
                                System.out.println("[TermHandler] Numbers Queue INVALID: slot[" + i + "]=" + queuedSlot + " != expected " + expectedSlot);
                            }
                            break;
                        }
                    }
                }
            } else {
                // Other terminals: Just check if each queued slot is in solution (any position)
                for (QueuedClick qc : clickQueue) {
                    if (!solution.contains(qc.slot())) {
                        allValid = false;
                        invalidClick = qc;
                        break;
                    }
                }
            }

            if (allValid && !clickQueue.isEmpty()) {
                // Re-simulate all queued clicks to keep local solution in sync
                // This is NECESSARY because solution was just rebuilt from server items
                // Without this, autoClickTick would pick slots that are already queued
                for (QueuedClick qc : clickQueue) {
                    simulateClick(qc.slot(), qc.button());
                }

                // Send the first queued click to server with random delay
                QueuedClick first = clickQueue.remove(0);
                final int slot = first.slot();
                final int button = first.button();

                // Calculate random delay
                int randomDelay = delayMin + (int)(Math.random() * (delayMax - delayMin + 1));

                if (DEBUG) {
                    System.out.println("[TermHandler] processQueue: scheduling queued click slot=" + slot +
                        " with delay=" + randomDelay + "ms (min=" + delayMin + ", max=" + delayMax + ")");
                }

                // Schedule with random delay
                timeoutExecutor.schedule(() -> {
                    mc.execute(() -> click(slot, button, false));
                }, randomDelay, TimeUnit.MILLISECONDS);
            } else {
                // Invalid queued clicks - clear queue
                if (DEBUG && invalidClick != null) {
                    System.out.println("[TermHandler] processQueue: INVALID click slot=" + invalidClick.slot() +
                        " not in solution=" + solution + " - clearing queue");
                }
                clickQueue.clear();
            }
        }
    }

    /**
     * Called when terminal refreshes (OpenScreen packet for same terminal type)
     * OpenScreen = server confirmation that previous click was processed
     * @param newWindowId The new window ID from OpenScreen packet
     */
    public void onTerminalRefresh(int newWindowId) {
        // SA: cwid = windowId
        this.windowId = newWindowId;
        // SA: clicked = false
        isClicked = false;
        isSolved = false;
        // Reset solve flag for new refresh cycle
        hasSolvedThisRefresh = false;
        // Clear pending clicks - server has confirmed them
        pendingClicks.clear();
        // SA: while (slots.length) slots.pop()
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
        }

        // Queue is now managed in processQueueAfterSolve() - remove before send
        // NOTE: timeOpened is NOT reset here - only on new terminal (onNewAttempt)
    }

    /**
     * Called when terminal is reopened/re-attempted (user closed and opened again)
     * This should reset all state tracking like clickedSlots
     * @param newWindowId The new window ID from OpenScreen packet
     */
    public void onNewAttempt(int newWindowId) {
        // SA: cwid = windowId
        this.windowId = newWindowId;
        // Default: reset time so click delay is reapplied
        timeOpened = System.currentTimeMillis();
        isClicked = false;
        isSolved = false;
        // Reset solve flag for new attempt
        hasSolvedThisRefresh = false;
        // Clear pending clicks
        pendingClicks.clear();
        // Clear the queue on new attempt
        clearQueue();
        // Also clear items
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
        }
    }

    /**
     * Handle slot update and return true if solution was updated
     */
    public abstract boolean handleSlotUpdate(ClientboundContainerSetSlotPacket packet);

    /**
     * Get the search criteria for debug output (e.g. letter for StartsWith, color for SelectAll)
     */
    public String getSearchCriteria() {
        return ""; // Override in subclasses
    }

    /**
     * Simulate a click for hideClicked feature
     * Calls subclass implementation and then checks if terminal is solved
     */
    public final void simulateClick(int slotIndex, int clickType) {
        simulateClickImpl(slotIndex, clickType);
        // Check if terminal is now solved (solution empty)
        if (solution.isEmpty()) {
            isSolved = true;
        }
    }

    /**
     * Override in subclasses to implement terminal-specific click simulation
     */
    protected void simulateClickImpl(int slotIndex, int clickType) {
        // Override in subclasses if needed
    }

    /**
     * Called when a click is actually SENT to the server (not just predicted/queued)
     * Override in subclasses that need to track clicks locally (e.g., Nether Star in StartsWith)
     */
    public void onClickSent(int slotIndex) {
        // Override in subclasses if needed
    }

    /**
     * SA-style click function with setTimeout
     */
    public void click(int slotIndex, int button, boolean doSimulateClick) {
        if (DEBUG) {
            System.out.println("[TermHandler] click: slot=" + slotIndex + ", button=" + button +
                ", simulate=" + doSimulateClick + ", isClicked was=" + isClicked);
        }

        if (doSimulateClick) {
            simulateClick(slotIndex, button);
        }

        // SA: clicked = true
        isClicked = true;

        if (!(mc.screen instanceof ContainerScreen screen)) {
            if (DEBUG) System.out.println("[TermHandler] click: no ContainerScreen!");
            return;
        }
        var menu = screen.getMenu();
        if (menu == null) {
            if (DEBUG) System.out.println("[TermHandler] click: no menu!");
            return;
        }

        // SA: Client.sendPacket(new C0EPacketClickWindow(cwid, slot, button, 0, null, 0))
        ClickType actionType = (button == GLFW.GLFW_MOUSE_BUTTON_3) ? ClickType.CLONE : ClickType.PICKUP;
        if (DEBUG) {
            System.out.println("[TermHandler] click: SENDING to server slot=" + slotIndex + ", containerId=" + menu.containerId);
        }
        // Track as pending until server confirms
        pendingClicks.add(slotIndex);
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, button, actionType, mc.player);

        // Notify subclass that click was actually sent (for local tracking like Nether Star)
        onClickSent(slotIndex);

        // SA: setTimeout with initialWindowId check
        final int initialWindowId = menu.containerId;
        long timeout = getQueueTimeout();
        currentTimeout = timeoutExecutor.schedule(() -> {
            // SA: if (!inTerminal || initialWindowId !== cwid) return
            if (windowId != initialWindowId) {
                if (DEBUG) System.out.println("[TermHandler] timeout: windowId changed, ignoring");
                return;
            }

            mc.execute(() -> {
                if (DEBUG) {
                    System.out.println("[TermHandler] timeout: fired after " + timeout + "ms" + 
                        (type == TerminalTypes.MELODY ? ", resetting state (no re-solve for Melody)" : ", re-solving"));
                }
                synchronized (clickQueue) {
                    clickQueue.clear();
                }
                // Clear pending clicks - timeout means server didn't respond
                pendingClicks.clear();
                
                // MELODY: Don't re-solve! Solution doesn't change on false click.
                // The lime pane just goes back, but we still need to click the same buttons.
                if (type != TerminalTypes.MELODY) {
                    // SA: solve()
                    handleSlotUpdate(null);
                } else {
                    // MELODY: Force GUI refresh even though solution doesn't change
                    if (type.getGUI() != null) {
                        type.getGUI().onSolutionUpdate();
                    }
                }
                
                // SA: clicked = false
                isClicked = false;
            });
        }, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * EXPERIMENTAL: Packet mode click - uses handleInventoryMouseClick without mouse movement
     * Uses proper Mojmap API instead of direct packet sending for ViaFabricPlus compatibility
     *
     * @param slotIndex The slot to click
     * @param button The mouse button (0 = left, 1 = right)
     * @param doSimulateClick Whether to simulate the click locally for visual feedback
     */
    public void clickPacket(int slotIndex, int button, boolean doSimulateClick) {
        if (DEBUG) {
            System.out.println("[TermHandler] clickPacket: slot=" + slotIndex + ", button=" + button +
                ", simulate=" + doSimulateClick + ", isClicked was=" + isClicked);
        }

        // CRITICAL: Cancel any pending timeout FIRST to prevent re-solve while clicking fast
        cancelTimeout();

        // HIGH PING MODE: Check FIRST if we should queue (before simulateClick!)
        // This preserves click order for order-dependent terminals like Numbers
        // CRITICAL: Use shouldQueue() which checks BOTH isClicked AND !clickQueue.isEmpty()
        // This prevents sending new clicks while queue still has pending items!
        if (shouldQueue()) {
            if (DEBUG) {
                System.out.println("[TermHandler] clickPacket: QUEUING click (isClicked=true, waiting for server)");
            }
            // For queued clicks: simulateClick to update local state
            if (doSimulateClick && type != TerminalTypes.MELODY) {
                simulateClick(slotIndex, button);
            }
            queueClick(slotIndex, button);
            return;  // Don't send packet, queue will handle it
        }

        // Not queuing - this is a direct click
        // simulateClick to update local state for visual feedback
        if (doSimulateClick && type != TerminalTypes.MELODY) {
            simulateClick(slotIndex, button);
            if (DEBUG) {
                System.out.println("[TermHandler] clickPacket: simulateClick called, solution size now=" + solution.size());
            }
        }

        // SA: clicked = true
        isClicked = true;

        // Track as pending until server confirms
        pendingClicks.add(slotIndex);

        // Execute the actual click on the main thread to avoid race conditions
        mc.execute(() -> {
            if (!(mc.screen instanceof ContainerScreen screen)) {
                if (DEBUG) System.out.println("[TermHandler] clickPacket: no ContainerScreen!");
                pendingClicks.remove(slotIndex);
                return;
            }
            var menu = screen.getMenu();
            if (menu == null) {
                if (DEBUG) System.out.println("[TermHandler] clickPacket: no menu!");
                pendingClicks.remove(slotIndex);
                return;
            }

            // Use handleInventoryMouseClick (Mojmap) for ViaFabricPlus compatibility
            ClickType actionType = (button == GLFW.GLFW_MOUSE_BUTTON_3) ? ClickType.CLONE : ClickType.PICKUP;

            if (DEBUG) {
                System.out.println("[TermHandler] clickPacket: calling handleInventoryMouseClick - slot=" + slotIndex +
                    ", containerId=" + menu.containerId);
            }

            // Use the proper Mojmap API (MultiPlayerGameMode#handleInventoryMouseClick)
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, button, actionType, mc.player);

            // Notify subclass that click was actually sent
            onClickSent(slotIndex);

            // SA: setTimeout with initialWindowId check
            final int initialWindowId = menu.containerId;
            long timeout = getQueueTimeout();
            currentTimeout = timeoutExecutor.schedule(() -> {
                if (windowId != initialWindowId) {
                    if (DEBUG) System.out.println("[TermHandler] clickPacket timeout: windowId changed, ignoring");
                    return;
                }

                mc.execute(() -> {
                    if (DEBUG) {
                        System.out.println("[TermHandler] clickPacket timeout: fired after " + timeout + "ms" +
                            (type == TerminalTypes.MELODY ? ", resetting state (no re-solve for Melody)" : ", re-solving"));
                    }
                    synchronized (clickQueue) {
                        clickQueue.clear();
                    }
                    pendingClicks.clear();

                    if (type != TerminalTypes.MELODY) {
                        handleSlotUpdate(null);
                    } else {
                        if (type.getGUI() != null) {
                            type.getGUI().onSolutionUpdate();
                        }
                    }

                    isClicked = false;
                });
            }, timeout, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Cancel the current timeout (used by Melody when row changes)
     */
    protected void cancelTimeout() {
        if (currentTimeout != null && !currentTimeout.isDone()) {
            currentTimeout.cancel(false);
            if (DEBUG) System.out.println("[TermHandler] Cancelled pending timeout");
        }
    }

    /**
     * Check if a click is valid
     */
    public boolean canClick(int slotIndex, int button) {
        // Don't allow clicking slots that are pending confirmation
        if (pendingClicks.contains(slotIndex)) {
            if (DEBUG) System.out.println("[TermHandler] canClick: slot=" + slotIndex + " is PENDING, rejecting");
            return false;
        }

        // Don't allow clicking slots already in queue
        synchronized (clickQueue) {
            for (QueuedClick qc : clickQueue) {
                if (qc.slot() == slotIndex) {
                    if (DEBUG) System.out.println("[TermHandler] canClick: slot=" + slotIndex + " already in QUEUE, rejecting");
                    return false;
                }
            }
        }

        int needed = 0;
        for (int slot : solution) {
            if (slot == slotIndex) needed++;
        }

        // Melody terminal
        if (type == TerminalTypes.MELODY) {
            return Utils.equalsOneOf(slotIndex, new int[]{16, 25, 34, 43});
        }

        // Not in solution
        if (!solution.contains(slotIndex)) {
            return false;
        }

        // Numbers terminal - must click in order
        if (type == TerminalTypes.NUMBERS && !solution.isEmpty() && slotIndex != solution.get(0)) {
            return false;
        }

        // Rubix terminal - check click type
        if (type == TerminalTypes.RUBIX) {
            if (needed < 3 && button == 1) return false;
            if (Utils.equalsOneOf(needed, new int[]{3, 4}) && button != 1) return false;
        }

        return true;
    }

    /**
     * Check if a slot click is pending (sent but not yet confirmed)
     */
    public boolean isSlotPending(int slotIndex) {
        return pendingClicks.contains(slotIndex);
    }

    /**
     * Add a slot to pending clicks (for HumanMouseEmulator mode)
     * Call this when a real mouse click is sent to track server confirmation.
     */
    public void addPendingClick(int slotIndex) {
        pendingClicks.add(slotIndex);
    }

    public void unsubscribe() {
        eventBus.unsubscribe(this);
    }

    // ==================== HIGH PING MODE: CLICK QUEUE (NoamAddons-style) ====================

    /**
     * Check if high ping mode is enabled for this terminal type.
     * Uses the setting from TerminalSolverModule.
     * Override in subclasses to disable high ping mode for specific terminals (e.g., Melody).
     */
    public boolean isHighPingModeEnabled() {
        return solverModule != null && solverModule.highPingMode;
    }

    /**
     * Check if new clicks should be queued instead of sent directly.
     * Rule: Once we start queuing, ALL subsequent clicks must also queue.
     * This prevents race conditions where a fast click bypasses the queue
     * and arrives at the server before queued clicks (breaks Numbers terminal order).
     *
     * @return true if clicks should be queued
     */
    public boolean shouldQueue() {
        if (!isHighPingModeEnabled()) {
            return false;
        }
        // Queue if: already clicked (waiting for server) OR queue has pending items
        synchronized (clickQueue) {
            boolean should = isClicked || !clickQueue.isEmpty();
            if (DEBUG && should) {
                System.out.println("[TermHandler] shouldQueue: YES (isClicked=" + isClicked + ", queueSize=" + clickQueue.size() + ")");
            }
            return should;
        }
    }

    /**
     * Check if the click queue has pending items
     */
    public boolean hasQueuedClicks() {
        synchronized (clickQueue) {
            return !clickQueue.isEmpty();
        }
    }

    /**
     * Get the queue timeout from settings (default 600ms like NoamAddons)
     */
    protected long getQueueTimeout() {
        return solverModule != null ? solverModule.queueTimeout : 600;
    }

    /**
     * SA-style: Queue a click (just add to queue, processing happens in setSlotListener)
     */
    public void queueClick(int slot, int button) {
        synchronized (clickQueue) {
            clickQueue.add(new QueuedClick(slot, button, System.currentTimeMillis()));
            if (DEBUG) {
                System.out.println("[TermHandler] queueClick: added slot=" + slot + ", queue size now=" + clickQueue.size());
            }
        }
    }

    /**
     * Get the current click queue (read-only copy)
     */
    public List<QueuedClick> getClickQueue() {
        synchronized (clickQueue) {
            return List.copyOf(clickQueue);
        }
    }

    /**
     * SA-style: Clear the click queue
     */
    public void clearQueue() {
        synchronized (clickQueue) {
            if (DEBUG && !clickQueue.isEmpty()) {
                System.out.println("[TermHandler] clearQueue: clearing " + clickQueue.size() + " queued clicks");
            }
            clickQueue.clear();
        }
    }

}
