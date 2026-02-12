package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

/**
 * Manage configuration profiles
 */
public class ProfileCommand extends Command {

    @Override
    public String getName() {
        return "profile";
    }

    @Override
    public String getDescription() {
        return "Manage configuration profiles";
    }

    @Override
    public String getUsage() {
        return "profile <save|load|list> [name]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"prof", "profiles"};
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

        String action = args[0].toLowerCase();

        switch (action) {
            case "save":
                if (args.length < 2) {
                    sendError("Please specify a profile name");
                    return;
                }
                sendInfo("Profile saving not yet implemented");
                break;

            case "load":
                if (args.length < 2) {
                    sendError("Please specify a profile name");
                    return;
                }
                sendInfo("Profile loading not yet implemented");
                break;

            case "list":
                sendInfo("No profiles defined");
                break;

            default:
                sendError("Unknown action: " + action);
                sendUsage();
        }
    }
}