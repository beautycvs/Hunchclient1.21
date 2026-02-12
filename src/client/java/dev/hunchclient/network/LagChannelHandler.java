package dev.hunchclient.network;

import dev.hunchclient.HunchClient;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Netty ChannelHandler that blocks the I/O thread while lag mode is enabled.
 *
 * This is injected into the Netty pipeline and blocks BOTH directions
 * because Netty uses a single event loop thread per connection.
 *
 * PopBlink-style: while (key.isDown) { Thread.sleep(50) }
 */
public class LagChannelHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "hunchclient_lag";

    /**
     * Outbound - called when sending packets to server.
     * Block here to delay outgoing packets.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        blockWhileLagEnabled();
        super.write(ctx, msg, promise);
    }

    /**
     * Inbound - called when receiving packets from server.
     * Block here to delay incoming packets.
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        blockWhileLagEnabled();
        super.channelRead(ctx, msg);
    }

    /**
     * PopBlink-style blocking: while (enabled) { sleep(50) }
     */
    private void blockWhileLagEnabled() {
        PacketQueueManager manager = PacketQueueManager.getInstance();

        while (manager.isLagModeEnabled()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
