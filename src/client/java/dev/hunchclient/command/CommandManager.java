package dev.hunchclient.command;

import dev.hunchclient.HunchClient;
import dev.hunchclient.command.commands.*;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Central command management system for HunchClient
 * Handles registration, execution, and management of all client commands
 */
public class CommandManager {

    private static CommandManager instance;
    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final List<String> commandHistory = new ArrayList<>();
    private static final int HISTORY_SIZE = 100;
    private int historyIndex = 0;

    public static final String SECONDARY_PREFIX = ".";
    public static final String HUNCHCLIENT_PREFIX = "/hunchclient ";
    public static final String HC_PREFIX = "/hc ";

    private CommandManager() {
        registerCommands();
    }

    public static CommandManager getInstance() {
        if (instance == null) {
            instance = new CommandManager();
        }
        return instance;
    }

    /**
     * Register all client commands
     */
    private void registerCommands() {
        // Core commands
        register(new HelpCommand());
        register(new ConfigCommand());
        register(new BindCommand());
        register(new ToggleCommand());
        register(new SetCommand());
        register(new ModulesCommand());
        register(new SaveCommand());
        register(new LoadCommand());
        register(new ResetCommand());

        // Feature commands
        register(new FriendCommand());
        register(new PokedexCommand());
        register(new EnemyCommand());
        register(new MacroCommand());
        register(new WaypointCommand());
        register(new PanicCommand());

        // Utility commands
        register(new ClearChatCommand());
        register(new SayCommand());
        register(new FilesCommand());
        register(new GuiCommand());
        register(new BlockMarkCommand());
        register(new DungeonBreakerExtrasCommand());
        register(new SecretResetCommand());

        // System commands
        register(new ReloadCommand());
        register(new DebugCommand());
        register(new InfoCommand());
        register(new ProfileCommand());
        register(new SSHelperDebugCommand());

        HunchClient.LOGGER.info("Registered {} commands", commands.size());
    }

    /**
     * Register a command
     */
    public void register(Command command) {
        commands.put(command.getName().toLowerCase(), command);

        // Register aliases
        for (String alias : command.getAliases()) {
            aliases.put(alias.toLowerCase(), command.getName().toLowerCase());
        }

        HunchClient.LOGGER.debug("Registered command: {} with {} aliases",
            command.getName(), command.getAliases().length);
    }

    /**
     * Execute a command from a message
     */
    public boolean execute(String message) {
        // Check for all prefixes (/hunchclient, /hc, and .)
        String prefix = null;
        if (message.startsWith(HUNCHCLIENT_PREFIX)) {
            prefix = HUNCHCLIENT_PREFIX;
        } else if (message.startsWith(HC_PREFIX)) {
            prefix = HC_PREFIX;
        } else if (message.startsWith(SECONDARY_PREFIX)) {
            prefix = SECONDARY_PREFIX;
        } else {
            return false;
        }

        String commandLine = message.substring(prefix.length()).trim();
        if (commandLine.isEmpty()) {
            return false;
        }

        // Add to history
        addToHistory(message);

        // Parse command and arguments
        String[] parts = commandLine.split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        // Resolve aliases
        if (aliases.containsKey(commandName)) {
            commandName = aliases.get(commandName);
        }

        // Find and execute command
        Command command = commands.get(commandName);
        if (command != null) {
            try {
                command.execute(args);
            } catch (Exception e) {
                sendError("Error executing command: " + e.getMessage());
                HunchClient.LOGGER.error("Error executing command " + commandName, e);
            }
            return true;
        }

        // Command not found
        sendError("Unknown command: " + commandName);
        sendMessage("§7Use §e" + SECONDARY_PREFIX + "help §7for a list of commands");
        return true;
    }

    /**
     * Execute a command directly (for binds and macros)
     */
    public void executeCommand(String commandName, String... args) {
        Command command = commands.get(commandName.toLowerCase());
        if (command != null) {
            try {
                command.execute(args);
            } catch (Exception e) {
                sendError("Error executing command: " + e.getMessage());
                HunchClient.LOGGER.error("Error executing command " + commandName, e);
            }
        } else {
            sendError("Unknown command: " + commandName);
        }
    }

    /**
     * Get command suggestions for autocomplete
     */
    public List<String> getSuggestions(String partial) {
        // Determine which prefix was used
        final String usedPrefix;
        if (partial.startsWith(HUNCHCLIENT_PREFIX)) {
            usedPrefix = HUNCHCLIENT_PREFIX;
        } else if (partial.startsWith(HC_PREFIX)) {
            usedPrefix = HC_PREFIX;
        } else if (partial.startsWith(SECONDARY_PREFIX)) {
            usedPrefix = SECONDARY_PREFIX;
        } else {
            usedPrefix = SECONDARY_PREFIX; // Default to . prefix
        }

        String commandLine = partial.startsWith(usedPrefix) ? partial.substring(usedPrefix.length()) : partial;

        // If empty after prefix, suggest all commands
        if (commandLine.isEmpty()) {
            List<String> suggestions = new ArrayList<>();
            for (String cmd : commands.keySet()) {
                suggestions.add(usedPrefix + cmd);
            }
            return suggestions.stream().sorted().collect(Collectors.toList());
        }

        String[] parts = commandLine.split("\\s+", -1); // Use -1 to keep trailing empty strings

        if (parts.length == 1) {
            // Suggest commands that start with the input
            String prefix = parts[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            for (String cmd : commands.keySet()) {
                if (cmd.startsWith(prefix)) {
                    suggestions.add(usedPrefix + cmd);
                }
            }

            for (String alias : aliases.keySet()) {
                if (alias.startsWith(prefix)) {
                    suggestions.add(usedPrefix + alias);
                }
            }

            return suggestions.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        } else {
            // Suggest arguments for specific command
            String cmdName = parts[0].toLowerCase();
            if (aliases.containsKey(cmdName)) {
                cmdName = aliases.get(cmdName);
            }

            Command command = commands.get(cmdName);
            if (command != null) {
                // Get everything after the command name as arguments
                String argsString = commandLine.substring(parts[0].length()).trim();
                String[] currentArgs;
                if (argsString.isEmpty()) {
                    currentArgs = new String[0];
                } else {
                    currentArgs = argsString.split("\\s+", -1);
                }

                List<String> argSuggestions = command.getSuggestions(currentArgs);

                // If we have arg suggestions, prepend the command name and prefix
                if (!argSuggestions.isEmpty()) {
                    return argSuggestions.stream()
                        .map(s -> usedPrefix + parts[0] + " " + s)
                        .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Add command to history
     */
    private void addToHistory(String command) {
        commandHistory.add(command);
        if (commandHistory.size() > HISTORY_SIZE) {
            commandHistory.remove(0);
        }
        historyIndex = commandHistory.size();
    }

    /**
     * Get previous command from history
     */
    public String getPreviousCommand() {
        if (historyIndex > 0 && !commandHistory.isEmpty()) {
            historyIndex--;
            return commandHistory.get(historyIndex);
        }
        return "";
    }

    /**
     * Get next command from history
     */
    public String getNextCommand() {
        if (historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            return commandHistory.get(historyIndex);
        }
        historyIndex = commandHistory.size();
        return "";
    }

    /**
     * Get all registered commands
     */
    public Collection<Command> getCommands() {
        return commands.values();
    }

    /**
     * Get a command by name
     */
    public Command getCommand(String name) {
        String cmdName = name.toLowerCase();
        if (aliases.containsKey(cmdName)) {
            cmdName = aliases.get(cmdName);
        }
        return commands.get(cmdName);
    }

    /**
     * Check if a command exists
     */
    public boolean hasCommand(String name) {
        return commands.containsKey(name.toLowerCase()) ||
               aliases.containsKey(name.toLowerCase());
    }

    /**
     * Send a message to the player
     * IMPORTANT: Use inGameHud instead of player.sendMessage to bypass chat mixins
     */
    public static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Component.literal("§d[HunchClient] §f" + message));
        }
    }

    /**
     * Send an error message to the player
     */
    public static void sendError(String message) {
        sendMessage("§c" + message);
    }

    /**
     * Send a success message to the player
     */
    public static void sendSuccess(String message) {
        sendMessage("§a" + message);
    }

    /**
     * Send a warning message to the player
     */
    public static void sendWarning(String message) {
        sendMessage("§e" + message);
    }

    /**
     * Send an info message to the player
     */
    public static void sendInfo(String message) {
        sendMessage("§b" + message);
    }
}
