package com.fxz.rpc.feign.plus.core.remoting.netty;

import com.alibaba.fastjson.JSON;
import com.fxz.fuled.common.utils.ThreadFactoryNamed;
import com.fxz.fuled.dynamic.threadpool.ThreadPoolRegistry;
import com.fxz.rpc.feign.plus.core.enu.HandleEnum;
import com.fxz.rpc.feign.plus.core.mock.MockHttpServletRequest;
import com.fxz.rpc.feign.plus.core.mock.MockHttpServletResponse;
import com.fxz.rpc.feign.plus.core.proxy.DispatcherServletInherit;
import com.fxz.rpc.feign.plus.core.remoting.RemotingServer;
import com.fxz.rpc.feign.plus.core.remoting.protocol.BaseMessage;
import com.fxz.rpc.feign.plus.core.remoting.protocol.Message2BytesCodec;
import com.fxz.rpc.feign.plus.core.remoting.protocol.RemotingCommand;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyRemotingServer extends AbstractNettyRemoting implements RemotingServer {
    private ServerBootstrap bootstrap;
    private volatile Channel serverChannel;
    private NioEventLoopGroup workGroup;
    private NioEventLoopGroup bossGroup;
    private ThreadPoolExecutor poolExecutor;
    private Timer timer = new Timer("response_future_timer", true);
    @Autowired
    private DispatcherServletInherit dispatcherServlet;

    public NettyRemotingServer(DispatcherServletInherit dispatcherServlet) {
        this.dispatcherServlet = dispatcherServlet;
    }

    @SuppressWarnings("all")
    @Override
    public void start() {
        poolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), ThreadFactoryNamed.named("server_handle_thread"));
        ThreadPoolRegistry.registerThreadPool("serverTaskQueueThreadPool", poolExecutor);
        workGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2, ThreadFactoryNamed.named("netty_server_work_thread_"));
        bossGroup = new NioEventLoopGroup(1, ThreadFactoryNamed.named("netty_server_accept_thread"));
        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new Message2BytesCodec());
                        ch.pipeline().addLast(new RemotingServerHandler());
                        ch.pipeline().addLast(new RemotingCommandHandle());
                    }
                });
        timer.scheduleAtFixedRate(new TimerTask() {
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
    public void shutdown() {
        timer.cancel();
        if (workGroup != null) {
            workGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (poolExecutor != null) {
            poolExecutor.shutdown();
        }
    }

    @Override
    public boolean isActive() {
        if (serverChannel == null || !serverChannel.isActive())
            return false;
        return true;
    }


    @Override
    public void bind(int port) {
        try {
            bootstrap.bind(port).sync().addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    serverChannel = future.channel();
                    log.info("netty server bind port:{} success", port);
                    return;
                }
                log.warn("netty server in port:{} fail,cause:{}", port, future.cause().getMessage());
                serverChannel = null;
            });
        } catch (InterruptedException e) {
            log.warn("netty server start fail,bind {} fail,cause:{}", port, e);
        }
    }

    @Override
    public void processRequestCommand(ChannelHandlerContext ctx, RemotingCommand cmd) {
        try {
            poolExecutor.execute(() -> {
                handleMessage(ctx, cmd);
            });
        } catch (RejectedExecutionException e) {
            log.error("to many remoting connection");
            RemotingCommand rp = new RemotingCommand();
            rp.setType(HandleEnum.RESPONSE_COMMAND.getCode());
            rp.setXid(cmd.getXid());
            rp.setCode(500);
            rp.setError("to many remoting connection ,reject remoting connect");
            ctx.pipeline().writeAndFlush(rp);
        } catch (Throwable throwable) {
            log.error(throwable.toString());
            RemotingCommand rp = new RemotingCommand();
            rp.setType(HandleEnum.RESPONSE_COMMAND.getCode());
            rp.setXid(cmd.getXid());
            rp.setCode(500);
            rp.setError(throwable.toString());
            ctx.pipeline().writeAndFlush(rp);
        }
    }

    /**
     * 构建好HttpServletRequest和HttpServletResponse对象，并调用DispatcherServlet上面的service方法
     *
     * @param ctx
     * @param cmd
     */
    private void handleMessage(ChannelHandlerContext ctx, RemotingCommand cmd) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String xid = cmd.getXid();
        RemotingCommand rp = new RemotingCommand();
        rp.setXid(xid);
        rp.setType(HandleEnum.RESPONSE_COMMAND.getCode());
        transformRemotingCommandToRequest(cmd, request);
        try {
            dispatcherServlet.service(request, response);
            transformResponseToRemotingCommand(response, rp);
        } catch (ServletException e) {
            log.error(e.toString());
            transformThrowableToRemotingCommand(response, rp, 500, e.toString());
        } catch (IOException e) {
            log.error(e.toString());
            transformThrowableToRemotingCommand(response, rp, 500, e.toString());
        } catch (Throwable throwable) {
            log.error(throwable.toString());
            transformThrowableToRemotingCommand(response, rp, 500, throwable.toString());
        }
        ctx.pipeline().writeAndFlush(rp);
    }


    /**
     * 将远程的RemotingCommand对象状态为HttpServletRequest对象
     *
     * @param rq
     * @param request
     */
    private void transformRemotingCommandToRequest(RemotingCommand rq, MockHttpServletRequest request) {
        recodeHeadersToRequest(rq, request);
        String method = rq.getMethod();
        URI uri = URI.create(rq.getUrl());
        request.setMethod(method);
        request.setScheme(uri.getScheme());
        request.setContent(rq.getBody());
        request.setRequestURI(uri.getPath());
        if (!StringUtils.isEmpty(uri.getQuery())) {
            request.setParameters(transformQueryToMap(uri.getQuery()));
        }
    }


    /**
     * 将RemotingCommand中header成员变量设置到HttpServletRequest的请求头中去
     *
     * @param rq
     * @param request
     */
    private void recodeHeadersToRequest(RemotingCommand rq, MockHttpServletRequest request) {
        Map<String, Collection<String>> headers = rq.getHeader();
        if (null != headers) {
            for (String key : headers.keySet()) {
                Collection<String> values = headers.get(key);
                if (values != null) {
                    for (Object value : values) {
                        request.addHeader(key, value);
                    }
                }
            }
        }
    }


    /**
     * 将http中的GET请求中的参数转变为map对象
     *
     * @param query
     * @return
     */
    private Map<String, String> transformQueryToMap(String query) {
        String[] paramStr = query.split("&");
        if (null == paramStr || paramStr.length == 0) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String temp : paramStr) {
            String[] split = temp.split("=");
            result.put(split[0], split[1]);
        }
        return result;
    }

    /**
     * 将HttpServletResponse对象转变为RemotingCommand对象
     *
     * @param response
     * @param rp
     */
    private void transformResponseToRemotingCommand(MockHttpServletResponse response, RemotingCommand rp) {
        rp.setBody(response.getContentAsByteArray());
        rp.setCode(response.getStatus());
        addHeaderToRemotingCommand(response, rp);
    }

    /**
     * 将异常对象Throwable转变为RemotingCommand对象
     *
     * @param response
     * @param rp
     * @param errorCode
     * @param errorMsg
     */
    private void transformThrowableToRemotingCommand(MockHttpServletResponse response, RemotingCommand rp, int errorCode, String errorMsg) {
        rp.setCode(errorCode);
        rp.setError(errorMsg);
        addHeaderToRemotingCommand(response, rp);
    }

    /**
     * 将HttpServletResponse中的请求头添加到RemotingCommand对象中
     *
     * @param response
     * @param rp
     */
    private void addHeaderToRemotingCommand(MockHttpServletResponse response, RemotingCommand rp) {
        Collection<String> headerNames = response.getHeaderNames();
        if (null != headerNames) {
            Map<String, Collection<String>> headers = new HashMap<>();
            for (String headerName : headerNames) {
                headers.put(headerName, response.getHeaders(headerName));
            }
            rp.setHeader(headers);
        }
    }

    @SuppressWarnings("all")
    private class RemotingServerHandler extends SimpleChannelInboundHandler<BaseMessage> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("remoting client connected current server success,remoting address {}", ctx.channel().remoteAddress());
            super.channelActive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.warn("{} broken connect,cause:{}", ctx.channel().remoteAddress(), cause.toString());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, BaseMessage baseMessage) throws Exception {
            RemotingCommand remotingCommand = JSON.parseObject(new String(baseMessage.getBody()), RemotingCommand.class);
            processMessageReceived(ctx, remotingCommand);
        }
    }

}
