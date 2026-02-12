package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.dungeons.SecretTriggerbotModule;

/**
 * Reset SecretTriggerbot clicked blocks list
 */
public class SecretResetCommand extends Command {

    @Override
    public String getName() {
        return "secretreset";
    }

    @Override
    public String getDescription() {
        return "Clear SecretTriggerbot clicked blocks list";
    }

    @Override
    public String getUsage() {
        return "secretreset";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"sr", "resetsecrets", "clearsecrets"};
    }

    @Override
    public Category getCategory() {
        return Category.UTILITY;
    }

    @Override
    public void execute(String[] args) {
        ModuleManager manager = ModuleManager.getInstance();
        SecretTriggerbotModule module = manager.getModule(SecretTriggerbotModule.class);

        if (module == null) {
            sendError("SecretTriggerbot module not found!");
            return;
        }

        module.clearClickedBlocks();
        sendSuccess("§aSecretTriggerbot clicked blocks cleared! You can now click secrets again.");
    }
}
