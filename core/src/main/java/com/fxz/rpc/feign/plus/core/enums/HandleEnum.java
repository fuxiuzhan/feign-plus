package com.fxz.rpc.feign.plus.core.enums;

public enum HandleEnum {
    REQUEST_COMMAND(1),
    RESPONSE_COMMAND(2),
    PING_COMMAND(0);

    private int code;

    HandleEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }}
