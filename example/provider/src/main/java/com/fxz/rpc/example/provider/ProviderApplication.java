package com.fxz.rpc.example.provider;

import com.fxz.fuled.service.annotation.EnableFuledBoot;
import com.fxz.rpc.feign.plus.core.reporter.LogReporter;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;

@EnableFuledBoot
@Import(LogReporter.class)
public class ProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }
}
