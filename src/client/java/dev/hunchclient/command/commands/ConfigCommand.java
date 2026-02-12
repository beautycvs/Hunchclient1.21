package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.util.ConfigManager;
import dev.hunchclient.HunchClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manage client configurations
 */
public class ConfigCommand extends Command {

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public String getDescription() {
        return "Manage client configurations";
    }

    @Override
    public String getUsage() {
        return "config <save|load|delete|list|reload> [name]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"cfg", "conf"};
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

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "save":
                if (args.length < 2) {
                    handleSave("default");
                } else {
                    handleSave(args[1]);
                }
                break;

            case "load":
                if (args.length < 2) {
                    sendError("Please specify a config name to load");
                    sendMessage("§7Use §econfig list §7to see available configs");
                } else {
                    handleLoad(args[1]);
                }
                break;

            case "delete":
            case "remove":
                if (args.length < 2) {
                    sendError("Please specify a config name to delete");
                } else {
                    handleDelete(args[1]);
                }
                break;

            case "list":
                handleList();
                break;

            case "reload":
                handleReload();
                break;

            case "folder":
            case "open":
                handleOpenFolder();
                break;

            default:
                sendError("Unknown subcommand: " + subCommand);
                sendMessage("§7Valid options: §esave, load, delete, list, reload, folder");
        }
    }

    private void handleSave(String configName) {
        // Validate name
        if (!isValidConfigName(configName)) {
            sendError("Invalid config name. Use only letters, numbers, and underscores");
            return;
        }

        try {
            // Save main config with name
            File configDir = new File(mc.gameDirectory, "hunchclient/configs");
            configDir.mkdirs();

            File configFile = new File(configDir, configName + ".json");
            boolean exists = configFile.exists();

            // Save the config
            ConfigManager.saveToFile(configFile);

            if (exists) {
                sendSuccess("Config §e" + configName + " §ahas been updated");
            } else {
                sendSuccess("Config §e" + configName + " §ahas been saved");
            }

            // Also save binds
            dev.hunchclient.command.bind.BindManager.getInstance().saveBinds();

        } catch (Exception e) {
            sendError("Failed to save config: " + e.getMessage());
            HunchClient.LOGGER.error("Failed to save config", e);
        }
    }

    private void handleLoad(String configName) {
        try {
            File configDir = new File(mc.gameDirectory, "hunchclient/configs");
            File configFile = new File(configDir, configName + ".json");

            if (!configFile.exists()) {
                sendError("Config §e" + configName + " §cdoes not exist");
                sendMessage("§7Use §econfig list §7to see available configs");
                return;
            }

            // Load the config
            ConfigManager.loadFromFile(configFile);

            sendSuccess("Loaded config §e" + configName);

            // Also reload binds
            dev.hunchclient.command.bind.BindManager.getInstance().loadBinds();

        } catch (Exception e) {
            sendError("Failed to load config: " + e.getMessage());
            HunchClient.LOGGER.error("Failed to load config", e);
        }
    }

    private void handleDelete(String configName) {
        if (configName.equalsIgnoreCase("default")) {
            sendError("Cannot delete the default config");
            return;
        }

        try {
            File configDir = new File(mc.gameDirectory, "hunchclient/configs");
            File configFile = new File(configDir, configName + ".json");

            if (!configFile.exists()) {
                sendError("Config §e" + configName + " §cdoes not exist");
                return;
            }

            if (configFile.delete()) {
                sendSuccess("Deleted config §e" + configName);
            } else {
                sendError("Failed to delete config §e" + configName);
            }

        } catch (Exception e) {
            sendError("Failed to delete config: " + e.getMessage());
            HunchClient.LOGGER.error("Failed to delete config", e);
        }
    }

    private void handleList() {
        try {
            File configDir = new File(mc.gameDirectory, "hunchclient/configs");
            if (!configDir.exists() || !configDir.isDirectory()) {
                sendMessage("§7No configs found");
                return;
            }

            File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) {
                sendMessage("§7No configs found");
                sendMessage("§7Use §econfig save <name> §7to create one");
                return;
            }

            sendMessage("§d§l=== Available Configs (" + files.length + ") ===");
            for (File file : files) {
                String name = file.getName().replace(".json", "");
                long size = file.length() / 1024; // KB
                long modified = file.lastModified();
                long daysAgo = (System.currentTimeMillis() - modified) / (1000 * 60 * 60 * 24);

                String info = String.format("§e%s §7(%dKB, %d days ago)", name, size, daysAgo);
                sendMessage(info);
            }

            sendMessage("");
            sendMessage("§7Use §econfig load <name> §7to load a config");

        } catch (Exception e) {
            sendError("Failed to list configs: " + e.getMessage());
            HunchClient.LOGGER.error("Failed to list configs", e);
        }
    }

    private void handleReload() {
        try {
            ConfigManager.load();
            dev.hunchclient.command.bind.BindManager.getInstance().loadBinds();
            sendSuccess("Reloaded all configurations");
        } catch (Exception e) {
            sendError("Failed to reload: " + e.getMessage());
            HunchClient.LOGGER.error("Failed to reload config", e);
        }
    }

    private void handleOpenFolder() {
        try {
            File configDir = new File(mc.gameDirectory, "hunchclient");
            configDir.mkdirs();

            // Try to open the folder in the system file explorer
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", configDir.getAbsolutePath());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", configDir.getAbsolutePath());
            } else if (os.contains("nix") || os.contains("nux")) {
                pb = new ProcessBuilder("xdg-open", configDir.getAbsolutePath());
            } else {
                sendError("Unsupported operating system");
                return;
            }
            pb.start();

            sendSuccess("Opened config folder");
        } catch (Exception e) {
            sendError("Failed to open folder: " + e.getMessage());
        }
    }

    private boolean isValidConfigName(String name) {
        return name.matches("[a-zA-Z0-9_-]+");
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("save", "load", "delete", "list", "reload", "folder")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("delete"))) {
            // Suggest existing config names
            List<String> configs = new ArrayList<>();
            try {
                File configDir = new File(mc.gameDirectory, "hunchclient/configs");
                if (configDir.exists() && configDir.isDirectory()) {
                    File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
                    if (files != null) {
                        for (File file : files) {
                            configs.add(file.getName().replace(".json", ""));
                        }
                    }
                }
            } catch (Exception ignored) {}

            return configs.stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return super.getSuggestions(args);
    }
}