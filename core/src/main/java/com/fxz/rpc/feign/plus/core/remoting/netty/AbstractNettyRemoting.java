package com.fxz.rpc.feign.plus.core.remoting.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.fxz.rpc.feign.plus.core.enu.HandleEnum;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingSendRequestException;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingTimeoutException;
import com.fxz.rpc.feign.plus.core.remoting.protocol.BaseMessage;
import com.fxz.rpc.feign.plus.core.remoting.protocol.RemotingCommand;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public abstract class AbstractNettyRemoting {

    //存放唯一标示(UUID)和结果
    protected final ConcurrentHashMap<String, ResponseFuture> responseTable = new ConcurrentHashMap<>();

    public void processMessageReceived(ChannelHandlerContext ctx, RemotingCommand cmd) {
        if (cmd.getType() == HandleEnum.REQUEST_COMMAND.getCode()) {
            processRequestCommand(ctx, cmd);
        } else if (cmd.getType() == HandleEnum.RESPONSE_COMMAND.getCode()) {
            processResponseCommand(ctx, cmd);
        } else if (cmd.getType() == HandleEnum.PING_COMMAND.getCode()) {
            log.debug("receive ping from remoting client");
        }
    }

    /**
     * 这个方法用于服务端实现
     *
     * @param ctx
     * @param cmd
     */
    public void processRequestCommand(final ChannelHandlerContext ctx, final RemotingCommand cmd) {

    }

    public void processResponseCommand(final ChannelHandlerContext ctx, final RemotingCommand cmd) {
        String xid = cmd.getXid();
        ResponseFuture responseFuture = responseTable.get(xid);
        responseFuture.putResponse(cmd);
    }

    public RemotingCommand invokeSyncImpl(final Channel channel, final RemotingCommand request, final long timeoutMillis) throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException {
        String xid = request.getXid();
        try {
            final SocketAddress addr = channel.remoteAddress();
            ResponseFuture responseFuture = new ResponseFuture(xid, timeoutMillis, null);
            responseTable.put(xid, responseFuture);
            channel.writeAndFlush(request).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    responseFuture.setSendRequestOK(true);
                    return;
                }
                responseFuture.setSendRequestOK(false);
                responseFuture.setCause(future.cause());
                responseFuture.putResponse(null);
            });
            RemotingCommand responseCommand = responseFuture.waitResponse(timeoutMillis);
            if (null == responseCommand) {
                if (responseFuture.isSendRequestOK()) {
                    throw new RemotingTimeoutException(addr.toString(), timeoutMillis,
                            responseFuture.getCause());
                } else {
                    throw new RemotingSendRequestException(addr.toString(), responseFuture.getCause());
                }
            }
            return responseCommand;
        } finally {
            responseTable.remove(xid);
        }

    }


    /**
     * 扫描结果列表，将超时的结果释放
     */
    protected void scanResponseTable() {
        final List<ResponseFuture> rfList = new LinkedList<>();
        Iterator<Map.Entry<String, ResponseFuture>> iterator = this.responseTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ResponseFuture> next = iterator.next();
            ResponseFuture rep = next.getValue();
            if ((rep.getBeginTimestamp() + rep.getTimeoutMillis() + 1000) <= System.currentTimeMillis()) {
                rep.release();
                iterator.remove();
                rfList.add(rep);
            }
        }

        for (ResponseFuture rf : rfList) {
            executeInvokeCallback(rf);
        }
    }


    private void executeInvokeCallback(ResponseFuture rf) {
        rf.executeInvokeCallback();
    }

    protected class RemotingCommandHandle extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            BaseMessage baseMessage = new BaseMessage();
            baseMessage.setBody(JSON.toJSONString(msg).getBytes());
            ctx.writeAndFlush(baseMessage);
        }
    }
}
