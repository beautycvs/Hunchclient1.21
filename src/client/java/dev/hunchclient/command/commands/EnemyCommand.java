package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.util.EnemyManager;
import java.util.List;

/**
 * Manage enemy/target list
 */
public class EnemyCommand extends Command {

    @Override
    public String getName() {
        return "enemy";
    }

    @Override
    public String getDescription() {
        return "Manage enemy/target list";
    }

    @Override
    public String getUsage() {
        return "enemy <add|remove|list|clear> [player]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"e", "enemies", "target", "targets"};
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

        EnemyManager manager = EnemyManager.getInstance();
        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 2) {
                    sendError("Please specify a player name");
                    return;
                }
                String addName = args[1];
                if (manager.isEnemy(addName)) {
                    sendWarning(addName + " is already an enemy");
                } else {
                    manager.addEnemy(addName);
                    sendSuccess("Added §e" + addName + " §ato enemy list");
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
                if (manager.removeEnemy(removeName)) {
                    sendSuccess("Removed §e" + removeName + " §afrom enemy list");
                } else {
                    sendError(removeName + " is not in your enemy list");
                }
                break;

            case "list":
                List<String> enemies = manager.getEnemies();
                if (enemies.isEmpty()) {
                    sendInfo("Your enemy list is empty");
                } else {
                    sendMessage("§c§l=== Enemy List (" + enemies.size() + ") ===");
                    for (String enemy : enemies) {
                        sendMessage("§c• §e" + enemy);
                    }
                }
                break;

            case "clear":
                int count = manager.getEnemies().size();
                manager.clearEnemies();
                sendSuccess("Cleared §e" + count + " §aenemies from list");
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

        // For remove command, suggest enemies from the list
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete"))) {
            EnemyManager manager = EnemyManager.getInstance();
            String partial = args[1].toLowerCase();
            return manager.getEnemies().stream()
                .filter(e -> e.toLowerCase().startsWith(partial))
                .collect(java.util.stream.Collectors.toList());
        }

        return java.util.Collections.emptyList();
    }
}