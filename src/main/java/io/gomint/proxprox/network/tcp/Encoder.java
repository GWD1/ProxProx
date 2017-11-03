package io.gomint.proxprox.network.tcp;


import io.gomint.proxprox.network.tcp.protocol.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Encoder extends MessageToByteEncoder<Packet> {

    @Override
    protected void encode( ChannelHandlerContext channelHandlerContext, Packet packet, ByteBuf buf ) throws Exception {
        packet.write( buf );
    }

}
