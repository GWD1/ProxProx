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
import io.gomint.proxprox.api.event.PlayerSwitchedEvent;
import io.gomint.proxprox.network.protocol.*;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.util.*;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( of = { "ip", "port" }, callSuper = false )
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

    // Entities
    private Set<Long> spawnedEntities = new HashSet<>();

    // Hold back
    private List<CachedData> packets = new ArrayList<>();

    /**
     * Create a new AbstractConnection to a server.
     *
     * @param proxProx           The proxy instance
     * @param upstreamConnection The upstream connection which requested to connect to this downstream
     * @param ip                 The ip of the server we want to connect to
     * @param port               The port of the server we want to connect to
     */
    public DownstreamConnection( ProxProx proxProx, UpstreamConnection upstreamConnection, String ip, int port ) {
        this.upstreamConnection = upstreamConnection;
        this.proxProx = proxProx;

        this.ip = ip;
        this.port = port;

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

                    //case CONNECTION_CLOSED:
                    case CONNECTION_DISCONNECTED:
                        logger.info( "Disconnected downstream..." );
                        DownstreamConnection.this.close();
                        break;

                    default:
                        break;
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
                    EncapsulatedPacket data = connection.getConnection().poll();
                    if ( data == null ) {
                        continue;
                    }

                    PacketBuffer buffer = new PacketBuffer( data.getPacketData(), 0 );
                    if ( buffer.getRemaining() <= 0 ) {
                        // Malformed packet:
                        return;
                    }

                    if ( !handlePacket( buffer, data.getReliability(), data.getOrderingChannel(), false ) ) {
                        if ( upstreamConnection != null ) {
                            if ( !DownstreamConnection.this.equals( upstreamConnection.getDownStream() ) ) {
                                packets.add( new CachedData( data.getReliability(), data.getPacketData(), data.getOrderingChannel() ) );
                            } else {
                                upstreamConnection.getConnection().send( data.getReliability(), data.getOrderingChannel(), data.getPacketData() );
                            }
                        }
                    }
                }
            }
        } );
        this.connectionReadThread.start();
    }

    @Override
    protected void announceRewrite( PacketReliability reliability, int orderingChannel, byte[] packet ) {
        if ( upstreamConnection != null ) {
            if ( !DownstreamConnection.this.equals( upstreamConnection.getDownStream() ) ) {
                packets.add( new CachedData( reliability, packet, orderingChannel ) );
            } else {
                upstreamConnection.getConnection().send( reliability, orderingChannel, packet );
            }
        }
    }

    @Override
    protected boolean handlePacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batched ) {
        // Grab the packet ID from the packet's data
        byte packetId = buffer.readByte();
        if ( packetId == (byte) 0xFE && buffer.getRemaining() > 0 ) {
            packetId = buffer.readByte();
        }

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.ADD_ENTITY_PACKET:
                PacketAddEntity packetAddEntity = new PacketAddEntity();
                packetAddEntity.deserialize( buffer );

                spawnedEntities.add( packetAddEntity.getEntityId() );
                return false;

            case Protocol.ADD_PLAYER_PACKET:
                PacketAddPlayer packetAddPlayer  = new PacketAddPlayer();
                packetAddPlayer.deserialize( buffer );

                spawnedEntities.add( packetAddPlayer.getEntityId() );
                return false;

            case Protocol.PLAY_STATUS_PACKET:
                PacketPlayState packetPlayState = new PacketPlayState();
                packetPlayState.deserialize( buffer );

                // We have been logged in. But we miss a spawn packet
                if ( packetPlayState.getState() == PacketPlayState.PlayState.LOGIN_SUCCESS ) {
                    logger.info( "Connected to downstream for " + this.upstreamConnection.getName() );
                    state = ConnectionState.CONNECTED;
                }

                // The first spawn state must come through
                if ( packetPlayState.getState() == PacketPlayState.PlayState.SPAWN ) {
                    this.upstreamConnection.switchToDownstream( this );
                    this.proxProx.getPluginManager().callEvent( new PlayerSwitchedEvent( this.upstreamConnection, this ) );
                    return false;
                }

                return true;
            case Protocol.DISONNECT_PACKET:
                PacketDisconnect packetDisconnect = new PacketDisconnect();
                packetDisconnect.deserialize( buffer );

                if ( upstreamConnection.getPendingDownStream() != null || upstreamConnection.connectToLastKnown() ) {
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
        this.connectionReadThread.interrupt();
    }

    public void send( byte[] data ) {
        if ( connection.getConnection() != null ) {
            connection.getConnection().send( data );
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

    /**
     * Get the connection to the server
     *
     * @return The connection to the server
     */
    public Connection getConnection() {
        return connection.getConnection();
    }

    /**
     * Return a collection of all currently spawned entities
     *
     * @return
     */
    public Set<Long> getSpawnedEntities() {
        return spawnedEntities;
    }

    /**
     * Send all cached packets since we are ready for it now
     */
    public void sendInit() {
        for ( CachedData packet : packets ) {
            if ( upstreamConnection != null ) {
                upstreamConnection.getConnection().send( packet.getReliability(), packet.getOrderingChannel(), packet.getData() );
            }
        }

        packets.clear();
    }

}
