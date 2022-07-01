package com.fxz.rpc.feign.plus.core.remoting;


import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingConnectException;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingSendRequestException;
import com.fxz.rpc.feign.plus.core.remoting.exception.RemotingTimeoutException;
import com.fxz.rpc.feign.plus.core.remoting.protocol.RemotingCommand;

public interface RemotingClient extends RemotingService {

    RemotingCommand invokeSync(final String addr, final RemotingCommand command, final long timeoutMillis) throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException, RemotingConnectException;

    /**
     * 远程连接服务器
     *
     * @param ip   服务器地址
     * @param port 服务器端口
     */
    void connect(String ip, int port);


    /**
     * 对应的key的通道是否活跃状态
     * @param key
     * @return
     */
    boolean channelActive(String key);
}
