package com.fxz.rpc.example.provider;

import com.fxz.fuled.service.annotation.EnableFuledBoot;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFuledBoot
@EnableFeignClients
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
