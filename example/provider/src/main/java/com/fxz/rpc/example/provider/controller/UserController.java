package com.fxz.rpc.example.provider.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @GetMapping("/findUserById")
    String findUserById() {
//    String findUserById(@RequestParam("id") String id) {
        log.info("request->{}", "id");
        return "userId->" + "id" + "  from provider";
    }
}
