package com.fxz.rpc.feign.plus.core.remoting.netty;

import com.alibaba.fastjson.JSON;
import com.fxz.fuled.common.utils.ThreadFactoryNamed;
import com.fxz.fuled.dynamic.threadpool.ThreadPoolRegistry;
import com.fxz.rpc.feign.plus.core.enu.HandleEnum;
import com.fxz.rpc.feign.plus.core.remoting.RemotingClient;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingConnectException;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingSendRequestException;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingTimeoutException;
import com.fxz.rpc.feign.plus.core.remoting.protocol.RemotingCommand;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class NettyRemotingClient extends AbstractNettyRemoting implements RemotingClient {
    private Timer timer = new Timer("clientHouseKeepingService", true);
    private ConcurrentHashMap<String, List<Channel>> channelTables = new ConcurrentHashMap<>();
    private Bootstrap bootstrap;
    private NioEventLoopGroup workGroup;
    private ThreadPoolExecutor poolExecutor;

    @Override
    public RemotingCommand invokeSync(String serviceName, RemotingCommand command, long timeoutMillis) throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException, RemotingConnectException {
        Channel channel = getAndCheckChannel(serviceName);
        if (channel == null) {
            throw new RemotingConnectException(serviceName);
        }
        return invokeSyncImpl(channel, command, timeoutMillis);
    }

    @Override
    public boolean channelActive(String key) {
        List<Channel> channels = channelTables.get(key);
        if (!CollectionUtils.isEmpty(channels)) {
            return channels.stream().filter(c -> c.isActive()).findFirst().get() != null;
        }
        return false;
    }

    public void removeUnActiveChannel() {
        for (Map.Entry<String, List<Channel>> entry : channelTables.entrySet()) {
            String key = entry.getKey();
            List<Channel> channels = entry.getValue();
            List<Channel> unActives = channels.stream().filter(c -> !c.isActive()).collect(Collectors.toList());
            channels.removeAll(unActives);
            channelTables.put(key, channels);
        }
    }


    /**
     * 这里个方法在整个生命周期只会被调用一次
     */
    @SuppressWarnings("all")
    @Override
    public void start() {
        poolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), ThreadFactoryNamed.named("client_handle_thread"));
        ThreadPoolRegistry.registerThreadPool("clientTaskQueueThreadPool", poolExecutor);
        workGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2, ThreadFactoryNamed.named("netty_client_work_thread"));
        bootstrap = new Bootstrap();
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000 * 10)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new LineBasedFrameDecoder(1024 * 100));
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new StringDecoder());
                        ch.pipeline().addLast(new StringEncoder());
                        ch.pipeline().addLast(new RemotingClientHandler());
                        ch.pipeline().addLast(new RemotingCommandHandle());
                    }
                });

        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    //扫描结果响应表
                    scanResponseTable();
                } catch (Exception e) {
                    log.error("scanResponseTable exception", e);
                }
            }
        }, 1000 * 3, 1000 * 10);
    }

    @Override
    public void connect(String serviceName, String ip, int port) {
        bootstrap.connect(ip, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                List<Channel> channels = new ArrayList<>();
                if (channelTables.get(serviceName) != null) {
                    channelTables.get(serviceName).add(future.channel());
                } else {
                    channels.add(future.channel());
                    channelTables.put(serviceName, channels);
                }
                log.info("connect  to remoting server success, remoting address {}:{} ", ip, port);
                return;
            }
            log.warn("connect to {}:{} fail,cause:{}", ip, port, future.cause().getMessage());
        });
    }

    @PreDestroy
    @Override
    public void shutdown() {
        if (workGroup != null) {
            workGroup.shutdownGracefully();
        }
        timer.cancel();
        if (!CollectionUtils.isEmpty(channelTables)) {
            for (List<Channel> channels : channelTables.values()) {
                channels.stream().forEach(c -> {
                    c.close();
                });
            }
        }
        channelTables.clear();
    }

    /**
     * 此处可以扩展loadbalancer
     *
     * @param serviceName
     * @return
     */
    private Channel getAndCheckChannel(String serviceName) {
        List<Channel> channels = channelTables.get(serviceName);
        return channels.stream().filter(c -> c.isActive()).findFirst().orElse(null);
    }


    @SuppressWarnings("all")
    private class RemotingClientHandler extends SimpleChannelInboundHandler<String> {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                switch (event.state()) {
                    case ALL_IDLE:
                        RemotingCommand ping = new RemotingCommand();
                        ping.setType(HandleEnum.PING_COMMAND.getCode());
                        ctx.pipeline().writeAndFlush(ping);
                        break;
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error(cause.toString());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            poolExecutor.execute(() -> {
                RemotingCommand remotingCommand = JSON.parseObject(msg, RemotingCommand.class);
                processMessageReceived(ctx, remotingCommand);
            });
        }
    }
}
