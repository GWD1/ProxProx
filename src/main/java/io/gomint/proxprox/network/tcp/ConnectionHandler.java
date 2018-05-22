package io.gomint.proxprox.network.tcp;

import com.google.common.collect.MapMaker;
import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.UpstreamConnection;
import io.gomint.proxprox.network.tcp.protocol.FlushTickPacket;
import io.gomint.proxprox.network.tcp.protocol.Packet;
import io.gomint.proxprox.network.tcp.protocol.SendPlayerToServerPacket;
import io.gomint.proxprox.network.tcp.protocol.WrappedMCPEPacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ConnectionHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOGGER = LoggerFactory.getLogger( ConnectionHandler.class );
    private ChannelHandlerContext ctx;

    private Consumer<Void> whenConnected;
    private Consumer<PacketBuffer> dataAcceptor;
    private Consumer<Void> disconnectCallback;

    private final UpstreamConnection upstreamConnection;

    ConnectionHandler( UpstreamConnection connection ) {
        super( true );
        this.upstreamConnection = connection;
    }

    @Override
    public void channelActive( ChannelHandlerContext ctx ) throws Exception {
        this.ctx = ctx;

        if ( this.whenConnected != null ) {
            this.whenConnected.accept( null );
        }
    }

    public void send( Packet packet ) {
        flush( new FlushItem( ctx.channel(), packet ) );
    }

    private void flush( FlushItem item ) {
        EventLoop loop = item.channel.eventLoop();
        Flusher flusher = flusherLookup.get( loop );
        if ( flusher == null ) {
            Flusher alt = flusherLookup.putIfAbsent( loop, flusher = new Flusher( loop ) );
            if ( alt != null ) {
                flusher = alt;
            }
        }

        flusher.queued.add( item );
        flusher.start();
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
            this.upstreamConnection.flushSendQueue();
        }
    }

    @Override
    public void exceptionCaught( ChannelHandlerContext ctx, Throwable cause ) throws Exception {
        LOGGER.error( "Caught exception in connection handling: ", cause );
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
        try {
            this.ctx.close().get( 1, TimeUnit.SECONDS );
        } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
            LOGGER.error( "Could not close connection: ", e );
        }
    }

    private static final class Flusher implements Runnable {
        final WeakReference<EventLoop> eventLoopRef;
        final Queue<FlushItem> queued = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean( false );
        final HashSet<Channel> channels = new HashSet<>();
        int runsWithNoWork = 0;

        private Flusher( EventLoop eventLoop ) {
            this.eventLoopRef = new WeakReference<>( eventLoop );
        }

        void start() {
            if ( !running.get() && running.compareAndSet( false, true ) ) {
                EventLoop eventLoop = eventLoopRef.get();
                if ( eventLoop != null ) {
                    eventLoop.execute( this );
                }
            }
        }

        @Override
        public void run() {
            boolean doneWork = false;
            FlushItem flush;
            while ( null != ( flush = queued.poll() ) ) {
                Channel channel = flush.channel;
                if ( channel.isActive() ) {
                    channels.add( channel );
                    channel.write( flush.request );
                    doneWork = true;
                }
            }

            // Always flush what we have (don't artificially delay to try to coalesce more messages)
            for ( Channel channel : channels ) {
                channel.flush();
            }

            channels.clear();

            if ( doneWork ) {
                runsWithNoWork = 0;
            } else {
                // either reschedule or cancel
                if ( ++runsWithNoWork > 5 ) {
                    running.set( false );
                    if ( queued.isEmpty() || !running.compareAndSet( false, true ) ) {
                        return;
                    }
                }
            }

            EventLoop eventLoop = eventLoopRef.get();
            if ( eventLoop != null && !eventLoop.isShuttingDown() ) {
                eventLoop.schedule( this, 10000, TimeUnit.NANOSECONDS );
            }
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + Integer.toHexString( this.hashCode() );
    }

    private static final ConcurrentMap<EventLoop, Flusher> flusherLookup = new MapMaker()
            .concurrencyLevel( 16 )
            .weakKeys()
            .makeMap();

    private static class FlushItem {
        final Channel channel;
        final Object request;

        private FlushItem( Channel channel, Object request ) {
            this.channel = channel;
            this.request = request;
        }
    }
}
