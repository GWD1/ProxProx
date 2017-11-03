package io.gomint.proxprox.network.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.util.concurrent.ThreadFactory;

public class Pipeline {

    public static final String FRAME_DECODER = "frameDecoder";
    public static final String FRAME_PREPENDER = "framePrepender";
    public static final String CONNECTION_HANDLER = "connectionHandler";
    public static final String PACKET_DECODER = "packetDecoder";
    public static final String PACKET_ENCODER = "packetEncoder";

    public static EventLoopGroup newEventLoopGroup( int threads, ThreadFactory factory ) {
        return Epoll.isAvailable() ? new EpollEventLoopGroup( threads, factory ) : new NioEventLoopGroup( threads, factory );
    }

    public static Class<? extends Channel> getChannel() {
        return Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class;
    }

    public static Class<? extends ServerChannel> getServerChannel() {
        return Epoll.isAvailable() ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
    }

    public static void prepare( ChannelPipeline pipeline, ConnectionHandler connectionHandler ) {
        pipeline.addLast( FRAME_DECODER, new LengthFieldBasedFrameDecoder( Integer.MAX_VALUE, 0, 4, 0, 4 ) );
        pipeline.addLast( PACKET_DECODER, new Decoder() );
        pipeline.addLast( FRAME_PREPENDER, new LengthFieldPrepender( 4 ) );
        pipeline.addLast( PACKET_ENCODER, new Encoder() );
        pipeline.addLast( CONNECTION_HANDLER, connectionHandler );
    }

}
