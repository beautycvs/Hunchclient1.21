package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.util.FriendManager;
import java.util.List;

/**
 * Manage friend list
 */
public class FriendCommand extends Command {

    @Override
    public String getName() {
        return "friend";
    }

    @Override
    public String getDescription() {
        return "Manage friend list";
    }

    @Override
    public String getUsage() {
        return "friend <add|remove|list|clear> [player]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"f", "friends"};
    }

    @Override
    public Category getCategory() {
        return Category.SOCIAL;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        FriendManager manager = FriendManager.getInstance();
        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 2) {
                    sendError("Please specify a player name");
                    return;
                }
                String addName = args[1];
                if (manager.isFriend(addName)) {
                    sendWarning(addName + " is already a friend");
                } else {
                    manager.addFriend(addName);
                    sendSuccess("Added §e" + addName + " §ato friends list");
                }
                break;

            case "remove":
            case "delete":
            case "del":
                if (args.length < 2) {
                    sendError("Please specify a player name");
                    return;
                }
                String removeName = args[1];
                if (manager.removeFriend(removeName)) {
                    sendSuccess("Removed §e" + removeName + " §afrom friends list");
                } else {
                    sendError(removeName + " is not in your friends list");
                }
                break;

            case "list":
                List<String> friends = manager.getFriends();
                if (friends.isEmpty()) {
                    sendInfo("Your friends list is empty");
                } else {
                    sendMessage("§d§l=== Friends List (" + friends.size() + ") ===");
                    for (String friend : friends) {
                        sendMessage("§a• §e" + friend);
                    }
                }
                break;

            case "clear":
                int count = manager.getFriends().size();
                manager.clearFriends();
                sendSuccess("Cleared §e" + count + " §afriends from list");
                break;

            default:
                sendError("Unknown action: " + action);
                sendUsage();
        }
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            return java.util.Arrays.asList("add", "remove", "list", "clear");
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return java.util.Arrays.asList("add", "remove", "list", "clear")
                .stream()
                .filter(s -> s.startsWith(partial))
                .collect(java.util.stream.Collectors.toList());
        }

        // For remove command, suggest friends from the list
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete"))) {
            FriendManager manager = FriendManager.getInstance();
            String partial = args[1].toLowerCase();
            return manager.getFriends().stream()
                .filter(f -> f.toLowerCase().startsWith(partial))
                .collect(java.util.stream.Collectors.toList());
        }

        return java.util.Collections.emptyList();
    }
}