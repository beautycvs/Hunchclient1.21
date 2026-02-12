package dev.hunchclient.module.impl.terminal;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Base class for clientside terminal simulation GUIs. */
public abstract class TermSimGUI extends ContainerScreen {
    protected final TerminalType terminalType;
    public final SimpleContainer inventory; // Public for TerminalManager access (fake packets)
    protected final Random random = new Random();
    protected int simulatedPing = 0;
    protected long lastClickTime = 0;
    protected boolean isCompleted = false;
    protected Runnable onCompleteCallback;
    private boolean layoutInitialized = false;
    private long sessionStartTime = 0L;
    private long lastTickTimeMs = net.minecraft.Util.getMillis();
    private long tickAccumulatorMs = 0L;

    // Visual feedback for correct/wrong clicks
    private int feedbackColor = 0;
    private float feedbackAlpha = 0.0f;
    private long feedbackStartTime = 0;

    // Realistic ping simulation: clicks are queued and processed after delay
    private final java.util.concurrent.ConcurrentLinkedQueue<PendingClick> pendingClicks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.Set<Integer> slotsBeingProcessed = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static class PendingClick {
        final int slot;
        final int button;
        final long processTime; // When this click should be processed

        PendingClick(int slot, int button, long processTime) {
            this.slot = slot;
            this.button = button;
            this.processTime = processTime;
        }
    }

    public TermSimGUI(TerminalType type, Inventory playerInventory) {
        super(createScreenHandler(type, playerInventory), playerInventory, Component.literal(type.getDisplayName()));
        this.terminalType = type;
        this.inventory = (SimpleContainer) this.menu.getContainer();
    }

    /**
     * Constructor with custom title (for StartsWithSim, SelectAllSim etc)
     */
    public TermSimGUI(TerminalType type, Inventory playerInventory, String customTitle) {
        super(createScreenHandler(type, playerInventory), playerInventory, Component.literal(customTitle));
        this.terminalType = type;
        this.inventory = (SimpleContainer) this.menu.getContainer();
    }

    private static ChestMenu createScreenHandler(TerminalType type, Inventory playerInventory) {
        int size = type.getWindowSize();
        SimpleContainer inv = new SimpleContainer(size);
        int rows = size / 9;

        MenuType<?> handlerType;
        if (rows == 6) {
            handlerType = MenuType.GENERIC_9x6;
        } else if (rows == 5) {
            handlerType = MenuType.GENERIC_9x5;
        } else if (rows == 4) {
            handlerType = MenuType.GENERIC_9x4;
        } else {
            handlerType = MenuType.GENERIC_9x3;
        }

        return new ChestMenu(handlerType, 0, playerInventory, inv, rows);
    }

    /**
     * Initialize the terminal layout after construction.
     */
    public void initializeLayout() {
    if (layoutInitialized) {
        return;
    }
    layoutInitialized = true;

    // Send fake OpenScreenS2CPacket to initialize terminal handler (first open)
    try {
        meteordevelopment.orbit.IEventBus eventBus = dev.hunchclient.HunchModClient.EVENT_BUS;
        if (eventBus != null) {
            net.minecraft.network.protocol.game.ClientboundOpenScreenPacket openPacket =
                new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                    0,  // syncId
                    this.menu.getType(),  // screen type
                    Component.literal(terminalType.getDisplayName())  // title
                );
            dev.hunchclient.event.PacketEvent.Receive openEvent =
                dev.hunchclient.event.PacketEvent.Receive.of(openPacket);
            eventBus.post(openEvent);
        }
    } catch (Exception e) {
        // Silently fail if TerminalSolver is not enabled
    }

    initializeBackground();
    initializeTerminal();
    sessionStartTime = System.currentTimeMillis();
    lastTickTimeMs = net.minecraft.Util.getMillis();
    tickAccumulatorMs = 0L;
}

    /**
     * Called every render for animations (like Melody)
     * We use render instead of tick because tick() is final in HandledScreen
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        long now = net.minecraft.Util.getMillis();
        tickAccumulatorMs += now - lastTickTimeMs;
        lastTickTimeMs = now;

        final long tickDurationMs = 50L; // 20 ticks per second
        while (tickAccumulatorMs >= tickDurationMs) {
            containerTick();
            tickAccumulatorMs -= tickDurationMs;
        }

        // Process pending clicks (realistic ping simulation)
        processPendingClicks();

        // Render visual feedback overlay
        if (feedbackAlpha > 0.0f) {
            long elapsed = System.currentTimeMillis() - feedbackStartTime;
            float fadeDuration = 300.0f; // 300ms fade out
            feedbackAlpha = Math.max(0.0f, 1.0f - (elapsed / fadeDuration));

            if (feedbackAlpha > 0.0f) {
                int alpha = (int)(feedbackAlpha * 0.3f * 255); // Max 30% opacity
                int color = (alpha << 24) | (feedbackColor & 0xFFFFFF);
                context.fill(0, 0, this.width, this.height, color);
            }
        }
    }

    /**
     * Process clicks that have finished their "network delay"
     * This simulates the server response coming back after ping delay
     */
    private void processPendingClicks() {
        if (isCompleted) {
            pendingClicks.clear();
            slotsBeingProcessed.clear();
            return;
        }

        long currentTime = System.currentTimeMillis();
        PendingClick click;

        while ((click = pendingClicks.peek()) != null && click.processTime <= currentTime) {
            pendingClicks.poll(); // Remove from queue
            slotsBeingProcessed.remove(click.slot);

            // Now actually process the click (server responded)
            processClickResult(click.slot, click.button);
        }
    }

    /**
     * Process the actual click result after ping delay
     * This is what happens when the "server responds"
     */
    private void processClickResult(int slot, int button) {
        if (isCompleted) return;

        Boolean result = handleTerminalClickWithResult(slot, button);

        if (result == null) {
            return; // Ignored click
        } else if (result) {
            // Correct click - simulate GUI refresh like real Hypixel
            if (dev.hunchclient.module.impl.terminalsolver.gui.TermGui.currentTerm != null) {
                dev.hunchclient.module.impl.terminalsolver.gui.TermGui.currentTerm.simulateClick(slot, button);
            }
            simulateTerminalRefresh();

            if (checkCompletion()) {
                isCompleted = true;
                onTerminalComplete();
            }
        } else {
            // Wrong click - fail and close terminal
            onTerminalFail();
        }
    }

    /**
     * Override this for terminal-specific tick logic (e.g. Melody animation)
     */
    protected void containerTick() {
        // Override in subclasses
    }

    /**
     * Trigger visual feedback for click result
     * @param isCorrect true for green (success), false for red (fail)
     */
    protected void triggerVisualFeedback(boolean isCorrect) {
        feedbackColor = isCorrect ? 0x00FF00 : 0xFF0000; // Green or red
        feedbackAlpha = 1.0f;
        feedbackStartTime = System.currentTimeMillis();
    }

    /**
     * Play the standard success/fail sound used by all terminals.
     */
    protected void playClickSound(boolean isCorrect) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        client.player.playSound(
            isCorrect ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
            0.5f,
            isCorrect ? 1.5f : 0.5f
        );
    }

    /**
     * Initialize background with black stained glass panes
     */
    protected void initializeBackground() {
        ItemStack blackPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        blackPane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, blackPane.copy());
        }
    }

    /**
     * Override this to initialize terminal-specific items
     */
    protected abstract void initializeTerminal();

    /**
     * Override this to handle terminal-specific click logic
     * @param slot The slot that was clicked
     * @param button The mouse button (0 = left, 1 = right)
     * @return true if the click was valid for this terminal
     */
    protected boolean handleTerminalClick(int slot, int button) {
        // Default implementation for backwards compatibility
        return handleTerminalClick(slot);
    }

    /**
     * Override this to handle terminal-specific click logic (simple version)
     * @param slot The slot that was clicked
     * @return true if the click was valid for this terminal
     */
    protected boolean handleTerminalClick(int slot) {
        // Default: delegate to button-aware version with left click
        return false;
    }

    /**
     * Check if the terminal is completed
     */
    protected abstract boolean checkCompletion();

    @Override
    protected void slotClicked(net.minecraft.world.inventory.Slot slot, int slotId, int button, ClickType actionType) {
        // Prevent default container behavior
        if (slot == null) {
            super.slotClicked(null, slotId, button, actionType);
            return;
        }

        if (isCompleted || slot.container != inventory) {
            return;
        }

        // Ignore black panes (background)
        ItemStack stack = slot.getItem();
        if (stack.is(Items.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        // Get the actual inventory slot index
        int inventorySlot = slot.getContainerSlot();

        // Check if this slot is already being processed (waiting for server response)
        if (slotsBeingProcessed.contains(inventorySlot)) {
            return; // Can't click same slot while waiting for response
        }

        lastClickTime = System.currentTimeMillis();

        // REALISTIC PING SIMULATION:
        // If ping is 0, process immediately (no delay)
        // Otherwise, queue the click to be processed after ping delay
        if (simulatedPing <= 0) {
            // No ping - process immediately (old behavior)
            Boolean result = handleTerminalClickWithResult(inventorySlot, button);

            if (result == null) {
                return;
            } else if (result) {
                simulateTerminalRefresh();
                if (checkCompletion()) {
                    isCompleted = true;
                    onTerminalComplete();
                }
            } else {
                onTerminalFail();
            }
        } else {
            // Queue click to be processed after ping delay (realistic behavior)
            slotsBeingProcessed.add(inventorySlot);
            long processTime = System.currentTimeMillis() + simulatedPing;
            pendingClicks.add(new PendingClick(inventorySlot, button, processTime));
        }
    }

    /**
     * Handle a custom click initiated by the TerminalSolver overlay.
     * Mirrors the internal logic of {@link #checkHotbarMouseClicked} but works directly with slot indices.
     */
    public boolean handleCustomClick(int slotIndex, int button) {
        if (isCompleted || slotIndex < 0 || slotIndex >= inventory.getContainerSize()) {
            return false;
        }

        ItemStack stack = inventory.getItem(slotIndex);

        if (stack.is(Items.BLACK_STAINED_GLASS_PANE)) {
            return false;
        }

        // Check if this slot is already being processed (waiting for server response)
        if (slotsBeingProcessed.contains(slotIndex)) {
            return false; // Can't click same slot while waiting for response
        }

        lastClickTime = System.currentTimeMillis();

        // REALISTIC PING SIMULATION for custom clicks too
        if (simulatedPing > 0) {
            // Queue click to be processed after ping delay
            slotsBeingProcessed.add(slotIndex);
            long processTime = System.currentTimeMillis() + simulatedPing;
            pendingClicks.add(new PendingClick(slotIndex, button, processTime));
            return true; // Click accepted (queued)
        }

        // No ping - process immediately
        Boolean result = handleTerminalClickWithResult(slotIndex, button);

        if (result == null) {
            return false;
        } else if (result) {
            // SUCCESS - already handled in child class (sound played there)
            if (dev.hunchclient.module.impl.terminalsolver.gui.TermGui.currentTerm != null) {
                dev.hunchclient.module.impl.terminalsolver.gui.TermGui.currentTerm.simulateClick(slotIndex, button);
            }
            simulateTerminalRefresh();

            if (checkCompletion()) {
                isCompleted = true;
                onTerminalComplete();
            }
            return true;
        } else {
            // FAIL - already handled in child class (sound played there)
            onTerminalFail();
        }
        return true;
    }

    /**
     * Simulate a terminal GUI refresh (like Hypixel does after each click)
     * This sends a fake OpenScreenS2CPacket to trigger the TerminalHandler's refresh logic
     */
    protected void simulateTerminalRefresh() {
        // Visual feedback - briefly clear and restore all items (mimics Hypixel's refresh)
        Minecraft.getInstance().execute(() -> {
            // Store current items
            ItemStack[] tempItems = new ItemStack[inventory.getContainerSize()];
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                tempItems[i] = inventory.getItem(i).copy();
            }

            // Clear all slots briefly (visual refresh effect)
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                inventory.setItem(i, ItemStack.EMPTY);
            }

            // Restore items after 1 tick (50ms)
            Minecraft.getInstance().execute(() -> {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    inventory.setItem(i, tempItems[i]);
                }
            });
        });

        try {
            meteordevelopment.orbit.IEventBus eventBus = dev.hunchclient.HunchModClient.EVENT_BUS;
            if (eventBus != null) {
                // Send fake OpenScreenS2CPacket to simulate terminal refresh
                net.minecraft.network.protocol.game.ClientboundOpenScreenPacket refreshPacket =
                    new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                        menu.containerId,  // Use current sync ID
                        menu.getType(),  // Current screen type
                        getTitle()  // Current title
                    );
                dev.hunchclient.event.PacketEvent.Receive refreshEvent =
                    dev.hunchclient.event.PacketEvent.Receive.of(refreshPacket);
                eventBus.post(refreshEvent);

                // Re-send all slot updates to refresh the solver's view
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket slotPacket =
                            new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                                menu.containerId, 0, i, stack
                            );
                        dev.hunchclient.event.PacketEvent.Receive slotEvent =
                            dev.hunchclient.event.PacketEvent.Receive.of(slotPacket);
                        eventBus.post(slotEvent);
                    }
                }

                // Send final slot update to trigger solution recalculation
                int lastSlot = inventory.getContainerSize() - 1;
                ItemStack lastStack = inventory.getItem(lastSlot);
                if (lastStack != null) {
                    net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket finalPacket =
                        new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                            menu.containerId, 0, lastSlot, lastStack
                        );
                    dev.hunchclient.event.PacketEvent.Receive finalEvent =
                        dev.hunchclient.event.PacketEvent.Receive.of(finalPacket);
                    eventBus.post(finalEvent);
                }
            }
        } catch (Exception e) {
            // Silently fail if TerminalSolver is not enabled
        }
    }

    /**
     * Handle click with 3-state result: true=correct, false=wrong, null=ignore
     * Override this for terminals that need fail handling
     */
    protected Boolean handleTerminalClickWithResult(int slot, int button) {
        // Default: convert old boolean to Boolean (null = ignore)
        boolean result = handleTerminalClick(slot, button);
        return result ? true : null; // true=correct, null=ignore (old behavior)
    }

    /**
     * Called when player clicks wrong item - close GUI and reset terminal
     */
    protected void onTerminalFail() {
        // CRITICAL: Stop mouse emulator BEFORE closing GUI to prevent ydotool crashes
        stopMouseEmulatorImmediately();

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            client.player.displayClientMessage(Component.literal("§c✗ Wrong! Try again..."), false);
            client.execute(() -> client.setScreen(null));
        }
    }

    /**
     * Called when the terminal is successfully completed
     */
    protected void onTerminalComplete() {
        // Normal completion - do a SOFT reset (triggers visualization) but ASYNC to not block MC thread
        // This is different from emergency stop which kills processes synchronously
        new Thread(() -> {
            try {
                dev.hunchclient.util.HumanMouseEmulator emulator = dev.hunchclient.util.HumanMouseEmulator.get();
                if (emulator != null) {
                    // Just reset (starts visualization), NO emergency stop needed for normal completion
                    emulator.reset();
                    System.out.println("[TermSimGUI] Normal completion - async reset triggered");
                }
            } catch (Exception e) {
                System.err.println("[TermSimGUI] Error in async reset: " + e.getMessage());
            }
        }, "TermSimGUI-AsyncReset").start();

        long completionTime = sessionStartTime > 0L ? System.currentTimeMillis() - sessionStartTime : 0L;

        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            if (client.player != null) {
                double seconds = completionTime / 1000.0;
                String message = String.format("§aTerminal solved in §e%.3f§as", seconds);
                client.player.displayClientMessage(Component.literal(message), false);
            }
            client.execute(() -> client.setScreen(null));
        }
    }

    /**
     * CRITICAL: Stop mouse emulator immediately before GUI closes
     * This prevents ydotool from continuing to run after the terminal is gone
     */
    private static void stopMouseEmulatorImmediately() {
        try {
            dev.hunchclient.util.HumanMouseEmulator emulator = dev.hunchclient.util.HumanMouseEmulator.get();
            if (emulator != null) {
                // EMERGENCY STOP - kills all ydotool processes immediately
               // emulator.emergencyStop();
                // Reset triggers visualization and clears state
                emulator.reset();
                System.out.println("[TermSimGUI] Mouse emulator EMERGENCY STOPPED before GUI close");
            }
        } catch (Exception e) {
            System.err.println("[TermSimGUI] Error stopping mouse emulator: " + e.getMessage());
        }
    }

    /**
     * Handle key presses - specifically catch ESC to trigger emergency stop
     * This is MORE SPECIFIC than close() which fires on ANY screen change
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        // ESC key = 256 in GLFW
        if (input.key() == 256 && !isCompleted) {
            System.out.println("[TermSimGUI] ESC pressed during incomplete terminal - emergency stopping");
            stopMouseEmulatorImmediately();
        }
        return super.keyPressed(input);
    }

    /**
     * Set the simulated ping delay in milliseconds
     */
    public void setSimulatedPing(int ping) {
        this.simulatedPing = Math.max(0, ping);
    }

    /**
     * Set callback for when terminal is completed
     */
    public void setOnCompleteCallback(Runnable callback) {
        this.onCompleteCallback = callback;
    }

    public boolean isTerminalCompleted() {
        return isCompleted;
    }

    /**
     * Helper to set a stack in the inventory at a specific slot.
     * Sends a fake ScreenHandlerSlotUpdateS2CPacket so the TerminalSolver sees the change.
     */
    protected void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.getContainerSize()) {
            inventory.setItem(slot, stack);

            // Send fake packet for TerminalSolver
            try {
                meteordevelopment.orbit.IEventBus eventBus = dev.hunchclient.HunchModClient.EVENT_BUS;
                if (eventBus != null) {
                    net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet =
                        new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                            0, 0, slot,
                            stack != null ? stack : net.minecraft.world.item.ItemStack.EMPTY
                        );
                    dev.hunchclient.event.PacketEvent.Receive event =
                        dev.hunchclient.event.PacketEvent.Receive.of(packet);
                    eventBus.post(event);

                    // Mimic server behaviour: a final slot update re-triggers solver calculations.
                    int lastSlot = inventory.getContainerSize() - 1;
                    if (lastSlot >= 0) {
                        net.minecraft.world.item.ItemStack lastStack = inventory.getItem(lastSlot);
                        net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket sentinelPacket =
                            new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                                0, 0, lastSlot,
                                lastStack != null ? lastStack : net.minecraft.world.item.ItemStack.EMPTY
                            );
                        dev.hunchclient.event.PacketEvent.Receive sentinelEvent =
                            dev.hunchclient.event.PacketEvent.Receive.of(sentinelPacket);
                        eventBus.post(sentinelEvent);
                    }
                }
            } catch (Exception e) {
                // Silently fail if TerminalSolver is not enabled
            }
        }
    }

    /**
     * Helper method to get a stack from the inventory
     */
    protected ItemStack getStack(int slot) {
        if (slot >= 0 && slot < inventory.getContainerSize()) {
            return inventory.getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Helper method to create a named item stack
     */
    protected ItemStack createNamedStack(ItemStack base, String name) {
        ItemStack stack = base.copy();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

}
