package dev.hunchclient.module.impl.lancho;

import dev.hunchclient.module.impl.LanchoModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Command handler for Lancho
 */
public class LanchoCommand {

    private final Minecraft mc = Minecraft.getInstance();
    private final LanchoModule module;

    public LanchoCommand(LanchoModule module) {
        this.module = module;
    }

    /**
     * Handle lancho command
     * Usage: /lancho <subcommand> [args]
     */
    public boolean handleCommand(String[] args) {
        if (args.length == 0) {
            showHelp();
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "test":
                return handleTest();
            case "secret":
                return handleSecret(args);
            case "personality":
                return handlePersonality(args);
            case "status":
                return handleStatus();
            case "toggle":
                return handleToggle(args);
            case "settings":
                return handleSettings();
            case "reconnect":
                return handleReconnect();
            case "help":
                showHelp();
                return true;
            default:
                sendMessage("§cUnknown subcommand: " + subcommand);
                showHelp();
                return false;
        }
    }

    private boolean handleSecret(String[] args) {
        if (args.length < 2) {
            sendMessage("§cUsage: /lancho secret <secret>");
            return false;
        }

        String secret = args[1];
        module.setServerSecret(secret);
        sendMessage("§aServer secret updated!");
        return true;
    }

    private boolean handlePersonality(String[] args) {
        if (args.length < 2) {
            sendMessage("§cUsage: /lancho personality <personality>");
            sendMessage("§eAvailable: default, tiktok, femboy, rightextremist, marxistleninist");
            return false;
        }

        String personality = args[1].toLowerCase();
        module.setPersonality(personality);
        sendMessage("§aPersonality set to: " + personality);
        return true;
    }

    private boolean handleStatus() {
        sendMessage("§6§l=== Lancho Status ===");
        sendMessage("§eEnabled: " + (module.isEnabled() ? "§aYes" : "§cNo"));
        sendMessage("§eConnected: " + (module.isConnected() ? "§aYes" : "§cNo"));

        if (module.getRequestQueue() != null) {
            sendMessage("§eQueue: " + module.getRequestQueue().getQueueSize() + " requests");
            sendMessage("§eProcessing: " + (module.getRequestQueue().isProcessing() ? "§aYes" : "§cNo"));
        }

        sendMessage("§ePersonality: §f" + module.getPersonality());
        sendMessage("§eGuild Chat: " + (module.isGuildChatEnabled() ? "§aEnabled" : "§cDisabled"));
        sendMessage("§eAll Chat: " + (module.isAllChatEnabled() ? "§aEnabled" : "§cDisabled"));
        return true;
    }

    private boolean handleToggle(String[] args) {
        if (args.length < 2) {
            sendMessage("§cUsage: /lancho toggle <guild|all>");
            return false;
        }

        String option = args[1].toLowerCase();

        switch (option) {
            case "guild":
                module.setGuildChatEnabled(!module.isGuildChatEnabled());
                sendMessage("§eGuild chat: " + (module.isGuildChatEnabled() ? "§aEnabled" : "§cDisabled"));
                return true;
            case "all":
                module.setAllChatEnabled(!module.isAllChatEnabled());
                sendMessage("§eAll chat: " + (module.isAllChatEnabled() ? "§aEnabled" : "§cDisabled"));
                return true;
            default:
                sendMessage("§cUnknown option: " + option);
                return false;
        }
    }

    private boolean handleSettings() {
        sendMessage("§6§l=== Lancho Settings ===");
        sendMessage("§ePersonality: §f" + module.getPersonality());
        sendMessage("§ePersonality Strictness: §f" + module.getPersonalityStrictness() + "%");
        sendMessage("§eToxicity Level: §f" + module.getToxicityLevel() + "%");
        sendMessage("§eSassy Level: §f" + module.getSassyLevel() + "%");
        sendMessage("§eZesty Level: §f" + module.getZestyLevel() + "%");
        sendMessage("§eSubmissiveness: §f" + module.getSubmissivenessLevel() + "%");
        sendMessage("§eEmoji Frequency: §f" + module.getEmojiFrequency() + "%");
        sendMessage("§eMessage Length: §f" + module.getMessageLength());
        sendMessage("§eWeb Search: " + (module.isWebSearchEnabled() ? "§aEnabled" : "§cDisabled"));
        sendMessage("§eGuild Chat: " + (module.isGuildChatEnabled() ? "§aEnabled" : "§cDisabled"));
        sendMessage("§eAll Chat: " + (module.isAllChatEnabled() ? "§aEnabled" : "§cDisabled"));
        return true;
    }

    private boolean handleTest() {
        if (!module.isEnabled()) {
            sendMessage("§cLancho is disabled! Enable it first.");
            return false;
        }

        if (!module.isConnected()) {
            sendMessage("§cNot connected to server! Check your secret and connection.");
            return false;
        }

        if (module.getRequestQueue() == null) {
            sendMessage("§cRequest queue not initialized!");
            return false;
        }

        // Send test request with current settings
        String testPrompt = "This is a test message to verify Lancho is working. Please respond with a short confirmation.";
        String playerName = mc.player != null ? mc.player.getName().getString() : "TestUser";

        sendMessage("§aSending test request to Lancho AI...");
        sendMessage("§7Using current personality: §f" + module.getPersonality());
        sendMessage("§7Using message length: §f" + module.getMessageLength());

        // Queue the test request (simulating a "lancho" trigger)
        module.getRequestQueue().enqueue(testPrompt, "test", playerName, module.getPersonality());

        return true;
    }

    private boolean handleReconnect() {
        if (module.getConnectionManager() == null) {
            sendMessage("§cConnection manager not initialized!");
            return false;
        }

        sendMessage("§eReconnecting to server...");
        module.getConnectionManager().disconnect();
        module.getConnectionManager().resetReconnectAttempts();
        module.getConnectionManager().connect();
        return true;
    }

    private void showHelp() {
        sendMessage("§6§l=== Lancho Commands ===");
        sendMessage("§e/lancho test §7- Test if commands work");
        sendMessage("§e/lancho secret <secret> §7- Set server secret");
        sendMessage("§e/lancho personality <name> §7- Set AI personality");
        sendMessage("§e/lancho status §7- Show connection status");
        sendMessage("§e/lancho toggle <guild|all> §7- Toggle chat listeners");
        sendMessage("§e/lancho settings §7- Show all settings");
        sendMessage("§e/lancho reconnect §7- Reconnect to server");
        sendMessage("§e/lancho help §7- Show this help");
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6[Lancho] " + message), false);
        }
    }
}
