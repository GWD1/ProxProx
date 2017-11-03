package io.gomint.proxprox.network.tcp;

import io.gomint.proxprox.network.tcp.protocol.WrappedMCPEPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Decoder extends ByteToMessageDecoder {

    @Override
    protected void decode( ChannelHandlerContext channelHandlerContext, ByteBuf buf, List<Object> objects ) throws Exception {
        if ( buf instanceof EmptyByteBuf ) {
            // The Channel has disconnected and this is the last message we got. R.I.P. connection
            return;
        }

        WrappedMCPEPacket wrappedMCPEPacket = new WrappedMCPEPacket();
        wrappedMCPEPacket.read( buf );
        objects.add( wrappedMCPEPacket );
    }

}
