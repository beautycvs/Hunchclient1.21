package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.dungeons.SSHelperModule;

/**
 * Debug command for testing SS Alert notifications and toggling debug mode
 */
public class SSHelperDebugCommand extends Command {

    @Override
    public String getName() {
        return "sshelpertest";
    }

    @Override
    public String getDescription() {
        return "Test SS Alert notifications and toggle debug mode";
    }

    @Override
    public String getUsage() {
        return "sshelpertest [broke|restart|countdown|debug|hud]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"sst", "sstest"};
    }

    @Override
    public Category getCategory() {
        return Category.SYSTEM;
    }

    @Override
    public void execute(String[] args) {
        SSHelperModule ssHelper = ModuleManager.getInstance().getModule(SSHelperModule.class);

        if (ssHelper == null) {
            sendError("SSHelper module not found!");
            return;
        }

        if (!ssHelper.isEnabled()) {
            sendError("SSHelper module is not enabled!");
            return;
        }

        if (args.length == 0) {
            sendUsage();
            return;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "broke":
                ssHelper.debugShowAlert("§c§l§nSS BROKE!", 3000);
                sendSuccess("Showing SS BROKE alert for 3 seconds");
                break;

            case "restart":
                ssHelper.debugShowAlert("§a§l§nSS RESTART!", 3000);
                sendSuccess("Showing SS RESTART alert for 3 seconds");
                break;

            case "countdown":
                ssHelper.debugStartCountdown(5.0);
                sendSuccess("Starting 5 second P3 countdown");
                break;

            case "debug":
                ssHelper.toggleDebugMode();
                sendSuccess("Debug mode: " + (ssHelper.isDebugModeEnabled() ? "§aENABLED" : "§cDISABLED"));
                if (ssHelper.isDebugModeEnabled()) {
                    sendMessage("§7All timing events will be logged to console");
                }
                break;

            case "hud":
                ssHelper.toggleDebugHUD();
                sendSuccess("Debug HUD: " + (ssHelper.isDebugHUDEnabled() ? "§aENABLED" : "§cDISABLED"));
                if (ssHelper.isDebugHUDEnabled()) {
                    sendMessage("§7Live timing information will be shown on screen during P3");
                }
                break;

            default:
                sendError("Unknown test: " + action);
                sendUsage();
                break;
        }
    }
}
