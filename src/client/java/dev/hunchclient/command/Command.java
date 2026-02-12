package dev.hunchclient.command;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Base class for all HunchClient commands
 */
public abstract class Command {

    protected final Minecraft mc = Minecraft.getInstance();

    /**
     * Get the command name
     */
    public abstract String getName();

    /**
     * Get the command description
     */
    public abstract String getDescription();

    /**
     * Get the command usage
     */
    public abstract String getUsage();

    /**
     * Execute the command
     */
    public abstract void execute(String[] args);

    /**
     * Get command aliases (alternative names)
     */
    public String[] getAliases() {
        return new String[0];
    }

    /**
     * Get suggestions for command arguments (for autocomplete)
     */
    public List<String> getSuggestions(String[] args) {
        return Collections.emptyList();
    }

    /**
     * Check if the command requires a specific permission level
     */
    public boolean requiresPermission() {
        return false;
    }

    /**
     * Get the category of this command
     */
    public Category getCategory() {
        return Category.GENERAL;
    }

    /**
     * Send a message using the CommandManager
     */
    protected void sendMessage(String message) {
        CommandManager.sendMessage(message);
    }

    /**
     * Send an error message
     */
    protected void sendError(String message) {
        CommandManager.sendError(message);
    }

    /**
     * Send a success message
     */
    protected void sendSuccess(String message) {
        CommandManager.sendSuccess(message);
    }

    /**
     * Send a usage message
     */
    protected void sendUsage() {
        sendError("Usage: " + CommandManager.SECONDARY_PREFIX + getUsage());
    }

    /**
     * Send a warning message
     */
    protected void sendWarning(String message) {
        CommandManager.sendWarning(message);
    }

    /**
     * Send an info message
     */
    protected void sendInfo(String message) {
        CommandManager.sendInfo(message);
    }

    /**
     * Command categories
     */
    public enum Category {
        GENERAL("General", "General client commands"),
        MODULE("Module", "Module management commands"),
        CONFIG("Config", "Configuration commands"),
        UTILITY("Utility", "Utility commands"),
        SOCIAL("Social", "Social and communication commands"),
        SYSTEM("System", "System and debug commands"),
        MACRO("Macro", "Macro and automation commands");

        private final String name;
        private final String description;

        Category(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }
}