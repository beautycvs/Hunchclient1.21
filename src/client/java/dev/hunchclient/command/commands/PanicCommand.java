package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;

/**
 * Panic command - instantly disable all modules
 */
public class PanicCommand extends Command {

    @Override
    public String getName() {
        return "panic";
    }

    @Override
    public String getDescription() {
        return "Instantly disable all modules";
    }

    @Override
    public String getUsage() {
        return "panic";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"p", "emergency", "stop"};
    }

    @Override
    public Category getCategory() {
        return Category.GENERAL;
    }

    @Override
    public void execute(String[] args) {
        ModuleManager manager = ModuleManager.getInstance();
        int disabledCount = 0;

        for (Module module : manager.getEnabledModules()) {
            if (module.isToggleable()) {
                module.setEnabled(false);
                disabledCount++;
            }
        }

        if (disabledCount > 0) {
            sendSuccess("§c§lPANIC! §aDisabled §e" + disabledCount + " §amodules");
        } else {
            sendInfo("No active modules to disable");
        }
    }
}