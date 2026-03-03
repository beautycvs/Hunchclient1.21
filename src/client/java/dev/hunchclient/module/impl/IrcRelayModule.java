package dev.hunchclient.module.impl;

import dev.hunchclient.discord.DiscordIRCBridge;
import dev.hunchclient.gui.irc.IrcChatWindow;
import dev.hunchclient.module.Module;
import dev.hunchclient.network.SecureApiClient;
import dev.hunchclient.network.SecureApiClient.ApiResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import dev.hunchclient.module.impl.NameProtectModule;

/**
 * IRC relay module (ported from ChatTriggers script)
 * Now uses SecureApiClient for HMAC-signed secure communication.
 */
public class IrcRelayModule extends Module {

    private static final Path LAST_TS_FILE = Paths.get("config", "hunchclient", "irc_lastts.txt");
    private static final int MAX_PENDING_MESSAGES = 100;
    private static final long PENDING_STALE_MS = 60_000L;
    private static final int POLL_DELAY_TICKS = 10; // Poll chat twice per second
    private static final int USER_POLL_DELAY_TICKS = 100; // Poll users every 5 seconds (100 ticks)
    private static final int DM_POLL_DELAY_TICKS = 20; // Poll DMs every second (20 ticks)
    private static final int KEEPALIVE_DELAY_TICKS = 600; // Keepalive every 30 seconds (600 ticks)

    private static IrcRelayModule instance;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<PendingMessage> pendingSentMessages = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);
    private final AtomicBoolean userPollInFlight = new AtomicBoolean(false);
    private final AtomicBoolean dmPollInFlight = new AtomicBoolean(false);

    private long lastTimestamp = System.currentTimeMillis();
    private long lastDmTimestamp = System.currentTimeMillis();
    private boolean ircMode = false;
    private int ticksSincePoll = 0;
    private int ticksSinceUserPoll = 0;
    private int ticksSinceDmPoll = 0;
    private int ticksSinceKeepalive = 0;

    // IRC Chat Window callback
    private IrcChatWindow chatWindow = null;

    // User list
    private final List<String> onlineUsers = Collections.synchronizedList(new ArrayList<>());

    public IrcRelayModule() {
        super("IrcRelay", "Relay chat messages via the Hunch IRC backend", Category.MISC, true);
        instance = this;
    }

    public static IrcRelayModule getInstance() {
        return instance;
    }

    public void setChatWindow(IrcChatWindow window) {
        this.chatWindow = window;
    }

    public IrcChatWindow getChatWindow() {
        return this.chatWindow;
    }

    @Override
    protected void onEnable() {
        lastTimestamp = loadLastTimestamp();
        ticksSincePoll = 0;
        pollInFlight.set(false);
        initDiscordBridge();
        sendClientMessage("§b[IRC]§7 IRC relay ready");
    }

    @Override
    protected void onDisable() {
        ircMode = false;
        pendingSentMessages.clear();
        pollInFlight.set(false);
        DiscordIRCBridge.getInstance().shutdown();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }

        boolean maintainConnection = shouldMaintainConnection();
        if (maintainConnection) {
            initDiscordBridge();
        }

        if (!maintainConnection) {
            ticksSincePoll = 0;
            ticksSinceUserPoll = 0;
            ticksSinceDmPoll = 0;
            ticksSinceKeepalive = 0;
            return;
        }

        ticksSincePoll++;
        if (ticksSincePoll >= POLL_DELAY_TICKS) {
            ticksSincePoll = 0;
            pollIrc();
        }

        ticksSinceUserPoll++;
        if (ticksSinceUserPoll >= USER_POLL_DELAY_TICKS) {
            ticksSinceUserPoll = 0;
            pollUsers();
        }

        ticksSinceDmPoll++;
        if (ticksSinceDmPoll >= DM_POLL_DELAY_TICKS) {
            ticksSinceDmPoll = 0;
            pollDms();
        }

        ticksSinceKeepalive++;
        if (ticksSinceKeepalive >= KEEPALIVE_DELAY_TICKS) {
            ticksSinceKeepalive = 0;
            sendKeepalive();
        }
    }

    private boolean shouldMaintainConnection() {
    boolean result = ircMode || chatWindow != null || DiscordIRCBridge.getInstance().isLinked();
    return result;
}

    private void initDiscordBridge() {
        String nick = resolveIrcNick();
        if (nick != null) {
            DiscordIRCBridge.getInstance().init(nick);
        }
    }

    private String resolveIrcNick() {
        if (mc.player != null) {
            return mc.player.getName().getString();
        }
        return mc.getUser() != null ? mc.getUser().getName() : null;
    }

    /**
     * Toggle IRC mode on/off
     */
    public void toggleIrcMode() {
        ircMode = !ircMode;
    }

    /**
     * Send a message to IRC
     */
    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) {
            sendClientMessage("§c[IRC] Message cannot be empty");
            return;
        }
        sendIrcMessage(message, getPlayerName());
    }

    public boolean handleCommand(String message) {
        if (!message.startsWith("/irc")) {
            return false;
        }

        String arg = message.length() > 4 ? message.substring(4).trim() : "";

        if (arg.isEmpty()) {
            toggleIrcMode();
        } else {
            sendMessage(arg);
        }
        return true;
    }

    public boolean handleHccCommand(String message) {
        if (!message.startsWith("/hcc")) {
            return false;
        }

        String arg = message.length() > 4 ? message.substring(4).trim() : "";
        if (arg.isEmpty()) {
            sendClientMessage("§c[IRC] Usage: /hcc <message>");
        } else {
            sendMessage(arg);
        }
        return true;
    }

    public boolean handleOutgoingChat(String message) {
        if (!ircMode || message.startsWith("/")) {
            return false;
        }

        sendIrcMessage(message, getPlayerName());
        return true;
    }

    private void sendIrcMessage(String message, String user) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String processed = message;
        addPendingMessage(processed);

        String localTime = renderHms(System.currentTimeMillis(), getLocalOffsetMinutes());
        String timePrefix = "§8[§7C-" + localTime + "§8]§r";
        sendIrcChatToClient("§b[IRC]§r §a" + user + "§7: §f" + timePrefix + " " + processed);

        // Add to GUI chat window immediately
        // Forward to IRC Chat Window if available
if (chatWindow != null) {
    NameProtectModule nameProtect = NameProtectModule.getInstance();
    String protectedSender = nameProtect != null ? nameProtect.sanitizeString(sender) : sender;
    String protectedText = nameProtect != null ? nameProtect.sanitizeString(safeText) : safeText;
    chatWindow.addIrcMessage(protectedSender, protectedText, timestamp, isMe);
}

        SecureApiClient.ircSend(user, processed, getLocalOffsetMinutes())
            .thenAccept(result -> {
                if (!result.isSuccess() || !result.getOk()) {
                    removePendingMessage(processed);
                    sendClientMessage("§c[IRC] Fehler: " + result.getErrorMessage());
                }
            }).exceptionally(throwable -> {
                removePendingMessage(processed);
                sendClientMessage("§c[IRC] Send failed: " + throwable.getMessage());
                return null;
            });
    }

    private void pollIrc() {
        if (!pollInFlight.compareAndSet(false, true)) {
            return;
        }

        purgeStalePending();

        SecureApiClient.ircPoll(lastTimestamp)
            .thenAccept(this::handlePollResponse)
            .exceptionally(throwable -> {
                sendClientMessage("§c[IRC] Poll failed: " + throwable.getMessage());
                return null;
            })
            .whenComplete((ignored, error) -> pollInFlight.set(false));
    }

    private void handlePollResponse(ApiResponse result) {
    if (!result.isSuccess()) {
        return;
    }

        JsonObject json = result.getJsonObject();
        if (json != null && json.has("now") && json.get("now").isJsonPrimitive()) {
            long serverNow = json.get("now").getAsLong();
            updateTimestamp(serverNow);
        }

        JsonArray lines = json != null && json.has("lines") && json.get("lines").isJsonArray()
            ? json.getAsJsonArray("lines")
            : null;

        if (lines == null) {
            return;
        }

        for (JsonElement element : lines) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject line = element.getAsJsonObject();

            String text = line.has("text") ? line.get("text").getAsString() : "";
            String from = line.has("from") ? line.get("from").getAsString() : "Unknown";
            long ts = line.has("ts") && line.get("ts").isJsonPrimitive() ? line.get("ts").getAsLong() : 0L;
            Integer offset = line.has("utcOffsetMin") && line.get("utcOffsetMin").isJsonPrimitive()
                ? line.get("utcOffsetMin").getAsInt()
                : null;

            if (ts > 0) {
                updateTimestamp(ts);
            }

            final String safeText = text;
            final String sender = from;
            final long timestamp = ts;
            final Integer utcOffset = offset;

            Minecraft.getInstance().execute(() -> {
                if (mc.player == null) {
                    return;
                }

                boolean isMe = sender.equalsIgnoreCase(mc.player.getName().getString());
                boolean skipEcho = isMe && removePendingMessage(safeText);
                if (skipEcho) {
                    return;
                }

                if (!isMe) {
                    playNotification();
                }

                String nameColor = isMe ? "§a" : "§c";
                String userTime = renderHms(timestamp, utcOffset);
                String clientTime = renderHms(timestamp, getLocalOffsetMinutes());
                String timePrefix = userTime != null
                    ? "§8[§7U-" + userTime + " | C-" + clientTime + "§8]§r"
                    : "§8[§7C-" + clientTime + "§8]§r";

                sendIrcChatToClient("§b[IRC]§r " + nameColor + sender + "§7: §f" + timePrefix + " " + safeText);

                // Forward to IRC Chat Window if available
                if (chatWindow != null) {
                    chatWindow.addIrcMessage(sender, safeText, timestamp, isMe);
                }
            });
        }
    }

    private void playNotification() {
        if (mc.player == null) {
            return;
        }
        // Check if notifications are enabled
        if (!dev.hunchclient.util.GuiConfig.getInstance().isIrcNotificationsEnabled()) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f));
    }

    private void addPendingMessage(String text) {
        synchronized (pendingSentMessages) {
            if (pendingSentMessages.size() >= MAX_PENDING_MESSAGES) {
                pendingSentMessages.remove(0);
            }
            pendingSentMessages.add(new PendingMessage(text, System.currentTimeMillis()));
        }
    }

    private boolean removePendingMessage(String text) {
        synchronized (pendingSentMessages) {
            for (int i = 0; i < pendingSentMessages.size(); i++) {
                if (Objects.equals(pendingSentMessages.get(i).text, text)) {
                    pendingSentMessages.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    private void purgeStalePending() {
        long now = System.currentTimeMillis();
        synchronized (pendingSentMessages) {
            pendingSentMessages.removeIf(msg -> now - msg.created > PENDING_STALE_MS);
        }
    }

    private void updateTimestamp(long ts) {
    if (ts <= 0) {
        return;
    }
    long now = System.currentTimeMillis();
    if (ts > now + 5000) {
        return; // reject timestamps more than 5 seconds in the future
    }
    if (ts > lastTimestamp) {
        lastTimestamp = ts;
        saveLastTimestamp(ts);
    }
}

    private long loadLastTimestamp() {
        try {
            if (Files.exists(LAST_TS_FILE)) {
                String raw = Files.readString(LAST_TS_FILE, StandardCharsets.UTF_8).trim();
                if (!raw.isEmpty()) {
                    long parsed = Long.parseLong(raw);
                    sendClientMessage("§b[IRC]§7 Letzter Timestamp geladen: " + parsed);
                    return parsed;
                }
            }
        } catch (Exception ignored) {
        }
        long now = System.currentTimeMillis();
        saveLastTimestamp(now);
        return now;
    }

    private void saveLastTimestamp(long ts) {
        try {
            Path parent = LAST_TS_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(LAST_TS_FILE, StandardCharsets.UTF_8)) {
                writer.write(Long.toString(ts));
            }
        } catch (Exception e) {
            sendClientMessage("§c[IRC] Fehler beim Speichern des Timestamps: " + e.getMessage());
        }
    }

    private int getLocalOffsetMinutes() {
        ZoneOffset offset = ZonedDateTime.now().getOffset();
        return offset.getTotalSeconds() / 60;
    }

    private String renderHms(long epochMs, Integer offsetMinutes) {
        if (epochMs <= 0) {
            return "??h??m??s";
        }
        int targetOffset = offsetMinutes != null ? offsetMinutes : getLocalOffsetMinutes();
        targetOffset = Math.max(-18 * 60, Math.min(18 * 60, targetOffset));

        Instant instant = Instant.ofEpochMilli(epochMs);
        LocalTime time = instant.atOffset(ZoneOffset.ofTotalSeconds(targetOffset * 60)).toLocalTime();

        return String.format("%02dh%02dm%02ds", time.getHour(), time.getMinute(), time.getSecond());
    }

    private void sendClientMessage(String message) {
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    /**
     * Send IRC chat message to client (respects notification toggle)
     */
    private void sendIrcChatToClient(String message) {
        if (!dev.hunchclient.util.GuiConfig.getInstance().isIrcNotificationsEnabled()) {
            return;
        }
        sendClientMessage(message);
    }

    private String getPlayerName() {
        return mc.player != null ? mc.player.getName().getString() : "Player";
    }

    /**
     * Send keepalive to stay online in IRC
     */
    private void sendKeepalive() {
        String myUsername = getPlayerName();

        // Empty message = keepalive
        SecureApiClient.ircSend(myUsername, "", getLocalOffsetMinutes())
            .thenAccept(result -> {
                // Silent - don't spam chat with keepalive confirmations
            }).exceptionally(throwable -> {
                // Silent failure for keepalive
                return null;
            });
    }

    public static void shutdownExecutor() {
        // Now handled by SecureApiClient.shutdown()
        SecureApiClient.shutdown();
    }

    /**
     * Poll for online users
     */
    private void pollUsers() {
        if (!userPollInFlight.compareAndSet(false, true)) {
            return;
        }

        SecureApiClient.ircUsers()
            .thenAccept(this::handleUsersResponse)
            .exceptionally(throwable -> {
                // Silent failure for user polling
                return null;
            })
            .whenComplete((ignored, error) -> userPollInFlight.set(false));
    }

    private void handleUsersResponse(ApiResponse result) {
        if (!result.isSuccess()) {
            return;
        }

        JsonObject json = result.getJsonObject();
        if (json == null) {
            return;
        }

        JsonArray users = json.has("users") && json.get("users").isJsonArray()
            ? json.getAsJsonArray("users")
            : null;

        if (users == null) {
            return;
        }

        String myUsername = getPlayerName();

        synchronized (onlineUsers) {
            onlineUsers.clear();
            for (JsonElement element : users) {
                if (element.isJsonPrimitive()) {
                    String username = element.getAsString();
                    // Filter out own username
                    if (!username.equalsIgnoreCase(myUsername)) {
                        onlineUsers.add(username);
                    }
                }
            }

            // Add Lancho as fake player if connected and authenticated
            if (isLanchoConnected() && !onlineUsers.contains("Lancho")) {
                onlineUsers.add("Lancho");
            }
        }

        // Update chat window with user list
        if (chatWindow != null) {
            chatWindow.updateUserList(new ArrayList<>(onlineUsers));
        }
    }

    /**
     * Check if Lancho is connected and authenticated
     */
    private boolean isLanchoConnected() {
        try {
            LanchoModule lanchoModule = LanchoModule.getInstance();
            return lanchoModule != null && lanchoModule.isEnabled() && lanchoModule.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getOnlineUsers() {
        synchronized (onlineUsers) {
            return new ArrayList<>(onlineUsers);
        }
    }

    /**
     * Load conversation history with a user
     */
    public void loadConversation(String withUser, java.util.function.Consumer<List<ConversationMessage>> callback) {
        if (withUser == null || withUser.isEmpty()) {
            return;
        }

        SecureApiClient.ircConversation(withUser)
            .thenAccept(result -> {
                JsonObject json = result.getJsonObject();
                if (!result.isSuccess() || json == null) {
                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray messages = json.has("messages") && json.get("messages").isJsonArray()
                    ? json.getAsJsonArray("messages")
                    : null;

                if (messages == null) {
                    callback.accept(new ArrayList<>());
                    return;
                }

                List<ConversationMessage> history = new ArrayList<>();
                for (JsonElement element : messages) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject msg = element.getAsJsonObject();

                    String from = msg.has("from") ? msg.get("from").getAsString() : "Unknown";
                    String to = msg.has("to") ? msg.get("to").getAsString() : "";

                    // Try multiple field names for message
                    String text = "";
                    if (msg.has("text")) {
                        text = msg.get("text").getAsString();
                    } else if (msg.has("message")) {
                        text = msg.get("message").getAsString();
                    } else if (msg.has("msg")) {
                        text = msg.get("msg").getAsString();
                    }

                    long ts = msg.has("ts") && msg.get("ts").isJsonPrimitive()
                        ? msg.get("ts").getAsLong()
                        : System.currentTimeMillis();

                    history.add(new ConversationMessage(from, to, text, ts));
                }

                // Call callback on main thread
                Minecraft.getInstance().execute(() -> callback.accept(history));
            })
            .exceptionally(throwable -> {
                sendClientMessage("§c[IRC] Failed to load conversation: " + throwable.getMessage());
                Minecraft.getInstance().execute(() -> callback.accept(new ArrayList<>()));
                return null;
            });
    }

    /**
     * Send a DM to a specific user
     */
    public void sendDm(String toUser, String message) {
        if (message == null || message.isEmpty() || toUser == null || toUser.isEmpty()) {
            return;
        }

        String myUsername = getPlayerName();

        SecureApiClient.ircDmSend(myUsername, toUser, message)
            .thenAccept(result -> {
                if (!result.isSuccess() || !result.getOk()) {
                    sendClientMessage("§c[IRC] DM failed: " + result.getErrorMessage());
                }
            }).exceptionally(throwable -> {
                sendClientMessage("§c[IRC] DM send failed: " + throwable.getMessage());
                return null;
            });
    }

    /**
     * Poll for new DMs
     */
    private void pollDms() {
        if (!dmPollInFlight.compareAndSet(false, true)) {
            return;
        }

        String myUsername = getPlayerName();
        SecureApiClient.ircDmPoll(myUsername, lastDmTimestamp)
            .thenAccept(this::handleDmPollResponse)
            .exceptionally(throwable -> {
                // Silent failure for DM polling
                return null;
            })
            .whenComplete((ignored, error) -> dmPollInFlight.set(false));
    }

    private void handleDmPollResponse(ApiResponse result) {
    if (!result.isSuccess()) {
        return;
    }

        JsonObject json = result.getJsonObject();
        if (json != null && json.has("now") && json.get("now").isJsonPrimitive()) {
            long serverNow = json.get("now").getAsLong();
            if (serverNow > lastDmTimestamp) {
                lastDmTimestamp = serverNow;
            }
        }

        JsonArray dms = json != null && json.has("dms") && json.get("dms").isJsonArray()
            ? json.getAsJsonArray("dms")
            : null;

        if (dms == null) {
            return;
        }

        for (JsonElement element : dms) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject dm = element.getAsJsonObject();

            String from = dm.has("from") ? dm.get("from").getAsString() : "Unknown";

            // Try multiple field names for message (API accepts "message", "text", "msg")
            String message = "";
            if (dm.has("message")) {
                message = dm.get("message").getAsString();
            } else if (dm.has("text")) {
                message = dm.get("text").getAsString();
            } else if (dm.has("msg")) {
                message = dm.get("msg").getAsString();
            }

            long ts = dm.has("ts") && dm.get("ts").isJsonPrimitive() ? dm.get("ts").getAsLong() : System.currentTimeMillis();

            if (ts > 0 && ts > lastDmTimestamp) {
                lastDmTimestamp = ts;
            }

            final String sender = from;
            final String content = message;
            final long timestamp = ts;

            Minecraft.getInstance().execute(() -> {
                if (mc.player == null) {
                    return;
                }

                // Skip DMs from myself (echo)
                boolean isMe = sender.equalsIgnoreCase(mc.player.getName().getString());
                if (isMe) {
                    return; // Don't show my own DMs
                }

                // Play notification for incoming DM
                playNotification();

                // Notify chat window to route DM (only show in DM window, not in main chat)
               // Notify chat window to route DM (only show in DM window, not in main chat)
                if (chatWindow != null) {
                    chatWindow.handleIncomingDm(sender, content, timestamp);
                }
            });
        }
    }

    private static class PendingMessage {
        final String text;
        final long created;

        PendingMessage(String text, long created) {
            this.text = text;
            this.created = created;
        }
    }

    public static class ConversationMessage {
        public final String from;
        public final String to;
        public final String text;
        public final long timestamp;

        public ConversationMessage(String from, String to, String text, long timestamp) {
            this.from = from;
            this.to = to;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

}
