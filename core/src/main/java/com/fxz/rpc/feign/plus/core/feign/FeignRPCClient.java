package com.fxz.rpc.feign.plus.core.feign;

import com.fxz.rpc.feign.plus.core.constant.FeignRPCConstant;
import com.fxz.rpc.feign.plus.core.enums.HandleEnum;
import com.fxz.rpc.feign.plus.core.remoting.RemotingClient;
import com.fxz.rpc.feign.plus.core.remoting.protocol.RemotingCommand;
import feign.Client;
import feign.Request;
import feign.Response;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;

public class FeignRPCClient implements Client {

    public static final String UNDERLINE_SYMBOL = "_";
    public static final int REMOTING_RPC_TIMEOUT = 1000 * 60 * 5;
    private static Logger logger = LoggerFactory.getLogger(FeignRPCClient.class);

    private RemotingClient remotingClient;

    public FeignRPCClient(RemotingClient client) {
        this.remotingClient = client;
    }

    @SneakyThrows
    @Override
    public Response execute(Request request, Request.Options options) {
        String url = request.url();
        String uuid = uuidKey();
        RemotingCommand requestCommand = transformRequestToRemotingCommand(request, url, uuid);
        RemotingCommand remotingCommand = null;
        remotingCommand = remotingClient.invokeSync(request.requestTemplate().feignTarget().name(), requestCommand, REMOTING_RPC_TIMEOUT);
        return transformRemotingCommandToResponse(request, remotingCommand);
    }

    /**
     * 将Request对象转变为RemotingCommand对象
     *
     * @param request
     * @param url
     * @param uuid
     * @return
     */
    private RemotingCommand transformRequestToRemotingCommand(Request request, String url, String uuid) {
        RemotingCommand requestCommand = new RemotingCommand();
        requestCommand.setXid(uuid);
        requestCommand.setBody(request.body());
        requestCommand.setMethod(request.method());
        requestCommand.setUrl(url);
        requestCommand.setHeader(request.headers());
        requestCommand.setType(HandleEnum.REQUEST_COMMAND.getCode());
        return requestCommand;
    }


    /**
     * 根据uri构建唯一key
     *
     * @param uri
     * @return
     */
    private String createKey(URI uri) {
        int port = uri.getPort() + FeignRPCConstant.STEP;
        return uri.getHost() + UNDERLINE_SYMBOL + port;
    }


    /**
     * 构建唯一ID，用于维持当前调用链
     *
     * @return
     */
    private String uuidKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * 将RemotingCommand转变为Response对象
     *
     * @param response
     * @return
     */
    private Response transformRemotingCommandToResponse(Request request, RemotingCommand response) {
        if (null == response) {
            logger.error("not accept response result");
            throw new RuntimeException("not accept response result");
        }
        if (response.getCode() >= 500 && response.getCode() < 600) {
            logger.error("remote invoke error:{}", response.getRemark());
            throw new RuntimeException(response.getError());
        }
        Response.Builder builder = Response.builder().request(request).status(response.getCode()).body(response.getBody());
        builder.headers(response.getHeader());
        return builder.build();
    }
}
