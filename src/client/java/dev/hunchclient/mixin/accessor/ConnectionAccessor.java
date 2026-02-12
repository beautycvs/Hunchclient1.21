package dev.hunchclient.mixin.accessor;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for Connection's protected/private fields and methods.
 * Needed for FakeLag/Blink functionality.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {

    @Invoker("channelRead0")
    void invokeChannelRead0(ChannelHandlerContext ctx, Packet<?> packet) throws Exception;

    /**
     * Access the private channel field for Netty pipeline manipulation.
     */
    @Accessor("channel")
    Channel getChannel();
}
