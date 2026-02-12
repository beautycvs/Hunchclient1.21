package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

/**
 * Reload client systems
 */
public class ReloadCommand extends Command {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload client systems";
    }

    @Override
    public String getUsage() {
        return "reload";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"refresh"};
    }

    @Override
    public Category getCategory() {
        return Category.SYSTEM;
    }

    @Override
    public void execute(String[] args) {
        sendInfo("Reloading client systems...");

        // Reload config
        try {
            dev.hunchclient.util.ConfigManager.load();
            sendSuccess("Configuration reloaded");
        } catch (Exception e) {
            sendError("Failed to reload config: " + e.getMessage());
        }

        // Reload binds
        dev.hunchclient.command.bind.BindManager.getInstance().loadBinds();
        sendSuccess("Bindings reloaded");

        sendSuccess("Client systems reloaded");
    }
}