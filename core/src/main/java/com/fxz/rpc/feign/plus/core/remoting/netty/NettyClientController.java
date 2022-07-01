package com.fxz.rpc.feign.plus.core.remoting.netty;

import com.fxz.rpc.feign.plus.core.constant.FeignRPCConstant;
import com.fxz.rpc.feign.plus.core.util.BaseUtils;
import com.fxz.rpc.feign.plus.core.util.BeanFactoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyClientController implements CommandLineRunner {
    @Autowired
    private NettyRemotingClient client;
    @Autowired
    private DiscoveryClient discoveryClient;
    @Value("${spring.application.name}")
    private String serviceName;
    @Value("${server.port}")
    private Integer port;
    private static ScheduledExecutorService loopCheckThread = Executors.newSingleThreadScheduledExecutor();


    @Override
    public void run(String... args) throws Exception {
        initNetty();
        initServerList();
        loopCheckAndLoadRemoteConnection();
    }

    private void initNetty() {
        client.start();
    }

    private void loopCheckAndLoadRemoteConnection() {
        loopCheckThread.scheduleWithFixedDelay(() -> {
            //1.获取并创建新的链接
            List<String> services = discoveryClient.getServices();
            List<String> keys = new LinkedList<>();
            if (null != services) {
                for (String service : services) {
                    if (serviceName.equals(service) || !serverCacheSet.contains(service)) {
                        continue;
                    }
                    List<ServiceInstance> instances = discoveryClient.getInstances(service);
                    if (null != instances) {
                        for (ServiceInstance instance : instances) {
                            String ip = instance.getHost();
                            int port = instance.getPort() + FeignRPCConstant.STEP;
                            keys.add(BaseUtils.createKeyWithIPAndPort(ip, port));
                        }
                    }
                }
            }

            //1.创建最近启动的服务
            for (String key : keys) {
                if (!client.channelActive(key)) {
                    String[] ipAndPorts = key.split("_");
                    client.connect(ipAndPorts[0], Integer.parseInt(ipAndPorts[1]));
                }
            }

            //2.踢去掉失效的链接
            client.removeUnActiveChannel();

        }, 0, 5, TimeUnit.SECONDS);
    }


    private Set<String> serverCacheSet = new HashSet<>();

    //获取当前服务项目的需要创建连接的数量
    private void initServerList() {
        //从spring的ioc容器中获取类上面存在FeignClient的注解
        String[] beanNames = BeanFactoryUtils.getBeanFactory().getBeanNamesForAnnotation(FeignClient.class);
        if (beanNames.length == 0)
            return;
        for (String beanName : beanNames) {
            Object target = BeanFactoryUtils.getBeanFactory().getBean(beanName);
            Class<?>[] interfaces = AopUtils.getTargetClass(target).getInterfaces();
            if (interfaces == null || interfaces.length == 0)
                continue;
            for (Class interfaceTemp : interfaces) {
                FeignClient annotation = (FeignClient) interfaceTemp.getAnnotation(FeignClient.class);
                if (annotation == null) {
                    continue;
                }
                String value = annotation.value();
                serverCacheSet.add(value);
            }
        }
    }

}
