package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;
import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.ChatMessageEvent;
import dev.hunchclient.event.GuiEvent;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoMaskSwap - Automatically swaps to backup mask when current mask saves your life
 *
 * Detects: "Your [Bonzo's/Spirit] Mask saved your life!"
 * Then: /eq -> click mask slot -> close GUI
 */
public class AutoMaskSwapModule extends Module implements ConfigurableModule, SettingsProvider {

    private static final Minecraft mc = Minecraft.getInstance();

    // Settings
    private int clickDelay = 100;
    private int closeDelay = 50;
    private boolean suppressGui = true;
    private boolean testMode = false;

    private List<ModuleSetting> settings;

    // State
    private volatile boolean awaitingEquipmentGui = false;
    private volatile long awaitingStartTime = 0;
    private volatile long lastClickTime = 0;
    private volatile boolean hasClicked = false;
    private static final long AWAIT_TIMEOUT_MS = 2000;
    private static final long MIN_CLICK_DELAY_MS = 50; // 1 tick minimum = 0t protection
    private static final long FIRST_CLICK_DELAY_MS = 350; // Wait before first click after GUI opens
    private volatile long guiOpenedTime = 0;

    public AutoMaskSwapModule() {
        super("AutoMaskSwap", "Auto-swaps mask when it saves your life", Category.DUNGEONS, RiskLevel.RISKY);
        initializeSettings();
    }

    private void initializeSettings() {
        settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Suppress GUI",
            "Hide the /eq GUI while swapping",
            "suppressGui",
            () -> suppressGui,
            (value) -> suppressGui = value
        ));

        settings.add(new SliderSetting(
            "Click Delay",
            "Delay before clicking mask slot (ms)",
            "clickDelay",
            0f, 500f,
            () -> (float) clickDelay,
            (value) -> clickDelay = value.intValue()
        ).withDecimals(0).withSuffix(" ms"));

        settings.add(new SliderSetting(
            "Close Delay",
            "Delay before closing GUI after click (ms)",
            "closeDelay",
            0f, 200f,
            () -> (float) closeDelay,
            (value) -> closeDelay = value.intValue()
        ).withDecimals(0).withSuffix(" ms"));

        settings.add(new CheckboxSetting(
            "Test Mode",
            "Trigger on your own messages for testing",
            "testMode",
            () -> testMode,
            (value) -> testMode = value
        ));
    }

    @Override
    public List<ModuleSetting> getSettings() {
        return settings;
    }

    @Override
    protected void onEnable() {
        HunchModClient.EVENT_BUS.subscribe(this);
    }

    @Override
    protected void onDisable() {
        HunchModClient.EVENT_BUS.unsubscribe(this);
        reset();
    }

    private void reset() {
        awaitingEquipmentGui = false;
        hasClicked = false;
        guiOpenedTime = 0;
    }

    @EventHandler
    public void onChatMessage(ChatMessageEvent event) {
        if (!isEnabled()) return;

        // Player chat only triggers in test mode
        if (event.isPlayerChat() && !testMode) {
            return;
        }

        String message = event.getMessage();

        // Detect: "Your Bonzo's Mask saved your life!" or "Your Spirit Mask saved your life!"
        if (message.contains("Mask") && message.contains("saved your life")) {
            System.out.println("[AutoMaskSwap] Trigger detected! Starting swap...");
            triggerMaskSwap();
        }
    }

    @EventHandler
    public void onGuiDraw(GuiEvent.DrawBackground event) {
        if (!isEnabled() || !awaitingEquipmentGui) return;

        // Check timeout
        if (System.currentTimeMillis() - awaitingStartTime > AWAIT_TIMEOUT_MS) {
            System.out.println("[AutoMaskSwap] GUI timeout!");
            reset();
            return;
        }

        if (!(event.screen instanceof ContainerScreen containerScreen)) return;
        if (!(containerScreen.getMenu() instanceof ChestMenu handler)) return;

        String title = event.screen.getTitle().getString().toLowerCase();

        // Check if this is equipment GUI
        if (!title.contains("equipment") && !title.contains("wardrobe")) {
            return;
        }

        // Track when GUI actually opened (first frame we see it)
        if (guiOpenedTime == 0) {
            guiOpenedTime = System.currentTimeMillis();
            System.out.println("[AutoMaskSwap] GUI opened: " + title);
            
            // Suppress GUI by closing screen but schedule the click
            if (suppressGui) {
                // Schedule the click after delay, then close
                scheduleClickAndClose(handler);
                // Close the screen immediately (container stays open)
                mc.execute(() -> mc.setScreen(null));
                return;
            }
        }

        // Non-suppressed mode: wait and click in the draw event
        if (!suppressGui) {
            // Wait for first click delay
            if (System.currentTimeMillis() - guiOpenedTime < FIRST_CLICK_DELAY_MS + clickDelay) {
                return;
            }

            // Find and click mask
            if (!hasClicked) {
                int slotToClick = findMaskSlotInContainer(handler);
                System.out.println("[AutoMaskSwap] Slot to click: " + slotToClick);
                if (slotToClick != -1) {
                    if (clickSlot(handler, slotToClick, 0)) {
                        System.out.println("[AutoMaskSwap] Clicked slot " + slotToClick);
                        hasClicked = true;
                    }
                } else {
                    System.out.println("[AutoMaskSwap] No mask found, closing");
                    closeAndReset();
                }
            }

            // Close after click delay
            if (hasClicked && System.currentTimeMillis() - lastClickTime > closeDelay) {
                closeAndReset();
            }
        }
    }

    private void triggerMaskSwap() {
        System.out.println("[AutoMaskSwap] triggerMaskSwap() called");

        if (mc.player == null || mc.getConnection() == null) {
            System.out.println("[AutoMaskSwap] ABORT: player or connection null");
            return;
        }

        // Check if we have a backup mask first
        int maskSlot = findMaskSlotInInventory();
        System.out.println("[AutoMaskSwap] Mask slot in inventory: " + maskSlot);
        if (maskSlot == -1) {
            // No backup mask found
            System.out.println("[AutoMaskSwap] ABORT: No backup mask in inventory!");
            return;
        }

        // The mask just saved our life = it's on cooldown, swap to backup!
        // No need to check if we're wearing a mask - that's the whole point

        awaitingEquipmentGui = true;
        awaitingStartTime = System.currentTimeMillis();
        hasClicked = false;
        guiOpenedTime = 0; // Reset GUI open time

        System.out.println("[AutoMaskSwap] Opening /eq...");
        // Send /eq command
        mc.player.connection.sendCommand("eq");
    }

    private int findMaskSlotInInventory() {
        if (mc.player == null) return -1;

        // Only scan main inventory (0-35), skip armor slots (36-39) and offhand (40)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String skyblockId = getSkyblockId(stack);
            
            // Check for mask (including STARRED_ variants)
            if (isMaskId(skyblockId)) {
                System.out.println("[AutoMaskSwap] >>> FOUND MASK at slot " + i + ": " + skyblockId);
                return i;
            }
        }
        System.out.println("[AutoMaskSwap] No backup mask found in inventory!");
        return -1;
    }

    private int findMaskSlotInContainer(ChestMenu handler) {
        int totalSlots = handler.slots.size();
        int playerInvStart = totalSlots - 36; // Player inventory is last 36 slots

        for (int i = playerInvStart; i < totalSlots; i++) {
            ItemStack stack = handler.slots.get(i).getItem();
            if (stack.isEmpty()) continue;

            String skyblockId = getSkyblockId(stack);
            
            if (isMaskId(skyblockId)) {
                System.out.println("[AutoMaskSwap] >>> FOUND MASK in container at slot " + i + ": " + skyblockId);
                return i;
            }
        }
        return -1;
    }


    /**
     * Check if the skyblock ID is a Spirit or Bonzo mask (including STARRED_ variants)
     */
    private boolean isMaskId(String skyblockId) {
        if (skyblockId == null) return false;
        String upper = skyblockId.toUpperCase();
        return upper.equals("SPIRIT_MASK") || upper.equals("STARRED_SPIRIT_MASK") ||
               upper.equals("BONZO_MASK") || upper.equals("STARRED_BONZO_MASK");
    }

    /**
     * Click a slot in the container (same as AutoExperiments)
     * Includes 0t protection (minimum 50ms/1 tick between clicks)
     * Ensures execution on main thread
     */
    private boolean clickSlot(ChestMenu handler, int slotIndex, int button) {
        if (mc.gameMode == null || mc.player == null) return false;

        // 0t protection: ensure minimum 1 tick (50ms) since last click
        long now = System.currentTimeMillis();
        if (now - lastClickTime < MIN_CLICK_DELAY_MS) {
            return false; // Too soon, skip this frame
        }

        // Capture values for lambda
        final int containerId = handler.containerId;
        final ClickType actionType = (button == 2) ? ClickType.CLONE : ClickType.PICKUP;

        // Execute on main thread
        mc.execute(() -> {
            if (mc.gameMode != null && mc.player != null) {
                mc.gameMode.handleInventoryMouseClick(
                    containerId,
                    slotIndex,
                    button,
                    actionType,
                    mc.player
                );
            }
        });

        lastClickTime = now;
        return true;
    }

    private void closeAndReset() {
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
        });
        reset();
    }


    /**
     * Schedule click and close for suppressGui mode
     * Runs after FIRST_CLICK_DELAY + clickDelay, finds mask, clicks, then closes
     */
    private void scheduleClickAndClose(ChestMenu handler) {
        long totalDelay = FIRST_CLICK_DELAY_MS + clickDelay;
        
        new Thread(() -> {
            try {
                Thread.sleep(totalDelay);
                
                // Execute on main thread
                mc.execute(() -> {
                    if (mc.player == null) {
                        reset();
                        return;
                    }
                    
                    // Check if container is still open
                    if (!(mc.player.containerMenu instanceof ChestMenu currentHandler)) {
                        System.out.println("[AutoMaskSwap] Container closed unexpectedly");
                        reset();
                        return;
                    }
                    
                    // Find and click mask
                    int slotToClick = findMaskSlotInContainer(currentHandler);
                    System.out.println("[AutoMaskSwap] [Suppressed] Slot to click: " + slotToClick);
                    
                    if (slotToClick != -1) {
                        clickSlot(currentHandler, slotToClick, 0);
                        System.out.println("[AutoMaskSwap] [Suppressed] Clicked slot " + slotToClick);
                        
                        // Schedule close after closeDelay
                        new Thread(() -> {
                            try {
                                Thread.sleep(closeDelay);
                                mc.execute(() -> {
                                    if (mc.player != null) {
                                        mc.player.closeContainer();
                                    }
                                    reset();
                                });
                            } catch (InterruptedException e) {
                                reset();
                            }
                        }).start();
                    } else {
                        System.out.println("[AutoMaskSwap] [Suppressed] No mask found, closing");
                        if (mc.player != null) {
                            mc.player.closeContainer();
                        }
                        reset();
                    }
                });
            } catch (InterruptedException e) {
                reset();
            }
        }).start();
    }

    /**
     * Called from ClientPlayNetworkHandlerMixin when a screen is about to open.
     * @return true if the GUI should be suppressed (cancelled)
     */
    public boolean handleOpenScreen(ClientboundOpenScreenPacket packet) {
        if (!isEnabled() || !suppressGui || !awaitingEquipmentGui) {
            return false;
        }

        // Check timeout
        if (System.currentTimeMillis() - awaitingStartTime > AWAIT_TIMEOUT_MS) {
            awaitingEquipmentGui = false;
            return false;
        }

        String title = packet.getTitle().getString().toLowerCase();

        // Check if this is the equipment GUI - we need it to open for clicking
        // So we DON'T suppress it, but the GUI drawing will handle the click
        if (title.contains("equipment") || title.contains("wardrobe")) {
            // Actually, if suppressGui is on, we want to suppress the visual but still process
            // This is tricky - we need the container but not the screen
            // For now, don't suppress so the click logic can work
            return false;
        }

        return false;
    }

    private String getSkyblockId(ItemStack stack) {
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return null;
            }
            
            CompoundTag nbt = customData.copyTag();
            if (nbt == null) {
                return null;
            }
            
            // Debug: Print entire NBT
            System.out.println("[AutoMaskSwap] NBT: " + nbt.toString());
            
            // 1.21+: Skyblock ID is directly in custom_data under "id" key
            if (nbt.contains("id")) {
                String id = nbt.getString("id").orElse(null);
                System.out.println("[AutoMaskSwap] Found id directly: " + id);
                return id;
            }
            
            // Fallback: Legacy format with ExtraAttributes
            CompoundTag extraAttrs = null;
            if (nbt.contains("ExtraAttributes")) {
                extraAttrs = nbt.getCompound("ExtraAttributes").orElse(null);
            } else if (nbt.contains("extra_attributes")) {
                extraAttrs = nbt.getCompound("extra_attributes").orElse(null);
            }
            
            if (extraAttrs != null && extraAttrs.contains("id")) {
                String id = extraAttrs.getString("id").orElse(null);
                System.out.println("[AutoMaskSwap] Found id in ExtraAttributes: " + id);
                return id;
            }
        } catch (Exception e) {
            System.out.println("[AutoMaskSwap] Error getting SB ID: " + e.getMessage());
        }
        return null;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("suppressGui", suppressGui);
        config.addProperty("clickDelay", clickDelay);
        config.addProperty("closeDelay", closeDelay);
        config.addProperty("testMode", testMode);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("suppressGui")) {
            suppressGui = data.get("suppressGui").getAsBoolean();
        }
        if (data.has("clickDelay")) {
            clickDelay = data.get("clickDelay").getAsInt();
        }
        if (data.has("closeDelay")) {
            closeDelay = data.get("closeDelay").getAsInt();
        }
        if (data.has("testMode")) {
            testMode = data.get("testMode").getAsBoolean();
        }
    }
}
