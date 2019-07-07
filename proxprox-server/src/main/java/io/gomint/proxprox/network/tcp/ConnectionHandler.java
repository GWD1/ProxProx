package io.gomint.proxprox.network.tcp;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.UpstreamConnection;
import io.gomint.proxprox.network.tcp.protocol.FlushTickPacket;
import io.gomint.proxprox.network.tcp.protocol.Packet;
import io.gomint.proxprox.network.tcp.protocol.SendPlayerToServerPacket;
import io.gomint.proxprox.network.tcp.protocol.WrappedMCPEPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class ConnectionHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOGGER = LoggerFactory.getLogger( ConnectionHandler.class );
    private ChannelHandlerContext ctx;

    private Consumer<Void> whenConnected;
    private Consumer<PacketBuffer> dataAcceptor;
    private Consumer<Void> disconnectCallback;

    private final UpstreamConnection upstreamConnection;

    private final String ip;
    private final int port;

    ConnectionHandler( UpstreamConnection connection, String ip, int port ) {
        super( true );
        this.upstreamConnection = connection;
        this.ip = ip;
        this.port = port;
    }

    @Override
    public void channelActive( ChannelHandlerContext ctx ) throws Exception {
        this.ctx = ctx;

        if ( this.whenConnected != null ) {
            this.whenConnected.accept( null );
        }
    }

    public void send( Packet packet ) {
        this.ctx.writeAndFlush( packet );
    }

    @Override
    public void channelInactive( ChannelHandlerContext ctx ) throws Exception {
        if ( this.disconnectCallback != null ) {
            this.disconnectCallback.accept( null );
        }
    }

    @Override
    protected void channelRead0( ChannelHandlerContext channelHandlerContext, final Packet packet ) throws Exception {
        if ( packet instanceof WrappedMCPEPacket ) {
            for ( PacketBuffer buffer : ( (WrappedMCPEPacket) packet ).getBuffer() ) {
                this.dataAcceptor.accept( buffer );
            }
        } else if ( packet instanceof SendPlayerToServerPacket ) {
            this.upstreamConnection.connect( ( (SendPlayerToServerPacket) packet ).getHost(), ( (SendPlayerToServerPacket) packet ).getPort() );
        } else if ( packet instanceof FlushTickPacket ) {
            this.upstreamConnection.flushSendQueue( 0.06f ); // Always flush
        }
    }

    @Override
    public void exceptionCaught( ChannelHandlerContext ctx, Throwable cause ) throws Exception {
        LOGGER.error( "Caught exception in connection (connected to {}:{}) handling: ", this.ip, this.port, cause );
        this.disconnect();
    }

    public void onData( Consumer<PacketBuffer> consumer ) {
        this.dataAcceptor = consumer;
    }

    public void whenConnected( Consumer<Void> callback ) {
        this.whenConnected = callback;
    }

    public void whenDisconnected( Consumer<Void> callback ) {
        this.disconnectCallback = callback;
    }

    public void disconnect() {
        this.ctx.flush();
        this.ctx.close();
    }

}
