package com.fxz.rpc.feign.plus.core.remoting;


import com.fxz.rpc.feign.plus.core.remoting.netty.ResponseFuture;

public interface InvokeCallback {
    void operationComplete(ResponseFuture rf);
}
