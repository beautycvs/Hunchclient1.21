package dev.hunchclient.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Simple HTTP client for IRC backend communication.
 * Uses relay-token header for authentication (no HMAC signing).
 */
public class SecureApiClient {

    private static final Gson GSON = new Gson();
    private static final String BASE_URL = "https://hunch-irc-backend-v5t8.onrender.com";

    private static final int DEFAULT_CONNECT_TIMEOUT = 4000;
    private static final int DEFAULT_READ_TIMEOUT = 6000;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "SecureApi-Worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Gets the relay token for API authentication.
     */
    private static String getRelayToken() {
    return "hunch-irc121212";
}

    // ==================== IRC RELAY ====================

    /**
     * Sends an IRC message.
     */
    public static CompletableFuture<ApiResponse> ircSend(String user, String message, int utcOffsetMin) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("user", user);
            body.addProperty("message", message);
            body.addProperty("utcOffsetMin", utcOffsetMin);

            return doRequest("POST", "/send", body);
        }, EXECUTOR);
    }

    /**
     * Polls for IRC messages since a timestamp.
     */
    public static CompletableFuture<ApiResponse> ircPoll(long since) {
        return CompletableFuture.supplyAsync(() -> {
            String endpoint = "/poll?since=" + since;
            return doRequest("GET", endpoint, null);
        }, EXECUTOR);
    }

    /**
     * Fetches online IRC users.
     */
    public static CompletableFuture<ApiResponse> ircUsers() {
        return CompletableFuture.supplyAsync(() -> {
            return doRequest("GET", "/users", null);
        }, EXECUTOR);
    }

    /**
     * Sends a direct message.
     */
    public static CompletableFuture<ApiResponse> ircDmSend(String from, String target, String message) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("from", from);
            body.addProperty("target", target);
            body.addProperty("message", message);

            return doRequest("POST", "/dm", body);
        }, EXECUTOR);
    }

    /**
     * Polls for direct messages.
     */
    public static CompletableFuture<ApiResponse> ircDmPoll(String user, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String endpoint = "/dm/poll?since=" + since + "&user=" + user;
            return doRequest("GET", endpoint, null);
        }, EXECUTOR);
    }

    /**
     * Loads conversation history with a user.
     */
    public static CompletableFuture<ApiResponse> ircConversation(String withUser) {
        return CompletableFuture.supplyAsync(() -> {
            String endpoint = "/dm/conversation?user=" + withUser;
            return doRequest("GET", endpoint, null);
        }, EXECUTOR);
    }

    // ==================== SESSION ====================

    /**
     * Request initial session token from server.
     * Synchronous - called during auth flow.
     */
    public static ApiResponse sessionInit(String hwid) {
        JsonObject body = new JsonObject();
        body.addProperty("hwid", hwid);
        return doRequest("POST", "/auth/session", body, 8000, 8000);
    }

    /**
     * Refresh session token via heartbeat.
     * Synchronous - called by background scheduler.
     */
    public static ApiResponse sessionHeartbeat(String hwid, String currentToken) {
        JsonObject body = new JsonObject();
        body.addProperty("hwid", hwid);
        body.addProperty("token", currentToken);
        return doRequest("POST", "/auth/heartbeat", body, 5000, 5000);
    }

    // ==================== DISCORD LINK ====================

    /**
     * Links Discord user to IRC nickname.
     */
    public static CompletableFuture<ApiResponse> linkDiscord(String ircNick, String discordUserId) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("ircNick", ircNick);
            body.addProperty("discordUserId", discordUserId);

            return doRequest("POST", "/link-discord", body);
        }, EXECUTOR);
    }

    // ==================== INTERNAL ====================

    private static ApiResponse doRequest(String method, String endpoint, JsonObject body) {
        return doRequest(method, endpoint, body, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    private static ApiResponse doRequest(String method, String endpoint, JsonObject body,
                                         int connectTimeout, int readTimeout) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(BASE_URL + endpoint).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "HunchClient/2.0");
            conn.setRequestProperty("x-relay-token", getRelayToken());
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            if (body != null && ("POST".equals(method) || "PUT".equals(method))) {
                conn.setDoOutput(true);
                byte[] payload = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 400
                ? conn.getInputStream()
                : conn.getErrorStream();

            String rawBody = readStream(stream);
            JsonElement json = parseJson(rawBody);

            return new ApiResponse(status, json, rawBody, null);
        } catch (Exception e) {
            return new ApiResponse(-1, null, null, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static JsonElement parseJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(raw, JsonElement.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Shuts down the executor service.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * API response wrapper.
     */
    public static class ApiResponse {
        public final int status;
        public final JsonElement json;
        public final String rawBody;
        public final Exception error;

        public ApiResponse(int status, JsonElement json, String rawBody, Exception error) {
            this.status = status;
            this.json = json;
            this.rawBody = rawBody;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null && status >= 200 && status < 300;
        }

        public boolean hasError() {
            return error != null || status < 0;
        }

        public JsonObject getJsonObject() {
            return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
        }

        public String getErrorMessage() {
            JsonObject obj = getJsonObject();
            if (obj != null && obj.has("error")) {
                return obj.get("error").getAsString();
            }
            if (error != null) {
                return error.getMessage();
            }
            return "HTTP " + status;
        }

        public boolean getOk() {
            JsonObject obj = getJsonObject();
            return obj != null && obj.has("ok") && obj.get("ok").getAsBoolean();
        }
    }
}
