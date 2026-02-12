package dev.hunchclient.module.impl.misc;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * ChatUtilsModule - Chat and GUI utility features.
 *
 * Features:
 * - RemoveChatLimit: Unlimited chat history
 * - MiddleClickGUI: Convert left-clicks to middle-clicks in certain GUIs
 * - CopyChat: Right-click to copy chat messages
 * - CompactChat: Stack repeated messages
 */
public class ChatUtilsModule extends Module implements ConfigurableModule, SettingsProvider {

    private static ChatUtilsModule instance;

    // Settings
    private boolean removeChatLimit = false;
    private boolean middleClickGui = false;
    private boolean copyChat = false;
    private boolean compactChat = false;

    // CompactChat data
    private final Map<String, MessageHistory> chatHistory = new HashMap<>();

    // MiddleClickGui avoid list
    private static final Set<String> AVOID_GUIS = Set.of(
        "Wardrobe", "Drill Anvil", "Anvil", "Storage", "The Hex",
        "Composter", "Auctions", "Abiphone", "Chest", "Large Chest"
    );

    public ChatUtilsModule() {
        super("ChatUtils", "Chat and GUI utility features.", Category.MISC, RiskLevel.SAFE);
        instance = this;
    }

    public static ChatUtilsModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        chatHistory.clear();
    }

    @Override
    protected void onDisable() {
        chatHistory.clear();
    }

    // ========== RemoveChatLimit ==========

    /**
     * Returns the chat message limit (used by ChatHudMixin).
     * Returns Integer.MAX_VALUE if removeChatLimit is enabled, otherwise default (100).
     */
    public static int getChatMessageLimit() {
        if (instance != null && instance.isEnabled() && instance.removeChatLimit) {
            return Integer.MAX_VALUE;
        }
        return 100; // Default vanilla limit
    }

    // ========== MiddleClickGui ==========

    /**
     * Check if this slot click should be converted to middle-click.
     * @param screen The current screen
     * @param slot The clicked slot
     * @param button The mouse button (0 = left, 1 = right, 2 = middle)
     * @return true if should convert to middle-click
     */
    public boolean shouldConvertToMiddleClick(Screen screen, Slot slot, int button) {
        if (!isEnabled() || !middleClickGui) {
            return false;
        }

        // Only convert left-clicks
        if (button != 0) {
            return false;
        }

        if (slot == null || !slot.hasItem()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        // Don't convert clicks on player inventory
        if (slot.container == client.player.getInventory()) {
            return false;
        }

        // Check screen name against avoid list
        if (screen instanceof AbstractContainerScreen<?> handledScreen) {
            String title = handledScreen.getTitle().getString();
            for (String avoid : AVOID_GUIS) {
                if (title.startsWith(avoid)) {
                    return false;
                }
            }
        }

        ItemStack stack = slot.getItem();

        // Don't convert if item has a SkyBlock ID (is a real SB item)
        // Only convert for GUI decoration items (glass panes etc.)
        if (hasSkyblockId(stack)) {
            return false;
        }

        // Skip items named "Reforge Item" or "Salvage Item"
        String itemName = stack.getHoverName().getString();
        if (itemName.equals("Reforge Item") || itemName.equals("Salvage Item")) {
            return false;
        }

        return true;
    }

    /**
     * Check if an item has a SkyBlock ID (is a real SB item).
     */
    private boolean hasSkyblockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return false;
            }
            CompoundTag nbt = customData.copyTag();
            if (nbt == null) {
                return false;
            }

            // Check ExtraAttributes.id
            if (nbt.contains("ExtraAttributes")) {
                CompoundTag extra = nbt.getCompound("ExtraAttributes").orElse(null);
                if (extra != null && extra.contains("id")) {
                    return true;
                }
            }
            // Check extra_attributes.id (alternative format)
            if (nbt.contains("extra_attributes")) {
                CompoundTag extra = nbt.getCompound("extra_attributes").orElse(null);
                if (extra != null && extra.contains("id")) {
                    return true;
                }
            }
            // Check direct id
            if (nbt.contains("id")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return false;
    }

    // ========== CopyChat ==========

    /**
     * Check if copy chat feature is enabled.
     */
    public static boolean isCopyChatEnabled() {
        return instance != null && instance.isEnabled() && instance.copyChat;
    }

    /**
     * Handle copying chat message on right-click.
     * Called from ChatScreenMixin.
     */
    public void handleCopyChat(double mouseX, double mouseY) {
        if (!isEnabled() || !copyChat) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) {
            return;
        }

        ChatComponent chatHud = client.gui.getChat();
        // Note: Getting the message at click position would require accessor
        // For now, this is a placeholder that can be extended with proper accessor
    }

    // ========== CompactChat ==========

    /**
     * Process a chat message for compact chat (stack duplicates).
     * Returns the modified message or the original if not a duplicate.
     */
    public Component processCompactChat(Component message) {
        if (!isEnabled() || !compactChat) {
            return message;
        }

        String messageStr = ChatFormatting.stripFormatting(message.getString());
        if (messageStr == null || messageStr.isBlank()) {
            return message;
        }

        MessageHistory history = chatHistory.computeIfAbsent(messageStr, k -> new MessageHistory());

        // Reset count if message is older than 60 seconds
        long now = System.currentTimeMillis();
        if (now - history.lastTime > 60000) {
            history.times = 0;
        }

        history.times++;
        history.lastTime = now;

        if (history.times <= 1) {
            return message;
        }

        // Append count to message
        return Component.empty()
            .append(message)
            .append(Component.literal(" (x" + history.times + ")").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Check if compact chat should remove the previous duplicate.
     */
    public boolean shouldRemovePreviousDuplicate(String message) {
        if (!isEnabled() || !compactChat) {
            return false;
        }
        String stripped = ChatFormatting.stripFormatting(message);
        MessageHistory history = chatHistory.get(stripped);
        return history != null && history.times > 1;
    }

    /**
     * Clear compact chat history.
     */
    public void clearCompactChatHistory() {
        chatHistory.clear();
    }

    // ========== Utility Classes ==========

    private static class MessageHistory {
        int times = 0;
        long lastTime = 0L;
    }

    // ========== Getters/Setters ==========

    public boolean isRemoveChatLimit() { return removeChatLimit; }
    public void setRemoveChatLimit(boolean v) { this.removeChatLimit = v; }

    public boolean isMiddleClickGui() { return middleClickGui; }
    public void setMiddleClickGui(boolean v) { this.middleClickGui = v; }

    public boolean isCopyChat() { return copyChat; }
    public void setCopyChat(boolean v) { this.copyChat = v; }

    public boolean isCompactChat() { return compactChat; }
    public void setCompactChat(boolean v) { this.compactChat = v; }

    // ========== Config Persistence ==========

    @Override
    public JsonObject saveConfig() {
        JsonObject obj = new JsonObject();
        obj.addProperty("removeChatLimit", removeChatLimit);
        obj.addProperty("middleClickGui", middleClickGui);
        obj.addProperty("copyChat", copyChat);
        obj.addProperty("compactChat", compactChat);
        return obj;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("removeChatLimit")) {
            removeChatLimit = data.get("removeChatLimit").getAsBoolean();
        }
        if (data.has("middleClickGui")) {
            middleClickGui = data.get("middleClickGui").getAsBoolean();
        }
        if (data.has("copyChat")) {
            copyChat = data.get("copyChat").getAsBoolean();
        }
        if (data.has("compactChat")) {
            compactChat = data.get("compactChat").getAsBoolean();
        }
    }

    // ========== Settings GUI ==========

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Remove Chat Limit",
            "Unlimited chat message history",
            "chatutils_remove_limit",
            this::isRemoveChatLimit,
            this::setRemoveChatLimit
        ));

        settings.add(new CheckboxSetting(
            "Middle Click GUI",
            "Convert left-clicks to middle-clicks in certain GUIs",
            "chatutils_middle_click",
            this::isMiddleClickGui,
            this::setMiddleClickGui
        ));

        settings.add(new CheckboxSetting(
            "Copy Chat",
            "Right-click chat messages to copy them",
            "chatutils_copy_chat",
            this::isCopyChat,
            this::setCopyChat
        ));

        settings.add(new CheckboxSetting(
            "Compact Chat",
            "Stack repeated chat messages",
            "chatutils_compact_chat",
            this::isCompactChat,
            this::setCompactChat
        ));

        return settings;
    }
}
