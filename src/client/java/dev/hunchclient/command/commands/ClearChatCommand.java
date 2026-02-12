package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

/**
 * Clear the chat
 */
public class ClearChatCommand extends Command {

    @Override
    public String getName() {
        return "clearchat";
    }

    @Override
    public String getDescription() {
        return "Clear the chat";
    }

    @Override
    public String getUsage() {
        return "clearchat";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"cc", "clear", "cls"};
    }

    @Override
    public Category getCategory() {
        return Category.UTILITY;
    }

    @Override
    public void execute(String[] args) {
        if (mc.player != null && mc.gui != null) {
            mc.gui.getChat().clearMessages(false);
            sendSuccess("Chat cleared");
        }
    }
}