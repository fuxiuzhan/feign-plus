package com.fxz.rpc.example.provider;

import com.fxz.fuled.service.annotation.EnableFuledBoot;
import com.fxz.rpc.feign.plus.core.reporter.LogReporter;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@EnableFuledBoot
@EnableFeignClients
@Import(LogReporter.class)
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
