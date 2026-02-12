package dev.hunchclient.module.impl.lancho;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP Client for Lancho server communication.
 */
public class HttpClient {

    private static final Gson GSON = new Gson();
    private static final Object EXECUTOR_LOCK = new Object();
    private static volatile ExecutorService executor = createExecutor();

    private final String baseUrl;
    private final int timeout;

    public HttpClient(String baseUrl, int timeout) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = timeout;
    }

    /**
     * Make async HTTP request.
     */
    public CompletableFuture<HttpResponse> request(String method, String endpoint, JsonObject data) {
        return request(method, endpoint, data, timeout);
    }

    /**
     * Make async HTTP request with custom timeout.
     */
    public CompletableFuture<HttpResponse> request(String method, String endpoint, JsonObject data, int customTimeout) {
        System.out.println("[HttpClient] request() called with endpoint: " + endpoint);
        ExecutorService requestExecutor = getExecutor();
        System.out.println("[HttpClient] Using executor: " + requestExecutor);
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("[HttpClient] supplyAsync lambda started in thread: " + Thread.currentThread().getName());
            HttpURLConnection connection = null;
            try {
                String fullUrl = baseUrl + endpoint;
                System.out.println("[HttpClient] Creating connection to: " + fullUrl);
                URL url = URI.create(fullUrl).toURL();
                connection = (HttpURLConnection) url.openConnection();
                System.out.println("[HttpClient] Connection created");

                connection.setRequestMethod(method);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "HunchClient-Lancho/" + dev.hunchclient.module.impl.LanchoModule.VERSION);
                connection.setConnectTimeout(customTimeout);
                connection.setReadTimeout(customTimeout);

                if (data != null && ("POST".equals(method) || "PUT".equals(method))) {
                    connection.setDoOutput(true);
                    String jsonPayload = GSON.toJson(data);
                    System.out.println("[HttpClient] Sending to " + endpoint + ": " + jsonPayload);
                    try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                        writer.write(jsonPayload);
                        writer.flush();
                    }
                }

                int responseCode = connection.getResponseCode();
                System.out.println("[HttpClient] Response code: " + responseCode);
                InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

                if (stream == null) {
                    System.out.println("[HttpClient] No response stream");
                    return new HttpResponse(responseCode, null);
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String responseBody = response.toString();
                    System.out.println("[HttpClient] Response body: " + responseBody);

                    try {
                        JsonObject parsed = GSON.fromJson(responseBody, JsonObject.class);
                        return new HttpResponse(responseCode, parsed);
                    } catch (Exception parseError) {
                        System.out.println("[HttpClient] JSON parse error: " + parseError.getMessage());
                        return new HttpResponse(responseCode, null);
                    }
                }
            } catch (Exception e) {
                System.out.println("[HttpClient] Exception: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                return new HttpResponse(-1, null, e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, requestExecutor);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String processed = baseUrl == null ? "" : baseUrl.trim();

        if (processed.startsWith("wss://")) {
            processed = "https://" + processed.substring(6);
        } else if (processed.startsWith("ws://")) {
            processed = "http://" + processed.substring(5);
        }

        // Port 3000 on this backend only serves plain HTTP
        if (processed.startsWith("https://") && processed.contains(":3000")) {
            processed = "http://" + processed.substring("https://".length());
        }

        if (processed.endsWith("/")) {
            processed = processed.substring(0, processed.length() - 1);
        }

        return processed;
    }

    /**
     * Shutdown the executor service to prevent thread leaks.
     * CRITICAL: Call this on client shutdown!
     */
    public static void shutdown() {
        System.out.println("[HttpClient] Shutting down executor service...");
        ExecutorService toShutdown;
        synchronized (EXECUTOR_LOCK) {
            toShutdown = executor;
            executor = null;
        }
        if (toShutdown == null) {
            System.out.println("[HttpClient] Executor already cleared");
            return;
        }
        toShutdown.shutdown();
        try {
            if (!toShutdown.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                System.out.println("[HttpClient] Executor did not terminate in time, forcing shutdown...");
                toShutdown.shutdownNow();
                if (!toShutdown.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.err.println("[HttpClient] Executor did not terminate after forced shutdown");
                }
            }
            System.out.println("[HttpClient] Executor shutdown complete");
        } catch (InterruptedException e) {
            System.err.println("[HttpClient] Shutdown interrupted, forcing shutdown...");
            toShutdown.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "Lancho-Http");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static ExecutorService getExecutor() {
        ExecutorService current = executor;
        if (current == null || current.isShutdown() || current.isTerminated()) {
            synchronized (EXECUTOR_LOCK) {
                if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                    executor = createExecutor();
                }
                current = executor;
            }
        }
        return current;
    }

    /**
     * HTTP response wrapper.
     */
    public static class HttpResponse {
        public final int status;
        public final JsonObject data;
        public final Exception error;

        public HttpResponse(int status, JsonObject data) {
            this(status, data, null);
        }

        public HttpResponse(int status, JsonObject data, Exception error) {
            this.status = status;
            this.data = data;
            this.error = error;
        }

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        public boolean hasError() {
            return error != null || status < 0;
        }
    }
}
