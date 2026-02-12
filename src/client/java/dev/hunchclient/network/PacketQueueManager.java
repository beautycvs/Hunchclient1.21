package dev.hunchclient.network;

import dev.hunchclient.HunchClient;
import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.QueuePacketEvent;
import dev.hunchclient.mixin.accessor.ConnectionAccessor;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central manager for packet queuing.
 * Used by Blink/FakeLag modules to delay outgoing packets.
 *
 * Supports two modes:
 * 1. BLINK mode: Queue all packets until manual flush
 * 2. LAG mode (Clumsy-style): Delay each packet by X ms then auto-send
 *
 * Based on LiquidBounce's PacketQueueManager concept.
 */
public class PacketQueueManager {

    private static final PacketQueueManager INSTANCE = new PacketQueueManager();

    /** Queue of packets waiting to be sent */
    private final List<QueuedPacket> packetQueue = new CopyOnWriteArrayList<>();

    /** Tracked positions from movement packets (for dummy player) */
    private final List<Vec3> positions = new ArrayList<>();

    /** Whether queuing is currently active */
    private volatile boolean queuingEnabled = false;

    /** Lock to prevent recursive packet sending */
    private volatile boolean isFlushing = false;

    // ==================== LAG MODE (Clumsy-style) ====================

    /** Whether lag mode is active (delay then send) */
    private volatile boolean lagModeEnabled = false;

    /** Delay in milliseconds for lag mode */
    private volatile int lagDelayMs = 200;

    private PacketQueueManager() {}

    public static PacketQueueManager getInstance() {
        return INSTANCE;
    }

    /**
     * Called when a packet is about to be sent.
     * Fires QueuePacketEvent and returns the action to take.
     *
     * @param packet The packet being sent
     * @return The action to take (PASS = send normally, QUEUE = store for later)
     */
    public QueuePacketEvent.Action handleOutgoingPacket(Packet<?> packet) {
        // Don't intercept while flushing to prevent infinite loop
        if (isFlushing) {
            return QueuePacketEvent.Action.PASS;
        }

        // Don't queue if queuing is disabled
        if (!queuingEnabled) {
            return QueuePacketEvent.Action.PASS;
        }

        // Fire event to let modules decide
        QueuePacketEvent event = new QueuePacketEvent(packet);
        HunchModClient.EVENT_BUS.post(event);

        if (event.getAction() == QueuePacketEvent.Action.QUEUE) {
            // Track position if it's a movement packet
            if (packet instanceof ServerboundMovePlayerPacket movePacket) {
                trackPosition(movePacket);
            }

            // Add to queue
            packetQueue.add(new QueuedPacket(packet, System.currentTimeMillis()));
            HunchClient.LOGGER.debug("[PacketQueue] Queued packet: {}", packet.getClass().getSimpleName());
        }

        return event.getAction();
    }

    /**
     * Track player position from movement packet.
     */
    private void trackPosition(ServerboundMovePlayerPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double x = packet.hasPosition() ? packet.getX(mc.player.getX()) : mc.player.getX();
        double y = packet.hasPosition() ? packet.getY(mc.player.getY()) : mc.player.getY();
        double z = packet.hasPosition() ? packet.getZ(mc.player.getZ()) : mc.player.getZ();

        positions.add(new Vec3(x, y, z));
    }

    /**
     * Send all queued packets immediately.
     */
    public void flush() {
        if (packetQueue.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            cancel(); // Can't send, just clear
            return;
        }

        isFlushing = true;
        try {
            int count = packetQueue.size();
            for (QueuedPacket qp : packetQueue) {
                connection.send(qp.packet());
            }
            HunchClient.LOGGER.info("[PacketQueue] Flushed {} packets", count);
        } finally {
            packetQueue.clear();
            positions.clear();
            isFlushing = false;
        }
    }

    /**
     * Flush packets up to a certain index (for partial flush).
     *
     * @param upToIndex Exclusive index - flush packets 0 to upToIndex-1
     */
    public void flush(int upToIndex) {
        if (packetQueue.isEmpty() || upToIndex <= 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            cancel();
            return;
        }

        isFlushing = true;
        try {
            int actualCount = Math.min(upToIndex, packetQueue.size());
            List<QueuedPacket> toSend = new ArrayList<>(packetQueue.subList(0, actualCount));

            for (QueuedPacket qp : toSend) {
                connection.send(qp.packet());
            }

            // Remove sent packets
            packetQueue.subList(0, actualCount).clear();

            // Also trim positions
            if (positions.size() >= actualCount) {
                positions.subList(0, actualCount).clear();
            }

            HunchClient.LOGGER.info("[PacketQueue] Partial flush: {} packets", actualCount);
        } finally {
            isFlushing = false;
        }
    }

    /**
     * Discard all queued packets without sending.
     */
    public void cancel() {
        int count = packetQueue.size();
        packetQueue.clear();
        positions.clear();
        if (count > 0) {
            HunchClient.LOGGER.info("[PacketQueue] Cancelled {} packets", count);
        }
    }

    /**
     * Enable packet queuing.
     */
    public void enableQueuing() {
        queuingEnabled = true;
        HunchClient.LOGGER.info("[PacketQueue] Queuing enabled");
    }

    /**
     * Disable packet queuing.
     */
    public void disableQueuing() {
        queuingEnabled = false;
        HunchClient.LOGGER.info("[PacketQueue] Queuing disabled");
    }

    /**
     * Check if queuing is currently enabled.
     */
    public boolean isQueuingEnabled() {
        return queuingEnabled;
    }

    /**
     * Check if currently flushing (to prevent recursion).
     */
    public boolean isFlushing() {
        return isFlushing;
    }

    /**
     * Get the number of queued packets.
     */
    public int getQueueSize() {
        return packetQueue.size();
    }

    /**
     * Get tracked positions (for dummy player rendering).
     */
    public List<Vec3> getPositions() {
        return new ArrayList<>(positions);
    }

    /**
     * Get the first tracked position (where dummy should stand).
     */
    public Vec3 getFirstPosition() {
        return positions.isEmpty() ? null : positions.get(0);
    }

    /**
     * Record of a queued packet with timestamp.
     */
    public record QueuedPacket(Packet<?> packet, long timestamp) {}

    // ==================== LAG MODE METHODS (Clumsy-style) ====================
    // Blocking is now done by LagChannelHandler in the Netty pipeline.
    // This blocks BOTH directions at the I/O thread level - true PopBlink style!

    /**
     * Enable lag mode with specified delay.
     * Injects LagChannelHandler into Netty pipeline for deep blocking.
     *
     * @param delayMs Delay in milliseconds (like Clumsy's Delay setting)
     */
    public void enableLagMode(int delayMs) {
        this.lagDelayMs = delayMs;
        this.lagModeEnabled = true;

        // Inject our handler into the Netty pipeline
        injectLagHandler();

        HunchClient.LOGGER.info("[PacketLag] Lag mode enabled with {}ms delay", delayMs);
    }

    /**
     * Disable lag mode.
     * Removes LagChannelHandler from Netty pipeline.
     */
    public void disableLagMode() {
        this.lagModeEnabled = false;

        // Remove our handler from the Netty pipeline
        removeLagHandler();

        HunchClient.LOGGER.info("[PacketLag] Lag mode disabled");
    }

    /**
     * Inject LagChannelHandler into the Netty pipeline.
     */
    private void injectLagHandler() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).getChannel();

        if (channel == null || !channel.isOpen()) return;

        // Add before packet_handler to intercept at Netty level
        if (channel.pipeline().get(LagChannelHandler.HANDLER_NAME) == null) {
            try {
                channel.pipeline().addBefore("packet_handler", LagChannelHandler.HANDLER_NAME, new LagChannelHandler());
                HunchClient.LOGGER.info("[PacketLag] Injected LagChannelHandler into pipeline");
            } catch (Exception e) {
                HunchClient.LOGGER.error("[PacketLag] Failed to inject handler: {}", e.getMessage());
            }
        }
    }

    /**
     * Remove LagChannelHandler from the Netty pipeline.
     */
    private void removeLagHandler() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).getChannel();

        if (channel == null || !channel.isOpen()) return;

        if (channel.pipeline().get(LagChannelHandler.HANDLER_NAME) != null) {
            try {
                channel.pipeline().remove(LagChannelHandler.HANDLER_NAME);
                HunchClient.LOGGER.info("[PacketLag] Removed LagChannelHandler from pipeline");
            } catch (Exception e) {
                HunchClient.LOGGER.error("[PacketLag] Failed to remove handler: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if lag mode is enabled.
     */
    public boolean isLagModeEnabled() {
        return lagModeEnabled;
    }

    /**
     * Get current lag delay in ms.
     */
    public int getLagDelayMs() {
        return lagDelayMs;
    }

    /**
     * Set lag delay in ms.
     */
    public void setLagDelayMs(int delayMs) {
        this.lagDelayMs = delayMs;
    }

    /**
     * Shutdown (no-op, kept for API compatibility).
     */
    public void shutdown() {
        // No scheduler to shutdown anymore - using blocking approach
    }
}
