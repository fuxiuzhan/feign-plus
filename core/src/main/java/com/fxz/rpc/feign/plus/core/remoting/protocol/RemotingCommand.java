package com.fxz.rpc.feign.plus.core.remoting.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemotingCommand implements Serializable {
    private Integer type;
    private Integer code;
    private String xid;
    private String remark;
    private Map<String, Collection<String>> header;
    private String url;
    private String method;
    private byte[] body;
    private String error;
}
