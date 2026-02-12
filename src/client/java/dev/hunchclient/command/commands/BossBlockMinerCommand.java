package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.dungeons.BossBlockMinerModule;
import dev.hunchclient.module.impl.dungeons.secrets.DungeonManager;
import dev.hunchclient.module.impl.dungeons.secrets.Room;
import net.minecraft.client.Minecraft;

/**
 * Command to manage BossBlockMiner module state quickly from chat
 */
public class BossBlockMinerCommand extends Command {

    @Override
    public String getName() {
        return "bossblockminer";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"bbm", "blockminer"};
    }

    @Override
    public String getDescription() {
        return "Manage the BossBlockMiner module (edit mode, auto mode, reload, stats).";
    }

    @Override
    public String getUsage() {
        return "bossblockminer <editmode|automode|clear|reload|stats>";
    }

    @Override
    public void execute(String[] args) {
        BossBlockMinerModule module = ModuleManager.getInstance().getModule(BossBlockMinerModule.class);
        if (module == null) {
            sendError("[BossBlockMiner] Module not found!");
            return;
        }

        if (args.length == 0) {
            sendInfo("[BossBlockMiner] Commands:");
            sendInfo(" - /bbm editmode  §7Toggle edit mode");
            sendInfo(" - /bbm automode  §7Toggle auto mode");
            sendInfo(" - /bbm clear     §7Clear all marked blocks");
            sendInfo(" - /bbm reload    §7Reload minepoints from storage");
            sendInfo(" - /bbm stats     §7Show current status");
            return;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "editmode", "edit" -> {
                module.setEditMode(!module.isEditMode());
                sendSuccess("[BossBlockMiner] Edit mode: " + (module.isEditMode() ? "§aON" : "§cOFF"));
            }
            case "automode", "auto" -> {
                module.setAutoMode(!module.isAutoMode());
                sendSuccess("[BossBlockMiner] Auto mode: " + (module.isAutoMode() ? "§aON" : "§cOFF"));
            }
            case "clearroom" -> {
                // Clear marked blocks only from the current room
                module.clearMarkedBlocks();

                // Also remove from DungeonManager for the current room
                Room currentRoom = DungeonManager.getCurrentRoom();
                if (currentRoom != null && currentRoom.isMatched()) {
                    String roomName = currentRoom.getName();
                    DungeonManager.clearCustomMinepointsForRoom(roomName);
                    sendSuccess("[BossBlockMiner] Cleared all minepoints from room: " + roomName);
                } else {
                    sendSuccess("[BossBlockMiner] Cleared local marked blocks (not in a matched room)");
                }
            }
            case "clearconfig" -> {
                // Clear ALL marked blocks and ALL saved minepoints from all rooms
                module.clearMarkedBlocks();
                DungeonManager.clearAllCustomMinepoints();
                DungeonManager.saveCustomWaypoints(Minecraft.getInstance());
                sendSuccess("[BossBlockMiner] Cleared ALL minepoints from ALL rooms!");
            }
            case "reload" -> {
                module.loadMinepointsFromDungeonManager();
                sendSuccess("[BossBlockMiner] Reloaded minepoints from DungeonManager");
            }
            case "stats" -> {
                sendInfo("[BossBlockMiner] Statistics:");
                sendInfo(" - Marked blocks: §f" + module.getMarkedBlocks().size());
                sendInfo(" - Edit mode: " + (module.isEditMode() ? "§aON" : "§cOFF"));
                sendInfo(" - Auto mode: " + (module.isAutoMode() ? "§aON" : "§cOFF"));
            }
            default -> sendError("[BossBlockMiner] Unknown subcommand: " + subcommand);
        }
    }
}
