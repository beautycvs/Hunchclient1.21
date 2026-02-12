package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.util.ConfigManager;
import dev.hunchclient.command.bind.BindManager;

/**
 * Save current configuration
 */
public class SaveCommand extends Command {

    @Override
    public String getName() {
        return "save";
    }

    @Override
    public String getDescription() {
        return "Save current configuration";
    }

    @Override
    public String getUsage() {
        return "save";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"saveconfig"};
    }

    @Override
    public Category getCategory() {
        return Category.CONFIG;
    }

    @Override
    public void execute(String[] args) {
        try {
            // Save main config
            ConfigManager.save();
            sendSuccess("Configuration saved successfully");

            // Save binds
            BindManager.getInstance().saveBinds();
            sendSuccess("Bindings saved successfully");

        } catch (Exception e) {
            sendError("Failed to save configuration: " + e.getMessage());
        }
    }
}