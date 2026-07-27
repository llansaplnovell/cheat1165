package com.example.examplemod.client.hook;

import com.example.examplemod.client.api.event.EventBus;
import com.example.examplemod.client.event.PacketReceiveEvent;
import com.example.examplemod.client.event.PacketSendEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;

/**
 * Splices a Netty handler into the play connection's pipeline so every packet
 * can be observed (and optionally dropped/rewritten) through
 * {@link PacketSendEvent}/{@link PacketReceiveEvent}. Forge does not expose a
 * generic packet event on its own bus, so this goes one layer lower, using
 * the connection's own {@link NetworkManager#channel()}.
 */
public final class NetworkHook {

    private static final String HANDLER_NAME = "examplemod_client_hook";

    public void install(NetworkManager networkManager) {
        Channel channel = networkManager.channel();
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            return;
        }
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof IPacket) {
                    PacketReceiveEvent event = EventBus.getInstance().post(new PacketReceiveEvent((IPacket<?>) msg));
                    if (event.isCancelled()) {
                        return;
                    }
                }
                super.channelRead(ctx, msg);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof IPacket) {
                    PacketSendEvent event = EventBus.getInstance().post(new PacketSendEvent((IPacket<?>) msg));
                    if (event.isCancelled()) {
                        promise.setSuccess();
                        return;
                    }
                    msg = event.getPacket();
                }
                super.write(ctx, msg, promise);
            }
        });
    }
}
