package com.fxz.rpc.feign.plus.core.remoting.netty;

import com.fxz.fuled.dynamic.threadpool.ThreadPoolRegistry;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class NettyClientController implements CommandLineRunner {
    @Autowired
    private NettyRemotingClient client;
    @Autowired
    private DiscoveryClient discoveryClient;
    @Value("${spring.application.name}")
    private String serviceName;

    private static ScheduledThreadPoolExecutor loopCheckThread = new ScheduledThreadPoolExecutor(1);

    @Override
    public void run(String... args) {
        initNetty();
        initServerList();
        loopCheckAndLoadRemoteConnection();
        ThreadPoolRegistry.registerThreadPool("loopCheckThread", loopCheckThread);
    }

    private void initNetty() {
        client.start();
    }

    private void loopCheckAndLoadRemoteConnection() {
        loopCheckThread.scheduleWithFixedDelay(() -> {
            //1.获取并创建新的链接
            List<String> services = serverCacheSet.stream().collect(Collectors.toList());
            List<String> keys = new LinkedList<>();
            if (!CollectionUtils.isEmpty(services)) {
                for (String service : services) {
                    if (serviceName.equals(service)) {
                        continue;
                    }
                    List<ServiceInstance> instances = discoveryClient.getInstances(service);
                    if (null != instances) {
                        for (ServiceInstance instance : instances) {
                            String ip = instance.getHost();
                            int port = Integer.parseInt(instance.getMetadata().get(FeignRPCConstant.RPC_LISTEN_PORT));
                            keys.add(BaseUtils.createKeyWithIPAndPort(service, ip, port));
                        }
                    }
                }
            }

            //1.创建最近启动的服务
            for (String key : keys) {
                String[] ipAndPorts = key.split("_");
                if (!client.channelActive(ipAndPorts[0])) {
                    client.connect(ipAndPorts[0], ipAndPorts[1], Integer.parseInt(ipAndPorts[2]));
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
                if (StringUtils.isEmpty(value)) {
                    value = annotation.name();
                }
                serverCacheSet.add(value);
            }
        }
    }

}
