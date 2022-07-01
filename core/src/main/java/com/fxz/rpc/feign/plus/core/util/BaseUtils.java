package com.fxz.rpc.feign.plus.core.util;


public class BaseUtils {

    public static String createKeyWithIPAndPort(String serviceName, String ip, int port) {
        return serviceName + "_" + ip + "_" + port;
    }
}
