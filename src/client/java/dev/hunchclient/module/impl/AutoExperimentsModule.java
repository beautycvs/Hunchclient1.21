package dev.hunchclient.module.impl;

import com.google.gson.JsonObject;
import dev.hunchclient.event.GuiEvent;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Auto Experiments - automatically solves Chronomatron and Ultrasequencer experiments with built-in safety checks.
 *
 * Safety features:
 * - Private Island location check (Anticheat protection!)
 * - Middle click (button 2) for inventory interaction
 * - Preserves timing, delay behavior, and slot detection logic
 */
public class AutoExperimentsModule extends Module implements ConfigurableModule, SettingsProvider {

    // Settings
    private int clickDelay = 200;
    private boolean autoClose = true;
    private int serumCount = 0;
    private boolean getMaxXp = false;

    // State
    private HashMap<Integer, Integer> ultrasequencerOrder = new HashMap<>();
    private ArrayList<Integer> chronomatronOrder = new ArrayList<>(28);
    private long lastClickTime = 0L;
    private boolean hasAdded = false;
    private int lastAdded = 0;
    private int clicks = 0;

    private List<ModuleSetting> settings;

    private static final Minecraft mc = Minecraft.getInstance();
    private final IEventBus eventBus;
    private boolean orbitSubscribed = false;

    public AutoExperimentsModule(IEventBus eventBus) {
        super("AutoExperiments", "Automatically solves Chronomatron and Ultrasequencer experiments", Category.MISC, RiskLevel.RISKY);
        this.eventBus = eventBus;
        initializeSettings();
    }

    private void initializeSettings() {
        settings = new ArrayList<>();

        settings.add(new SliderSetting(
                "Click Delay",
                "Time in ms between automatic clicks",
                "clickDelay",
                0f, 1000f,
                () -> (float) clickDelay,
                (value) -> clickDelay = value.intValue()
        ).withDecimals(0).withSuffix(" ms"));

        settings.add(new CheckboxSetting(
                "Auto Close",
                "Automatically close the GUI after completing",
                "autoClose",
                () -> autoClose,
                (value) -> autoClose = value
        ));

        settings.add(new SliderSetting(
                "Serum Count",
                "Consumed Metaphysical Serum count",
                "serumCount",
                0f, 3f,
                () -> (float) serumCount,
                (value) -> serumCount = value.intValue()
        ).withDecimals(0));

        settings.add(new CheckboxSetting(
                "Get Max XP",
                "Solve Chronomatron to 15 and Ultrasequencer to 20 for max XP",
                "getMaxXp",
                () -> getMaxXp,
                (value) -> getMaxXp = value
        ));
    }

    @Override
    public List<ModuleSetting> getSettings() {
        return settings;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("clickDelay", clickDelay);
        config.addProperty("autoClose", autoClose);
        config.addProperty("serumCount", serumCount);
        config.addProperty("getMaxXp", getMaxXp);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("clickDelay")) clickDelay = data.get("clickDelay").getAsInt();
        if (data.has("autoClose")) autoClose = data.get("autoClose").getAsBoolean();
        if (data.has("serumCount")) serumCount = data.get("serumCount").getAsInt();
        if (data.has("getMaxXp")) getMaxXp = data.get("getMaxXp").getAsBoolean();
    }

    @Override
    protected void onEnable() {
        subscribeOrbitEvents();
        reset();
    }

    @Override
    protected void onDisable() {
        unsubscribeOrbitEvents();
        reset();
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

    private void reset() {
        ultrasequencerOrder.clear();
        chronomatronOrder.clear();
        hasAdded = false;
        lastAdded = 0;
        // NOTE: clicks and lastClickTime are NOT reset here (1:1 from original)
        // They persist across GUI opens to maintain timing behavior
    }

    @EventHandler
    public void onScreenOpen(GuiEvent.Open event) {
        if (!isEnabled()) return;

        // Reset when GUI closes (null screen)
        if (event.screen == null) {
            reset();
            return;
        }

        // Also reset when opening experiment GUI (fixes second experiment not working)
        if (event.screen instanceof ContainerScreen screen) {
            String title = screen.getTitle().getString();
            if (title.startsWith("Chronomatron (") || title.startsWith("Ultrasequencer (")) {
                reset();
            }
        }
    }

    @EventHandler
    public void onGuiDraw(GuiEvent.DrawBackground event) {
        if (!isEnabled()) {
            return;
        }

        // CRITICAL: Only work on Private Island (Anticheat protection!)
        if (!dev.hunchclient.util.DungeonUtils.isOnPrivateIsland()) {
            return;
        }

        if (!(event.screen instanceof ContainerScreen containerScreen)) return;

        var screenHandler = containerScreen.getMenu();
        if (!(screenHandler instanceof ChestMenu)) return;

        String title = event.screen.getTitle().getString();

        if (title.startsWith("Chronomatron (")) {
            solveChronomatron(screenHandler);
        } else if (title.startsWith("Ultrasequencer (")) {
            solveUltraSequencer(screenHandler);
        }
    }

    private void solveChronomatron(ChestMenu handler) {
        int maxChronomatron = getMaxXp ? 15 : 11 - serumCount;

        // Get slot 49 (the indicator)
        ItemStack indicatorStack = handler.slots.size() > 49 ? handler.slots.get(49).getItem() : ItemStack.EMPTY;
        Item indicatorItem = indicatorStack.getItem();

        // Get last added slot
        ItemStack lastAddedStack = handler.slots.size() > lastAdded ? handler.slots.get(lastAdded).getItem() : ItemStack.EMPTY;

        // Check if showing results (glowstone) and last slot is not enchanted
        if (indicatorItem == Items.GLOWSTONE && !lastAddedStack.hasFoil()) {
            if (autoClose && chronomatronOrder.size() > maxChronomatron) {
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
            }
            hasAdded = false;
        }

        // Check if in "remember" phase (clock showing)
        if (!hasAdded && indicatorItem == Items.CLOCK) {
            // Find the enchanted item (the one to remember)
            for (int i = 10; i <= 43; i++) {
                if (handler.slots.size() <= i) break;
                ItemStack stack = handler.slots.get(i).getItem();
                if (stack.hasFoil()) {
                    chronomatronOrder.add(i);
                    lastAdded = i;
                    hasAdded = true;
                    clicks = 0;
                    break;
                }
            }
        }

        // Auto-click the sequence
        if (hasAdded && indicatorItem == Items.CLOCK && chronomatronOrder.size() > clicks
                && System.currentTimeMillis() - lastClickTime > clickDelay) {
            int slotToClick = chronomatronOrder.get(clicks);
            clickSlot(handler, slotToClick, 2); // Middle click (button 2)
            lastClickTime = System.currentTimeMillis();
            clicks++;
        }
    }

    private void solveUltraSequencer(ChestMenu handler) {
        int maxUltraSequencer = getMaxXp ? 20 : 9 - serumCount;

        // Get slot 49 (the indicator)
        ItemStack indicatorStack = handler.slots.size() > 49 ? handler.slots.get(49).getItem() : ItemStack.EMPTY;
        Item indicatorItem = indicatorStack.getItem();

        // 1:1 from original: Reset when clock appears (start of clicking phase)
        if (indicatorItem == Items.CLOCK) {
            hasAdded = false;
        }

        // Scan during glowstone phase
        if (!hasAdded && indicatorItem == Items.GLOWSTONE) {
            // Check if slot 44 has an item
            if (handler.slots.size() <= 44) return;
            ItemStack slot44Stack = handler.slots.get(44).getItem();
            if (slot44Stack.isEmpty()) return;

            ultrasequencerOrder.clear();

            // Find all dye items and map by stack size
            for (int i = 9; i <= 44; i++) {
                if (handler.slots.size() <= i) break;
                ItemStack stack = handler.slots.get(i).getItem();

                if (!stack.isEmpty() && isDye(stack.getItem())) {
                    int stackSize = stack.getCount();
                    ultrasequencerOrder.put(stackSize - 1, i);
                }
            }

            hasAdded = true;
            clicks = 0;

            if (ultrasequencerOrder.size() > maxUltraSequencer && autoClose) {
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
            }
        }

        // Auto-click during clock phase
        if (indicatorItem == Items.CLOCK && ultrasequencerOrder.containsKey(clicks)
                && System.currentTimeMillis() - lastClickTime > clickDelay) {
            Integer slotToClick = ultrasequencerOrder.get(clicks);
            if (slotToClick != null) {
                clickSlot(handler, slotToClick, 2); // Middle click (button 2)
                lastClickTime = System.currentTimeMillis();
                clicks++;
            }
        }
    }

    /**
     * Check if an item is a dye (for Ultrasequencer)
     * In 1.8.9, all dyes were Items.dye with different metadata
     * In 1.21, each color is a separate item
     */
    private boolean isDye(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).getPath();

        // Standard dyes (white_dye, orange_dye, magenta_dye, etc.)
        if (itemId.endsWith("_dye")) {
            return true;
        }

        // Special dyes that don't end in _dye
        return itemId.equals("bone_meal") ||      // White dye
               itemId.equals("ink_sac") ||        // Black dye
               itemId.equals("lapis_lazuli") ||   // Blue dye
               itemId.equals("cocoa_beans");      // Brown dye
    }

    /**
     * Click a slot in the container.
     * Middle click (button 2) uses the CLONE action type.
     */
    private void clickSlot(ChestMenu handler, int slotIndex, int button) {
        if (mc.gameMode == null || mc.player == null) return;

        // Middle click requires CLONE action type (same logic as TerminalHandler.kt)
        ClickType actionType = (button == 2) ? ClickType.CLONE : ClickType.PICKUP;

        mc.gameMode.handleInventoryMouseClick(
                handler.containerId,
                slotIndex,
                button,
                actionType,
                mc.player
        );
    }
}
