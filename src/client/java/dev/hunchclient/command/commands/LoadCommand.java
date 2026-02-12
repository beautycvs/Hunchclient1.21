package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.util.ConfigManager;
import dev.hunchclient.command.bind.BindManager;

/**
 * Load configuration from file
 */
public class LoadCommand extends Command {

    @Override
    public String getName() {
        return "load";
    }

    @Override
    public String getDescription() {
        return "Load configuration from file";
    }

    @Override
    public String getUsage() {
        return "load";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"loadconfig", "reload"};
    }

    @Override
    public Category getCategory() {
        return Category.CONFIG;
    }

    @Override
    public void execute(String[] args) {
        try {
            // Load main config
            ConfigManager.load();
            sendSuccess("Configuration loaded successfully");

            // Load binds
            BindManager.getInstance().loadBinds();
            sendSuccess("Bindings loaded successfully");

        } catch (Exception e) {
            sendError("Failed to load configuration: " + e.getMessage());
        }
    }
}