package dev.hunchclient.module.impl.lancho;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Connection Manager for Lancho server
 */
public class ConnectionManager {

    private final HttpClient httpClient;
    private final Minecraft mc = Minecraft.getInstance();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean connected = false;
    private boolean connecting = false;
    private int reconnectAttempts = 0;
    private String clientId = null;

    private String serverSecret;
    private int maxReconnectAttempts;
    private int reconnectDelay;

    public ConnectionManager(HttpClient httpClient, String serverSecret, int maxReconnectAttempts, int reconnectDelay) {
        this.httpClient = httpClient;
        this.serverSecret = serverSecret;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelay = reconnectDelay;
    }

    /**
     * Connect to server
     */
    public CompletableFuture<Boolean> connect() {
        System.out.println("[ConnectionManager] connect() called");
        System.out.println("[ConnectionManager] connected=" + connected + ", connecting=" + connecting);

        if (connected || connecting) {
            System.out.println("[ConnectionManager] Already connected or connecting, aborting");
            return CompletableFuture.completedFuture(false);
        }

        System.out.println("[ConnectionManager] serverSecret=" + (serverSecret != null ? "***" + serverSecret.length() + "chars" : "null"));

        if (serverSecret == null || serverSecret.isEmpty()) {
            System.out.println("[ConnectionManager] Server secret is empty!");
            sendMessage("§6[Lancho] §cCannot connect: Server secret not set!");
            return CompletableFuture.completedFuture(false);
        }

        connecting = true;
        System.out.println("[ConnectionManager] Setting connecting=true, preparing auth data");

        JsonObject authData = new JsonObject();
        authData.addProperty("secret", serverSecret);
        authData.addProperty("playerId", mc.player != null ? mc.player.getStringUUID() : "unknown");
        authData.addProperty("playerName", mc.player != null ? mc.player.getName().getString() : "unknown");
        authData.addProperty("version", dev.hunchclient.module.impl.LanchoModule.VERSION);
        authData.addProperty("clientType", "lancho");

        // SECURITY: Do not log authentication secrets!
        sendMessage("§6[Lancho] §7Sending auth request...");
        sendMessage("§6[Lancho] §7Player: " + (mc.player != null ? mc.player.getName().getString() : "unknown"));
        // SECURITY: Removed secret logging - never log credentials!

        System.out.println("[ConnectionManager] About to send HTTP request");
        return httpClient.request("POST", "/authenticate", authData)
            .thenApply(response -> {
                System.out.println("[ConnectionManager] HTTP response received");
                System.out.println("[ConnectionManager] response.isSuccess()=" + response.isSuccess());
                System.out.println("[ConnectionManager] response.status=" + response.status);
                System.out.println("[ConnectionManager] response.data=" + response.data);

                connecting = false;

                if (!response.isSuccess() || response.data == null) {
                    System.out.println("[ConnectionManager] Response not successful or data is null");
                    handleConnectionError(response);
                    return false;
                }

                connected = true;
                reconnectAttempts = 0;
                clientId = response.data.has("serverId") ? response.data.get("serverId").getAsString() : null;

                System.out.println("[ConnectionManager] Successfully connected! clientId=" + clientId);
                sendMessage("§6[Lancho] §aConnected! Internet-based AI ready ⚡");
                return true;
            })
            .exceptionally(throwable -> {
                System.out.println("[ConnectionManager] Exception in thenApply: " + throwable.getMessage());
                throwable.printStackTrace();
                connecting = false;
                handleConnectionError(null);
                return false;
            });
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        connected = false;
        connecting = false;
        clientId = null;
        sendMessage("§6[Lancho] §7Disconnected from server");
    }

    /**
     * Handle connection error with reconnection logic
     */
    private void handleConnectionError(HttpClient.HttpResponse response) {
        reconnectAttempts++;

        String errorMsg;
        if (response != null && response.error != null) {
            errorMsg = response.error.getMessage();
        } else if (response != null) {
            errorMsg = "HTTP " + response.status;
            // Add response body if available for debugging
            if (response.data != null && response.data.has("error")) {
                errorMsg += " - " + response.data.get("error").getAsString();
            }
        } else {
            errorMsg = "null response";
        }

        if (reconnectAttempts < maxReconnectAttempts) {
            sendMessage("§6[Lancho] §cConnection failed: " + errorMsg);
            sendMessage("§6[Lancho] §7Retrying in " + (reconnectDelay / 1000) + "s... (" +
                       reconnectAttempts + "/" + maxReconnectAttempts + ")");

            scheduler.schedule(() -> connect(), reconnectDelay, TimeUnit.MILLISECONDS);
        } else {
            sendMessage("§6[Lancho] §cMax reconnection attempts reached.");
            sendMessage("§6[Lancho] §cLast error: " + errorMsg);
            sendMessage("§6[Lancho] §7Use /lancho reconnect to retry.");
        }
    }

    /**
     * Reset reconnection attempts
     */
    public void resetReconnectAttempts() {
        this.reconnectAttempts = 0;
    }

    /**
     * Send chat message to player (thread-safe)
     */
    private void sendMessage(String message) {
        // Execute on main thread to avoid "Rendersystem called from wrong thread" error
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    // =================== GETTERS ===================

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public String getClientId() {
        return clientId;
    }

    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    public void setServerSecret(String serverSecret) {
        this.serverSecret = serverSecret;
    }

    /**
     * Shutdown scheduler to prevent thread leaks.
     * CRITICAL: Always call this when disabling the module!
     */
    public void shutdown() {
        System.out.println("[ConnectionManager] Shutting down scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                System.out.println("[ConnectionManager] Scheduler did not terminate in time, forcing shutdown...");
                scheduler.shutdownNow();
            }
            System.out.println("[ConnectionManager] Scheduler shutdown complete");
        } catch (InterruptedException e) {
            System.err.println("[ConnectionManager] Shutdown interrupted, forcing shutdown...");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
