package com.fxz.rpc.feign.plus.core.remoting.protocol;

import lombok.Data;

import java.io.Serializable;

/**
 *
 */
@Data
public class BaseMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final byte HEADER1 = 0x0f;
    public static final byte HEADER2 = 0x0f;
    private byte type = 0x00;
    private byte version = 0x00;
    private byte[] body;
    private byte[] checksum = new byte[]{0x30, 0x31};

    public BaseMessage() {
    }

    public BaseMessage(BaseMessage baseMessage) {
        this.setType(baseMessage.getType());
        this.setBody(baseMessage.getBody());
        this.setChecksum(baseMessage.getChecksum());
    }
}
