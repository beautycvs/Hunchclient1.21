package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.dungeons.DungeonBreakerExtrasModule;
import dev.hunchclient.module.impl.dungeons.secrets.DungeonManager;
import dev.hunchclient.module.impl.dungeons.secrets.Room;
import net.minecraft.client.Minecraft;

/**
 * Command to manage the DungeonBreaker Extras module state quickly from chat
 */
public class DungeonBreakerExtrasCommand extends Command {

    @Override
    public String getName() {
        return "dbe";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"dungeonbreakerextras", "bossblockminer", "bbm", "blockminer"};
    }

    @Override
    public String getDescription() {
        return "Manage the DungeonBreaker Extras module (edit mode, auto mode, reload, stats).";
    }

    @Override
    public String getUsage() {
        return "dbe <editmode|automode|clearroom|clearconfig|reload|stats>";
    }

    @Override
    public java.util.List<String> getSuggestions(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            // Return all subcommands
            return java.util.Arrays.asList(
                "editmode",
                "automode",
                "clearroom",
                "clearconfig",
                "reload",
                "stats"
            );
        }

        if (args.length == 1) {
            // Filter subcommands based on partial input
            String partial = args[0].toLowerCase();
            return java.util.Arrays.asList("editmode", "automode", "clearroom", "clearconfig", "reload", "stats")
                .stream()
                .filter(s -> s.startsWith(partial))
                .collect(java.util.stream.Collectors.toList());
        }

        return java.util.Collections.emptyList();
    }

    @Override
    public void execute(String[] args) {
        DungeonBreakerExtrasModule module = ModuleManager.getInstance().getModule(DungeonBreakerExtrasModule.class);
        if (module == null) {
            sendError("[DungeonBreakerExtras] Module not found!");
            return;
        }

        if (args.length == 0) {
            sendInfo("[DungeonBreakerExtras] Commands:");
            sendInfo(" - /dbe editmode  §7Toggle edit mode");
            sendInfo(" - /dbe automode  §7Toggle auto mode");
            sendInfo(" - /dbe clear     §7Clear all marked blocks");
            sendInfo(" - /dbe reload    §7Reload minepoints from storage");
            sendInfo(" - /dbe stats     §7Show current status");
            return;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "editmode", "edit" -> {
                module.setEditMode(!module.isEditMode());
                sendSuccess("[DungeonBreakerExtras] Edit mode: " + (module.isEditMode() ? "§aON" : "§cOFF"));
            }
            case "automode", "auto" -> {
                module.setAutoMode(!module.isAutoMode());
                sendSuccess("[DungeonBreakerExtras] Auto mode: " + (module.isAutoMode() ? "§aON" : "§cOFF"));
            }
            case "clearroom" -> {
                // Clear marked blocks only from the current room
                module.clearMarkedBlocks();

                // Also remove from DungeonManager for the current room
                Room currentRoom = DungeonManager.getCurrentRoom();
                if (currentRoom != null && currentRoom.isMatched()) {
                    String roomName = currentRoom.getName();
                    DungeonManager.clearCustomMinepointsForRoom(roomName);
                    sendSuccess("[DungeonBreakerExtras] Cleared all minepoints from room: " + roomName);
                } else {
                    sendSuccess("[DungeonBreakerExtras] Cleared local marked blocks (not in a matched room)");
                }
            }
            case "clearconfig" -> {
                // Clear ALL marked blocks and ALL saved minepoints from all rooms
                module.clearMarkedBlocks();
                DungeonManager.clearAllCustomMinepoints();
                DungeonManager.saveCustomWaypoints(Minecraft.getInstance());
                sendSuccess("[DungeonBreakerExtras] Cleared ALL minepoints from ALL rooms!");
            }
            case "reload" -> {
                module.loadMinepointsFromDungeonManager();
                sendSuccess("[DungeonBreakerExtras] Reloaded minepoints from DungeonManager");
            }
            case "stats" -> {
                sendInfo("[DungeonBreakerExtras] Statistics:");
                sendInfo(" - Marked blocks: §f" + module.getMarkedBlocks().size());
                sendInfo(" - Edit mode: " + (module.isEditMode() ? "§aON" : "§cOFF"));
                sendInfo(" - Auto mode: " + (module.isAutoMode() ? "§aON" : "§cOFF"));
            }
            default -> sendError("[DungeonBreakerExtras] Unknown subcommand: " + subcommand);
        }
    }
}
