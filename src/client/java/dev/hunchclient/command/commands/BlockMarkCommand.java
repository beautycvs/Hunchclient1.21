package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.gui.BlockMarkEditScreen;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.dungeons.BossBlockMinerModule;
import net.minecraft.core.BlockPos;

public class BlockMarkCommand extends Command {

    @Override
    public String getName() {
        return "blockmark";
    }

    @Override
    public String getDescription() {
        return "Manage boss block marking and auto-mining";
    }

    @Override
    public String getUsage() {
        return "blockmark <edit|clear|list|reset>";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"bmark", "bm", "bbm"};
    }

    @Override
    public void execute(String[] args) {
        BossBlockMinerModule module = ModuleManager.getInstance().getModule(BossBlockMinerModule.class);

        if (module == null) {
            sendError("BossBlockMiner module not found!");
            return;
        }

        if (args.length == 0) {
            sendUsage();
            sendMessage("§7Available commands:");
            sendMessage("§7- §aedit §7- Open edit mode to mark blocks");
            sendMessage("§7- §aclear §7- Clear all marked blocks");
            sendMessage("§7- §alist §7- List all marked blocks");
            sendMessage("§7- §areset §7- Reset mined blocks status");
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "edit":
                // Toggle edit mode
                if (module.isEditMode()) {
                    // Already in edit mode - close it
                    module.setEditMode(false);
                    module.saveConfig();
                    sendSuccess("Edit mode disabled - Config saved");
                } else {
                    // Open edit mode
                    mc.execute(() -> mc.setScreen(new BlockMarkEditScreen(module)));
                    sendSuccess("Opening block marker edit mode...");
                    sendMessage("§7Right-click blocks to mark them");
                }
                break;

            case "clear":
                module.clearMarkedBlocks();
                sendSuccess("Cleared all marked blocks!");
                break;

            case "list":
                if (module.getMarkedBlocks().isEmpty()) {
                    sendMessage("§7No blocks marked.");
                } else {
                    sendSuccess("Marked Blocks (" + module.getMarkedBlocks().size() + "):");
                    int count = 0;
                    for (BlockPos pos : module.getMarkedBlocks()) {
                        sendMessage("§7" + (++count) + ". §f" + pos.toShortString());
                        if (count >= 20) {
                            sendMessage("§7... and " + (module.getMarkedBlocks().size() - 20) + " more");
                            break;
                        }
                    }
                }
                break;

            case "reset":
                module.resetMinedBlocks();
                sendSuccess("Reset mined blocks status!");
                break;

            default:
                sendError("Unknown subcommand: " + subCommand);
                sendUsage();
        }
    }

    @Override
    public Category getCategory() {
        return Category.UTILITY;
    }
}
