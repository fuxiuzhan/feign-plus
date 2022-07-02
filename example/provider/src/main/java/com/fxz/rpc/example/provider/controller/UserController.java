package com.fxz.rpc.example.provider.controller;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @PostMapping("/findUserById")
    String findUserById(@RequestParam("ids") List<String> ids) {
        log.info("request->{}", ids);
        return "userId->" + JSON.toJSONString(ids) + "  from provider";
    }
}
