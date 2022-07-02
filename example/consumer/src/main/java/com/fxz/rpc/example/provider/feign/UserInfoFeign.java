package com.fxz.rpc.example.provider.feign;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "rpcProvider")
public interface UserInfoFeign {

    @PostMapping(value = "/user/findUserById")
    String findUserById(@RequestParam("ids") List<String> ids);
}
