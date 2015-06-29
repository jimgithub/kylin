package org.kylin.transport.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.kylin.transport.AbstractClientFactory;
import org.kylin.transport.Client;
import org.kylin.transport.netty.handler.BlockCodecHandler;
import org.kylin.transport.netty.handler.MessageCodecHandler;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Created by jimmey on 15-6-25.
 */
public class NettyClientFactory extends AbstractClientFactory {

    @Override
    protected void createClient(URI uri, final Client.Listener... listeners) {
        Bootstrap bootstrap = newBootstrap();
        int connectTimeout = 4000;
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        bootstrap.channel(NioSocketChannel.class)
                .group(new NioEventLoopGroup())
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new BlockCodecHandler());
                        pipeline.addLast(new MessageCodecHandler());
                        pipeline.addLast(new MessageHandler());
                    }
                });

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
        if (future.awaitUninterruptibly(connectTimeout) && future.isSuccess() && future.channel().isActive()) {
            Channel channel = future.channel();
            final Client client = new NettyClient(uri, channel);
            for (Client.Listener l : listeners) {
                l.onConnected(client);
            }
            channel.closeFuture().addListener(new GenericFutureListener<Future<Void>>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    for (Client.Listener l : listeners) {
                        l.onClosed(client);
                    }
                }
            });
        } else {
            future.cancel(true);
            future.channel().close();
        }
    }

    protected Bootstrap newBootstrap() {
        return new Bootstrap().option(ChannelOption.SO_LINGER, -1)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
    }


}
