/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.gomint.jraknet.*;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.entity.Server;
import io.gomint.proxprox.network.protocol.PacketDisconnect;
import io.gomint.proxprox.network.protocol.PacketLogin;
import io.gomint.proxprox.network.protocol.PacketPlayState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.util.concurrent.TimeUnit;

/**
 * @author geNAZt
 * @version 1.0
 */
public class DownstreamConnection extends AbstractConnection implements Server {

    private static final Logger logger = LoggerFactory.getLogger( DownstreamConnection.class );

    // Needed connection data to reach the server
    private String ip;
    private int port;

    // Client connection
    private ClientSocket connection;
    private Thread connectionReadThread;

    // Upstream
    private UpstreamConnection upstreamConnection;

    // Proxy instance
    private ProxProx proxProx;

    /**
     * Create a new AbstractConnection to a server.
     *
     * @param proxProx The proxy instance
     * @param upstreamConnection The upstream connection which requested to connect to this downstream
     * @param ip   The ip of the server we want to connect to
     * @param port The port of the server we want to connect to
     */
    public DownstreamConnection( ProxProx proxProx, UpstreamConnection upstreamConnection, String ip, int port ) {
        this.upstreamConnection = upstreamConnection;
        this.proxProx = proxProx;

        this.connection = new ClientSocket();
        this.connection.setEventLoopFactory( new ThreadFactoryBuilder().setNameFormat( "jRaknet-downstream-%d" ).build() );
        this.connection.setEventHandler( new SocketEventHandler() {
            @Override
            public void onSocketEvent( Socket socket, SocketEvent socketEvent ) {
                switch ( socketEvent.getType() ) {
                    case CONNECTION_ATTEMPT_SUCCEEDED:
                        // We got accepted *yay*
                        DownstreamConnection.this.setup();
                        DownstreamConnection.this.upstreamConnection.onDownSteamConnected( DownstreamConnection.this );
                        break;

                    case CONNECTION_CLOSED:
                    case CONNECTION_DISCONNECTED:
                        logger.info( "Disconnected downstream..." );
                }
            }
        } );

        try {
            this.connection.initialize();
        } catch ( SocketException e ) {
            e.printStackTrace();
        }

        this.connection.connect( ip, port );
    }

    @Override
    protected void setup() {
        super.setup();

        this.connectionReadThread = this.proxProx.getNewServerConnectionThread( new Runnable() {
            @Override
            public void run() {
                while ( connection.getConnection() != null && connection.getConnection().isConnected() ) {
                    byte[] data = connection.getConnection().receive( 1, TimeUnit.SECONDS );
                    if ( data == null ) {
                        continue;
                    }

                    // We have a cached login Packet
                    if ( state == ConnectionState.CONNECTED ) {
                        if ( upstreamConnection != null ) {
                            upstreamConnection.send( data );
                        }

                        continue;
                    }

                    PacketBuffer buffer = new PacketBuffer( data, 0 );
                    if ( buffer.getRemaining() <= 0 ) {
                        // Malformed packet:
                        return;
                    }

                    if ( !handlePacket( buffer, false ) ) {
                        if ( upstreamConnection != null ) {
                            upstreamConnection.send( data );
                        }
                    }
                }
            }
        } );
        this.connectionReadThread.start();
    }

    @Override
    protected void announceRewrite( byte[] buffer ) {
        if ( upstreamConnection != null ) {
            upstreamConnection.send( buffer );
        }
    }

    @Override
    protected boolean handlePacket( PacketBuffer buffer, boolean batched ) {
        // Grab the packet ID from the packet's data
        byte packetId = buffer.readByte();
        if ( packetId == (byte) 0xfe && buffer.getRemaining() > 0 ) {
            packetId = buffer.readByte();
        }

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.BATCH_PACKET:
                handleBatchPacket( buffer, batched );
                return true;
            case Protocol.PLAY_STATUS_PACKET:
                PacketPlayState packetPlayState = new PacketPlayState();
                packetPlayState.deserialize( buffer );

                // We have been logged in. But we miss a spawn packet
                if ( packetPlayState.getState() == PacketPlayState.PlayState.LOGIN_SUCCESS ) {
                    state = ConnectionState.CONNECTED;
                }
                return true;
            case Protocol.DISONNECT_PACKET:
                PacketDisconnect packetDisconnect = new PacketDisconnect();
                packetDisconnect.deserialize( buffer );

                if ( upstreamConnection.connectToLastKnown() ) {
                    upstreamConnection.sendMessage( packetDisconnect.getMessage() );
                }

                return true;
            default:
                return false;
        }
    }

    /**
     * Close the connection to the underlying RakNet Server
     */
    public void close() {
        this.connection.close();
    }

    public void send( byte[] data ) {
        if ( this.connection.getConnection() != null ) {
            this.connection.getConnection().send( data );
        }
    }

    public void disconnect( String reason ) {
        if ( this.connection.getConnection() != null ) {
            logger.info( "Disconnecting DownStream for " + this.upstreamConnection.getUUID() );

            this.connection.getConnection().disconnect( reason );
        }
    }

    @Override
    public String getIP() {
        return this.ip;
    }

    @Override
    public int getPort() {
        return this.port;
    }

}
