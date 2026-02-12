package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.ModuleManager;

/**
 * Display client information
 */
public class InfoCommand extends Command {

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "Display client information";
    }

    @Override
    public String getUsage() {
        return "info";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"about", "version"};
    }

    @Override
    public Category getCategory() {
        return Category.GENERAL;
    }

    @Override
    public void execute(String[] args) {
        sendMessage("§d§l=== HunchClient Information ===");
        sendMessage("§7Version: §e1.21");
        sendMessage("§7Minecraft: §e" + mc.getLaunchedVersion());
        sendMessage("§7Developer: §eHunch");
        sendMessage("");

        ModuleManager manager = ModuleManager.getInstance();
        sendMessage("§7Modules: §e" + manager.getModules().size());
        sendMessage("§7Commands: §e" + dev.hunchclient.command.CommandManager.getInstance().getCommands().size());
        sendMessage("§7Binds: §e" + dev.hunchclient.command.bind.BindManager.getInstance().getAllBinds().size());
        sendMessage("");

        sendMessage("§7Website: §ehunchclient.com");
        sendMessage("§7Discord: §ediscord.gg/hunchclient");
    }
}