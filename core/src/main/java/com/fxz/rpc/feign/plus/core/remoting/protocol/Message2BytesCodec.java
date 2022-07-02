package com.fxz.rpc.feign.plus.core.remoting.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.nio.ByteOrder;
import java.util.List;

/**
 *
 */
public class Message2BytesCodec extends ByteToMessageCodec<BaseMessage> {

    /*
     * |HEADER1|HEADER2|Version|Type|DATA_Length|DATA|(non Javadoc)
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, BaseMessage msg, ByteBuf out) throws Exception {
        out.order(ByteOrder.LITTLE_ENDIAN);
        out.writeByte(BaseMessage.HEADER1);
        out.writeByte(BaseMessage.HEADER2);
        out.writeByte(msg.getVersion());
        out.writeInt(msg.getChecksum().length);
        out.writeBytes(msg.getChecksum());
        out.writeByte(msg.getType());
        out.writeInt(msg.getBody().length);
        out.writeBytes(msg.getBody());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >= (1 + 1 + 1 + 4)) {// |HEADER1|HEADER2|VERSION|CHECKSUMLEN|CHECKSUM|TYPE|DATA_LENGTH|DATA
            in.markReaderIndex();
            byte Header1 = in.readByte();
            byte Header2 = in.readByte();
            if (Header1 == BaseMessage.HEADER1 && Header2 == BaseMessage.HEADER2) {
                BaseMessage baseMessage = new BaseMessage();
                baseMessage.setVersion(in.readByte());
                int checksumlen = in.readInt();
                if (in.readableBytes() > checksumlen + 4) {
                    byte[] checksum = new byte[checksumlen];
                    in.readBytes(checksum);
                    baseMessage.setChecksum(checksum);
                    baseMessage.setType(in.readByte());
                    int bufferlen = in.readInt();
                    if (in.readableBytes() >= bufferlen) {
                        byte[] buffer = new byte[bufferlen];
                        in.readBytes(buffer);
                        baseMessage.setBody(buffer);
                        out.add(baseMessage);
                    } else {
                        in.resetReaderIndex();
                    }
                } else {
                    in.resetReaderIndex();
                }
            } else {
                in.resetReaderIndex();
            }
        }
    }
}
