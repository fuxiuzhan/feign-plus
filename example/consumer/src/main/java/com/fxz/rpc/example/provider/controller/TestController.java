package com.fxz.rpc.example.provider.controller;

import com.fxz.rpc.example.provider.feign.UserInfoFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class TestController {

    @Autowired
    UserInfoFeign userInfoFeign;

    @GetMapping("/find")
    public String userInfo(String userId) {
        return userInfoFeign.findUserById(userId);
    }
}
