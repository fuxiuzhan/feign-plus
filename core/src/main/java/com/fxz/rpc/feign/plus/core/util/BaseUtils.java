package com.fxz.rpc.feign.plus.core.util;


public class BaseUtils {

    public static String createKeyWithIPAndPort(String ip, int port) {
        return ip + "_" + port;
    }
}
