package dev.hunchclient.gui.irc;

import dev.hunchclient.module.impl.IrcRelayModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * DM Chat Window GUI Component
 * For 1-on-1 direct message conversations
 */
public class DmChatWindow {

    private final Minecraft mc;
    private final Font textRenderer;
    private final IrcRelayModule ircModule;
    private final String recipient;

    // Window dimensions
    private int x, y, width, height;

    // Chat messages
    private final List<ChatMessage> messages = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxMessages = 100;

    // Input field
    private EditBox inputField;

    // Dragging
    private boolean isDragging = false;
    private int dragStartX, dragStartY;

    // Resizing
    private boolean isResizing = false;
    private int resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight;
    private static final int RESIZE_HANDLE_SIZE = 15;

    // Close button
    private boolean isHoveringClose = false;

    // Colors
    private static final int BG_COLOR = 0xE8171717;
    private static final int BORDER_COLOR = 0xFF2A2A2A;
    private static final int DM_ACCENT_COLOR = 0xFFFF66CC; // Pink for DMs
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private static final int MY_MSG_COLOR = 0xFF5BFF8B; // Green
    private static final int OTHER_MSG_COLOR = 0xFFFFAA55; // Orange

    public DmChatWindow(Minecraft mc, String recipient, int x, int y, int width, int height) {
        this.mc = mc;
        this.textRenderer = mc.font;
        this.ircModule = IrcRelayModule.getInstance();
        this.recipient = recipient;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Create input field
        int inputHeight = 20;
        int inputY = y + height - inputHeight - 5;
        this.inputField = new EditBox(textRenderer, x + 5, inputY, width - 10, inputHeight, Component.literal(""));
        this.inputField.setMaxLength(256);
        this.inputField.setHint(Component.literal("Type DM to " + recipient + "..."));
        this.inputField.setBordered(true);
        this.inputField.setEditable(true);
        this.inputField.setCanLoseFocus(true);

        // Load conversation history
        loadConversationHistory();
    }

    private void loadConversationHistory() {
        if (ircModule != null) {
            ircModule.loadConversation(recipient, history -> {
                // Populate messages from history
                String myName = mc.player != null ? mc.player.getName().getString() : "Me";
                for (IrcRelayModule.ConversationMessage msg : history) {
                    boolean isFromMe = msg.from.equalsIgnoreCase(myName);
                    addMessage(new ChatMessage(msg.from, msg.text, msg.timestamp, isFromMe));
                }
            });
        }
    }

    public String getRecipient() {
        return recipient;
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        try {
            // Background
            context.fill(x, y, x + width, y + height, BG_COLOR);
            drawBorder(context, x, y, width, height, BORDER_COLOR);

            // Title bar
            int titleBarHeight = 25;
            context.fill(x + 1, y + 1, x + width - 1, y + titleBarHeight, 0xFF1F1F1F);

            String title = "§dDM: " + recipient;
            context.drawString(textRenderer, title, x + 10, y + 8, DM_ACCENT_COLOR, false);

            // Close button (X in top-right)
            int closeX = x + width - 20;
            int closeY = y + 5;
            isHoveringClose = mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15;
            int closeColor = isHoveringClose ? 0xFFFF5555 : TEXT_COLOR;
            context.drawString(textRenderer, "X", closeX, closeY, closeColor, false);

            // Render messages
            try {
                renderMessages(context);
            } catch (Exception e) {
                context.drawString(textRenderer, "§cError rendering messages", x + 10, y + 35, 0xFFFF5555, false);
            }

            // Input field background
            int inputY = y + height - 25;
            context.fill(x + 5, inputY, x + width - 5, inputY + 20, 0xFF3A3A3A);
            drawBorder(context, x + 5, inputY, width - 10, 20, DM_ACCENT_COLOR);

            // Input field
            try {
                inputField.render(context, mouseX, mouseY, delta);
            } catch (Exception e) {
                // Silently fail - input field rendering issues shouldn't crash the window
            }

            // Resize handle indicator (bottom-right corner)
            int handleX = x + width - RESIZE_HANDLE_SIZE;
            int handleY = y + height - RESIZE_HANDLE_SIZE;
            boolean isHoveringHandle = mouseX >= handleX && mouseX <= x + width &&
                                       mouseY >= handleY && mouseY <= y + height;
            int handleColor = isHoveringHandle ? DM_ACCENT_COLOR : BORDER_COLOR;

            // Draw resize handle lines (bottom-right corner)
            for (int i = 0; i < 3; i++) {
                int offset = i * 4 + 2;
                context.fill(x + width - offset, y + height - 2, x + width - offset + 2, y + height, handleColor);
                context.fill(x + width - 2, y + height - offset, x + width, y + height - offset + 2, handleColor);
            }
        } catch (Exception e) {
            System.err.println("Critical error in DmChatWindow.render: " + e.getMessage());
            e.printStackTrace();
            // Draw error overlay
            try {
                context.fill(x, y, x + width, y + height, 0xAA000000);
                context.drawCenteredString(textRenderer, "§cWindow Error", x + width / 2, y + height / 2, 0xFFFF5555);
            } catch (Exception ignored) {}
        }
    }

    private void renderMessages(GuiGraphics context) {
        int messageAreaX = x + 5;
        int messageAreaY = y + 30;
        int messageAreaWidth = width - 10;
        int messageAreaHeight = height - 65;

        // Scissor for clipping
        context.enableScissor(messageAreaX, messageAreaY, messageAreaX + messageAreaWidth, messageAreaY + messageAreaHeight);

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

        String sender = msg.getSender() + ":";
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

        String sender = msg.getSender() + ":";
        int contentX = timeWidth + textRenderer.width(sender) + 10;
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

        try {

            // Check close button FIRST
            int closeX = x + width - 20;
            int closeY = y + 5;
            if (mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15) {
                // Clear focus before closing
                inputField.setFocused(false);
                // Return false to signal window should be closed
                return false;
            }

            // Check if click is on input field
            int inputY = y + height - 25;
            boolean clickedOnInput = mouseX >= x + 5 && mouseX <= x + width - 5 &&
                                     mouseY >= inputY && mouseY <= inputY + 20;

            if (clickedOnInput) {
                inputField.setFocused(true);
                inputField.mouseClicked(click, doubled);
                return true;
            } else {
                inputField.setFocused(false);
            }

            // Check if click is inside window at all
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
                return false;
            }

            // Check resize handle (bottom-right corner)
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

            // Check if dragging title bar
            int titleBarHeight = 25;
            if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + titleBarHeight) {
                isDragging = true;
                dragStartX = (int) mouseX - x;
                dragStartY = (int) mouseY - y;
                return true;
            }

            return false;
        } catch (Exception e) {
            System.err.println("Error in DmChatWindow.mouseClicked: " + e.getMessage());
            // Clear focus on error
            inputField.setFocused(false);
            return false;
        }
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
            scrollOffset = Math.max(0, scrollOffset);
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

        // Check if recipient is Lancho - route to AI instead of IRC DM
        if (recipient.equalsIgnoreCase("Lancho")) {
            sendToLancho(text);
        } else {
            // Send DM and show immediately in window (optimistic UI)
            if (ircModule != null) {
                ircModule.sendDm(recipient, text);

                // Add to local message list immediately (won't show in main Minecraft chat)
                String playerName = mc.player != null ? mc.player.getName().getString() : "Me";
                addMessage(new ChatMessage(playerName, text, System.currentTimeMillis(), true));
            }
        }

        inputField.setValue("");
    }

    /**
     * Send message to Lancho AI instead of IRC DM
     */
    private void sendToLancho(String prompt) {
        try {
            dev.hunchclient.module.impl.LanchoModule lanchoModule = dev.hunchclient.module.impl.LanchoModule.getInstance();
            if (lanchoModule == null || !lanchoModule.isEnabled() || !lanchoModule.isConnected()) {
                addMessage(new ChatMessage("System", "Lancho is not connected!", System.currentTimeMillis(), false));
                return;
            }

            // Show user message immediately
            String playerName = mc.player != null ? mc.player.getName().getString() : "Me";
            addMessage(new ChatMessage(playerName, prompt, System.currentTimeMillis(), true));

            // Show typing indicator
            addMessage(new ChatMessage("Lancho", "§8Typing...", System.currentTimeMillis(), false));

            // Route to Lancho's request queue with callback
            lanchoModule.getRequestQueue().enqueueWithCallback(
                prompt,
                "dm", // Special chat type for DM
                playerName,
                lanchoModule.getPersonality(),
                response -> {
                    // Remove typing indicator
                    removeLastTypingIndicator();

                    // Add Lancho's response to DM window
                    if (response != null && !response.isEmpty()) {
                        addMessage(new ChatMessage("Lancho", response, System.currentTimeMillis(), false));
                    } else {
                        addMessage(new ChatMessage("Lancho", "§cNo response received", System.currentTimeMillis(), false));
                    }
                }
            );

        } catch (Exception e) {
            addMessage(new ChatMessage("System", "§cError: " + e.getMessage(), System.currentTimeMillis(), false));
        }
    }

    /**
     * Remove the last "Typing..." message (typing indicator)
     */
    private void removeLastTypingIndicator() {
        if (!messages.isEmpty()) {
            ChatMessage lastMsg = messages.get(messages.size() - 1);
            if (lastMsg.getContent().contains("§8Typing...")) {
                messages.remove(messages.size() - 1);
            }
        }
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
     * Add DM message (called when DM is received)
     */
    public void addDmMessage(String sender, String content, long timestamp) {
        boolean isFromMe = mc.player != null && sender.equalsIgnoreCase(mc.player.getName().getString());
        addMessage(new ChatMessage(sender, content, timestamp, isFromMe));
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
        inputField.setWidth(width - 10);
    }

    public boolean isClosing() {
        return false; // Close detection is handled in mouseClicked
    }

    public boolean isHoveringCloseButton(double mouseX, double mouseY) {
        int closeX = x + width - 20;
        int closeY = y + 5;
        return mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15;
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }
}
