package com.fxz.rpc.feign.plus.core.remoting.netty;


import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.fxz.rpc.feign.plus.core.constant.FeignRPCConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;

import javax.annotation.PostConstruct;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Order(-Integer.MAX_VALUE)
public class NettyServerController implements CommandLineRunner {
    @Autowired
    private NettyRemotingServer server;
    @Value("${server.port:8080}")
    private int port;

    @Autowired
    NacosDiscoveryProperties nacosDiscoveryProperties;

    private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    int listenPort = 2000;

    @PostConstruct
    public void init() {
        ServerSocket socket = null;
        while (true) {
            try {
                socket = new ServerSocket(listenPort);
                break;
            } catch (Exception e) {
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                    }
                }
            }
            listenPort++;
        }
        nacosDiscoveryProperties.getMetadata().put(FeignRPCConstant.RPC_LISTEN_PORT, listenPort + "");
    }

    @Override
    public void run(String... args) throws Exception {
        initServer();
        startServer();
    }

    private void initServer() {
        server.start();
    }


    //启动
    private void startServer() {
        //判断server是否正常如果不正常则再次绑定端口
        executor.scheduleWithFixedDelay(() -> {
                    if (!server.isActive()) {
                        server.bind(listenPort);
                    }
                }
                , 0, 5, TimeUnit.SECONDS);
    }

}
