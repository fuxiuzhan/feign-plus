package com.fxz.rpc.example.provider.feign;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "rpcProvider")
public interface UserInfoFeign {

    @GetMapping("/user/findUserById")
    String findUserById(@RequestParam("id") String id);
}
