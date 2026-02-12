package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Toggle modules on/off
 */
public class ToggleCommand extends Command {

    @Override
    public String getName() {
        return "toggle";
    }

    @Override
    public String getDescription() {
        return "Toggle a module on or off";
    }

    @Override
    public String getUsage() {
        return "toggle <module> [on|off]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"t", "tog"};
    }

    @Override
    public Category getCategory() {
        return Category.MODULE;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String moduleName = args[0];
        ModuleManager manager = ModuleManager.getInstance();
        Module module = manager.getModuleByName(moduleName);

        if (module == null) {
            sendError("Module not found: " + moduleName);
            sendMessage("§7Use §e.modules §7to see all available modules");
            return;
        }

        if (!module.isToggleable()) {
            sendError("Module §e" + module.getName() + " §ccannot be toggled");
            return;
        }

        if (args.length >= 2) {
            String state = args[1].toLowerCase();
            if (state.equals("on") || state.equals("enable") || state.equals("true")) {
                if (module.isEnabled()) {
                    sendWarning(module.getName() + " is already enabled");
                } else {
                    module.setEnabled(true);
                    sendSuccess("Enabled §e" + module.getName());
                }
            } else if (state.equals("off") || state.equals("disable") || state.equals("false")) {
                if (!module.isEnabled()) {
                    sendWarning(module.getName() + " is already disabled");
                } else {
                    module.setEnabled(false);
                    sendSuccess("Disabled §e" + module.getName());
                }
            } else {
                sendError("Invalid state: " + state);
                sendMessage("§7Use §eon§7/§eoff");
            }
        } else {
            // Toggle current state
            module.toggle();
            sendSuccess((module.isEnabled() ? "Enabled" : "Disabled") + " §e" + module.getName());
        }
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        ModuleManager manager = ModuleManager.getInstance();

        if (args.length == 1) {
            return manager.getModules().stream()
                .filter(Module::isToggleable)
                .map(Module::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            return Arrays.asList("on", "off")
                .stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return super.getSuggestions(args);
    }
}