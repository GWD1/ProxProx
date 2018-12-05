package io.gomint.proxprox.network.tcp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.gomint.proxprox.network.UpstreamConnection;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;

import java.util.function.Consumer;

public class Initializer {

    private static final int LOW_MARK = Integer.getInteger( "io.gomint.proxprox.low_mark", 2 << 18 );   // 0.5 mb
    private static final int HIGH_MARK = Integer.getInteger( "io.gomint.proxprox.high_mark", 2 << 20 ); // 2 mb
    private static final WriteBufferWaterMark MARK = new WriteBufferWaterMark( LOW_MARK, HIGH_MARK );

    private static final EventLoopGroup EVENT_LOOP_GROUP;

    static {
        EVENT_LOOP_GROUP = Pipeline.newEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "TCP Threads" ).build() );
    }

    public static Bootstrap buildBootstrap( UpstreamConnection upstreamConnection, String ip, int port, final Consumer<ConnectionHandler> connectionHandlerCallback ) {
        final ConnectionHandler connectionHandler = new ConnectionHandler( upstreamConnection, ip, port );

        final Bootstrap b = new Bootstrap()
                .group( EVENT_LOOP_GROUP )
                .channel( Pipeline.getChannel() )
                .handler( new ChannelInitializer<SocketChannel>() {
                              @Override
                              public void initChannel( SocketChannel ch ) throws Exception {
                                  try {
                                      ch.config().setOption( ChannelOption.IP_TOS, 0x18 );
                                  } catch ( ChannelException e ) {
                                      // Ignore
                                  }

                                  ch.config().setAllocator( PooledByteBufAllocator.DEFAULT );
                                  ch.config().setWriteBufferWaterMark( MARK );
                                  Pipeline.prepare( ch.pipeline(), connectionHandler );
                              }
                          }
                );

        connectionHandler.whenConnected( new Consumer<Void>() {
            @Override
            public void accept( Void aVoid ) {
                connectionHandlerCallback.accept( connectionHandler );
            }
        } );

        return b;
    }

}
