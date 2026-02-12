package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Display information about modules
 */
public class ModulesCommand extends Command {

    @Override
    public String getName() {
        return "modules";
    }

    @Override
    public String getDescription() {
        return "Display all available modules";
    }

    @Override
    public String getUsage() {
        return "modules [category]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"mods", "modlist", "ml"};
    }

    @Override
    public Category getCategory() {
        return Category.MODULE;
    }

    @Override
    public void execute(String[] args) {
        if (args.length > 0) {
            // Show modules by category
            String categoryName = args[0].toUpperCase();

            try {
                Module.Category category = Module.Category.valueOf(categoryName);
                displayCategoryModules(category);
            } catch (IllegalArgumentException e) {
                sendError("Invalid category: " + args[0]);
                sendMessage("§7Available categories: §eDUNGEONS, VISUALS, MOVEMENT, MISC, AI");
            }
        } else {
            // Show all modules grouped by category
            displayAllModules();
        }
    }

    private void displayAllModules() {
        ModuleManager manager = ModuleManager.getInstance();
        List<Module> modules = manager.getModules();
        int enabledCount = manager.getEnabledModules().size();

        sendMessage("§d§l=== HunchClient Modules ===");
        sendMessage("§7Total: §e" + modules.size() + " §7modules (§a" + enabledCount + " §7enabled)");
        sendMessage("");

        // Display by category
        for (Module.Category category : Module.Category.values()) {
            List<Module> categoryModules = manager.getModulesByCategory(category);
            if (categoryModules.isEmpty()) continue;

            String safeIndicator = category.isDefaultSafe() ? " §a[SAFE]" : " §c[RISKY]";
            sendMessage("§6" + category.getDisplayName() + safeIndicator + " §7(" + categoryModules.size() + "):");

            for (Module module : categoryModules) {
                String status = module.isEnabled() ? "§a✓" : "§c✗";
                String toggleable = module.isToggleable() ? "" : " §8[LOCKED]";
                String watchdog = module.isWatchdogSafe() ? "" : " §4[!]";

                sendMessage("  " + status + " §e" + module.getName() + toggleable + watchdog +
                           " §7- " + module.getDescription());
            }
            sendMessage("");
        }

        sendMessage("§7Legend: §a✓ §7= Enabled, §c✗ §7= Disabled, §4[!] §7= High Risk");
        sendMessage("§7Use §e.toggle <module> §7to toggle a module");
    }

    private void displayCategoryModules(Module.Category category) {
        ModuleManager manager = ModuleManager.getInstance();
        List<Module> modules = manager.getModulesByCategory(category);

        if (modules.isEmpty()) {
            sendMessage("§7No modules in category: §e" + category.getDisplayName());
            return;
        }

        String safeIndicator = category.isDefaultSafe() ? " §a[SAFE]" : " §c[RISKY]";
        sendMessage("§d§l=== " + category.getDisplayName() + " Modules" + safeIndicator + " ===");
        sendMessage("§7Found §e" + modules.size() + " §7modules");
        sendMessage("");

        for (Module module : modules) {
            String status = module.isEnabled() ? "§a[ENABLED]" : "§c[DISABLED]";
            String toggleable = module.isToggleable() ? "" : " §8[LOCKED]";
            String watchdog = module.isWatchdogSafe() ? " §2[SAFE]" : " §c[RISKY]";

            sendMessage("§e" + module.getName() + " " + status + watchdog + toggleable);
            sendMessage("  §7" + module.getDescription());
            sendMessage("");
        }
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.stream(Module.Category.values())
                .map(cat -> cat.name().toLowerCase())
                .filter(name -> name.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return super.getSuggestions(args);
    }
}