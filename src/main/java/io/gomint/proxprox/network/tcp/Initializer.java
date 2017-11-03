package io.gomint.proxprox.network.tcp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;

import java.util.function.Consumer;

public class Initializer {

    private static final EventLoopGroup EVENT_LOOP_GROUP;

    static {
        EVENT_LOOP_GROUP = Pipeline.newEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "TCP Threads" ).build() );
    }

    public static Bootstrap buildBootstrap( String name, final Consumer<ConnectionHandler> connectionHandlerCallback ) {
        final ConnectionHandler connectionHandler = new ConnectionHandler();

        final Bootstrap b = new Bootstrap()
                .group( EVENT_LOOP_GROUP )
                .channel( Pipeline.getChannel() )
                .handler( new ChannelInitializer<SocketChannel>() {
                              @Override
                              public void initChannel( SocketChannel ch ) throws Exception {
                                  ch.config().setOption( ChannelOption.IP_TOS, 0x18 );
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

    public static ServerBootstrap buildServerBootstrap( String name, final Consumer<ConnectionHandler> newConnection ) {
        return new ServerBootstrap()
                .group( EVENT_LOOP_GROUP )
                .channel( Pipeline.getServerChannel() )
                .childOption( ChannelOption.TCP_NODELAY, true )
                .childOption( ChannelOption.SO_LINGER, 0 )
                .childOption( ChannelOption.SO_KEEPALIVE, true )
                .childOption( ChannelOption.ALLOCATOR, new PooledByteBufAllocator( true ) )
                .childOption( ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024 )
                .childOption( ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024 )
                .childHandler( new ChannelInitializer<SocketChannel>() {
                                   @Override
                                   public void initChannel( SocketChannel ch ) throws Exception {
                                       final ConnectionHandler connectionHandler = new ConnectionHandler();
                                       Pipeline.prepare( ch.pipeline(), connectionHandler );

                                       connectionHandler.whenConnected( new Consumer<Void>() {
                                           @Override
                                           public void accept( Void aVoid ) {
                                               newConnection.accept( connectionHandler );
                                           }
                                       } );
                                   }
                               }
                );
    }

}
