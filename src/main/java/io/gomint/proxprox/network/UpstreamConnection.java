/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.Connection;
import io.gomint.jraknet.PacketBuffer;
import io.gomint.jraknet.PacketReliability;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.entity.Server;
import io.gomint.proxprox.api.event.PlayerLoginEvent;
import io.gomint.proxprox.network.protocol.Packet;
import io.gomint.proxprox.network.protocol.PacketDisconnect;
import io.gomint.proxprox.network.protocol.PacketLogin;
import io.gomint.proxprox.network.protocol.PacketPlayState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author geNAZt
 * @version 1.0
 */
public class UpstreamConnection extends AbstractConnection implements Player {

    private static final Logger logger = LoggerFactory.getLogger( UpstreamConnection.class );
    private final ProxProx proxProx;

    // AbstractConnection stuff
    private final Connection connection;
    private Thread connectionReadThread;
    private PacketBuffer loginPacket;

    // Downstream
    private DownstreamConnection currentDownStream;
    private DownstreamConnection pendingDownStream;
    private boolean firstSpawn = true;

    // User data
    private UUID uuid;
    private String username;

    /**
     * Create a new AbstractConnection wrapper which represents the communication from User <-> Proxy
     *
     * @param proxProx   The proxy instance from which this connection comes from
     * @param connection The established connection
     */
    public UpstreamConnection( ProxProx proxProx, Connection connection ) {
        super();
        this.proxProx = proxProx;
        this.connection = connection;

        logger.info( "New upstream connection for " + connection.getAddress().toString() + " (GUID: " + connection.getGuid() + ")" );

        this.setup();
    }

    @Override
    protected void setup() {
        super.setup();

        // Create thread for reading data
        this.connectionReadThread = this.proxProx.getNewClientConnectionThread( new Runnable() {
            @Override
            public void run() {
                while ( connection.isConnected() ) {
                    byte[] data = connection.receive( 1, TimeUnit.SECONDS );
                    if ( data == null ) {
                        continue;
                    }

                    // We have a cached login Packet
                    if ( state == ConnectionState.CONNECTED ) {
                        if ( currentDownStream != null ) {
                            currentDownStream.send( data );
                        }

                        continue;
                    }

                    PacketBuffer buffer = new PacketBuffer( data, 0 );
                    if ( buffer.getRemaining() <= 0 ) {
                        // Malformed packet:
                        return;
                    }

                    handlePacket( buffer, false );
                }
            }
        } );
        this.connectionReadThread.start();
    }

    @Override
    protected void handlePacket( PacketBuffer buffer, boolean batched ) {
        // Grab the packet ID from the packet's data
        byte packetId = buffer.readByte();
        if ( packetId == (byte) 0xfe && buffer.getRemaining() > 0 ) {
            packetId = buffer.readByte();
        }

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.BATCH_PACKET:
                // The first batch should include the login packet
                this.loginPacket = new PacketBuffer( buffer.getBuffer(), 0 );

                handleBatchPacket( buffer, batched );
                break;
            case Protocol.LOGIN_PACKET:
                // Parse the login packet
                PacketLogin loginPacket = new PacketLogin();
                loginPacket.deserialize( buffer );

                // TODO: Implement correct login logic
                this.uuid = loginPacket.getUUID();
                this.username = loginPacket.getUserName();

                send( new PacketPlayState( PacketPlayState.PlayState.LOGIN_SUCCESS ) );

                PlayerLoginEvent event = this.proxProx.getPluginManager().callEvent( new PlayerLoginEvent( this ) );
                if ( event.isCancelled() ) {
                    disconnect( event.getDisconnectReason() );
                    return;
                }


                logger.info( "Logged in as " + loginPacket.getUserName() + " (UUID: " + loginPacket.getUUID().toString() + ")" );
                this.state = ConnectionState.CONNECTED;

                // Connect to default server
                connect( "127.0.0.1", 19134 );

                new Thread( new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep( 30 * 1000 );
                        } catch ( InterruptedException e ) {
                            e.printStackTrace();
                        }

                        connect( "127.0.0.1", 19135 );
                    }
                } ).start();

                break;
            default:
                logger.info( "Unknown packet ID in handshake: " + Integer.toHexString( packetId & 0xFF ) );
        }
    }

    /**
     * Connect to a new DownStream server
     *
     * @param ip    The ip of the server
     * @param port  The port of the server
     */
    public void connect( String ip, int port ) {
        // Check if we have a pending connection
        if ( this.pendingDownStream != null ) {
            // Disconnect
            this.pendingDownStream.close();
            this.pendingDownStream = null;
        }

        this.pendingDownStream = new DownstreamConnection( this.proxProx, this, ip, port );
    }

    /**
     * New downstream connected
     *
     * @param downstreamConnection The downstream which connected
     */
    void onDownSteamConnected( DownstreamConnection downstreamConnection ) {
        // Send login packet
        downstreamConnection.send( this.loginPacket.getBuffer() );

        // When this is first spawn send state
        if ( this.firstSpawn ) {
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );
            this.firstSpawn = false;
        } else {
            // TODO: Maybe send some sort of dimension change?
            // send( new PacketDimensionChange() );
        }

        // Clean up pending
        this.pendingDownStream = null;

        // Close old connection and store new one
        if ( this.currentDownStream != null ) {
            this.currentDownStream.close();
        }

        this.currentDownStream = downstreamConnection;
    }

    /**
     * Send data to the user
     *
     * @param data The data which should be send
     */
    public void send( byte[] data ) {
        this.connection.send( data );
    }

    /**
     * Send a packet to the client
     *
     * @param packet The packet which should be send
     */
    public void send( Packet packet ) {
        PacketBuffer buffer = new PacketBuffer( packet.estimateLength() == -1 ? 64 : packet.estimateLength() + 2 );
        buffer.writeByte( (byte) 0xFE );
        buffer.writeByte( packet.getId() );
        packet.serialize( buffer );
        this.connection.send( PacketReliability.RELIABLE, packet.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
    }

    /**
     * Disconnect this user
     *
     * @param reason The reason to display
     */
    public void disconnect( String reason ) {
        send( new PacketDisconnect( reason ) );
        // this.connection.disconnect( reason );

        if ( this.pendingDownStream != null ) {
            this.pendingDownStream.disconnect( reason );
            this.pendingDownStream = null;
        }

        if ( this.currentDownStream != null ) {
            this.currentDownStream.disconnect( reason );
            this.currentDownStream = null;
        }
    }

    /**
     * Get the current connected down stream
     *
     * @return The current connected down stream or null
     */
    public DownstreamConnection getDownStream() {
        return currentDownStream;
    }

    /**
     * Get the pending down stream connection
     *
     * @return The current pending down stream or null
     */
    public DownstreamConnection getPendingDownStream() {
        return pendingDownStream;
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    @Override
    public String getName() {
        return this.username;
    }

    @Override
    public Server getServer() {
        return this.currentDownStream;
    }

}
