package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

/**
 * Manage macros (sequences of commands)
 */
public class MacroCommand extends Command {

    @Override
    public String getName() {
        return "macro";
    }

    @Override
    public String getDescription() {
        return "Manage macros (sequences of commands)";
    }

    @Override
    public String getUsage() {
        return "macro <create|run|delete|list> [name] [commands...]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"m", "macros"};
    }

    @Override
    public Category getCategory() {
        return Category.MACRO;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create":
                sendInfo("Macro creation not yet implemented");
                break;
            case "run":
                sendInfo("Macro execution not yet implemented");
                break;
            case "delete":
                sendInfo("Macro deletion not yet implemented");
                break;
            case "list":
                sendInfo("No macros defined");
                break;
            default:
                sendError("Unknown action: " + action);
                sendUsage();
        }
    }
}