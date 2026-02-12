package dev.hunchclient.module.impl.sbd;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.impl.sbd.cache.PartyMember;
import dev.hunchclient.module.impl.sbd.cache.PartyMemberCache;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.util.PartyJoinUtil;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Auto-kick module for Hypixel Skyblock Dungeons
 * Automatically kicks players who don't meet requirements (PB time, secrets, etc.)
 *
 * WATCHDOG SAFE: YES
 * - Only monitors chat and sends chat commands
 * - No packet manipulation or gameplay automation beyond chat
 */
public class AutoKickModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IAutoKick {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoKickModule.class);
    private static AutoKickModule instance;

    private final Minecraft mc = Minecraft.getInstance();

    // Config values
    private int selectedFloorIndex = 4; // 0=F7, 1=M4, 2=M5, 3=M6, 4=M7
    private static final String[] FLOOR_OPTIONS = {"F7", "M4", "M5", "M6", "M7"};
    private String requiredPB = ""; // e.g., "5:30" or "330" (seconds)
    private String requiredSecrets = ""; // e.g., "50000" or "50k"
    private boolean sendKickMessage = false;
    private String customKickMessage = "Kicking {name} (PB: {pb} | Required: {required})";
    private int kickDelay = 1500; // Delay between kicks in milliseconds (default 1.5 seconds)
    private int kickMessageDelay = 400; // Delay between party message and actual kick

    // Kick queue system to prevent Hypixel from blocking rapid kicks
    private final Queue<KickTask> kickQueue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService kickScheduler;
    private boolean isProcessingKicks = false;

    // Settings list
    private List<ModuleSetting> settings;

    // Inner class to hold kick tasks
    private static class KickTask {
        final PartyMember member;
        final String type;
        final String reason;
        final String playerStat;
        final String required;

        KickTask(PartyMember member, String type, String reason, String playerStat, String required) {
            this.member = member;
            this.type = type;
            this.reason = reason;
            this.playerStat = playerStat;
            this.required = required;
        }
    }

    public AutoKickModule() {
        super("SBD AutoKick", "Auto-kick players below dungeon requirements", Category.MISC, false);
        instance = this;
        initializeSettings();
    }

    private void initializeSettings() {
        settings = new ArrayList<>();

        settings.add(new DropdownSetting(
            "Floor",
            "Which floor to check requirements for",
            "selectedFloor",
            FLOOR_OPTIONS,
            () -> selectedFloorIndex,
            (index) -> selectedFloorIndex = index
        ));

        settings.add(new TextBoxSetting(
            "Required PB",
            "S+ PB requirement (e.g. 5:30 or 330). Leave empty for no requirement",
            "requiredPB",
            () -> requiredPB,
            (value) -> requiredPB = formatPBInput(value),
            "5:30"
        ));

        settings.add(new TextBoxSetting(
            "Required Secrets",
            "Minimum secrets required (e.g. 50k or 50000). Leave empty for no requirement",
            "requiredSecrets",
            () -> requiredSecrets,
            (value) -> requiredSecrets = value,
            "50k"
        ));

        settings.add(new CheckboxSetting(
            "Send Kick Message",
            "Announce kick reason in party chat",
            "sendKickMessage",
            () -> sendKickMessage,
            (value) -> sendKickMessage = value
        ));

        settings.add(new TextBoxSetting(
            "Kick Message",
            "Custom kick message format. Use {name}, {pb}, {secrets}, {reqt} (req time), {reqs} (req secrets)",
            "customKickMessage",
            () -> customKickMessage,
            (value) -> setCustomKickMessage(value),
            "Kicking {name} (PB: {pb} | Req: {reqt})"
        ));

        settings.add(new SliderSetting(
            "Kick Delay (ms)",
            "Delay between kicks to avoid Hypixel rate limiting",
            "kickDelay",
            500f, 5000f,
            () -> (float) kickDelay,
            (value) -> kickDelay = value.intValue()
        ).withDecimals(0).withSuffix(" ms"));

        settings.add(new SliderSetting(
            "Message Delay (ms)",
            "Delay between party kick message and the actual /p kick command",
            "kickMessageDelay",
            0f, 2000f,
            () -> (float) kickMessageDelay,
            value -> kickMessageDelay = value.intValue()
        ).withDecimals(0).withSuffix(" ms"));
    }

    @Override
    public List<ModuleSetting> getSettings() {
        return settings;
    }

    public static AutoKickModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        LOGGER.info("SBD AutoKick enabled");
        // Initialize the kick scheduler
        if (kickScheduler == null || kickScheduler.isShutdown()) {
            kickScheduler = Executors.newSingleThreadScheduledExecutor();
        }
    }

    @Override
    protected void onDisable() {
        LOGGER.info("SBD AutoKick disabled");
        // Clear the kick queue when disabling
        kickQueue.clear();
        isProcessingKicks = false;
        // Cancel any pending scheduled tasks
        try {
            kickScheduler.shutdownNow();
        } catch (Exception e) {
            LOGGER.error("Error shutting down kick scheduler", e);
        }
    }

    /**
     * Handle incoming chat messages
     * Called from ChatHudMixin or similar
     */
    public boolean handleChatMessage(String message) {
        if (!isEnabled()) {
            return false;
        }

        // Use PartyJoinUtil for universal join detection
        PartyJoinUtil.JoinInfo joinInfo = PartyJoinUtil.parseJoinMessage(message);
        if (joinInfo == null) {
            return false;
        }

        // Only process dungeon joins (not regular party joins)
        if (joinInfo.getType() != PartyJoinUtil.JoinType.DUNGEON) {
            return false;
        }

        String username = joinInfo.getUsername();
        String dungeonClass = joinInfo.getDungeonClass();
        int classLevel = joinInfo.getClassLevelAsInt();

        handlePartyFinderJoin(username, dungeonClass, classLevel);
        return false; // Don't cancel the message
    }

    private void handlePartyFinderJoin(String username, String dungeonClass, int classLevel) {
        PartyMember member = PartyMemberCache.get(username);

        if (!member.isDataLoaded() && !member.isLoading()) {
            // Start loading
            member.init().thenAccept(v -> checkAndKick(member));
        } else if (member.isDataLoaded()) {
            // Data already loaded
            checkAndKick(member);
        } else {
            // Loading in progress, wait for it
            member.init().thenAccept(v -> checkAndKick(member));
        }
    }

    private void checkAndKick(PartyMember member) {
        if (!member.isDataLoaded()) {
            sendMessage("§8[§eSBD§8]§r " + member.getUsername() + " §8[§eLoading...§8]");
            return;
        }

        if (member.getStats() == null) {
            sendMessage("§8[§eSBD§8]§r " + member.getUsername() + " §8[§cAPI Failed§8]");
            return;
        }

        // Print player info (even if stats are 0)
        String selectedFloor = FLOOR_OPTIONS[selectedFloorIndex];
        sendMessage("§8[§eSBD§8]§r " + member.getInfoString(selectedFloor));

        // Check PB requirement
        long requiredPBMs = parseRequiredPB();
        if (requiredPBMs > 0) {
            long playerPB = member.getPBForFloor(selectedFloor);
            if (playerPB < 0) {
                // No S+ PB
                kickPlayer(member, "pb", "No S+ PB", member.getStats().getPBString(playerPB), formatTime(requiredPBMs));
                return;
            } else if (playerPB > requiredPBMs) {
                // PB too slow
                kickPlayer(member, "pb", "PB too slow", member.getStats().getPBString(playerPB), formatTime(requiredPBMs));
                return;
            }
        }

        // Check secrets requirement
        int requiredSecretsCount = parseRequiredSecrets();
        if (requiredSecretsCount > 0) {
            int playerSecrets = member.getTotalSecrets();
            if (playerSecrets < requiredSecretsCount) {
                kickPlayer(member, "secrets", "Not enough secrets", String.valueOf(playerSecrets), String.valueOf(requiredSecretsCount));
                return;
            }
        }

        // Player meets all requirements
        // sendMessage("§a[SBD] " + member.getUsername() + " meets all requirements!");
    }

    private void kickPlayer(PartyMember member, String type, String reason, String playerStat, String required) {
        // Add to queue instead of kicking immediately
        kickQueue.offer(new KickTask(member, type, reason, playerStat, required));

        // Start processing queue if not already processing
        if (!isProcessingKicks) {
            isProcessingKicks = true;
            processNextKick();
        }
    }

    private void processNextKick() {
        if (kickQueue.isEmpty()) {
            isProcessingKicks = false;
            return;
        }

        KickTask task = kickQueue.poll();
        if (task == null) {
            isProcessingKicks = false;
            return;
        }

        // Send kick message
        String kickMsg = switch (task.type) {
            case "pb" -> String.format("§8[§eSBD§8]§r Kicking %s (PB: §e%s§r | Req: §e%s§r)",
                task.member.getUsername(), task.playerStat, task.required);
            case "secrets" -> String.format("§8[§eSBD§8]§r Kicking %s (Secrets: §e%s§r | Req: §e%s§r)",
                task.member.getUsername(), task.playerStat, task.required);
            default -> String.format("§8[§eSBD§8]§r Kicking %s (%s)", task.member.getUsername(), task.reason);
        };

        sendMessage(kickMsg);

        // Send party chat message if enabled
        if (sendKickMessage) {
            String formattedMessage = formatKickMessage(task.member, task.type, task.playerStat, task.required);
            sendCommand("/pc " + formattedMessage);

            // Small delay before kick command
            kickScheduler.schedule(() -> {
                sendCommand("/party kick " + task.member.getUsername());
                // Process next kick after delay
                kickScheduler.schedule(this::processNextKick, kickDelay, TimeUnit.MILLISECONDS);
            }, Math.max(0, kickMessageDelay), TimeUnit.MILLISECONDS);
        } else {
            // Direct kick without party message
            sendCommand("/party kick " + task.member.getUsername());
            // Process next kick after delay
            kickScheduler.schedule(this::processNextKick, kickDelay, TimeUnit.MILLISECONDS);
        }
    }

    private String formatKickMessage(PartyMember member, String type, String playerStat, String required) {
        String message = customKickMessage;

        // Replace {name}
        message = message.replace("{name}", member.getUsername());

        // Replace {pb} and {reqt} for PB kicks
        if (type.equals("pb")) {
            message = message.replace("{pb}", playerStat);
            message = message.replace("{reqt}", required);
            message = message.replace("{secrets}", String.valueOf(member.getTotalSecrets()));
            message = message.replace("{reqs}", parseRequiredSecrets() > 0 ? String.valueOf(parseRequiredSecrets()) : "N/A");
        }
        // Replace {secrets} and {reqs} for secret kicks
        else if (type.equals("secrets")) {
            message = message.replace("{secrets}", playerStat);
            message = message.replace("{reqs}", required);
            String selectedFloor = FLOOR_OPTIONS[selectedFloorIndex];
            long playerPB = member.getPBForFloor(selectedFloor);
            message = message.replace("{pb}", member.getStats().getPBString(playerPB));
            message = message.replace("{reqt}", parseRequiredPB() > 0 ? formatTime(parseRequiredPB()) : "N/A");
        }

        return message;
    }

    private void sendCommand(String command) {
        mc.execute(() -> {
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendChat(command);
            }
        });
    }


    private void sendMessage(String message) {
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    /**
     * Formats PB input to always use "m:ss" format
     * Accepts: "330", "5:30", "530" -> converts to "5:30"
     */
    private String formatPBInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        // Remove all non-digit characters except colon
        String cleaned = input.replaceAll("[^0-9:]", "");

        if (cleaned.isEmpty()) {
            return "";
        }

        // Already in "m:ss" format - validate and return
        if (cleaned.contains(":")) {
            String[] parts = cleaned.split(":");
            if (parts.length == 2 && !parts[0].isEmpty() && parts[1].matches("\\d{1,2}")) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                if (seconds < 60) {
                    return String.format("%d:%02d", minutes, seconds);
                }
            }
            // Invalid format with colon - try to parse as number
            cleaned = cleaned.replace(":", "");
        }

        // Parse as total seconds and convert to "m:ss"
        try {
            int totalSeconds = Integer.parseInt(cleaned);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid PB input: {}", input);
            return "";
        }
    }

    private long parseRequiredPB() {
        if (requiredPB == null || requiredPB.trim().isEmpty()) {
            return 0;
        }

        String pb = requiredPB.trim();

        // Format: "5:30" -> 330 seconds -> 330000 ms
        if (pb.matches("\\d+:\\d{2}")) {
            String[] parts = pb.split(":");
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            return (minutes * 60 + seconds) * 1000L;
        }

        // Format: "330" -> 330 seconds -> 330000 ms
        try {
            int seconds = Integer.parseInt(pb);
            return seconds * 1000L;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid PB format: {}", pb);
            return 0;
        }
    }

    private int parseRequiredSecrets() {
        if (requiredSecrets == null || requiredSecrets.trim().isEmpty()) {
            return 0;
        }

        String secrets = requiredSecrets.trim();

        // Format: "50k" -> 50000
        if (secrets.toLowerCase().endsWith("k")) {
            try {
                int value = Integer.parseInt(secrets.substring(0, secrets.length() - 1));
                return value * 1000;
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid secrets format: {}", secrets);
                return 0;
            }
        }

        // Format: "50000" -> 50000
        try {
            return Integer.parseInt(secrets);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid secrets format: {}", secrets);
            return 0;
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public String getCustomKickMessage() {
        return customKickMessage;
    }

    public void setCustomKickMessage(String customKickMessage) {
        this.customKickMessage = customKickMessage == null || customKickMessage.isEmpty()
            ? "Kicking {name} (PB: {pb} | Req: {reqt})"
            : customKickMessage;
    }

    // Config management
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("selectedFloorIndex", selectedFloorIndex);
        config.addProperty("requiredPB", requiredPB);
        config.addProperty("requiredSecrets", requiredSecrets);
        config.addProperty("sendKickMessage", sendKickMessage);
        config.addProperty("customKickMessage", customKickMessage);
        config.addProperty("kickDelay", kickDelay);
        config.addProperty("kickMessageDelay", kickMessageDelay);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("selectedFloorIndex")) {
            selectedFloorIndex = config.get("selectedFloorIndex").getAsInt();
        } else if (config.has("selectedFloor")) {
            // Legacy support
            String floor = config.get("selectedFloor").getAsString();
            for (int i = 0; i < FLOOR_OPTIONS.length; i++) {
                if (FLOOR_OPTIONS[i].equals(floor)) {
                    selectedFloorIndex = i;
                    break;
                }
            }
        }
        if (config.has("requiredPB")) {
            requiredPB = config.get("requiredPB").getAsString();
        }
        if (config.has("requiredSecrets")) {
            requiredSecrets = config.get("requiredSecrets").getAsString();
        }
        if (config.has("sendKickMessage")) {
            sendKickMessage = config.get("sendKickMessage").getAsBoolean();
        }
        if (config.has("customKickMessage")) {
            setCustomKickMessage(config.get("customKickMessage").getAsString());
        }
        if (config.has("kickDelay")) {
            kickDelay = config.get("kickDelay").getAsInt();
        }
        if (config.has("kickMessageDelay")) {
            kickMessageDelay = config.get("kickMessageDelay").getAsInt();
        }
    }

}
