package dev.hunchclient.module.impl;

import dev.hunchclient.event.GuiEvent;
import dev.hunchclient.event.PacketEvent;
import dev.hunchclient.event.TerminalEvent;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.module.impl.terminalsolver.*;
import dev.hunchclient.module.impl.terminalsolver.gui.*;
import dev.hunchclient.render.NVGRenderer;
import com.google.gson.JsonObject;
import dev.hunchclient.util.Color;
import dev.hunchclient.util.Colors;
import dev.hunchclient.util.HumanMouseEmulator;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.orbit.IEventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Terminal Solver module for handling all F7 terminal types end-to-end. */
public class TerminalSolverModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.ITerminalSolver {

    // Settings - ideally configurable via a GUI
    public boolean renderType = false; // false = Normal, true = Custom GUI

    @Override
    public boolean getRenderType() { return renderType; }
    public boolean cancelToolTip = true;
    public boolean hideClicked = false;
    private boolean blockIncorrectClicks = true;
    private boolean cancelMelodySolver = false;
    public boolean showNumbers = true;
    public float customTermSize = 1.5f;  // Default to 1.5 for better visibility, can go up to 3.0
    public boolean customAnimations = true;
    public float roundness = 9f;
    public float gap = 5f;
    public boolean stackedClickMode = true;
    public int queuePreviewLimit = 6;

    // Custom Font Settings
    private boolean useCustomFont = false;

    // High Ping Mode Settings (NoamAddons-style Q-Terms)
    public boolean highPingMode = false;
    public int queueTimeout = 600;  // Default 600ms like NoamAddons reSyncTimeout
    public int queueDelayMin = 1;   // Random delay before processing next queued click (min)
    public int queueDelayMax = 10;  // Random delay before processing next queued click (max)

    // Auto-Click Settings - SIMPLIFIED
    private boolean autoClickEnabled = false;
    private int clickDelay = 90;                 // Fixed delay between clicks (ms)
    private float clickSpeed = 1.0f;             // WindMouse speed multiplier (0.5 = slow, 1.0 = normal, 1.5 = fast)
    private int mouseModeIndex = 0;              // 0 = WindMouse, 1 = Spline, 2 = Bezier
    
    // Auto-Click Mode: 0 = Human Mouse, 1 = Experimental Packet
    private int autoClickModeIndex = 0;          // 0 = Human Mouse (with movement), 1 = Packet (direct)
    public int packetClickDelay = 60;            // Delay between packet clicks in ms (MINIMUM 60ms!)
    private boolean suppressPacketGui = false;   // Hide terminal GUI while packet mode auto-clicks
    
    // Tick phase tracking - CRITICAL to prevent 2 clicks in 1 tick (= BAN!)
    private long lastClickTick = -1;                // Tick number of last click
    private long currentTick = 0;                   // Current tick counter

    // Spline Mode Settings (separate from WindMouse)
    private float splineSpeedDivisor = 10.0f;    // dist/divisor = frames. Higher = faster (4-20 range)
    private float splineMinWait = 0.5f;          // Min delay between frames (ms)
    private float splineMaxWait = 3.0f;          // Max delay between frames (ms)

    // Bezier Mode Settings (BezMouse algorithm)
    private int bezierDeviation = 15;            // Control point deviation (% of distance, 10-30 typical)
    private int bezierSpeed = 2;                 // Speed multiplier (1-5, lower = faster)

    // Mouse Visualizer settings
    private boolean showMouseVisualization = true;  // Show trail/clicks after terminal closes

    // Colors - IMPORTANT: Use semi-transparent overlays so numbers are visible!
    public Color backgroundColor = Colors.gray26;
    public Color panesColor = new Color(0, 255, 0, 100); // Semi-transparent green
    public Color rubixColor1 = Colors.MINECRAFT_DARK_AQUA;
    public Color rubixColor2 = new Color(0, 100, 100);
    public Color oppositeRubixColor1 = new Color(170, 85, 0);
    public Color oppositeRubixColor2 = new Color(210, 85, 0);
    public Color melodyColumnColor = new Color(170, 0, 170, 100); // Semi-transparent purple
    public Color melodyRowColor = new Color(255, 0, 0, 100);      // Semi-transparent red
    public Color melodyPointerColor = new Color(0, 255, 0, 100);  // Semi-transparent green

    // Numbers terminal colors for custom GUI (slots 1, 2, 3+)
    public Color numbersSlot1Color = new Color(85, 255, 85);   // Bright green
    public Color numbersSlot2Color = new Color(255, 215, 0);   // Yellow/gold
    public Color numbersSlot3Color = new Color(255, 80, 80);   // Red

    // State
    public TerminalHandler currentTerm = null;
    public TerminalHandler lastTermOpened = null;
    private String lastWindowName = null; // Track window name to detect refreshes vs new terminals

    private static final long TERMINAL_REOPEN_GRACE_MS = 350L;
    private boolean pendingTermClose = false;
    private long pendingTermCloseAt = 0L;

    // Flag to prevent emergency stop during terminal refresh
    // Set when OpenScreen packet arrives (refresh), cleared after removed() is processed
    private volatile boolean refreshInProgress = false;

    private static final Minecraft mc = Minecraft.getInstance();

    private static final Pattern TERM_SOLVER_REGEX = Pattern.compile("^(.{1,16}) activated a terminal! \\((\\d)/(\\d)\\)$");
    private static final Pattern STARTS_WITH_REGEX = Pattern.compile("What starts with: '(\\w+)'\\?");
    private static final Pattern SELECT_ALL_REGEX = Pattern.compile("Select all the (.+) items!");

    private final IEventBus eventBus;

    // High-frequency executor for smooth mouse movements (not limited to 20 TPS)
    private ScheduledExecutorService autoClickExecutor;
    private java.util.concurrent.ScheduledFuture<?> autoClickTask; // Track the scheduled task
    private boolean orbitSubscribed = false;

    public TerminalSolverModule(IEventBus eventBus) {
        super("TerminalSolver", "Renders solution for terminals in Floor 7", Category.DUNGEONS, true);
        this.eventBus = eventBus;
        // Set module reference for TerminalHandler settings access
        TerminalHandler.setModule(this);
    }

    private void autoClickTick() {
        // Safety: Reset emulator if no terminal is active but emulator was used
        if (currentTerm == null) {
            HumanMouseEmulator emulator = HumanMouseEmulator.get();
            if (emulator.isAvailable() && !emulator.isIdle()) {
                emulator.reset();
            }
            return;
        }

        if (!isEnabled() || !autoClickEnabled) return;

        if (currentTerm.solution.isEmpty()) {
            return;
        }

        // Safety: Stop emulator if window loses focus
        // NOTE: Skip this check in packet mode since we don't need mouse control
        boolean experimentalPacketModeEarly = (autoClickModeIndex == 1);
        if (!experimentalPacketModeEarly && (mc.getWindow() == null || !mc.isWindowActive())) {
            HumanMouseEmulator emulator = HumanMouseEmulator.get();
            if (emulator.isMoving()) {
                emulator.stopMovement();
            }
            return;
        }

        // Safety: EMERGENCY STOP if no GUI is open (terminal was interrupted)
        if (mc.screen == null) {
            HumanMouseEmulator.get().emergencyStop();
            leftTerm();
            return;
        }

        // Safety: Reset everything if movement key is pressed (user wants to leave)
        if (mc.screen == null && isMovementKeyPressed()) {
            HumanMouseEmulator.get().reset();
            leftTerm();
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceOpen = now - terminalOpenedTime;
        boolean experimentalPacketMode = (autoClickModeIndex == 1);

        // Wait for initial delay before any clicks (both modes)
        if (timeSinceOpen < FIRST_CLICK_DELAY_MS) {
            // In normal mode: pre-position mouse during this phase
            if (!experimentalPacketMode) {
                HumanMouseEmulator emulator = HumanMouseEmulator.get();
                if (!emulator.hasPosition() && !currentTerm.solution.isEmpty()) {
                    triggerPreMove(emulator);
                }
            }
            return;
        }
        
        // Normal mouse mode: additional checks
        if (!experimentalPacketMode) {
            // Wait until previous movement is complete
            HumanMouseEmulator emulator = HumanMouseEmulator.get();
            if (emulator.isMoving()) {
                // Safety: Check if movement is stuck (taking too long)
                if (movementStartTime > 0 && now - movementStartTime > MOVEMENT_TIMEOUT_MS) {
                    System.err.println("[AutoClick] Movement stuck for " + (now - movementStartTime) + "ms, forcing reset!");
                    emulator.reset();
                    movementStartTime = 0;
                    // Continue to trigger next movement
                } else {
                    return;
                }
            } else {
                // Movement completed, reset timer
                movementStartTime = 0;
            }
        }

        // Delay between clicks - use appropriate delay based on mode
        // SAFETY: Enforce minimum 60ms in packet mode to avoid ban!
        int currentDelay = experimentalPacketMode ? Math.max(60, packetClickDelay) : clickDelay;
        if (now - lastAutoClickTime < currentDelay) {
            return;
        }

        // Melody terminal has special timing-based logic
        if (currentTerm.type == TerminalTypes.MELODY) {
            // Check if already solved to prevent retries after successful click
            if (!currentTerm.isSolved) {
                triggerMelodyClick();
            }
            return;
        }

        triggerAutoClick();
    }

    @Override
    protected void onEnable() {
        subscribeOrbitEvents();
        startAutoClickExecutor();
        updateGuiSettings();
    }

    @Override
    protected void onDisable() {
        unsubscribeOrbitEvents();
        stopAutoClickExecutor();
        leftTerm();
    }

    private void subscribeOrbitEvents() {
        if (!orbitSubscribed) {
            eventBus.subscribe(this);
            orbitSubscribed = true;
        }
    }

    private void unsubscribeOrbitEvents() {
        if (orbitSubscribed) {
            eventBus.unsubscribe(this);
            orbitSubscribed = false;
        }
    }

    private void startAutoClickExecutor() {
        if (autoClickExecutor != null && autoClickTask != null) {
            if (!autoClickExecutor.isShutdown() && !autoClickExecutor.isTerminated() &&
                !autoClickTask.isDone() && !autoClickTask.isCancelled()) {
                return;
            }
            try {
                if (autoClickTask != null) autoClickTask.cancel(false);
                autoClickExecutor.shutdownNow();
            } catch (Exception ignored) {}
        }
        autoClickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TerminalAutoClick");
            t.setDaemon(true);
            return t;
        });
        autoClickTask = autoClickExecutor.scheduleAtFixedRate(this::safeAutoClickTick, 0, 10, TimeUnit.MILLISECONDS);
    }

    private void safeAutoClickTick() {
        try {
            autoClickTick();
        } catch (Exception e) {
            System.err.println("[TerminalSolver] CRITICAL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopAutoClickExecutor() {
        if (autoClickTask != null) {
            autoClickTask.cancel(false);
            autoClickTask = null;
        }
        if (autoClickExecutor == null) {
            return;
        }
        autoClickExecutor.shutdown();
        try {
            if (!autoClickExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                autoClickExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            autoClickExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            autoClickExecutor = null;
        }
    }

    public void updateGuiSettings() {
        // Update TermGui static fields
        TermGui.customTermSize = this.customTermSize;
        TermGui.gap = this.gap;
        TermGui.roundness = this.roundness;
        TermGui.backgroundColor = this.backgroundColor;
        // Bei Numbers Terminal + Packet Click Mode + Auto-Click: hideClicked automatisch aus
        boolean effectiveHideClicked = this.hideClicked;
        if (this.currentTerm != null && this.currentTerm.type == TerminalTypes.NUMBERS 
            && this.autoClickEnabled && this.autoClickModeIndex == 1) {
            effectiveHideClicked = false;
        }
        TermGui.hideClicked = effectiveHideClicked;
        TermGui.customAnimations = this.customAnimations;
        TermGui.compactClickMode = this.stackedClickMode;
        TermGui.queuePreviewLimit = Math.max(1, this.queuePreviewLimit);
        TermGui.showQueuePreview = this.queuePreviewLimit > 0;
        TermGui.useCustomFont = this.useCustomFont;

        // Update GUI colors - use the specific terminal colors
        PanesGui.panesColor = this.panesColor;
        RubixGui.rubixColor1 = this.rubixColor1;
        RubixGui.rubixColor2 = this.rubixColor2;
        RubixGui.oppositeRubixColor1 = this.oppositeRubixColor1;
        RubixGui.oppositeRubixColor2 = this.oppositeRubixColor2;
        NumbersGui.orderColor = this.numbersSlot1Color;
        NumbersGui.orderColor2 = this.numbersSlot2Color;
        NumbersGui.orderColor3 = this.numbersSlot3Color;
        NumbersGui.showNumbers = this.showNumbers;
        MelodyGui.melodyColumnColor = this.melodyColumnColor;
        MelodyGui.melodyRowColor = this.melodyRowColor;
        MelodyGui.melodyPointerColor = this.melodyPointerColor;

        TermGui.currentTerm = this.currentTerm;
    }

    private boolean shouldSuppressPacketGui() {
        return suppressPacketGui && autoClickEnabled && autoClickModeIndex == 1
            && currentTerm != null && currentTerm.type != TerminalTypes.MELODY;
    }

    @EventHandler
    public void onScreenOpen(GuiEvent.Open event) {
        if (!isEnabled()) return;

        if (event.screen instanceof AbstractContainerScreen<?> containerScreen) {
            Component nameText = event.screen.getTitle();
            if (nameText == null) return;
            String windowName = nameText.getString();
            int windowId = containerScreen.getMenu().containerId;
            handleTerminalOpen(windowName, windowId);
        }
    }

    @EventHandler
    public void onScreenClose(GuiEvent.Close event) {
        if (!isEnabled() || currentTerm == null) return;
        pendingTermClose = true;
        pendingTermCloseAt = System.currentTimeMillis();
    }

    @EventHandler
    public void onScreenRemoved(GuiEvent.Removed event) {
        if (!isEnabled() || currentTerm == null) return;
        if (refreshInProgress) {
            refreshInProgress = false;
            return;
        }
        HumanMouseEmulator.get().emergencyStop();
        leftTerm();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        if (event.packet instanceof ClientboundOpenScreenPacket packet) {
            Component nameText = packet.getTitle();
            if (nameText == null) return;
            String windowName = nameText.getString();
            int windowId = packet.getContainerId();
            handleTerminalOpen(windowName, windowId);

        } else if (event.packet instanceof ClientboundContainerClosePacket) {
            if (isEnabled() && currentTerm != null) {
                pendingTermClose = true;
                pendingTermCloseAt = System.currentTimeMillis();
                HumanMouseEmulator.get().stopMovement();
            }

        } else if (event.packet instanceof ClientboundSystemChatPacket packet) {
            if (!packet.overlay()) {
                Component content = packet.content();
                if (content != null) {
                    String msg = content.getString();
                    Matcher matcher = TERM_SOLVER_REGEX.matcher(msg);
                    if (matcher.find()) {
                        String playerName = (mc.player != null && mc.player.getName() != null)
                                ? mc.player.getName().getString()
                                : null;
                        if (playerName != null && playerName.equals(matcher.group(1))) {
                            if (lastTermOpened != null) {
                                eventBus.post(new TerminalEvent.Solved(lastTermOpened));
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        if (event.packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket) {
            // Track isClicked for other features (e.g., animation triggers)
            if (currentTerm != null) {
                currentTerm.isClicked = true;
            }
        } else if (event.packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket) {
            leftTerm();
        }
    }

    private void handleTerminalOpen(String windowName, int windowId) {
        TerminalTypes newTermType = TerminalTypes.findByWindowName(windowName);
        if (newTermType == null) return;

        if (pendingTermClose && currentTerm != null) {
            long sinceClose = System.currentTimeMillis() - pendingTermCloseAt;
            if (sinceClose <= TERMINAL_REOPEN_GRACE_MS && newTermType == currentTerm.type) {
                cancelPendingTermClose();
                if ((newTermType == TerminalTypes.SELECT || newTermType == TerminalTypes.STARTS_WITH)
                    && windowName.equals(lastWindowName)) {
                    refreshInProgress = true;
                    startAutoClickExecutor();
                    TermGui.currentTerm = currentTerm;
                    updateGuiSettings();
                    currentTerm.onTerminalRefresh(windowId);
                    return;
                }
                finalizePendingTermClose();
            } else {
                finalizePendingTermClose();
            }
        }

        if (currentTerm != null && newTermType == currentTerm.type) {
            if (newTermType == TerminalTypes.SELECT || newTermType == TerminalTypes.STARTS_WITH) {
                if (windowName.equals(lastWindowName)) {
                    refreshInProgress = true;
                    startAutoClickExecutor();
                    currentTerm.onTerminalRefresh(windowId);
                    return;
                }
            } else {
                refreshInProgress = true;
                startAutoClickExecutor();
                currentTerm.onTerminalRefresh(windowId);
                return;
            }
        }
        currentTerm = switch (newTermType) {
            case PANES -> new PanesHandler(eventBus);
            case RUBIX -> new RubixHandler(eventBus);
            case NUMBERS -> new NumbersHandler(eventBus);
            case STARTS_WITH -> {
                Matcher matcher = STARTS_WITH_REGEX.matcher(windowName);
                if (!matcher.find()) {
                    yield null;
                }
                String letter = matcher.group(1);
                yield new StartsWithHandler(letter, eventBus);
            }
            case SELECT -> {
                Matcher matcher = SELECT_ALL_REGEX.matcher(windowName);
                if (!matcher.find()) {
                    yield null;
                }
                String color = matcher.group(1)
                    .replace(" ", "_")  // "light blue" → "light_blue" to match registry names
                    .toLowerCase();
                yield new SelectAllHandler(color, eventBus);
            }
            case MELODY -> new MelodyHandler(eventBus);
        };

        if (currentTerm != null) {
            TermGui.currentTerm = currentTerm;
            updateGuiSettings();
            eventBus.post(new TerminalEvent.Opened(currentTerm));
            lastTermOpened = currentTerm;
            lastWindowName = windowName;
            terminalOpenedTime = System.currentTimeMillis();
            lastClickTick = -1;

            // Keep this log for terminal dumps
            System.out.println("[TerminalSolver] Opened: " + currentTerm.type);

            startAutoClickExecutor();

            if (showMouseVisualization && autoClickEnabled) {
                HumanMouseEmulator.get().setSessionStartTime(System.currentTimeMillis());
            }

            if (currentTerm.type == TerminalTypes.MELODY) {
                resetMelodyState();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(GuiEvent.MouseClick event) {
        if (!isEnabled() || currentTerm == null) return;
        if (shouldSuppressPacketGui()) {
            event.cancel();
            return;
        }

        // Custom GUI mode
        if (renderType && !(currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver)) {
            // Convert GUI-scaled coordinates to window coordinates
            double scale = mc.getWindow().getGuiScale();
            int windowX = (int)(event.mouseX * scale);
            int windowY = (int)(event.mouseY * scale);

            // === NVG CLICK HANDLING ===
            if (currentTerm.type.getGUI() == null) return;

            // Create Click object for the NVG API
            net.minecraft.client.input.MouseButtonInfo mouseInput = new net.minecraft.client.input.MouseButtonInfo(event.button, 0);
            net.minecraft.client.input.MouseButtonEvent click = new net.minecraft.client.input.MouseButtonEvent(windowX, windowY, mouseInput);
            Integer clickedSlot = currentTerm.type.getGUI().mouseClicked(event.screen, click);
            if (clickedSlot != null) {
                event.cancel();
            }
            return;
        }

        // Normal overlay mode

        // Optional: focused slot lookup via accessor/mixin
        try {
            // TODO: Add HandledScreenAccessor mixin
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onDrawBackground(GuiEvent.DrawBackground event) {
        if (!isEnabled() || currentTerm == null || !renderType) return;
        if (currentTerm.type == null) return;
        if (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver) return;
        if (shouldSuppressPacketGui()) {
            event.cancel();
            return;
        }

        try {
            if (currentTerm.type.getGUI() == null) return;

            NVGRenderer.beginFrame(mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight());
            try {
                currentTerm.type.getGUI().render(event.drawContext);
            } finally {
                NVGRenderer.endFrame();
            }

            currentTerm.type.getGUI().renderText();

            event.cancel();
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onDrawForeground(GuiEvent.DrawForeground event) {
        if (!isEnabled() || currentTerm == null || !renderType) return;
        if (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver) return;
        if (shouldSuppressPacketGui()) {
            event.cancel();
            return;
        }

        event.cancel();
    }

    @EventHandler
    public void onDrawGui(GuiEvent.Draw event) {
        if (!isEnabled() || currentTerm == null) return;
        if (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver) return;

        if (shouldSuppressPacketGui()) {
            renderPacketProgress(event.drawContext);
            event.cancel();
            return;
        }

        if (renderType) {
            event.cancel();
            return;
        }

       
    }

    @EventHandler
    public void onDrawSlot(GuiEvent.DrawSlot event) {
        if (!isEnabled() || currentTerm == null || currentTerm.type == null) return;

        if (shouldSuppressPacketGui()) {
            event.cancel();
            return;
        }

        if (renderType) {
            event.cancel();
            return;
        }

        if (currentTerm.type == TerminalTypes.MELODY && cancelMelodySolver) return;

        int slotIndex = event.slot.index;
        int inventorySize = 0;
        if (event.screen instanceof AbstractContainerScreen<?> screen) {
            inventorySize = screen.getMenu().slots.size();
        }

        event.cancel();
        if (!currentTerm.solution.contains(slotIndex) || slotIndex > inventorySize - 37) return;

        GuiGraphics ctx = event.drawContext;
        int x = event.slot.x;
        int y = event.slot.y;

        switch (currentTerm.type) {
            case PANES, STARTS_WITH, SELECT ->
                ctx.fill(x, y, x + 16, y + 16, panesColor.getRgba());

            case NUMBERS -> {
                int index = currentTerm.solution.indexOf(slotIndex);
                if (index < 3) {
                    Color color = switch (index) {
                        case 0 -> numbersSlot1Color;
                        case 1 -> numbersSlot2Color;
                        default -> numbersSlot3Color;
                    };
                    ctx.fill(x, y, x + 16, y + 16, color.getRgba());
                }
                if (showNumbers && event.slot.getItem() != null) {
                    String amount = String.valueOf(event.slot.getItem().getCount());
                    int textX = x + 8 - event.screen.getFont().width(amount) / 2;
                    ctx.drawString(event.screen.getFont(), amount, textX, y + 4, Colors.WHITE.getRgba(), false);
                }
            }

            case RUBIX -> {
                int needed = 0;
                for (int slot : currentTerm.solution) if (slot == slotIndex) needed++;
                int text = (needed < 3) ? needed : (needed - 5);
                if (text != 0) {
                    Color color = switch (text) {
                        case 2 -> rubixColor2;
                        case 1 -> rubixColor1;
                        case -2 -> oppositeRubixColor2;
                        default -> oppositeRubixColor1;
                    };
                    ctx.fill(x, y, x + 16, y + 16, color.getRgba());
                    String textStr = String.valueOf(text);
                    int textX = x + 8 - event.screen.getFont().width(textStr) / 2;
                    ctx.drawString(event.screen.getFont(), textStr, textX, y + 4, Colors.WHITE.getRgba(), false);
                }
            }

            case MELODY -> {
                Color color;
                if (slotIndex / 9 == 0 || slotIndex / 9 == 5) {
                    color = melodyColumnColor;
                } else if ((slotIndex % 9) >= 1 && (slotIndex % 9) <= 5) {
                    color = melodyPointerColor;
                } else {
                    color = melodyPointerColor;
                }
                ctx.fill(x, y, x + 16, y + 16, color.getRgba());
            }
        }
    }

    private void renderPacketProgress(GuiGraphics ctx) {
        if (ctx == null || mc.font == null) return;

        TerminalHandler term = currentTerm;
        if (term == null || term.type == null) return;

        int remaining = Math.max(0, term.solution.size());
        int queued = 0;
        try {
            queued = term.getClickQueue().size();
        } catch (Exception ignored) {}

        String header = "Packet Solver: " + formatTerminalName(term.type);
        String status = remaining == 0 ? "Solved" : remaining + " left";
        if (queued > 0) {
            status += " | queued " + queued;
        }

        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        int boxWidth = Math.max(mc.font.width(header), mc.font.width(status)) + 12;
        int boxHeight = mc.font.lineHeight * 2 + 8;
        int x = (guiWidth - boxWidth) / 2;
        int y = (guiHeight - boxHeight) / 2;

        ctx.fill(x, y, x + boxWidth, y + boxHeight, 0xAA000000);
        ctx.drawString(mc.font, header, x + 6, y + 4, Colors.WHITE.getRgba(), false);
        ctx.drawString(mc.font, status, x + 6, y + 4 + mc.font.lineHeight,
            Colors.WHITE.withAlpha(0.8f).getRgba(), false);
    }

    private String formatTerminalName(TerminalTypes type) {
        if (type == null) return "Terminal";
        String readable = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return readable.substring(0, 1).toUpperCase(Locale.ROOT) + readable.substring(1);
    }

    @EventHandler
    public void onTooltipDraw(GuiEvent.DrawTooltip event) {
        if (isEnabled() && cancelToolTip && currentTerm != null) {
            event.cancel();
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        // Increment tick counter - CRITICAL for packet mode click limiting
        currentTick++;

        if (pendingTermClose && currentTerm != null) {
            if (System.currentTimeMillis() - pendingTermCloseAt > TERMINAL_REOPEN_GRACE_MS) {
                finalizePendingTermClose();
            }
        }
    }

    private void cancelPendingTermClose() {
        pendingTermClose = false;
        pendingTermCloseAt = 0L;
    }

    private void finalizePendingTermClose() {
        if (!pendingTermClose) return;
        if (currentTerm != null) {
            leftTerm();
        } else {
            cancelPendingTermClose();
        }
    }

    private void leftTerm() {
        cancelPendingTermClose();
        if (currentTerm != null) {
            // Reset mouse emulator BEFORE closing GUI
            HumanMouseEmulator.get().reset();

            currentTerm.unsubscribe();
            eventBus.post(new TerminalEvent.Closed(currentTerm));

            // Safe GUI cleanup - check for null before calling closeGui()
            if (currentTerm.type != null && currentTerm.type.getGUI() != null) {
                currentTerm.type.getGUI().closeGui();
            }

            currentTerm = null;
            TermGui.currentTerm = null;
            lastWindowName = null;

            resetMelodyState();
        }
    }


    /**
     * Called on world unload/disconnect to force cleanup all terminal state
     */
    public void onWorldUnload() {
        leftTerm();
        HumanMouseEmulator.get().reset();
        lastTermOpened = null;
    }

    // ==================== Simple Auto-Click System ====================

    // Monitor offset - on Windows: auto-detected from Minecraft window position
    // On Linux: hardcoded for ydotool coordinate system (configurable via setMonitorOffset)
    private int monitorOffsetX = System.getProperty("os.name").toLowerCase().contains("win") ? 0 : 1440;
    private int monitorOffsetY = 0;

    // Delay between clicks
    private long lastAutoClickTime = 0;
    private long terminalOpenedTime = 0; // When terminal was opened
    private long movementStartTime = 0; // When mouse movement started (for stuck detection)
    private static final long FIRST_CLICK_DELAY_MS = 350; // 350ms delay before first click (time to move to first answer)
    private static final long MOVEMENT_TIMEOUT_MS = 2000; // 2 seconds max for any movement
    private final java.util.Random clickRandom = new java.util.Random();


    /**
     * Check if any movement key (WASD, Space, Shift) is pressed
     */
    private boolean isMovementKeyPressed() {
        if (mc.getWindow() == null) return false;
        long handle = mc.getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /**
     * Pre-move to first target during FIRST_CLICK_DELAY phase (no click)
     */
    private void triggerPreMove(HumanMouseEmulator emulator) {
        if (currentTerm == null || currentTerm.solution.isEmpty()) return;

        if (!emulator.isAvailable()) {
            if (!emulator.init()) {
                return;
            }
        }

        // Apply movement parameters
        double guiScale = Math.max(1.0, customTermSize);
        double speedMult = clickSpeed;

        switch (mouseModeIndex) {
            case 1 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.SPLINE);
                emulator.setSplineParams(splineSpeedDivisor, splineMinWait, splineMaxWait);
            }
            case 2 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.BEZIER);
                emulator.setBezierParams(bezierDeviation, bezierSpeed);
            }
            default -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.WINDMOUSE);
                emulator.setWindMouseParams(
                    9.0, 3.0,
                    2.0 / speedMult, 10.0 / speedMult,
                    25.0 * speedMult * guiScale, 8.0 * guiScale
                );
            }
        }

        // Get first slot
        int firstSlot = selectNextSlot(emulator);
        if (firstSlot == -1) return;

        TermGui.Box box = TermGui.getSlotBox(firstSlot);
        if (box == null) return;

        // Calculate center with random offset
        int offsetX = clickRandom.nextInt(9) - 4;
        int offsetY = clickRandom.nextInt(9) - 4;
        int centerX = (int)(box.x + box.w / 2f) + offsetX;
        int centerY = (int)(box.y + box.h / 2f) + offsetY;

        // Convert client coordinates to desktop coordinates (handles windowed mode offset)
        int[] desktop = emulator.clientToScreen(mc.getWindow().handle(), centerX, centerY);
        int desktopX = desktop[0];
        int desktopY = desktop[1];

        emulator.moveTo(desktopX, desktopY);
    }

    /**
     * Move mouse to next solution slot and click (or send packet directly in experimental mode)
     * Uses coordinates directly from TermGui.itemIndexMap for exact positioning
     */
    private void triggerAutoClick() {
        if (currentTerm == null || currentTerm.solution.isEmpty()) return;

        boolean experimentalPacketMode = (autoClickModeIndex == 1);

        if (experimentalPacketMode) {
            if (lastClickTick == currentTick) return;

            // Get next slot to click
            int nextSlot = -1;

            // For order-dependent terminals, always use first slot
            if (currentTerm.type == TerminalTypes.NUMBERS || currentTerm.type == TerminalTypes.MELODY) {
                nextSlot = currentTerm.solution.get(0);
            } else {
                // For other terminals, just take first available
                java.util.Set<Integer> uniqueSlots = new java.util.HashSet<>(currentTerm.solution);
                if (!uniqueSlots.isEmpty()) {
                    nextSlot = uniqueSlots.iterator().next();
                }
            }

            if (nextSlot == -1) {
                return;
            }

            // Rubix terminal: determine if we need right-click
            boolean useRightClick = false;
            if (currentTerm.type == TerminalTypes.RUBIX) {
                int slotCount = 0;
                for (int slot : currentTerm.solution) {
                    if (slot == nextSlot) slotCount++;
                }
                useRightClick = slotCount >= 3;
            }

            int button = useRightClick ? 1 : 0;

            if (currentTerm.type != TerminalTypes.MELODY) {
                currentTerm.simulateClick(nextSlot, button);
            }

            if (currentTerm.shouldQueue()) {
                currentTerm.queueClick(nextSlot, button);
            } else {
                currentTerm.clickPacket(nextSlot, button, false);
            }

            lastClickTick = currentTick;
            lastAutoClickTime = System.currentTimeMillis();
            return;
        }

        // ==================== NORMAL MOUSE MODE ====================
        HumanMouseEmulator emulator = HumanMouseEmulator.get();

        if (!emulator.isAvailable()) {
            if (!emulator.init()) {
                return;
            }
        }

        // Apply parameters based on mouse mode - SIMPLIFIED using single clickSpeed
        double guiScale = Math.max(1.0, customTermSize); // Scale factor from GUI size
        double speedMult = clickSpeed; // 0.5 = slow, 1.0 = normal, 1.5 = fast

        switch (mouseModeIndex) {
            case 1 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.SPLINE);
                emulator.setSplineParams(splineSpeedDivisor, splineMinWait, splineMaxWait);
            }
            case 2 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.BEZIER);
                emulator.setBezierParams(bezierDeviation, bezierSpeed);
            }
            default -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.WINDMOUSE);
                emulator.setWindMouseParams(
                    9.0, 3.0,
                    2.0 / speedMult, 10.0 / speedMult,
                    25.0 * speedMult * guiScale, 8.0 * guiScale
                );
            }
        }

        // Get next slot - use nearest-slot algorithm for multi-target terminals
        int nextSlot = selectNextSlot(emulator);
        if (nextSlot == -1) {
            // Solution became empty during execution (safety check)
            return;
        }

        // Rubix terminal: determine if we need right-click
        // Count how many times this slot appears in solution
        boolean useRightClick = false;
        if (currentTerm.type == TerminalTypes.RUBIX) {
            int slotCount = 0;
            for (int slot : currentTerm.solution) {
                if (slot == nextSlot) slotCount++;
            }
            // If slot appears 3+ times, use right-click (more efficient)
            useRightClick = slotCount >= 3;
        }

        int button = useRightClick ? 1 : 0;

        TermGui.Box box = TermGui.getSlotBox(nextSlot);
        if (box == null) return;

        // Calculate center of the slot box with random offset (more human-like)
        int offsetX = clickRandom.nextInt(9) - 4; // -4 to +4 pixels
        int offsetY = clickRandom.nextInt(9) - 4;
        int centerX = (int)(box.x + box.w / 2f) + offsetX;
        int centerY = (int)(box.y + box.h / 2f) + offsetY;

        // Convert client coordinates to desktop coordinates (handles windowed mode offset)
        Minecraft mc = Minecraft.getInstance();
        int[] desktop = emulator.clientToScreen(mc.getWindow().handle(), centerX, centerY);
        int desktopX = desktop[0];
        int desktopY = desktop[1];

        boolean isLastClick = currentTerm.solution.size() == 1;

        // Track movement start time for stuck detection
        movementStartTime = System.currentTimeMillis();

        // ALWAYS move mouse to slot first (for legitimacy)
        emulator.moveTo(desktopX, desktopY);

        // Mouse arrived at slot - handle click EXACTLY like packet mode:
        // 1. simulateClick FIRST (before queue check)
        // 2. Then either queue or send packet (with doSimulateClick=false since already simulated)
        if (currentTerm.type != TerminalTypes.MELODY) {
            currentTerm.simulateClick(nextSlot, button);
        }

        if (currentTerm.shouldQueue()) {
            currentTerm.queueClick(nextSlot, button);
        } else {
            currentTerm.clickPacket(nextSlot, button, false);
        }

        if (isLastClick) {
            emulator.reset();
        }

        lastAutoClickTime = System.currentTimeMillis();
    }

    // Momentum tracking for human-like movement
    private double lastMoveX = 0;
    private double lastMoveY = 0;
    private int lastClickedSlot = -1;

    /**
     * Select next slot using human-like decision making:
     * - Momentum: Continue in current direction when possible
     * - Clustering: Group nearby slots, clear one area before moving
     * - Not perfect: Sometimes pick "good enough" not "optimal"
     */
    private int selectNextSlot(HumanMouseEmulator emulator) {
        if (currentTerm == null || currentTerm.solution.isEmpty()) {
            return -1;
        }

        // For order-dependent terminals, always use first slot
        if (currentTerm.type == TerminalTypes.NUMBERS || currentTerm.type == TerminalTypes.MELODY) {
            return currentTerm.solution.get(0);
        }

        // Get unique slots from solution
        java.util.Set<Integer> uniqueSlots = new java.util.HashSet<>(currentTerm.solution);

        if (uniqueSlots.size() == 1) {
            return uniqueSlots.iterator().next();
        }

        // No position yet - start with slot closest to center (human starts from middle)
        if (!emulator.hasPosition()) {
            lastMoveX = 0;
            lastMoveY = 0;
            lastClickedSlot = -1;
            return findCenterSlot(uniqueSlots);
        }

        double mouseX = emulator.getCurrentX() - monitorOffsetX;
        double mouseY = emulator.getCurrentY() - monitorOffsetY;

        // Build scored list of candidates (only non-pending slots)
        int bestSlot = uniqueSlots.iterator().next();
        double bestScore = Double.MAX_VALUE;

        for (int slot : uniqueSlots) {
            TermGui.Box box = TermGui.getSlotBox(slot);
            if (box == null) continue;

            double slotX = box.x + box.w / 2.0;
            double slotY = box.y + box.h / 2.0;

            double dx = slotX - mouseX;
            double dy = slotY - mouseY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // Base score is distance (lower = better)
            double score = dist;

            // Momentum bonus: Prefer slots in same direction as last move
            if (dist > 10 && (Math.abs(lastMoveX) > 5 || Math.abs(lastMoveY) > 5)) {
                // Normalize vectors
                double moveMag = Math.sqrt(lastMoveX * lastMoveX + lastMoveY * lastMoveY);
                double dirMag = dist;

                if (moveMag > 0 && dirMag > 0) {
                    // Dot product for direction similarity (-1 to 1)
                    double dot = (lastMoveX * dx + lastMoveY * dy) / (moveMag * dirMag);

                    // Bonus for same direction (dot > 0), penalty for opposite (dot < 0)
                    // Max 30% bonus for perfect alignment
                    score *= (1.0 - dot * 0.3);
                }
            }

            // Cluster bonus: Prefer slots near the last clicked slot (clear one area)
            if (lastClickedSlot >= 0 && lastClickedSlot != slot) {
                TermGui.Box lastBox = TermGui.getSlotBox(lastClickedSlot);
                if (lastBox != null) {
                    double lastX = lastBox.x + lastBox.w / 2.0;
                    double lastY = lastBox.y + lastBox.h / 2.0;
                    double clusterDist = Math.sqrt(
                        (slotX - lastX) * (slotX - lastX) +
                        (slotY - lastY) * (slotY - lastY)
                    );
                    // Small bonus for nearby slots (within ~2 slot widths)
                    if (clusterDist < 100) {
                        score *= 0.85; // 15% bonus for cluster
                    }
                }
            }

            // Very close slots get extra priority (don't skip obvious ones)
            if (dist < 50) {
                score *= 0.7; // 30% bonus for very close
            }

            if (score < bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        // Update momentum for next call
        TermGui.Box bestBox = TermGui.getSlotBox(bestSlot);
        if (bestBox != null) {
            double targetX = bestBox.x + bestBox.w / 2.0;
            double targetY = bestBox.y + bestBox.h / 2.0;
            lastMoveX = targetX - mouseX;
            lastMoveY = targetY - mouseY;
        }
        lastClickedSlot = bestSlot;

        return bestSlot;
    }

    /**
     * Find slot closest to screen center (human natural starting point)
     */
    private int findCenterSlot(java.util.Set<Integer> slots) {
        // Approximate screen center
        double centerX = mc.getWindow().getScreenWidth() / 2.0;
        double centerY = mc.getWindow().getScreenHeight() / 2.0;

        int closest = -1;
        double closestDist = Double.MAX_VALUE;

        for (int slot : slots) {
            TermGui.Box box = TermGui.getSlotBox(slot);
            if (box == null) continue;

            double dx = (box.x + box.w / 2.0) - centerX;
            double dy = (box.y + box.h / 2.0) - centerY;
            double dist = dx * dx + dy * dy;

            if (dist < closestDist) {
                closestDist = dist;
                closest = slot;
            }
        }

        return closest >= 0 ? closest : slots.iterator().next();
    }

    // Melody state - Simple: click when green, pre-position otherwise
    private int melodyPreposSlot = -1;

    /**
     * Melody terminal - SIMPLE VERSION
     * 1. Find the green button (any terracotta in solution)
     * 2. Click it
     * 3. Pre-position when not green
     */
    private void triggerMelodyClick() {
        if (currentTerm == null || currentTerm.type != TerminalTypes.MELODY) return;

        HumanMouseEmulator emulator = HumanMouseEmulator.get();

        if (!emulator.isAvailable()) {
            if (!emulator.init()) {
                return;
            }
        }

        // Find green button from solution - just use the first slot in solution
        // Don't check if item is still LIME_TERRACOTTA - solution is already validated
        int greenButtonSlot = -1;
        if (!currentTerm.solution.isEmpty()) {
            greenButtonSlot = currentTerm.solution.get(0);
        }

        long now = System.currentTimeMillis();

        // GREEN BUTTON = CLICK NOW
        if (greenButtonSlot != -1 && !emulator.isMoving()) {
            // If already positioned on green button, just click
            if (melodyPreposSlot == greenButtonSlot) {
                lastAutoClickTime = now;
                emulator.click(false);
                return;
            }

            // Otherwise move and click
            melodyQuickClick(emulator, greenButtonSlot);
            melodyPreposSlot = greenButtonSlot;
            return;
        }

        // PRE-POSITION: Use prePositionSlot from MelodyHandler (next row's terracotta)
        if (currentTerm instanceof MelodyHandler) {
            int preposSlot = ((MelodyHandler) currentTerm).getPrePositionSlot();
            if (preposSlot != -1 && melodyPreposSlot != preposSlot && !emulator.isMoving()) {
                moveToMelodySlot(emulator, preposSlot);
                melodyPreposSlot = preposSlot;
            }
        }
    }

    /**
     * Reset melody state when terminal opens
     */
    private void resetMelodyState() {
        melodyPreposSlot = -1;
    }

    /**
     * Quick click for skip attempts - faster movement
     */
    private void melodyQuickClick(HumanMouseEmulator emulator, int slot) {
        double guiScale = Math.max(1.0, customTermSize);

        // Fast settings for skip attempts (50% faster than normal)
        switch (mouseModeIndex) {
            case 1 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.SPLINE);
                emulator.setSplineParams(splineSpeedDivisor * 1.5, splineMinWait * 0.7, splineMaxWait * 0.7);
            }
            case 2 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.BEZIER);
                emulator.setBezierParams(Math.max(5, bezierDeviation - 5), Math.max(1, bezierSpeed - 1));
            }
            default -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.WINDMOUSE);
                emulator.setWindMouseParams(9.0, 3.0, 1.0, 5.0, 40.0 * guiScale, 12.0 * guiScale);
            }
        }

        TermGui.Box box = TermGui.getSlotBox(slot);
        if (box == null) return;

        int offsetX = clickRandom.nextInt(5) - 2;
        int offsetY = clickRandom.nextInt(5) - 2;
        int centerX = (int)(box.x + box.w / 2f) + offsetX;
        int centerY = (int)(box.y + box.h / 2f) + offsetY;

        // Convert client coordinates to desktop coordinates (handles windowed mode offset)
        Minecraft mc = Minecraft.getInstance();
        int[] desktop = emulator.clientToScreen(mc.getWindow().handle(), centerX, centerY);
        emulator.moveAndClick(desktop[0], desktop[1], false);
    }

    /**
     * Move to a melody slot position (without clicking)
     */
    private void moveToMelodySlot(HumanMouseEmulator emulator, int slot) {
        double speedMult = clickSpeed;
        double guiScale = Math.max(1.0, customTermSize);

        switch (mouseModeIndex) {
            case 1 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.SPLINE);
                emulator.setSplineParams(splineSpeedDivisor, splineMinWait, splineMaxWait);
            }
            case 2 -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.BEZIER);
                emulator.setBezierParams(bezierDeviation, bezierSpeed);
            }
            default -> {
                emulator.setMouseMode(HumanMouseEmulator.MouseMode.WINDMOUSE);
                emulator.setWindMouseParams(
                    9.0, 3.0,
                    2.0 / speedMult, 10.0 / speedMult,
                    25.0 * speedMult * guiScale, 8.0 * guiScale
                );
            }
        }

        TermGui.Box box = TermGui.getSlotBox(slot);
        if (box == null) return;

        // Small random offset
        int offsetX = clickRandom.nextInt(7) - 3;
        int offsetY = clickRandom.nextInt(7) - 3;
        int centerX = (int)(box.x + box.w / 2f) + offsetX;
        int centerY = (int)(box.y + box.h / 2f) + offsetY;

        // Convert client coordinates to desktop coordinates (handles windowed mode offset)
        Minecraft mc = Minecraft.getInstance();
        int[] desktop = emulator.clientToScreen(mc.getWindow().handle(), centerX, centerY);
        int desktopX = desktop[0];
        int desktopY = desktop[1];

        // Move only (no click) - we'll click when pane arrives
        emulator.moveTo(desktopX, desktopY);
    }

    /**
     * Set monitor offset for multi-monitor setups
     */
    public void setMonitorOffset(int x, int y) {
        this.monitorOffsetX = x;
        this.monitorOffsetY = y;
    }



    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();

        // Render settings
        config.addProperty("renderType", renderType);
        config.addProperty("hideClicked", hideClicked);
        config.addProperty("customTermSize", customTermSize);
        config.addProperty("customAnimations", customAnimations);
        config.addProperty("roundness", roundness);
        config.addProperty("gap", gap);
        config.addProperty("showNumbers", showNumbers);
        config.addProperty("cancelToolTip", cancelToolTip);
        config.addProperty("stackedClickMode", stackedClickMode);
        config.addProperty("queuePreviewLimit", queuePreviewLimit);
        config.addProperty("useCustomFont", useCustomFont);

        // High Ping Mode settings
        config.addProperty("highPingMode", highPingMode);
        config.addProperty("queueTimeout", queueTimeout);
        config.addProperty("queueDelayMin", queueDelayMin);
        config.addProperty("queueDelayMax", queueDelayMax);

        // Auto-Click settings - SIMPLIFIED
        config.addProperty("autoClickEnabled", autoClickEnabled);
        config.addProperty("clickDelay", clickDelay);
        config.addProperty("clickSpeed", clickSpeed);
        config.addProperty("mouseModeIndex", mouseModeIndex);
        config.addProperty("bezierDeviation", bezierDeviation);
        config.addProperty("bezierSpeed", bezierSpeed);
        config.addProperty("autoClickModeIndex", autoClickModeIndex);
        config.addProperty("showMouseVisualization", showMouseVisualization);
        config.addProperty("packetClickDelay", packetClickDelay);
        config.addProperty("suppressPacketGui", suppressPacketGui);

        // Colors
        config.addProperty("backgroundColor", backgroundColor.getRgba());
        config.addProperty("panesColor", panesColor.getRgba());
        config.addProperty("rubixColor1", rubixColor1.getRgba());
        config.addProperty("rubixColor2", rubixColor2.getRgba());
        config.addProperty("oppositeRubixColor1", oppositeRubixColor1.getRgba());
        config.addProperty("oppositeRubixColor2", oppositeRubixColor2.getRgba());
        config.addProperty("melodyColumnColor", melodyColumnColor.getRgba());
        config.addProperty("melodyRowColor", melodyRowColor.getRgba());
        config.addProperty("melodyPointerColor", melodyPointerColor.getRgba());

        config.addProperty("numbersSlot1Color", numbersSlot1Color.getRgba());
        config.addProperty("numbersSlot2Color", numbersSlot2Color.getRgba());
        config.addProperty("numbersSlot3Color", numbersSlot3Color.getRgba());

        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        // Render settings
        if (data.has("renderType")) renderType = data.get("renderType").getAsBoolean();
        if (data.has("hideClicked")) hideClicked = data.get("hideClicked").getAsBoolean();
        if (data.has("customTermSize")) customTermSize = data.get("customTermSize").getAsFloat();
        if (data.has("customAnimations")) customAnimations = data.get("customAnimations").getAsBoolean();
        if (data.has("roundness")) roundness = data.get("roundness").getAsFloat();
        if (data.has("gap")) gap = data.get("gap").getAsFloat();
        if (data.has("showNumbers")) showNumbers = data.get("showNumbers").getAsBoolean();
        if (data.has("cancelToolTip")) cancelToolTip = data.get("cancelToolTip").getAsBoolean();
        if (data.has("stackedClickMode")) stackedClickMode = data.get("stackedClickMode").getAsBoolean();
        if (data.has("queuePreviewLimit")) queuePreviewLimit = data.get("queuePreviewLimit").getAsInt();
        if (data.has("useCustomFont")) useCustomFont = data.get("useCustomFont").getAsBoolean();

        // High Ping Mode settings
        if (data.has("highPingMode")) highPingMode = data.get("highPingMode").getAsBoolean();
        if (data.has("queueTimeout")) queueTimeout = data.get("queueTimeout").getAsInt();
        if (data.has("queueDelayMin")) queueDelayMin = data.get("queueDelayMin").getAsInt();
        if (data.has("queueDelayMax")) queueDelayMax = data.get("queueDelayMax").getAsInt();

        // Auto-Click settings - SIMPLIFIED
        if (data.has("autoClickEnabled")) autoClickEnabled = data.get("autoClickEnabled").getAsBoolean();
        if (data.has("clickDelay")) clickDelay = data.get("clickDelay").getAsInt();
        if (data.has("clickSpeed")) clickSpeed = data.get("clickSpeed").getAsFloat();
        if (data.has("mouseModeIndex")) {
            mouseModeIndex = data.get("mouseModeIndex").getAsInt();
            HumanMouseEmulator.MouseMode mode = switch (mouseModeIndex) {
                case 1 -> HumanMouseEmulator.MouseMode.SPLINE;
                case 2 -> HumanMouseEmulator.MouseMode.BEZIER;
                default -> HumanMouseEmulator.MouseMode.WINDMOUSE;
            };
            HumanMouseEmulator.get().setMouseMode(mode);
        }
        if (data.has("bezierDeviation")) bezierDeviation = data.get("bezierDeviation").getAsInt();
        if (data.has("bezierSpeed")) bezierSpeed = data.get("bezierSpeed").getAsInt();
        if (data.has("autoClickModeIndex")) autoClickModeIndex = data.get("autoClickModeIndex").getAsInt();
        if (data.has("showMouseVisualization")) showMouseVisualization = data.get("showMouseVisualization").getAsBoolean();
        if (data.has("packetClickDelay")) packetClickDelay = data.get("packetClickDelay").getAsInt();
        if (data.has("suppressPacketGui")) suppressPacketGui = data.get("suppressPacketGui").getAsBoolean();

        // Colors
        if (data.has("backgroundColor")) backgroundColor = new Color(data.get("backgroundColor").getAsInt());
        if (data.has("panesColor")) panesColor = new Color(data.get("panesColor").getAsInt());
        if (data.has("rubixColor1")) rubixColor1 = new Color(data.get("rubixColor1").getAsInt());
        if (data.has("rubixColor2")) rubixColor2 = new Color(data.get("rubixColor2").getAsInt());
        if (data.has("oppositeRubixColor1")) oppositeRubixColor1 = new Color(data.get("oppositeRubixColor1").getAsInt());
        if (data.has("oppositeRubixColor2")) oppositeRubixColor2 = new Color(data.get("oppositeRubixColor2").getAsInt());
        if (data.has("melodyColumnColor")) melodyColumnColor = new Color(data.get("melodyColumnColor").getAsInt());
        if (data.has("melodyRowColor")) melodyRowColor = new Color(data.get("melodyRowColor").getAsInt());
        if (data.has("melodyPointerColor")) melodyPointerColor = new Color(data.get("melodyPointerColor").getAsInt());

        if (data.has("numbersSlot1Color")) numbersSlot1Color = new Color(data.get("numbersSlot1Color").getAsInt());
        if (data.has("numbersSlot2Color")) numbersSlot2Color = new Color(data.get("numbersSlot2Color").getAsInt());
        if (data.has("numbersSlot3Color")) numbersSlot3Color = new Color(data.get("numbersSlot3Color").getAsInt());

        updateGuiSettings();
    }

    // SettingsProvider implementation
    @Override
    public java.util.List<ModuleSetting> getSettings() {
        java.util.List<ModuleSetting> settings = new java.util.ArrayList<>();

        // Render Type
        settings.add(new CheckboxSetting(
            "Custom GUI",
            "Use custom terminal GUI renderer",
            "terminal_render_type",
            () -> renderType,
            val -> renderType = val
        ));

        // Custom Size (conditional)
        settings.add(new SliderSetting(
            "GUI Size",
            "Size multiplier for custom GUI",
            "terminal_custom_size",
            0.5f, 3.0f,
            () -> customTermSize,
            val -> customTermSize = val
        ).withDecimals(1).withSuffix("x").setVisible(() -> renderType));

        // Show Numbers
        settings.add(new CheckboxSetting(
            "Show Numbers",
            "Display click order numbers",
            "terminal_show_numbers",
            () -> showNumbers,
            val -> showNumbers = val
        ));

        // Hide Clicked
        settings.add(new CheckboxSetting(
            "Hide Clicked",
            "Hide already clicked items",
            "terminal_hide_clicked",
            () -> hideClicked,
            val -> hideClicked = val
        ));

        // Background Color
        settings.add(new ColorPickerSetting(
            "Background Color",
            "Terminal GUI background color",
            "terminal_bg_color",
            () -> backgroundColor.getRgba(),
            color -> backgroundColor = new Color(color)
        ));

        // Panes Color
        settings.add(new ColorPickerSetting(
            "Panes Color",
            "Color for glass panes terminal",
            "terminal_panes_color",
            () -> panesColor.getRgba(),
            color -> panesColor = new Color(color)
        ));

        // Block Incorrect Clicks
        settings.add(new CheckboxSetting(
            "Block Wrong Clicks",
            "Prevent clicking wrong items",
            "terminal_block_incorrect",
            () -> blockIncorrectClicks,
            val -> blockIncorrectClicks = val
        ));

        // Cancel Tooltip
        settings.add(new CheckboxSetting(
            "Cancel Tooltip",
            "Hide item tooltips in terminals",
            "terminal_cancel_tooltip",
            () -> cancelToolTip,
            val -> cancelToolTip = val
        ));

        // Stacked Click Mode (only visible when custom GUI enabled)
        settings.add(new CheckboxSetting(
            "Stacked Click Mode",
            "Show click queue stacked vertically",
            "terminal_stacked_click_mode",
            () -> stackedClickMode,
            val -> stackedClickMode = val
        ).setVisible(() -> renderType));

        // Custom Animations (only visible when custom GUI enabled)
        settings.add(new CheckboxSetting(
            "Custom Animations",
            "Enable smooth animations in custom GUI",
            "terminal_custom_animations",
            () -> customAnimations,
            val -> customAnimations = val
        ).setVisible(() -> renderType));

        // Roundness (only visible when custom GUI enabled)
        settings.add(new SliderSetting(
            "Roundness",
            "Corner roundness for custom GUI",
            "terminal_roundness",
            0f, 20f,
            () -> roundness,
            val -> roundness = val
        ).withDecimals(0).withSuffix("px").setVisible(() -> renderType));

        // Gap (only visible when custom GUI enabled)
        settings.add(new SliderSetting(
            "Gap",
            "Spacing between items in custom GUI",
            "terminal_gap",
            0f, 20f,
            () -> gap,
            val -> gap = val
        ).withDecimals(0).withSuffix("px").setVisible(() -> renderType));

        // Use Custom Font (only visible when custom GUI enabled)
        settings.add(new CheckboxSetting(
            "Use Custom Font",
            "Use font from CustomFont module",
            "terminal_use_custom_font",
            () -> useCustomFont,
            val -> {
                useCustomFont = val;
                updateGuiSettings();
            }
        ).setVisible(() -> renderType));

        // ==================== High Ping Mode Settings ====================

        settings.add(new CheckboxSetting(
            "High Ping Mode",
            "Queue clicks for high latency (NoamAddons Q-Terms style)",
            "terminal_high_ping_mode",
            () -> highPingMode,
            val -> highPingMode = val
        ));

        settings.add(new SliderSetting(
            "Queue Timeout",
            "Time to wait for server response before clearing queue",
            "terminal_queue_timeout",
            200f, 1500f,
            () -> (float) queueTimeout,
            val -> queueTimeout = val.intValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> highPingMode));

        settings.add(new SliderSetting(
            "Queue Delay Min",
            "Minimum random delay before processing next queued click",
            "terminal_queue_delay_min",
            0f, 50f,
            () -> (float) queueDelayMin,
            val -> queueDelayMin = val.intValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> highPingMode));

        settings.add(new SliderSetting(
            "Queue Delay Max",
            "Maximum random delay before processing next queued click",
            "terminal_queue_delay_max",
            0f, 50f,
            () -> (float) queueDelayMax,
            val -> queueDelayMax = Math.max(queueDelayMin, val.intValue())
        ).withDecimals(0).withSuffix("ms").setVisible(() -> highPingMode));

        // ==================== Auto-Click Settings ====================

        settings.add(new CheckboxSetting(
            "Auto-Click",
            "Automatically click terminal solutions (requires Custom GUI)",
            "terminal_auto_click",
            () -> autoClickEnabled,
            val -> autoClickEnabled = val
        ).setVisible(() -> renderType));

        // Auto-Click Mode Dropdown
        settings.add(new DropdownSetting(
            "Click Mode",
            "§aHuman Mouse = realistic movement | §6Experimental Packet = direct (§cfast but risky!)",
            "terminal_autoclick_mode",
            new String[]{"Human Mouse", "§6Experimental Packet"},
            () -> autoClickModeIndex,
            val -> autoClickModeIndex = val
        ).setVisible(() -> renderType && autoClickEnabled));

        // Mouse Mode Dropdown - only visible in Human Mouse mode
        settings.add(new DropdownSetting(
            "Mouse Movement",
            "Mouse movement algorithm (WindMouse = natural, Spline = smooth, Bezier = curved)",
            "terminal_mouse_mode",
            new String[]{"WindMouse", "Spline", "Bezier"},
            () -> mouseModeIndex,
            val -> {
                mouseModeIndex = val;
                // Apply the mode to the emulator
                HumanMouseEmulator.MouseMode mode = switch (val) {
                    case 1 -> HumanMouseEmulator.MouseMode.SPLINE;
                    case 2 -> HumanMouseEmulator.MouseMode.BEZIER;
                    default -> HumanMouseEmulator.MouseMode.WINDMOUSE;
                };
                HumanMouseEmulator.get().setMouseMode(mode);
            }
        ).setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0));

        // WindMouse Speed - only visible in WindMouse mode + Human Mouse click mode
        settings.add(new SliderSetting(
            "WindMouse Speed",
            "Movement speed (0.5=slow, 1.0=normal, 1.5=fast)",
            "terminal_click_speed",
            0.3f, 1.5f,
            () -> clickSpeed,
            val -> clickSpeed = val
        ).withDecimals(1).withSuffix("x").setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0 && mouseModeIndex == 0));

        // Spline Speed Divisor - only visible in Spline mode + Human Mouse click mode
        settings.add(new SliderSetting(
            "Spline Speed",
            "Higher = faster movement (frames = distance/speed)",
            "terminal_spline_speed_divisor",
            4f, 25f,
            () -> splineSpeedDivisor,
            val -> splineSpeedDivisor = val
        ).withDecimals(0).setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0 && mouseModeIndex == 1));

        // Spline Min Wait - only visible in Spline mode + Human Mouse click mode
        settings.add(new SliderSetting(
            "Spline Min Delay",
            "Minimum delay between frames (ms)",
            "terminal_spline_min_wait",
            0.1f, 3f,
            () -> splineMinWait,
            val -> splineMinWait = val
        ).withDecimals(1).withSuffix("ms").setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0 && mouseModeIndex == 1));

        // Spline Max Wait - only visible in Spline mode + Human Mouse click mode
        settings.add(new SliderSetting(
            "Spline Max Delay",
            "Maximum delay between frames (ms)",
            "terminal_spline_max_wait",
            1f, 10f,
            () -> splineMaxWait,
            val -> splineMaxWait = val
        ).withDecimals(1).withSuffix("ms").setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0 && mouseModeIndex == 1));

        // Bezier Deviation - only visible in Bezier mode + Human Mouse click mode
        settings.add(new SliderSetting(
            "Bezier Curve",
            "Control point deviation - higher = more curved path",
            "terminal_bezier_deviation",
            5f, 40f,
            () -> (float) bezierDeviation,
            val -> {
                bezierDeviation = Math.round(val);
                HumanMouseEmulator.get().setBezierParams(bezierDeviation, bezierSpeed);
            }
        ).withDecimals(0).withSuffix("%").setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0 && mouseModeIndex == 2));

        // Bezier Speed - only visible in Bezier mode + Human Mouse click mode
        settings.add(new SliderSetting(
            "Bezier Speed",
            "Speed multiplier (lower = faster)",
            "terminal_bezier_speed",
            1f, 5f,
            () -> (float) bezierSpeed,
            val -> {
                bezierSpeed = Math.round(val);
                HumanMouseEmulator.get().setBezierParams(bezierDeviation, bezierSpeed);
            }
        ).withDecimals(0).setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0 && mouseModeIndex == 2));

        // Click Delay - single slider instead of min/max
        settings.add(new SliderSetting(
            "Click Delay",
            "Delay between clicks (ms)",
            "terminal_click_delay",
            20f, 200f,
            () -> (float) clickDelay,
            val -> clickDelay = val.intValue()
        ).withDecimals(0).withSuffix("ms").setVisible(() -> renderType && autoClickEnabled));

        // Mouse Visualizer toggle (only visible when auto-click enabled and NOT in packet mode)
        settings.add(new CheckboxSetting(
            "Show Mouse Trail",
            "Show trail and clicks after terminal closes (5 seconds)",
            "terminal_show_mouse_visualization",
            () -> showMouseVisualization,
            val -> showMouseVisualization = val
        ).setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 0));

        // Packet Click Delay - only visible in packet mode
        // IMPORTANT: Minimum 60ms to avoid anti-cheat detection!
        settings.add(new SliderSetting(
            "Packet Click Delay",
            "§cDelay between packet clicks (ms) - MINIMUM 60ms to avoid BAN!",
            "terminal_packet_click_delay",
            60f, 200f,
            () -> (float) packetClickDelay,
            val -> packetClickDelay = Math.max(60, val.intValue())
        ).withDecimals(0).withSuffix("ms").setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 1));

        settings.add(new CheckboxSetting(
            "Suppress Packet GUI",
            "Hide terminal GUI and show compact progress text while packet clicking (except Melody)",
            "terminal_packet_suppress_gui",
            () -> suppressPacketGui,
            val -> suppressPacketGui = val
        ).setVisible(() -> renderType && autoClickEnabled && autoClickModeIndex == 1));

        return settings;
    }

    /**
     * Get the font to use for Terminal GUI
     * Returns custom font if enabled, otherwise NVGRenderer default
     */
    public dev.hunchclient.render.Font getFont() {
        if (useCustomFont) {
            CustomFontModule customFont = CustomFontModule.getInstance();
            if (customFont != null && customFont.isEnabled()) {
                return customFont.getSelectedFont();
            }
        }
        return dev.hunchclient.render.NVGRenderer.defaultFont;
    }
}
