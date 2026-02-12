package dev.hunchclient.gui.irc;

import dev.hunchclient.module.impl.LanchoModule;
import dev.hunchclient.module.impl.lancho.LanchoHistoryManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Lancho AI Chat Window
 * Local-only DM window for chatting with Lancho AI
 */
public class LanchoDmWindow {

    private final Minecraft mc;
    private final Font textRenderer;
    private final LanchoModule lanchoModule;

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

    // Thinking animation
    private long thinkingStartTime = 0;

    // Colors
    private static final int BG_COLOR = 0xE8171717;
    private static final int BORDER_COLOR = 0xFF2A2A2A;
    private static final int ACCENT_COLOR = 0xFF9966FF; // Purple for Lancho AI
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private static final int MY_MSG_COLOR = 0xFF5BFF8B; // Green
    private static final int AI_MSG_COLOR = 0xFFFF66FF; // Pink for AI

    public LanchoDmWindow(Minecraft mc, int x, int y, int width, int height) {
        this.mc = mc;
        this.textRenderer = mc.font;
        this.lanchoModule = LanchoModule.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Create input field
        int inputHeight = 20;
        int inputY = y + height - inputHeight - 5;
        this.inputField = new EditBox(textRenderer, x + 5, inputY, width - 10, inputHeight, Component.literal(""));
        this.inputField.setMaxLength(256);
        this.inputField.setHint(Component.literal("Ask Lancho AI..."));
        this.inputField.setBordered(true);
        this.inputField.setEditable(true);
        this.inputField.setCanLoseFocus(true);

        // Load conversation history from local storage
        loadLocalHistory();
    }

    private void loadLocalHistory() {
        // Load ALL Lancho history (not session-specific)
        List<LanchoHistoryManager.ConversationMessage> history = LanchoHistoryManager.loadHistory();

        String myName = mc.player != null ? mc.player.getName().getString() : "Me";

        // Add messages to chat window
        for (LanchoHistoryManager.ConversationMessage msg : history) {
            boolean isFromMe = msg.role.equals("user");
            String sender = isFromMe ? myName : "Lancho";
            addMessage(new ChatMessage(sender, msg.content, msg.timestamp, isFromMe));
        }

        if (!history.isEmpty()) {
            addMessage(new ChatMessage("System", "§7Loaded " + history.size() + " messages from history", System.currentTimeMillis(), false));
        }
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR);
        drawBorder(context, x, y, width, height, BORDER_COLOR);

        // Title bar
        int titleBarHeight = 25;
        context.fill(x + 1, y + 1, x + width - 1, y + titleBarHeight, 0xFF1F1F1F);

        String title = "§dLancho AI Chat";
        context.drawString(textRenderer, title, x + 10, y + 8, TEXT_COLOR, false);

        // Connection status
        boolean connected = lanchoModule != null && lanchoModule.isConnected();
        String status = connected ? "§a●" : "§c●";
        context.drawString(textRenderer, status, x + width - 30, y + 8, TEXT_COLOR, false);

        // Close button
        int closeX = x + width - 20;
        int closeY = y + 5;
        isHoveringClose = mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15;
        int closeColor = isHoveringClose ? 0xFFFF4444 : 0xFF666666;
        context.drawString(textRenderer, "X", closeX, closeY, closeColor, false);

        // Render messages
        renderMessages(context);

        // Input field background
        int inputY = y + height - 25;
        context.fill(x + 5, inputY, x + width - 5, inputY + 20, 0xFF3A3A3A);
        drawBorder(context, x + 5, inputY, width - 10, 20, ACCENT_COLOR);

        // Input field
        inputField.render(context, mouseX, mouseY, delta);

        // Resize handle indicator
        int handleX = x + width - RESIZE_HANDLE_SIZE;
        int handleY = y + height - RESIZE_HANDLE_SIZE;
        boolean isHoveringHandle = mouseX >= handleX && mouseX <= x + width &&
                                   mouseY >= handleY && mouseY <= y + height;
        int handleColor = isHoveringHandle ? ACCENT_COLOR : BORDER_COLOR;

        for (int i = 0; i < 3; i++) {
            int offset = i * 4 + 2;
            context.fill(x + width - offset, y + height - 2, x + width - offset + 2, y + height, handleColor);
            context.fill(x + width - 2, y + height - offset, x + width, y + height - offset + 2, handleColor);
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
        String time = "§8[" + msg.getFormattedTime() + "]";
        int timeWidth = textRenderer.width(time);
        context.drawString(textRenderer, time, x, y, 0xFF666666, false);

        int senderColor = msg.isFromMe() ? MY_MSG_COLOR : AI_MSG_COLOR;
        String sender = msg.getSender() + ":";
        context.drawString(textRenderer, sender, x + timeWidth + 5, y, senderColor, false);

        int contentX = x + timeWidth + textRenderer.width(sender) + 10;
        int maxContentWidth = width - (contentX - x);

        // Check if this is the thinking indicator
        String content = msg.getContent();
        if (content.contains("§8Thinking")) {
            // Animated dots for thinking indicator
            long elapsed = System.currentTimeMillis() - thinkingStartTime;
            int dotCount = (int)((elapsed / 500) % 4); // 0, 1, 2, 3 dots cycling
            String dots = ".".repeat(dotCount);
            content = "§8Lancho is thinking" + dots;
        }

        // Wrap text
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

    private int calculateMessageHeight(ChatMessage msg, int width) {
        String time = "§8[" + msg.getFormattedTime() + "]";
        int timeWidth = textRenderer.width(time);

        String sender = msg.getSender() + ":";
        int contentX = timeWidth + textRenderer.width(sender) + 10;
        int maxContentWidth = width - contentX;

        List<String> lines = wrapText(msg.getContent(), maxContentWidth);
        return lines.size() * textRenderer.lineHeight + 2;
    }

    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Check close button FIRST
        int closeX = x + width - 20;
        int closeY = y + 5;
        if (mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15) {
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

        if (lanchoModule == null || !lanchoModule.isEnabled()) {
            addMessage(new ChatMessage("System", "§cLancho module is not enabled!", System.currentTimeMillis(), false));
            return;
        }

        if (!lanchoModule.isConnected()) {
            addMessage(new ChatMessage("System", "§cNot connected to Lancho server!", System.currentTimeMillis(), false));
            return;
        }

        // Add user message to window
        String myName = mc.player != null ? mc.player.getName().getString() : "Me";
        addMessage(new ChatMessage(myName, text, System.currentTimeMillis(), true));

        // Clear input immediately
        inputField.setValue("");

        // Show thinking indicator
        thinkingStartTime = System.currentTimeMillis();
        addMessage(new ChatMessage("Lancho", "§8Thinking...", System.currentTimeMillis(), false));

        // Send to Lancho AI with callback
        lanchoModule.sendDirectMessage(text, response -> {
            mc.execute(() -> {
                // Remove thinking indicator
                removeThinkingIndicator();

                if (response != null && !response.isEmpty()) {
                    // Add AI response to window
                    addMessage(new ChatMessage("Lancho", response, System.currentTimeMillis(), false));
                } else {
                    addMessage(new ChatMessage("System", "§cNo response from Lancho", System.currentTimeMillis(), false));
                }
            });
        });
    }

    private void removeThinkingIndicator() {
        if (!messages.isEmpty()) {
            ChatMessage lastMsg = messages.get(messages.size() - 1);
            if (lastMsg.getContent().contains("§8Thinking")) {
                messages.remove(messages.size() - 1);
            }
        }
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        if (messages.size() > maxMessages) {
            messages.remove(0);
        }
        scrollOffset = 0; // Auto-scroll to bottom
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

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);           // Top
        context.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        context.fill(x, y, x + 1, y + height, color);          // Left
        context.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }
}
