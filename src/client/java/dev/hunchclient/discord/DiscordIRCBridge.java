package dev.hunchclient.discord;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hunchclient.network.SecureApiClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.UnixDomainSocketAddress;

public class DiscordIRCBridge {
    private static DiscordIRCBridge INSTANCE;

    private String discordUserId = null;
    private String discordUsername = null;
    private String discordDiscriminator = null;
    private String discordGlobalName = null;
    private String arrpcFallbackUserId = null;
    private String arrpcFallbackUsername = null;
    private String arrpcFallbackDiscriminator = null;
    private String arrpcFallbackGlobalName = null;
    private boolean arrpcFallbackActive = false;
    private boolean linked = false;
    private boolean connecting = false;
    private long lastAttemptTime = 0;
    private static final long RETRY_COOLDOWN_MS = 60000; // Only retry once per minute
    private RandomAccessFile pipe;          // Windows named pipe
    private SocketChannel unixSocket;       // Linux/macOS Unix socket
    private Process socatBridge;            // Flatpak socat bridge process
    private java.net.Socket tcpSocketConnection; // Flatpak TCP bridge socket
    private java.io.InputStream tcpInputStream;   // TCP input stream
    private java.io.OutputStream tcpOutputStream; // TCP output stream
    private InputStream activeInputStream;        // Active input stream (Unix/TCP)
    private OutputStream activeOutputStream;      // Active output stream (Unix/TCP)

    private static final Gson GSON = new Gson();
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final boolean IS_FLATPAK = checkFlatpak();

    // Discord Application ID
    private static final long DISCORD_APP_ID = 1277417590754377841L;

    // IPC opcodes
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;

    private DiscordIRCBridge() {
    }

    public static DiscordIRCBridge getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DiscordIRCBridge();
        }
        return INSTANCE;
    }

    public void init(final String ircNick) {
        if (connecting) {
            return;
        }
        if (linked && !arrpcFallbackActive) {
            return;
        }

        resetArrpcCandidate();

        // Rate limit retries to avoid spam when Discord is not running
        long now = System.currentTimeMillis();
        if (now - lastAttemptTime < RETRY_COOLDOWN_MS) {
            return;
        }
        lastAttemptTime = now;

        final String resolvedNick = ircNick != null && !ircNick.isEmpty() ? ircNick : "<unknown>";
        System.out.println("[Discord-IRC] Preparing Discord link for IRC nick: " + resolvedNick);

        connecting = true;
        System.out.println("[Discord-IRC] Starting Discord IPC connection...");

        // Run in separate thread to avoid blocking
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Try to connect to Discord IPC
                    System.out.println("[Discord-IRC] Looking for Discord IPC...");

                    // Try each IPC socket until we find real Discord (not arRPC)
                    boolean foundRealDiscord = false;

                    if (IS_WINDOWS) {
                        // Windows: Use named pipes
                        for (int i = 0; i < 10 && !foundRealDiscord; i++) {
                            try {
                                String pipeName = "\\\\.\\pipe\\discord-ipc-" + i;
                                System.out.println("[Discord-IRC] Trying pipe: " + pipeName);
                                pipe = new RandomAccessFile(pipeName, "rw");
                                System.out.println("[Discord-IRC] Connected to pipe: " + pipeName);

                                // Try handshake and check if it's real Discord
                                if (tryHandshakeAndValidate(resolvedNick)) {
                                    foundRealDiscord = true;
                                } else {
                                    // Close and try next
                                    try { pipe.close(); } catch (Exception e) {}
                                    pipe = null;
                                }
                            } catch (IOException e) {
                                // Pipe doesn't exist, try next one
                            }
                        }
                    } else {
                        // Linux/macOS: Use Unix domain sockets
                        String[] possiblePaths = getUnixSocketPaths();

                        outer:
                        for (String basePath : possiblePaths) {
                            for (int i = 0; i < 10; i++) {
                                try {
                                    Path socketPath = Path.of(basePath, "discord-ipc-" + i);
                                    if (Files.exists(socketPath)) {
                                        System.out.println("[Discord-IRC] Trying socket: " + socketPath);
                                        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
                                        unixSocket = SocketChannel.open(address);
                                        unixSocket.configureBlocking(true);
                                        // Use Channels utility for Unix domain sockets (socket() not supported)
                                        activeInputStream = Channels.newInputStream(unixSocket);
                                        activeOutputStream = Channels.newOutputStream(unixSocket);
                                        System.out.println("[Discord-IRC] Connected to socket: " + socketPath);

                                        // Try handshake and check if it's real Discord
                                        if (tryHandshakeAndValidate(resolvedNick)) {
                                            foundRealDiscord = true;
                                            break outer;
                                        } else {
                                            // Close and try next
                                            closeActiveConnection();
                                        }
                                    }
                                } catch (IOException e) {
                                    // Socket doesn't exist or can't connect, try next one
                                }
                            }
                        }
                    }

                    // If running in Flatpak and not found, try the socat bridge
                    if (!foundRealDiscord && IS_FLATPAK) {
                        System.out.println("[Discord-IRC] Flatpak detected, trying to find Discord on host...");
                        String hostSocket = findHostDiscordSocket();
                        if (hostSocket != null) {
                            String bridgeResult = startFlatpakBridge(hostSocket);
                            if (bridgeResult != null && bridgeResult.startsWith("TCP:")) {
                                int port = Integer.parseInt(bridgeResult.substring(4));
                                try {
                                    java.net.Socket tcpSocket = new java.net.Socket("127.0.0.1", port);
                                    try {
                                        tcpSocket.setSoTimeout(2000);
                                    } catch (Exception ignored) {}
                                    tcpInputStream = tcpSocket.getInputStream();
                                    tcpOutputStream = tcpSocket.getOutputStream();
                                    tcpSocketConnection = tcpSocket;
                                    activeInputStream = tcpInputStream;
                                    activeOutputStream = tcpOutputStream;
                                    System.out.println("[Discord-IRC] Connected via Flatpak TCP bridge on port " + port);

                                    if (tryHandshakeAndValidate(resolvedNick)) {
                                        foundRealDiscord = true;
                                    } else {
                                        closeActiveConnection();
                                    }
                                } catch (IOException e) {
                                    System.err.println("[Discord-IRC] Failed to connect to TCP bridge: " + e.getMessage());
                                }
                            }
                        } else {
                            System.err.println("[Discord-IRC] Could not find Discord socket on host");
                        }
                    }

                    // If no real Discord found, but we saw an arRPC socket with a usable snowflake, fall back to it
                    if (!foundRealDiscord && arrpcFallbackUserId != null) {
                        discordUserId = arrpcFallbackUserId;
                        discordUsername = arrpcFallbackUsername;
                        discordDiscriminator = arrpcFallbackDiscriminator;
                        discordGlobalName = arrpcFallbackGlobalName;
                        arrpcFallbackActive = true;
                        linked = true;
                        foundRealDiscord = true;
                        System.out.println("[Discord-IRC] Falling back to arRPC socket (no native Discord found).");
                        linkDiscordToIRC(resolvedNick);
                    }

                    if (!foundRealDiscord) {
                        System.err.println("[Discord-IRC] Could not find real Discord IPC!");
                        System.err.println("[Discord-IRC] Make sure Discord (not arRPC) is running");
                        if (IS_FLATPAK) {
                            System.err.println("[Discord-IRC] Note: Running in Flatpak - ensure 'socat' is installed on host");
                        }
                        connecting = false;
                        return;
                    }

                    // If we get here, we found real Discord and user data is already set
                    connecting = false;

                } catch (Exception e) {
                    System.err.println("[Discord-IRC] Exception during connect:");
                    e.printStackTrace();
                    connecting = false;
                } finally {
                    if (!linked) {
                        closeActiveConnection();
                    }
                }
            }
        }, "Discord-IPC-Thread").start();
    }

    /**
     * Get possible Unix socket paths for Discord IPC on Linux/macOS
     */
    private static String[] getUnixSocketPaths() {
        String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
        String tmpDir = System.getenv("TMPDIR");
        String temp = System.getenv("TEMP");
        String tmp = System.getenv("TMP");

        java.util.List<String> paths = new java.util.ArrayList<>();

        // Primary: XDG_RUNTIME_DIR (most common on Linux)
        if (xdgRuntime != null && !xdgRuntime.isEmpty()) {
            paths.add(xdgRuntime);
            // Flatpak Discord
            paths.add(xdgRuntime + "/app/com.discordapp.Discord");
            paths.add(xdgRuntime + "/app/com.discordapp.DiscordCanary");
            paths.add(xdgRuntime + "/app/com.discordapp.DiscordPTB");
            // Vesktop / Vencord desktop builds
            paths.add(xdgRuntime + "/app/dev.vencord.Vesktop");
            paths.add(xdgRuntime + "/app/io.github.vencord.Vesktop");
            paths.add(xdgRuntime + "/app/app.vencord.Vesktop");
            // Legcord / WebCord variants
            paths.add(xdgRuntime + "/app/net.legcord.Legcord");
            paths.add(xdgRuntime + "/app/io.github.spacingbat3.webcord");
            // Snap Discord
            paths.add(xdgRuntime + "/snap.discord");
            paths.add(xdgRuntime + "/snap.discord-canary");
            paths.add(xdgRuntime + "/snap.discord-ptb");
        }

        // Explicit /run/user/{uid} paths (in case XDG_RUNTIME_DIR is not set in Java process)
        String uid = getLinuxUid();
        if (uid != null) {
            paths.add("/run/user/" + uid);
            paths.add("/run/user/" + uid + "/app/com.discordapp.Discord");
            paths.add("/run/user/" + uid + "/app/com.discordapp.DiscordCanary");
            paths.add("/run/user/" + uid + "/app/com.discordapp.DiscordPTB");
            paths.add("/run/user/" + uid + "/app/dev.vencord.Vesktop");
            paths.add("/run/user/" + uid + "/app/io.github.vencord.Vesktop");
            paths.add("/run/user/" + uid + "/app/app.vencord.Vesktop");
            paths.add("/run/user/" + uid + "/app/net.legcord.Legcord");
            paths.add("/run/user/" + uid + "/app/io.github.spacingbat3.webcord");
            paths.add("/run/user/" + uid + "/snap.discord");
            paths.add("/run/user/" + uid + "/snap.discord-canary");
            paths.add("/run/user/" + uid + "/snap.discord-ptb");
        }

        // Fallback paths
        if (tmpDir != null && !tmpDir.isEmpty()) paths.add(tmpDir);
        if (temp != null && !temp.isEmpty()) paths.add(temp);
        if (tmp != null && !tmp.isEmpty()) paths.add(tmp);
        paths.add("/tmp");
        paths.add("/var/tmp");

        // macOS specific
        if (IS_MAC) {
            String home = System.getProperty("user.home");
            if (home != null) {
                paths.add(home + "/Library/Application Support/discord");
                paths.add(home + "/Library/Application Support/discordcanary");
                paths.add(home + "/Library/Application Support/discordptb");
                paths.add(home + "/Library/Application Support/Vesktop");
                paths.add(home + "/Library/Application Support/Legcord");
                paths.add(home + "/Library/Application Support/WebCord");
            }
        }

        return paths.toArray(new String[0]);
    }

    /**
     * Get current user's UID on Linux
     */
    private static String getLinuxUid() {
        try {
            String[] cmd = IS_FLATPAK
                ? new String[]{"flatpak-spawn", "--host", "id", "-u"}
                : new String[]{"id", "-u"};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Check if running inside a Flatpak sandbox
     */
    private static boolean checkFlatpak() {
        String flatpakId = System.getenv("FLATPAK_ID");
        if (flatpakId != null && !flatpakId.isEmpty()) {
            System.out.println("[Discord-IRC] Running in Flatpak: " + flatpakId);
            return true;
        }
        if (Files.exists(Path.of("/.flatpak-info"))) {
            System.out.println("[Discord-IRC] Running in Flatpak (detected via /.flatpak-info)");
            return true;
        }
        return false;
    }

    /**
     * Find Discord IPC socket on the host system (for Flatpak)
     */
    private static String findHostDiscordSocket() {
        try {
            // Get UID on host
            ProcessBuilder uidPb = new ProcessBuilder("flatpak-spawn", "--host", "id", "-u");
            Process uidP = uidPb.start();
            String uid = null;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(uidP.getInputStream()))) {
                uid = reader.readLine();
            }
            uidP.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);

            if (uid == null || uid.isEmpty()) {
                System.err.println("[Discord-IRC] Could not get host UID");
                return null;
            }
            uid = uid.trim();

            // Check for Discord socket on host
            String[] basePaths = {
                "/run/user/" + uid,
                "/run/user/" + uid + "/app/com.discordapp.Discord",
                "/run/user/" + uid + "/app/com.discordapp.DiscordCanary",
                "/run/user/" + uid + "/app/com.discordapp.DiscordPTB",
                "/run/user/" + uid + "/app/dev.vencord.Vesktop",
                "/run/user/" + uid + "/app/io.github.vencord.Vesktop",
                "/run/user/" + uid + "/app/app.vencord.Vesktop",
                "/run/user/" + uid + "/app/net.legcord.Legcord",
                "/run/user/" + uid + "/app/io.github.spacingbat3.webcord",
                "/run/user/" + uid + "/snap.discord",
                "/run/user/" + uid + "/snap.discord-canary",
                "/run/user/" + uid + "/snap.discord-ptb",
                "/tmp"
            };

            for (String basePath : basePaths) {
                    for (int i = 0; i < 10; i++) {
                        String socketPath = basePath + "/discord-ipc-" + i;
                        ProcessBuilder checkPb = new ProcessBuilder(
                            "flatpak-spawn", "--host", "test", "-S", socketPath
                        );
                    Process checkP = checkPb.start();
                    boolean finished = checkP.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (finished && checkP.exitValue() == 0) {
                        System.out.println("[Discord-IRC] Found Discord socket on host: " + socketPath);
                        return socketPath;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Discord-IRC] Error finding host Discord socket: " + e.getMessage());
        }
        return null;
    }

    /**
     * Start a socat bridge to relay Discord IPC from host to Flatpak
     * Returns the local socket path if successful, null otherwise
     */
    private String startFlatpakBridge(String hostSocketPath) {
        try {
            // Create a local socket path inside the Flatpak sandbox
            String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
            String localSocketPath;
            if (xdgRuntime != null && !xdgRuntime.isEmpty()) {
                localSocketPath = xdgRuntime + "/discord-ipc-bridge";
            } else {
                localSocketPath = "/tmp/discord-ipc-bridge";
            }

            // Remove existing socket if any
            Files.deleteIfExists(Path.of(localSocketPath));

            // Start socat on host to bridge the connection
            // socat creates a listening socket that forwards to Discord's socket
            // We use flatpak-spawn --host to run socat on the host, but connect from inside Flatpak

            // Actually, we need a different approach:
            // Use socat INSIDE the flatpak to connect to host via flatpak-spawn
            // This is tricky because socat needs to be available

            // Alternative: Use TCP as bridge
            // Host: socat TCP-LISTEN:PORT,fork UNIX-CONNECT:discord-socket
            // Flatpak: connect to localhost:PORT

            // Find a free port
            int port = 16372; // Random high port for Discord IPC bridge

            // Kill any existing bridge on this port
            try {
                new ProcessBuilder("flatpak-spawn", "--host", "pkill", "-f", "discord-ipc-bridge-" + port).start().waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}

            // Start TCP bridge on host
            ProcessBuilder bridgePb = new ProcessBuilder(
                "flatpak-spawn", "--host", "/bin/sh", "-c",
                String.format("exec socat -d TCP-LISTEN:%d,fork,reuseaddr UNIX-CONNECT:%s 2>/dev/null & echo discord-ipc-bridge-%d",
                    port, hostSocketPath, port)
            );
            socatBridge = bridgePb.start();

            // Wait a moment for socat to start
            Thread.sleep(200);

            // Check if socat is running
            ProcessBuilder checkPb = new ProcessBuilder(
                "flatpak-spawn", "--host", "pgrep", "-f", "discord-ipc-bridge-" + port
            );
            Process checkP = checkPb.start();
            boolean running = checkP.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && checkP.exitValue() == 0;

            if (running) {
                System.out.println("[Discord-IRC] Started socat bridge on port " + port);
                return "TCP:" + port; // Special marker for TCP connection
            } else {
                System.err.println("[Discord-IRC] Failed to start socat bridge - is socat installed on host?");
                System.err.println("[Discord-IRC] Install with: sudo dnf install socat");
            }
        } catch (Exception e) {
            System.err.println("[Discord-IRC] Error starting Flatpak bridge: " + e.getMessage());
        }
        return null;
    }

    /**
     * Try handshake with current connection and validate it's real Discord (not arRPC)
     * Returns true if successful, false if arRPC or invalid
     */
    private boolean tryHandshakeAndValidate(String ircNick) {
        try {
            // Send HANDSHAKE
            System.out.println("[Discord-IRC] Sending HANDSHAKE...");
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", String.valueOf(DISCORD_APP_ID));
            sendPacket(OP_HANDSHAKE, handshake);

            // Read READY response
            System.out.println("[Discord-IRC] Waiting for READY...");
            JsonObject ready = readPacket();
            if (ready == null) {
                System.err.println("[Discord-IRC] Received null/invalid READY payload");
                return false;
            }
            System.out.println("[Discord-IRC] READY response: " + ready.toString());

            // Extract user data
            if (ready.has("data")) {
                JsonObject data = ready.getAsJsonObject("data");
                if (data != null && data.has("user")) {
                    JsonObject user = data.getAsJsonObject("user");
                    if (user != null && user.has("id")) {
                        String rawUsername = user.has("username") ? user.get("username").getAsString() : "Unknown";
                        String userId = user.get("id").getAsString();

                        // Reject obvious bogus data from browser bridges
                        boolean arrpcLike = rawUsername != null && rawUsername.toLowerCase().contains("arrpc");
                        boolean hasArrpcConfig = dataContainsArrpcHint(data);
                        boolean validSnowflake = isLikelyDiscordSnowflake(userId);
                        if (arrpcLike || hasArrpcConfig) {
                            if (validSnowflake) {
                                rememberArrpcFallback(userId, rawUsername, user);
                                System.out.println("[Discord-IRC] arRPC detected; continuing to search for real Discord socket...");
                            } else {
                                System.out.println("[Discord-IRC] Detected arRPC-like IPC with invalid user id, trying next...");
                            }
                            return false;
                        }
                        if (!validSnowflake) {
                            System.out.println("[Discord-IRC] IPC returned non-Discord user id '" + userId + "', trying next socket...");
                            return false;
                        }

                        // Real Discord found!
                        discordUserId = userId;
                        discordUsername = rawUsername;
                        discordDiscriminator = user.has("discriminator") ? user.get("discriminator").getAsString() : "0";
                        discordGlobalName = user.has("global_name") && !user.get("global_name").isJsonNull()
                            ? user.get("global_name").getAsString() : null;
                        arrpcFallbackActive = false;

                        System.out.println("[Discord-IRC] Found real Discord! User: " + discordUsername + "#" + discordDiscriminator);
                        System.out.println("[Discord-IRC] Discord User ID: " + discordUserId);

                        // Mark as linked
                        linked = true;

                        // Send mapping to IRC Relay
                        linkDiscordToIRC(ircNick);
                        return true;
                    }
                }
            }

            System.err.println("[Discord-IRC] No user data in READY response");
            return false;
        } catch (Exception e) {
            System.err.println("[Discord-IRC] Handshake failed: " + e.getMessage());
            return false;
        }
    }

    private void sendPacket(int opcode, JsonObject data) throws IOException {
        byte[] jsonBytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(8 + jsonBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(jsonBytes.length);
        buffer.put(jsonBytes);
        buffer.flip();

        if (IS_WINDOWS) {
            pipe.write(buffer.array());
            return;
        }

        OutputStream out = activeOutputStream;
        if (out != null) {
            out.write(buffer.array());
            out.flush();
            return;
        }

        if (unixSocket != null) {
            while (buffer.hasRemaining()) {
                unixSocket.write(buffer);
            }
            return;
        }

        throw new IOException("No Discord IPC connection available");
    }

    private JsonObject readPacket() throws IOException {
        ByteBuffer headerBuf = ByteBuffer.allocate(8);
        headerBuf.order(ByteOrder.LITTLE_ENDIAN);

        if (IS_WINDOWS) {
            byte[] header = new byte[8];
            pipe.readFully(header);
            headerBuf.put(header);
            headerBuf.flip();
        } else {
            byte[] header = readFully(8);
            headerBuf.put(header);
            headerBuf.flip();
        }

        int opcode = headerBuf.getInt();
        int length = headerBuf.getInt();

        System.out.println("[Discord-IRC] Read packet: opcode=" + opcode + ", length=" + length);

        byte[] dataBytes;
        if (IS_WINDOWS) {
            dataBytes = new byte[length];
            pipe.readFully(dataBytes);
        } else {
            dataBytes = readFully(length);
        }

        String jsonString = new String(dataBytes, 0, length, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(jsonString);
        if (parsed == null || !parsed.isJsonObject()) {
            System.err.println("[Discord-IRC] Unexpected IPC payload: " + jsonString);
            return null;
        }
        return parsed.getAsJsonObject();
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int read = 0;
        InputStream in = activeInputStream;

        if (in != null) {
            while (read < length) {
                int r = in.read(buffer, read, length - read);
                if (r == -1) {
                    throw new IOException("Connection closed");
                }
                read += r;
            }
            return buffer;
        }

        if (unixSocket != null) {
            ByteBuffer dataBuf = ByteBuffer.allocate(length);
            while (dataBuf.hasRemaining()) {
                if (unixSocket.read(dataBuf) == -1) {
                    throw new IOException("Connection closed");
                }
            }
            return dataBuf.array();
        }

        throw new IOException("No Discord IPC input available");
    }

    private boolean dataContainsArrpcHint(JsonObject data) {
        if (data == null) return false;
        try {
            if (data.has("config")) {
                String cfg = data.get("config").toString().toLowerCase();
                return cfg.contains("arrpc");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isLikelyDiscordSnowflake(String id) {
        if (id == null) return false;
        int len = id.length();
        if (len < 17 || len > 20) return false;
        for (int i = 0; i < len; i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private void closeActiveConnection() {
        try {
            if (activeInputStream != null) activeInputStream.close();
        } catch (Exception ignored) {}
        try {
            if (activeOutputStream != null) activeOutputStream.close();
        } catch (Exception ignored) {}
        try {
            if (tcpInputStream != null) tcpInputStream.close();
        } catch (Exception ignored) {}
        try {
            if (tcpOutputStream != null) tcpOutputStream.close();
        } catch (Exception ignored) {}
        try {
            if (tcpSocketConnection != null) tcpSocketConnection.close();
        } catch (Exception ignored) {}
        try {
            if (unixSocket != null) unixSocket.close();
        } catch (Exception ignored) {}
        try {
            if (pipe != null) pipe.close();
        } catch (Exception ignored) {}
        try {
            if (socatBridge != null) socatBridge.destroy();
        } catch (Exception ignored) {}

        activeInputStream = null;
        activeOutputStream = null;
        tcpInputStream = null;
        tcpOutputStream = null;
        tcpSocketConnection = null;
        unixSocket = null;
        pipe = null;
        socatBridge = null;
    }

    private void resetArrpcCandidate() {
        arrpcFallbackUserId = null;
        arrpcFallbackUsername = null;
        arrpcFallbackDiscriminator = null;
        arrpcFallbackGlobalName = null;
    }

    private void rememberArrpcFallback(String userId, String username, JsonObject user) {
        arrpcFallbackUserId = userId;
        arrpcFallbackUsername = username;
        arrpcFallbackDiscriminator = user.has("discriminator") ? user.get("discriminator").getAsString() : "0";
        arrpcFallbackGlobalName = user.has("global_name") && !user.get("global_name").isJsonNull()
            ? user.get("global_name").getAsString() : null;
    }


    private void linkDiscordToIRC(final String ircNick) {
        if (discordUserId == null) {
            System.err.println("[Discord-IRC] No Discord User ID available");
            connecting = false;
            return;
        }

        System.out.println("[Discord-IRC] Linking IRC nick '" + ircNick + "' to Discord user " + discordUserId);

        SecureApiClient.linkDiscord(ircNick, discordUserId)
            .thenAccept(result -> {
                if (result.isSuccess() && result.getOk()) {
                    linked = true;
                    System.out.println("[Discord-IRC] Successfully linked!");
                    System.out.println("[Discord-IRC] Your IRC messages will now show your Discord avatar");
                } else {
                    System.err.println("[Discord-IRC] Failed to link: " + result.getErrorMessage());
                }
                connecting = false;
            })
            .exceptionally(throwable -> {
                System.err.println("[Discord-IRC] Error linking to relay: " + throwable.getMessage());
                connecting = false;
                return null;
            });
    }

    public boolean isLinked() {
        return linked;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public String getDiscordUsername() {
        return discordUsername;
    }

    public String getDiscordDiscriminator() {
        return discordDiscriminator;
    }

    public String getDiscordGlobalName() {
        return discordGlobalName;
    }

    /**
     * Get the Discord tag (username#discriminator or just username for new Discord)
     */
    public String getDiscordTag() {
        if (discordUsername == null) return null;
        if (discordDiscriminator == null || discordDiscriminator.equals("0")) {
            return discordUsername;
        }
        return discordUsername + "#" + discordDiscriminator;
    }

    /**
     * Get display name (global_name if available, otherwise tag)
     */
    public String getDiscordDisplayName() {
        if (discordGlobalName != null && !discordGlobalName.isEmpty()) {
            return discordGlobalName;
        }
        return getDiscordTag();
    }

    public void shutdown() {
        closeActiveConnection();
        discordUserId = null;
        discordUsername = null;
        discordDiscriminator = null;
        discordGlobalName = null;
        arrpcFallbackActive = false;
        resetArrpcCandidate();
        linked = false;
        connecting = false;
    }
}
