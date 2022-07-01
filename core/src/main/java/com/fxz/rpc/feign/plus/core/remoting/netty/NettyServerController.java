package com.fxz.rpc.feign.plus.core.remoting.netty;


import com.fxz.rpc.feign.plus.core.constant.FeignRPCConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Order(-Integer.MAX_VALUE)
public class NettyServerController implements CommandLineRunner {
    @Autowired
    private NettyRemotingServer server;
    @Value("${server.port:8080}")
    private int port;

    private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

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
                        server.bind(port + FeignRPCConstant.STEP);
                    }
                }
                , 0, 5, TimeUnit.SECONDS);
    }

}
