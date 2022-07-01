package com.fxz.rpc.feign.plus.core.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Slf4j
public class DispatcherServletInherit extends DispatcherServlet {


    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
    }

}
