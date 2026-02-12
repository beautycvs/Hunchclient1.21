package dev.hunchclient.gui.irc;

import dev.hunchclient.module.impl.IrcRelayModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * IRC Chat Window GUI Component
 * Features: Message display, scrolling, DM system, user list
 */
public class IrcChatWindow {

    private final Font textRenderer;
    private final IrcRelayModule ircModule;

    // Window dimensions
    private int x, y, width, height;

    // Chat messages
    private final List<ChatMessage> messages = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxMessages = 100;

    // Input field
    private EditBox inputField;

    // User list
    private final List<String> onlineUsers = new ArrayList<>();
    private static final int USER_LIST_WIDTH = 100;
    private int userListScrollOffset = 0;

    // DM window callback
    private Consumer<String> onOpenDmWindow;

    // DM window lookup (for routing incoming DMs)
    private java.util.function.Function<String, Object> dmWindowLookup;

    // Close button callback
    private Runnable onClose;

    // Dragging
    private boolean isDragging = false;
    private int dragStartX, dragStartY;

    // Resizing
    private boolean isResizing = false;
    private int resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight;
    private static final int RESIZE_HANDLE_SIZE = 15;

    // Colors
    private static final int BG_COLOR = 0xE8171717;
    private static final int BORDER_COLOR = 0xFF2A2A2A;
    private static final int ACCENT_COLOR = 0xFF6699CC;
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private static final int DM_COLOR = 0xFFFF66CC; // Pink for DMs
    private static final int MY_MSG_COLOR = 0xFF5BFF8B; // Green
    private static final int OTHER_MSG_COLOR = 0xFFFFAA55; // Orange

    public IrcChatWindow(Minecraft mc, int x, int y, int width, int height) {
        System.out.println("[IrcChatWindow] New instance created: " + this.hashCode());
        this.textRenderer = mc.font;
        this.ircModule = IrcRelayModule.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Create input field
        int inputHeight = 20;
        int inputY = y + height - inputHeight - 5;
        this.inputField = new EditBox(textRenderer, x + 5, inputY, width - 10 - USER_LIST_WIDTH, inputHeight, Component.literal(""));
        this.inputField.setMaxLength(256);
        this.inputField.setHint(Component.literal("Type message..."));
        this.inputField.setBordered(true);
        this.inputField.setEditable(true);
        this.inputField.setCanLoseFocus(true);
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR);
        drawBorder(context, x, y, width, height, BORDER_COLOR);

        // Title bar
        int titleBarHeight = 25;
        context.fill(x + 1, y + 1, x + width - 1, y + titleBarHeight, 0xFF1F1F1F);

        String title = "§bIRC Chat";
        context.drawString(textRenderer, title, x + 10, y + 8, TEXT_COLOR, false);

        // Close button (X) in top-right of title bar
        int closeButtonSize = 16;
        int closeButtonX = x + width - closeButtonSize - 5;
        int closeButtonY = y + 5;
        boolean closeButtonHovered = mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonSize &&
                                     mouseY >= closeButtonY && mouseY <= closeButtonY + closeButtonSize;

        int closeButtonColor = closeButtonHovered ? 0xFFFF5555 : 0xFF888888;
        context.fill(closeButtonX, closeButtonY, closeButtonX + closeButtonSize, closeButtonY + closeButtonSize,
                    closeButtonHovered ? 0xFF3A0000 : 0xFF2A2A2A);
        drawBorder(context, closeButtonX, closeButtonY, closeButtonSize, closeButtonSize, closeButtonColor);

        // Draw X
        String xText = "§c✕";
        int xWidth = textRenderer.width(xText);
        context.drawString(textRenderer, xText, closeButtonX + (closeButtonSize - xWidth) / 2,
                        closeButtonY + (closeButtonSize - textRenderer.lineHeight) / 2 + 1, closeButtonColor, false);

        // User count in title
        String userCount = "§7(" + onlineUsers.size() + " online)";
        context.drawString(textRenderer, userCount, x + width - textRenderer.width(userCount) - closeButtonSize - 30, y + 8, TEXT_COLOR, false);

        // Render user list sidebar
        renderUserList(context, mouseX, mouseY);

        // Render messages (adjusted for user list)
        renderMessages(context);

        // Input field background (very visible)
        int inputY = y + height - 25;
        int messageAreaWidth = width - USER_LIST_WIDTH - 10;
        context.fill(x + 5, inputY, x + 5 + messageAreaWidth - 10, inputY + 20, 0xFF3A3A3A);
        drawBorder(context, x + 5, inputY, messageAreaWidth - 10, 20, ACCENT_COLOR);

        // Input field
        inputField.render(context, mouseX, mouseY, delta);

        // Resize handle indicator (bottom-right corner)
        int handleX = x + width - RESIZE_HANDLE_SIZE;
        int handleY = y + height - RESIZE_HANDLE_SIZE;
        boolean isHoveringHandle = mouseX >= handleX && mouseX <= x + width &&
                                   mouseY >= handleY && mouseY <= y + height;
        int handleColor = isHoveringHandle ? ACCENT_COLOR : BORDER_COLOR;

        // Draw resize handle lines (bottom-right corner)
        for (int i = 0; i < 3; i++) {
            int offset = i * 4 + 2;
            context.fill(x + width - offset, y + height - 2, x + width - offset + 2, y + height, handleColor);
            context.fill(x + width - 2, y + height - offset, x + width, y + height - offset + 2, handleColor);
        }
    }

    private void renderUserList(GuiGraphics context, int mouseX, int mouseY) {
        int userListX = x + width - USER_LIST_WIDTH;
        int userListY = y + 30;
        int userListHeight = height - 30;

        // User list background
        context.fill(userListX, userListY, x + width, y + height, 0xFF1A1A1A);

        // Separator line
        context.fill(userListX - 1, userListY, userListX, y + height, BORDER_COLOR);

        // User list title
        context.drawString(textRenderer, "§7Users", userListX + 5, userListY + 5, TEXT_COLOR, false);

        // Scissor for user list
        int userListContentY = userListY + 20;
        int userListContentHeight = userListHeight - 20;
        context.enableScissor(userListX, userListContentY, x + width, userListContentY + userListContentHeight);

        int currentY = userListContentY - userListScrollOffset;
        int lineHeight = textRenderer.lineHeight + 4;

        for (String user : onlineUsers) {
            if (currentY >= userListContentY - lineHeight && currentY <= userListContentY + userListContentHeight) {
                boolean isHovering = mouseX >= userListX && mouseX <= x + width &&
                                   mouseY >= currentY && mouseY <= currentY + lineHeight;

                // Highlight on hover
                if (isHovering) {
                    context.fill(userListX, currentY, x + width, currentY + lineHeight, 0xFF2A2A2A);
                }

                // Draw username
                String displayName = user;
                if (textRenderer.width(displayName) > USER_LIST_WIDTH - 10) {
                    displayName = textRenderer.plainSubstrByWidth(displayName, USER_LIST_WIDTH - 15) + "...";
                }
                context.drawString(textRenderer, "§7" + displayName, userListX + 5, currentY + 2, TEXT_COLOR, false);
            }
            currentY += lineHeight;
        }

        context.disableScissor();
    }

    private void renderMessages(GuiGraphics context) {
        int messageAreaX = x + 5;
        int messageAreaY = y + 30;
        int messageAreaWidth = width - USER_LIST_WIDTH - 15; // Adjusted for user list
        int messageAreaHeight = height - 65;

        // DEBUG: Show message count
        context.drawString(textRenderer, "Messages: " + messages.size(), messageAreaX, messageAreaY + 5, 0xFFFFFFFF, false);

        // Scissor for clipping
        context.enableScissor(messageAreaX, messageAreaY + 20, messageAreaX + messageAreaWidth, messageAreaY + messageAreaHeight);

        int currentY = messageAreaY + messageAreaHeight - scrollOffset;

        // Render messages from bottom to top
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);

            // Calculate message height (including wrapped lines)
            int msgHeight = calculateMessageHeight(msg, messageAreaWidth);
            currentY -= msgHeight;

            if (currentY < messageAreaY - msgHeight) break;
            if (currentY > messageAreaY + messageAreaHeight) continue;

            renderMessage(context, msg, messageAreaX, currentY, messageAreaWidth);
        }

        context.disableScissor();
    }

    private void renderMessage(GuiGraphics context, ChatMessage msg, int x, int y, int width) {
        // Time stamp
        String time = "§8[" + msg.getFormattedTime() + "]";
        int timeWidth = textRenderer.width(time);
        context.drawString(textRenderer, time, x, y, 0xFF888888, false);

        // Sender name with color
        int nameColor = msg.isFromMe() ? MY_MSG_COLOR : OTHER_MSG_COLOR;
        if (msg.isPrivate()) nameColor = DM_COLOR;

        String senderPrefix = msg.isPrivate() ? "§d[DM] " : "";
        String sender = senderPrefix + msg.getSender() + ":";
        context.drawString(textRenderer, sender, x + timeWidth + 5, y, nameColor, false);

        // Message content
        int contentX = x + timeWidth + textRenderer.width(sender) + 10;
        int maxContentWidth = width - (contentX - x);
        String content = msg.getContent();

        // Wrap text if too long
        List<String> lines = wrapText(content, maxContentWidth);
        for (int i = 0; i < lines.size(); i++) {
            context.drawString(textRenderer, lines.get(i), contentX, y + (i * textRenderer.lineHeight), TEXT_COLOR, false);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (textRenderer.width(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.isEmpty() ? List.of("") : lines;
    }

    /**
     * Calculate the total height of a message including wrapped lines
     */
    private int calculateMessageHeight(ChatMessage msg, int width) {
        // Calculate available width for message content
        String time = "§8[" + msg.getFormattedTime() + "]";
        int timeWidth = textRenderer.width(time);

        String senderPrefix = msg.isPrivate() ? "§d[DM] " : "";
        String sender = senderPrefix + msg.getSender() + ":";

        int contentX = timeWidth + textRenderer.width(sender) + 15; // 5 + 10 spacing
        int maxContentWidth = width - contentX;

        // Get wrapped lines
        List<String> lines = wrapText(msg.getContent(), maxContentWidth);

        // Height = number of lines * font height
        return lines.size() * textRenderer.lineHeight + 2; // +2 for line spacing
    }

    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // 2) Bounds-Check FIRST - if outside window, don't process anything
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        // 1) Input field (höchste Priorität)
        int inputY = y + height - 25;
        int messageAreaWidth = width - USER_LIST_WIDTH - 10;
        boolean clickedOnInput = mouseX >= x + 5 && mouseX <= x + 5 + messageAreaWidth - 10 &&
                                 mouseY >= inputY && mouseY <= inputY + 20;

        if (clickedOnInput) {
            inputField.setFocused(true);
            inputField.mouseClicked(click, doubled);
            return true;
        } else {
            inputField.setFocused(false);
        }

        // 3) **Resize-Handle hat Priorität**
        int handleX = x + width - RESIZE_HANDLE_SIZE;
        int handleY = y + height - RESIZE_HANDLE_SIZE;
        if (button == 0 && mouseX >= handleX && mouseX <= x + width &&
            mouseY >= handleY && mouseY <= y + height) {
            isResizing = true;
            resizeStartX = (int) mouseX;
            resizeStartY = (int) mouseY;
            resizeStartWidth = width;
            resizeStartHeight = height;
            return true;
        }

        // 4) User-List-Klick - check if in user list area at all
        int userListX = x + width - USER_LIST_WIDTH;
        int userListY = y + 30; // After title bar

        // User list goes from title bar to just above input field
        if (button == 0 && mouseX >= userListX && mouseX <= x + width &&
            mouseY >= userListY && mouseY <= y + height - 30) {

            // Try to handle user click
            boolean handled = handleUserListClick(mouseX, mouseY);

            // Debug output
            if (handled) {
                System.out.println("User clicked! Opening DM window");
            }

            if (handled) {
                return true;
            }
            // If not handled, continue to other handlers (e.g., scrolling in user list)
        }

        // 5) Close button (X)
        int closeButtonSize = 16;
        int closeButtonX = x + width - closeButtonSize - 5;
        int closeButtonY = y + 5;
        if (button == 0 && mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonSize &&
            mouseY >= closeButtonY && mouseY <= closeButtonY + closeButtonSize) {
            // Close the IRC chat window
            if (onClose != null) {
                onClose.run();
            }
            return true;
        }

        // 6) Dragging der Titelbar
        int titleBarHeight = 25;
        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + titleBarHeight) {
            isDragging = true;
            dragStartX = (int) mouseX - x;
            dragStartY = (int) mouseY - y;
            return true;
        }

        return false;
    }

    private boolean handleUserListClick(double mouseX, double mouseY) {
        int userListX = x + width - USER_LIST_WIDTH;
        int userListY = y + 30;
        int userListContentY = userListY + 20;
        int userListHeight = height - 30;
        int userListContentHeight = userListHeight - 20;
        int lineHeight = textRenderer.lineHeight + 4;

        int currentY = userListContentY - userListScrollOffset;

        // Only check users within the visible scissor area
        int visibleBottom = userListContentY + userListContentHeight;

        System.out.println("[IrcChatWindow:" + this.hashCode() + "] handleUserListClick called: mouseY=" + mouseY + ", users=" + onlineUsers.size() + ", callback=" + (onOpenDmWindow != null ? "SET" : "NULL"));

        for (String user : onlineUsers) {
            // Check if this user's row is visible
            boolean isRowVisible = currentY >= userListContentY - lineHeight && currentY <= visibleBottom;

            System.out.println("  Checking user '" + user + "': currentY=" + currentY + ", mouseY=" + mouseY +
                             ", range=" + currentY + "-" + (currentY + lineHeight) + ", visible=" + isRowVisible);

            if (isRowVisible &&
                mouseY >= currentY && mouseY <= currentY + lineHeight &&
                mouseX >= userListX && mouseX <= x + width) {
                // User geklickt - DM-Fenster öffnen
                System.out.println("  -> MATCH! Opening DM for " + user);
                if (onOpenDmWindow != null) {
                    onOpenDmWindow.accept(user);
                } else {
                    System.out.println("  -> ERROR: onOpenDmWindow callback is NULL!");
                }
                return true;
            }
            currentY += lineHeight;
        }

        System.out.println("  -> No user matched");
        return false;
    }

    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0 && (isDragging || isResizing)) {
            isDragging = false;
            isResizing = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (isDragging) {
            setPosition((int) mouseX - dragStartX, (int) mouseY - dragStartY);
            return true;
        }
        if (isResizing) {
            int deltaWidth = (int) mouseX - resizeStartX;
            int deltaHeight = (int) mouseY - resizeStartY;
            int newWidth = Math.max(250, resizeStartWidth + deltaWidth);
            int newHeight = Math.max(200, resizeStartHeight + deltaHeight);
            setSize(newWidth, newHeight);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y + 30 && mouseY <= y + height - 30) {
            scrollOffset -= (int)(verticalAmount * 20);
            if (scrollOffset < 0) scrollOffset = 0;
            return true;
        }
        return false;
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        // Enter OR Numpad Enter to send
        if ((keyCode == 257 || keyCode == 335) && inputField.isFocused()) { // ENTER or NUMPAD_ENTER
            sendMessage();
            return true;
        }

        return inputField.keyPressed(input);
    }

    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        char chr = (char)input.codepoint();
        int modifiers = input.modifiers();

        return inputField.charTyped(input);
    }

    private void sendMessage() {
        String text = inputField.getValue().trim();
        if (text.isEmpty()) return;

        // Send message directly (no DM support in old IRC)
        ircModule.sendMessage(text);
        inputField.setValue("");
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        if (messages.size() > maxMessages) {
            messages.remove(0);
        }
        // Auto-scroll to bottom on new message
        scrollOffset = 0;
    }

    /**
     * Add IRC message from IrcRelayModule
     */
    public void addIrcMessage(String sender, String content, long timestamp, boolean isFromMe) {
        addMessage(new ChatMessage(sender, content, timestamp, isFromMe));
    }

    public void addSystemMessage(String text) {
        addMessage(new ChatMessage("System", text, System.currentTimeMillis(), false));
    }

    /**
     * Update the online user list
     */
    public void updateUserList(List<String> users) {
        synchronized (onlineUsers) {
            onlineUsers.clear();
            onlineUsers.addAll(users);
        }
    }

    /**
     * Handle incoming DM - route to DM window
     */
    public void handleIncomingDm(String sender, String content, long timestamp) {
        // Trigger DM window opening if callback is set
        if (onOpenDmWindow != null) {
            onOpenDmWindow.accept(sender);
        }

        // Route message to the DM window (if it exists now)
        if (dmWindowLookup != null) {
            Object dmWindowObj = dmWindowLookup.apply(sender);
            if (dmWindowObj instanceof dev.hunchclient.gui.irc.DmChatWindow dmWindow) {
                dmWindow.addDmMessage(sender, content, timestamp);
            }
        }
    }

    /**
     * Set callback for opening DM windows
     */
    public void setOnOpenDmWindow(Consumer<String> callback) {
        this.onOpenDmWindow = callback;
    }

    /**
     * Set DM window lookup function
     */
    public void setDmWindowLookup(java.util.function.Function<String, Object> lookup) {
        this.dmWindowLookup = lookup;
    }

    /**
     * Set callback for closing the IRC chat window
     */
    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        // Update input field position
        int inputHeight = 20;
        int inputY = y + height - inputHeight - 5;
        inputField.setPosition(x + 5, inputY);
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        // Update input field size
        int inputHeight = 20;
        int inputY = y + height - inputHeight - 5;
        inputField.setPosition(x + 5, inputY);
        int messageAreaWidth = width - USER_LIST_WIDTH - 10;
        inputField.setWidth(Math.max(50, messageAreaWidth - 10));
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }
}
