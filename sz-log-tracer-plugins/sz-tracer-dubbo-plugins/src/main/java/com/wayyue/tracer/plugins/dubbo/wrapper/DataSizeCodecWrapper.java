package com.wayyue.tracer.plugins.dubbo.wrapper;


import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.Codec2;
import com.alibaba.dubbo.remoting.buffer.ChannelBuffer;
import com.alibaba.dubbo.remoting.exchange.Request;
import com.alibaba.dubbo.remoting.exchange.Response;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.wayyue.tracer.plugins.dubbo.constants.AttachmentKeyConstants;

import java.io.IOException;

public class DataSizeCodecWrapper implements Codec2 {
    /**
     * origin codec
     */
    protected Codec2 codec;

    public DataSizeCodecWrapper(Codec2 codec) {
        this.codec = codec;
    }

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException {
        if (message instanceof Request) {
            Object data = ((Request) message).getData();
            if (data instanceof RpcInvocation) {
                RpcInvocation invocation = (RpcInvocation) data;
                encodeRequestWithTracer(channel, buffer, message, invocation);
                return;
            }
        } else if (message instanceof Response) {
            Object response = ((Response) message).getResult();
            if (response instanceof RpcResult) {
                encodeResultWithTracer(channel, buffer, message);
                return;
            }
        }
        codec.encode(channel, buffer, message);
    }

    /**
     * @param channel       a long connection
     * @param buffer        buffer
     * @param message       the original Request object
     * @param invocation    Invocation in Request
     * @throws IOException  serialization exception
     */
    protected void encodeRequestWithTracer(Channel channel, ChannelBuffer buffer, Object message,
                                           RpcInvocation invocation) throws IOException {
        long startTime = System.currentTimeMillis();
        int index = buffer.writerIndex();
        // serialization
        codec.encode(channel, buffer, message);
        int reqSize = buffer.writerIndex() - index;
        long elapsed = System.currentTimeMillis() - startTime;
        invocation.setAttachment(AttachmentKeyConstants.CLIENT_SERIALIZE_SIZE, String.valueOf(reqSize));
        invocation.setAttachment(AttachmentKeyConstants.CLIENT_SERIALIZE_TIME, String.valueOf(elapsed));
    }

    /**
     * @param channel       a long connection
     * @param buffer        buffer
     * @param message        the original Request object
     * @throws IOException  serialization exception
     */
    protected void encodeResultWithTracer(Channel channel, ChannelBuffer buffer, Object message) throws IOException {
        Object result = ((Response) message).getResult();
        long startTime = System.currentTimeMillis();
        int index = buffer.writerIndex();
        codec.encode(channel, buffer, message);
        int respSize = buffer.writerIndex() - index;
        long elapsed = System.currentTimeMillis() - startTime;
        ((RpcResult) result).setAttachment(AttachmentKeyConstants.SERVER_SERIALIZE_SIZE,
            String.valueOf(respSize));
        ((RpcResult) result).setAttachment(AttachmentKeyConstants.SERVER_SERIALIZE_TIME,
            String.valueOf(elapsed));
    }

    /**
     * deserialization operation
     * @param channel
     * @param input
     * @return
     * @throws IOException
     */
    @Override
    public Object decode(Channel channel, ChannelBuffer input) throws IOException {
        long startTime = System.currentTimeMillis();
        int index = input.readerIndex();
        Object ret = codec.decode(channel, input);
        int size = input.readerIndex() - index;
        long elapsed = System.currentTimeMillis() - startTime;
        if (ret instanceof Request) {
            // server-side deserialize the Request
            Object data = ((Request) ret).getData();
            if (data instanceof RpcInvocation) {
                RpcInvocation invocation = (RpcInvocation) data;
                invocation.setAttachment(AttachmentKeyConstants.SERVER_DESERIALIZE_SIZE,
                    String.valueOf(size));
                invocation.setAttachment(AttachmentKeyConstants.SERVER_DESERIALIZE_TIME,
                    String.valueOf(elapsed));
            }
        } else if (ret instanceof Response) {
            // client-side deserialize the Response
            Object result = ((Response) ret).getResult();
            if (result instanceof RpcResult) {
                RpcResult rpcResult = (RpcResult) result;
                rpcResult.setAttachment(AttachmentKeyConstants.CLIENT_DESERIALIZE_SIZE,
                    String.valueOf(size));
                rpcResult.setAttachment(AttachmentKeyConstants.CLIENT_DESERIALIZE_TIME,
                    String.valueOf(elapsed));
            }
        }
        return ret;
    }
}
