package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.command.bind.BindManager;
import dev.hunchclient.command.bind.Bind;
import org.lwjgl.glfw.GLFW;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manage key bindings for modules
 */
public class BindCommand extends Command {

    @Override
    public String getName() {
        return "bind";
    }

    @Override
    public String getDescription() {
        return "Manage key bindings for modules";
    }

    @Override
    public String getUsage() {
        return "bind <add|remove|list|clear> [key] [module]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"b", "keybind"};
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

        BindManager bindManager = BindManager.getInstance();
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
            case "set":
                if (args.length < 3) {
                    sendError("Usage: bind add <key> <module>");
                    sendMessage("§7Example: §ebind add G Sprint");
                    return;
                }
                handleAddBind(bindManager, args);
                break;

            case "remove":
            case "delete":
            case "del":
                if (args.length < 2) {
                    sendError("Usage: bind remove <key>");
                    return;
                }
                handleRemoveBind(bindManager, args[1]);
                break;

            case "list":
                handleListBinds(bindManager);
                break;

            case "clear":
                handleClearBinds(bindManager);
                break;

            default:
                sendError("Unknown subcommand: " + subCommand);
                sendMessage("§7Valid options: §eadd, remove, list, clear");
        }
    }

    private void handleAddBind(BindManager bindManager, String[] args) {
        String keyName = args[1].toUpperCase();
        String moduleName = args[2];

        // Verify module exists
        dev.hunchclient.module.ModuleManager moduleManager = dev.hunchclient.module.ModuleManager.getInstance();
        dev.hunchclient.module.Module module = moduleManager.getModuleByName(moduleName);

        if (module == null) {
            sendError("Module not found: " + moduleName);
            sendMessage("§7Use §e.modules §7to see all available modules");
            return;
        }

        if (!module.isToggleable()) {
            sendError("Module §e" + module.getName() + " §ccannot be toggled");
            return;
        }

        // Parse the key
        int keyCode = parseKey(keyName);
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            sendError("Invalid key: " + keyName);
            sendMessage("§7Examples: §eG, F1, SHIFT, CTRL, ALT, SPACE, MOUSE1");
            return;
        }

        // Check if bind already exists
        Bind existingBind = bindManager.getBind(keyCode);
        if (existingBind != null) {
            sendWarning("Key §e" + keyName + " §eis already bound to: §f" + existingBind.getModuleName());
            sendMessage("§7Use §ebind remove " + keyName + " §7to remove it first");
            return;
        }

        // Create the bind
        Bind bind = new Bind(keyCode, keyName, moduleName);
        bindManager.addBind(bind);

        sendSuccess("Bound §e" + keyName + " §ato toggle §e" + moduleName);
    }

    private void handleRemoveBind(BindManager bindManager, String keyName) {
        keyName = keyName.toUpperCase();
        int keyCode = parseKey(keyName);

        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            sendError("Invalid key: " + keyName);
            return;
        }

        Bind bind = bindManager.getBind(keyCode);
        if (bind == null) {
            sendError("No bind found for key: " + keyName);
            return;
        }

        bindManager.removeBind(keyCode);
        sendSuccess("Removed bind for §e" + keyName);
    }

    private void handleListBinds(BindManager bindManager) {
        List<Bind> binds = bindManager.getAllBinds();

        if (binds.isEmpty()) {
            sendMessage("§7No binds configured");
            sendMessage("§7Use §ebind add <key> <action> §7to create one");
            return;
        }

        sendMessage("§d§l=== Key Bindings (" + binds.size() + ") ===");
        for (Bind bind : binds) {
            String keyDisplay = bind.getKeyName();
            String actionDisplay = bind.getModuleName();

            // Color code different action types
            if (actionDisplay.startsWith("toggle ")) {
                actionDisplay = "§atoggle §e" + actionDisplay.substring(7);
            } else if (actionDisplay.startsWith("enable ")) {
                actionDisplay = "§2enable §e" + actionDisplay.substring(7);
            } else if (actionDisplay.startsWith("disable ")) {
                actionDisplay = "§cdisable §e" + actionDisplay.substring(8);
            } else if (actionDisplay.startsWith(".")) {
                actionDisplay = "§bcommand §f" + actionDisplay;
            }

            sendMessage("§e" + keyDisplay + " §7-> " + actionDisplay);
        }
    }

    private void handleClearBinds(BindManager bindManager) {
        int count = bindManager.getAllBinds().size();

        if (count == 0) {
            sendMessage("§7No binds to clear");
            return;
        }

        bindManager.clearAllBinds();
        sendSuccess("Cleared §e" + count + " §abinds");
    }

    private int parseKey(String keyName) {
        keyName = keyName.toUpperCase();

        // Special keys
        switch (keyName) {
            case "SHIFT":
            case "LSHIFT":
                return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RSHIFT":
                return GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "CTRL":
            case "LCTRL":
            case "CONTROL":
                return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RCTRL":
                return GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "ALT":
            case "LALT":
                return GLFW.GLFW_KEY_LEFT_ALT;
            case "RALT":
                return GLFW.GLFW_KEY_RIGHT_ALT;
            case "SPACE":
                return GLFW.GLFW_KEY_SPACE;
            case "ENTER":
            case "RETURN":
                return GLFW.GLFW_KEY_ENTER;
            case "ESC":
            case "ESCAPE":
                return GLFW.GLFW_KEY_ESCAPE;
            case "TAB":
                return GLFW.GLFW_KEY_TAB;
            case "BACKSPACE":
                return GLFW.GLFW_KEY_BACKSPACE;
            case "DELETE":
                return GLFW.GLFW_KEY_DELETE;
            case "INSERT":
                return GLFW.GLFW_KEY_INSERT;
            case "HOME":
                return GLFW.GLFW_KEY_HOME;
            case "END":
                return GLFW.GLFW_KEY_END;
            case "PAGEUP":
            case "PGUP":
                return GLFW.GLFW_KEY_PAGE_UP;
            case "PAGEDOWN":
            case "PGDN":
                return GLFW.GLFW_KEY_PAGE_DOWN;
            case "UP":
                return GLFW.GLFW_KEY_UP;
            case "DOWN":
                return GLFW.GLFW_KEY_DOWN;
            case "LEFT":
                return GLFW.GLFW_KEY_LEFT;
            case "RIGHT":
                return GLFW.GLFW_KEY_RIGHT;
            case "CAPSLOCK":
            case "CAPS":
                return GLFW.GLFW_KEY_CAPS_LOCK;
        }

        // Function keys
        if (keyName.startsWith("F") && keyName.length() <= 3) {
            try {
                int num = Integer.parseInt(keyName.substring(1));
                if (num >= 1 && num <= 25) {
                    return GLFW.GLFW_KEY_F1 + (num - 1);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Number keys
        if (keyName.length() == 1 && keyName.charAt(0) >= '0' && keyName.charAt(0) <= '9') {
            return GLFW.GLFW_KEY_0 + (keyName.charAt(0) - '0');
        }

        // Letter keys
        if (keyName.length() == 1 && keyName.charAt(0) >= 'A' && keyName.charAt(0) <= 'Z') {
            return GLFW.GLFW_KEY_A + (keyName.charAt(0) - 'A');
        }

        // Mouse buttons (special handling required)
        if (keyName.startsWith("MOUSE") || keyName.startsWith("BUTTON")) {
            try {
                String numStr = keyName.replace("MOUSE", "").replace("BUTTON", "");
                int button = Integer.parseInt(numStr);
                // Use negative values to indicate mouse buttons
                return -button;
            } catch (NumberFormatException ignored) {}
        }

        return GLFW.GLFW_KEY_UNKNOWN;
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "clear")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return Arrays.asList("G", "F", "R", "V", "B", "F1", "F2", "F3", "SHIFT", "CTRL", "ALT");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return Arrays.asList("toggle", "enable", "disable", ".help", ".panic");
        }

        return super.getSuggestions(args);
    }
}