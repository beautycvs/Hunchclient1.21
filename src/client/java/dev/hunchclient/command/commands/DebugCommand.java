package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.ModuleManager;

/**
 * Toggle debug mode and display debug information
 */
public class DebugCommand extends Command {

    private static boolean debugMode = false;

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public String getDescription() {
        return "Toggle debug mode and display debug information";
    }

    @Override
    public String getUsage() {
        return "debug [on|off|info]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"dbg"};
    }

    @Override
    public Category getCategory() {
        return Category.SYSTEM;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            debugMode = !debugMode;
            sendMessage("Debug mode: " + (debugMode ? "§aENABLED" : "§cDISABLED"));
        } else {
            String action = args[0].toLowerCase();
            switch (action) {
                case "on":
                    debugMode = true;
                    sendSuccess("Debug mode ENABLED");
                    break;
                case "off":
                    debugMode = false;
                    sendSuccess("Debug mode DISABLED");
                    break;
                case "info":
                    displayDebugInfo();
                    break;
                default:
                    sendError("Unknown action: " + action);
            }
        }
    }

    private void displayDebugInfo() {
        sendMessage("§d§l=== Debug Information ===");
        sendMessage("§7Client: §eHunchClient 1.21");
        sendMessage("§7Minecraft: §e" + mc.getLaunchedVersion());

        if (mc.player != null) {
            sendMessage("§7Position: §e" + String.format("%.1f, %.1f, %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            sendMessage("§7World: §e" + (mc.level != null ? mc.level.dimension().location().toString() : "None"));
        }

        ModuleManager manager = ModuleManager.getInstance();
        sendMessage("§7Modules: §e" + manager.getModules().size() + " total, " +
                    manager.getEnabledModules().size() + " enabled");

        sendMessage("§7Memory: §e" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + " MB used");
        sendMessage("§7Debug Mode: " + (debugMode ? "§aENABLED" : "§cDISABLED"));
    }

    public static boolean isDebugMode() {
        return debugMode;
    }
}