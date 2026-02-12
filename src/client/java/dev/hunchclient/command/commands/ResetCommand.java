package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;

/**
 * Reset modules or settings to defaults
 */
public class ResetCommand extends Command {

    @Override
    public String getName() {
        return "reset";
    }

    @Override
    public String getDescription() {
        return "Reset modules or settings to defaults";
    }

    @Override
    public String getUsage() {
        return "reset <all|module> [name]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"default", "defaults"};
    }

    @Override
    public Category getCategory() {
        return Category.CONFIG;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String target = args[0].toLowerCase();

        if (target.equals("all")) {
            resetAll();
        } else if (target.equals("module") && args.length > 1) {
            resetModule(args[1]);
        } else {
            // Try to reset as module name
            resetModule(target);
        }
    }

    private void resetAll() {
        ModuleManager manager = ModuleManager.getInstance();
        int count = 0;

        for (Module module : manager.getModules()) {
            if (module.isEnabled() && module.isToggleable()) {
                module.setEnabled(false);
                count++;
            }
        }

        sendSuccess("Reset §e" + count + " §amodules to disabled state");
        sendInfo("Use §e.load §bto restore your saved configuration");
    }

    private void resetModule(String moduleName) {
        ModuleManager manager = ModuleManager.getInstance();
        Module module = manager.getModuleByName(moduleName);

        if (module == null) {
            sendError("Module not found: " + moduleName);
            return;
        }

        if (module.isEnabled() && module.isToggleable()) {
            module.setEnabled(false);
            sendSuccess("Reset §e" + module.getName() + " §ato disabled state");
        } else if (!module.isToggleable()) {
            sendError("Cannot reset non-toggleable module: " + module.getName());
        } else {
            sendInfo("Module §e" + module.getName() + " §bis already disabled");
        }
    }
}