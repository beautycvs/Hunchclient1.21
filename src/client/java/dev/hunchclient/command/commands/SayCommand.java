package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

/**
 * Send a message in chat
 */
public class SayCommand extends Command {

    @Override
    public String getName() {
        return "say";
    }

    @Override
    public String getDescription() {
        return "Send a message in chat";
    }

    @Override
    public String getUsage() {
        return "say <message>";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"chat", "msg"};
    }

    @Override
    public Category getCategory() {
        return Category.UTILITY;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String message = String.join(" ", args);
        if (mc.player != null) {
            mc.player.connection.sendChat(message);
        }
    }
}