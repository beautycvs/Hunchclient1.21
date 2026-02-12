package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.command.CommandManager;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Display help information about commands
 */
public class HelpCommand extends Command {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Display help information about commands";
    }

    @Override
    public String getUsage() {
        return "help [command|category|page]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"?", "h", "commands"};
    }

    @Override
    public Category getCategory() {
        return Category.GENERAL;
    }

    @Override
    public void execute(String[] args) {
        CommandManager manager = CommandManager.getInstance();

        if (args.length == 0) {
            // Display general help
            displayGeneralHelp(1);
        } else {
            String arg = args[0].toLowerCase();

            // Check if it's a page number
            try {
                int page = Integer.parseInt(arg);
                displayGeneralHelp(page);
                return;
            } catch (NumberFormatException ignored) {}

            // Check if it's a category
            try {
                Category category = Category.valueOf(arg.toUpperCase());
                displayCategoryHelp(category);
                return;
            } catch (IllegalArgumentException ignored) {}

            // Check if it's a command
            Command command = manager.getCommand(arg);
            if (command != null) {
                displayCommandHelp(command);
                return;
            }

            // Not found
            sendError("Unknown command or category: " + arg);
        }
    }

    private void displayGeneralHelp(int page) {
        CommandManager manager = CommandManager.getInstance();
        List<Command> commands = new ArrayList<>(manager.getCommands());
        commands.sort(Comparator.comparing(Command::getName));

        int commandsPerPage = 10;
        int totalPages = (int) Math.ceil(commands.size() / (double) commandsPerPage);
        page = Math.max(1, Math.min(page, totalPages));

        sendMessage("§d§l=== HunchClient Commands (Page " + page + "/" + totalPages + ") ===");
        sendMessage("§7Prefix: §e" + CommandManager.SECONDARY_PREFIX);

        int start = (page - 1) * commandsPerPage;
        int end = Math.min(start + commandsPerPage, commands.size());

        for (int i = start; i < end; i++) {
            Command cmd = commands.get(i);
            String aliases = cmd.getAliases().length > 0 ?
                " §8[" + String.join(", ", cmd.getAliases()) + "]" : "";
            sendMessage("§e" + CommandManager.SECONDARY_PREFIX + cmd.getName() + aliases + " §7- " + cmd.getDescription());
        }

        sendMessage("");
        sendMessage("§7Use §e" + CommandManager.SECONDARY_PREFIX + "help <command> §7for detailed usage");
        sendMessage("§7Use §e" + CommandManager.SECONDARY_PREFIX + "help <category> §7to see category commands");

        if (totalPages > 1) {
            String nav = "§7Page: ";
            if (page > 1) nav += "§e[<-] ";
            nav += "§d" + page + "/" + totalPages;
            if (page < totalPages) nav += " §e[->]";
            sendMessage(nav);
        }
    }

    private void displayCategoryHelp(Category category) {
        CommandManager manager = CommandManager.getInstance();
        List<Command> commands = manager.getCommands().stream()
            .filter(cmd -> cmd.getCategory() == category)
            .sorted(Comparator.comparing(Command::getName))
            .collect(Collectors.toList());

        sendMessage("§d§l=== " + category.getName() + " Commands ===");
        sendMessage("§7" + category.getDescription());
        sendMessage("");

        if (commands.isEmpty()) {
            sendMessage("§7No commands in this category");
        } else {
            for (Command cmd : commands) {
                String aliases = cmd.getAliases().length > 0 ?
                    " §8[" + String.join(", ", cmd.getAliases()) + "]" : "";
                sendMessage("§e" + CommandManager.SECONDARY_PREFIX + cmd.getUsage() + aliases);
                sendMessage("  §7" + cmd.getDescription());
            }
        }
    }

    private void displayCommandHelp(Command command) {
        sendMessage("§d§l=== " + command.getName() + " Command ===");
        sendMessage("§7" + command.getDescription());
        sendMessage("");
        sendMessage("§eUsage: §f" + CommandManager.SECONDARY_PREFIX + command.getUsage());

        if (command.getAliases().length > 0) {
            sendMessage("§eAliases: §f" + String.join(", ", command.getAliases()));
        }

        sendMessage("§eCategory: §f" + command.getCategory().getName());

        // Additional command-specific help could be added here
        List<String> examples = getCommandExamples(command.getName());
        if (!examples.isEmpty()) {
            sendMessage("");
            sendMessage("§eExamples:");
            for (String example : examples) {
                sendMessage("  §7" + example);
            }
        }
    }

    private List<String> getCommandExamples(String commandName) {
        List<String> examples = new ArrayList<>();

        switch (commandName.toLowerCase()) {
            case "bind":
                examples.add(CommandManager.SECONDARY_PREFIX + "bind add G toggle Sprint");
                examples.add(CommandManager.SECONDARY_PREFIX + "bind remove G");
                examples.add(CommandManager.SECONDARY_PREFIX + "bind list");
                break;
            case "toggle":
                examples.add(CommandManager.SECONDARY_PREFIX + "toggle Sprint");
                examples.add(CommandManager.SECONDARY_PREFIX + "toggle FullBright on");
                break;
            case "set":
                examples.add(CommandManager.SECONDARY_PREFIX + "set Sprint speed 1.5");
                examples.add(CommandManager.SECONDARY_PREFIX + "set FullBright gamma 15");
                break;
            case "config":
                examples.add(CommandManager.SECONDARY_PREFIX + "config save myconfig");
                examples.add(CommandManager.SECONDARY_PREFIX + "config load myconfig");
                examples.add(CommandManager.SECONDARY_PREFIX + "config list");
                break;
        }

        return examples;
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            // Add command names
            CommandManager manager = CommandManager.getInstance();
            suggestions.addAll(manager.getCommands().stream()
                .map(Command::getName)
                .collect(Collectors.toList()));

            // Add category names
            for (Category cat : Category.values()) {
                suggestions.add(cat.name().toLowerCase());
            }

            // Add page numbers
            for (int i = 1; i <= 5; i++) {
                suggestions.add(String.valueOf(i));
            }

            return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return super.getSuggestions(args);
    }
}