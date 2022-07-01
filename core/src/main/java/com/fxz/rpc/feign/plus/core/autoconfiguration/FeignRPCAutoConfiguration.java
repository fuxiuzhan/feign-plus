package com.fxz.rpc.feign.plus.core.autoconfiguration;

import com.fxz.rpc.feign.plus.core.feign.FeignRPCClient;
import com.fxz.rpc.feign.plus.core.proxy.DispatcherServletInherit;
import com.fxz.rpc.feign.plus.core.remoting.RemotingClient;
import com.fxz.rpc.feign.plus.core.remoting.RemotingServer;
import com.fxz.rpc.feign.plus.core.remoting.netty.NettyClientController;
import com.fxz.rpc.feign.plus.core.remoting.netty.NettyRemotingClient;
import com.fxz.rpc.feign.plus.core.remoting.netty.NettyRemotingServer;
import com.fxz.rpc.feign.plus.core.remoting.netty.NettyServerController;
import com.fxz.rpc.feign.plus.core.util.ApplicationUtils;
import com.fxz.rpc.feign.plus.core.util.BeanFactoryUtils;
import com.netflix.loadbalancer.ILoadBalancer;
import feign.Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(FeignRPCClient.class)
@ConditionalOnProperty(value = "feign.rpc.enable", matchIfMissing = true)
public class FeignRPCAutoConfiguration {


    @Bean
    @ConditionalOnClass(ILoadBalancer.class)
    public Client feignClient(CachingSpringLoadBalancerFactory cachingFactory,
                              SpringClientFactory clientFactory, RemotingClient client) {
        FeignRPCClient feignRPCClient = new FeignRPCClient(client);
        return new LoadBalancerFeignClient(feignRPCClient, cachingFactory, clientFactory);
    }

    @Bean
    @ConditionalOnMissingClass("com.netflix.loadbalancer.ILoadBalancer")
    public Client feignClient(RemotingClient client) {
        //这里使用我们创建的netty客户端
        return new FeignRPCClient(client);
    }

    @Bean
    public RemotingClient remotingClient() {
        return new NettyRemotingClient();
    }

    @Bean
    public RemotingServer remotingServer(DispatcherServletInherit dispatcherServlet) {
        return new NettyRemotingServer(dispatcherServlet);
    }

    @Bean
    public NettyClientController clientController() {
        return new NettyClientController();
    }


    @Bean
    public NettyServerController serverController() {
        return new NettyServerController();
    }


    @Bean
    public BeanFactoryUtils beanFactoryUtils() {
        return new BeanFactoryUtils();
    }

    @Bean
    public ApplicationUtils applicationUtils() {
        return new ApplicationUtils();
    }

}
