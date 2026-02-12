package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;

import java.io.File;

/**
 * Open the HunchClient config folder
 */
public class FilesCommand extends Command {

    @Override
    public String getName() {
        return "files";
    }

    @Override
    public String getDescription() {
        return "Open the HunchClient config folder";
    }

    @Override
    public String getUsage() {
        return "files";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"folder", "config"};
    }

    @Override
    public Category getCategory() {
        return Category.UTILITY;
    }

    @Override
    public void execute(String[] args) {
        try {
            // Use the same path as ConfigManager: config/hunchclient
            File configFolder = new File(new File(mc.gameDirectory, "config"), "hunchclient");
            if (!configFolder.exists()) {
                configFolder.mkdirs();
            }

            // Try to open the folder in the system file explorer
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec(new String[]{"explorer", configFolder.getAbsolutePath()});
                sendSuccess("Opened config folder");
            } else if (os.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec(new String[]{"open", configFolder.getAbsolutePath()});
                sendSuccess("Opened config folder");
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux - try multiple file managers
                String path = configFolder.getAbsolutePath();

                // Try xdg-open first (standard), then common file managers
                String[] commands = {
                    "xdg-open",   // Standard
                    "nautilus",   // GNOME
                    "dolphin",    // KDE
                    "thunar",     // XFCE
                    "nemo",       // Cinnamon
                    "pcmanfm"     // LXDE
                };

                boolean opened = false;
                for (String cmd : commands) {
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{cmd, path});
                        // Give it a moment to fail if command doesn't exist
                        Thread.sleep(100);
                        if (p.isAlive() || p.exitValue() == 0) {
                            opened = true;
                            sendSuccess("Opened config folder with " + cmd);
                            break;
                        }
                    } catch (Exception ignored) {
                        // Try next command
                    }
                }

                if (!opened) {
                    sendError("Could not open folder - no file manager found");
                    sendInfo("Path: " + path);
                }
            } else {
                sendError("Unsupported operating system: " + os);
                sendInfo("Config folder: " + configFolder.getAbsolutePath());
            }
        } catch (Exception e) {
            sendError("Failed to open config folder: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
