package com.fxz.rpc.feign.plus.core.remoting.netty;

import com.alibaba.fastjson.JSON;
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
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.util.CollectionUtils;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class NettyRemotingClient extends AbstractNettyRemoting implements RemotingClient {
    private Timer timer = new Timer("clientHouseKeepingService", true);
    //存放唯一标示key（服务名称+"_"+端口号）和channel关系
    private ConcurrentHashMap<String, Channel> channelTables = new ConcurrentHashMap<>();
    private Bootstrap bootstrap;
    private NioEventLoopGroup workGroup;
    private AtomicInteger workThreadIndex = new AtomicInteger(0);
    private AtomicInteger clientHandleThreadIndex = new AtomicInteger(0);
    private ThreadPoolExecutor poolExecutor;

    @Override
    public RemotingCommand invokeSync(String addr, RemotingCommand command, long timeoutMillis) throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException, RemotingConnectException {
        Channel channel = getAndCheckChannel(addr);
        if (channel == null) {
            throw new RemotingConnectException(addr);
        }
        return invokeSyncImpl(channel, command, timeoutMillis);
    }

    @Override
    public boolean channelActive(String key) {
        Channel channel = channelTables.get(key);
        if (channel == null)
            return false;
        if (!channel.isActive())
            return false;
        return true;
    }

    public void removeUnActiveChannel() {
        List<String> keyList = new LinkedList<>();
        for (Map.Entry<String, Channel> entry : channelTables.entrySet()) {
            String key = entry.getKey();
            Channel value = entry.getValue();
            if (!value.isActive()) {
                keyList.add(key);
            }
        }

        for (String key : keyList) {
            channelTables.remove(key);
        }
    }


    /**
     * 这里个方法在整个生命周期只会被调用一次
     */
    @SuppressWarnings("all")
    @Override
    public void start() {
        TaskQueue taskQueue = new TaskQueue(10000);
        poolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2, 10, TimeUnit.SECONDS, taskQueue, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "client_handle_thread_" + clientHandleThreadIndex.getAndIncrement());
            }
        });
        taskQueue.setParent(poolExecutor);
        workGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "netty_client_work_thread_" + workThreadIndex.getAndIncrement());
            }
        });
        bootstrap = new Bootstrap();
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024 * 10)
                .option(ChannelOption.SO_RCVBUF, 1024 * 1024 * 10)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000 * 10)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 8, 0, 8));
                        ch.pipeline().addLast(new StringDecoder());
                        ch.pipeline().addLast(new RemotingClientHandler());
                        ch.pipeline().addLast(new RemotingCommandHandle());
                        ch.pipeline().addFirst(new StringEncoder());
                        ch.pipeline().addFirst(new LengthFieldPrepender(8));
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
    public void connect(String ip, int port) {
        bootstrap.connect(ip, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channelTables.put(ip + "_" + port, future.channel());
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
            for (Channel channel : channelTables.values()) {
                channel.close();
            }
        }
        channelTables.clear();
    }


    private Channel getAndCheckChannel(String addr) {
        Channel channel = channelTables.get(addr);
        if (channel != null && !channel.isActive()) {
            channelTables.remove(addr);
            channel = null;
        }
        return channel;
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
