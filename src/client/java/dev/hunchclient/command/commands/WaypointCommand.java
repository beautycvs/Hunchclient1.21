package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

/**
 * Manage waypoints
 */
public class WaypointCommand extends Command {

    @Override
    public String getName() {
        return "waypoint";
    }

    @Override
    public String getDescription() {
        return "Manage waypoints";
    }

    @Override
    public String getUsage() {
        return "waypoint <add|remove|list|goto> [name] [x] [y] [z]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"wp", "waypoints"};
    }

    @Override
    public Category getCategory() {
        return Category.UTILITY;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                sendInfo("Waypoint creation not yet implemented");
                break;
            case "remove":
                sendInfo("Waypoint removal not yet implemented");
                break;
            case "list":
                sendInfo("No waypoints defined");
                break;
            case "goto":
                sendInfo("Waypoint navigation not yet implemented");
                break;
            default:
                sendError("Unknown action: " + action);
                sendUsage();
        }
    }
}