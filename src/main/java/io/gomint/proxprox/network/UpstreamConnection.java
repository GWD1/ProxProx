/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.Connection;
import io.gomint.jraknet.EncapsulatedPacket;
import io.gomint.jraknet.PacketBuffer;
import io.gomint.jraknet.PacketReliability;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.ChatColor;
import io.gomint.proxprox.api.data.ServerDataHolder;
import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.entity.Server;
import io.gomint.proxprox.api.event.PermissionCheckEvent;
import io.gomint.proxprox.api.event.PlayerLoggedinEvent;
import io.gomint.proxprox.api.event.PlayerLoginEvent;
import io.gomint.proxprox.api.event.PlayerSwitchEvent;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.debugger.NetworkDebugger;
import io.gomint.proxprox.network.protocol.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // User data
    private UUID uuid;
    private String username;
    private boolean valid;
    private long xboxId;

    // Last known good server
    private ServerDataHolder lastKnownServer;
    private boolean firstServer = true;

    // Metadata
    private Map<String, Object> metaData = new ConcurrentHashMap<>();

    // Debug
    @Getter
    private NetworkDebugger networkDebugger = new NetworkDebugger();

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
                Thread.currentThread().setName( "UpStream unknown (" + connection.getGuid() + ") [Packet Read/Rewrite]" );

                while ( connection.isConnected() ) {
                    EncapsulatedPacket data = connection.poll();
                    if ( data == null ) {
                        continue;
                    }

                    PacketBuffer buffer = new PacketBuffer( data.getPacketData(), 0 );
                    if ( buffer.getRemaining() <= 0 ) {
                        // Malformed packet:
                        return;
                    }

                    // Do we want to handle it?
                    if ( !handlePacket( buffer, data.getReliability(), data.getOrderingChannel(), false ) ) {
                        if ( currentDownStream != null && currentDownStream.getConnection() != null ) {
                            currentDownStream.getConnection().send( data.getReliability(), data.getOrderingChannel(), data.getPacketData() );
                        }
                    }
                }
            }
        } );
        this.connectionReadThread.start();
    }

    @Override
    protected void announceRewrite( PacketReliability reliability, int orderingChannel, byte[] packet ) {
        if ( currentDownStream != null && currentDownStream.getConnection() != null ) {
            currentDownStream.getConnection().send( reliability, orderingChannel, packet );
        }
    }

    @Override
    protected boolean handlePacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batched ) {
        // Store in debugger
        this.networkDebugger.addPacket( new PacketBuffer( buffer.getBuffer(),0 ), batched );

        // Grab the packet ID from the packet's data
        byte packetId = buffer.readByte();
        if ( packetId == (byte) 0xFE && buffer.getRemaining() > 0 ) {
            packetId = buffer.readByte();
        }

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.BATCH_PACKET:
                // The first batch should include the login packet
                if ( this.loginPacket == null ) {
                    this.loginPacket = new PacketBuffer( buffer.getBuffer(), 0 );
                }

                return handleBatchPacket( buffer, reliability, orderingChannel, batched );

            case Protocol.LOGIN_PACKET:
                // Parse the login packet
                PacketLogin loginPacket = new PacketLogin();
                loginPacket.deserialize( buffer );

                // When we are in online mode kick all invalid users
                if ( this.proxProx.getConfig().isOnlineMode() && !loginPacket.isValid() ) {
                    disconnect( "Only valid xbox live accounts can join. Please login" );
                    return true;
                }

                // Log xbox accounts if needed
                this.valid = loginPacket.isValid();
                if ( loginPacket.isValid() ) {
                    logger.info( "Got valid XBOX Live Account ID: " + loginPacket.getXboxId() );
                    this.xboxId = loginPacket.getXboxId();
                }

                // Check for uuid or name equals
                for ( UpstreamConnection upstreamConnection : this.proxProx.getPlayers() ) {
                    if ( loginPacket.getUUID().equals( upstreamConnection.getUUID() ) || loginPacket.getUserName().equals( upstreamConnection.getName() ) ) {
                        disconnect( "Logged in from another location" );
                        return true;
                    }
                }

                this.uuid = loginPacket.getUUID();
                this.username = loginPacket.getUserName();

                send( new PacketPlayState( PacketPlayState.PlayState.LOGIN_SUCCESS ) );

                PlayerLoginEvent event = this.proxProx.getPluginManager().callEvent( new PlayerLoginEvent( this ) );
                if ( event.isCancelled() ) {
                    disconnect( event.getDisconnectReason() );
                    return true;
                }

                logger.info( "Logged in as " + loginPacket.getUserName() + " (UUID: " + loginPacket.getUUID().toString() + ")" );
                Thread.currentThread().setName( "UpStream " + getUUID() + " [Packet Read/Rewrite]" );
                this.state = ConnectionState.CONNECTED;

                this.proxProx.addPlayer( this );

                // Connect to the default Server
                this.connect( this.proxProx.getConfig().getDefaultServer().getIp(), this.proxProx.getConfig().getDefaultServer().getPort() );
                return true;

            case Protocol.TEXT_PACKET:
                if ( this.state != ConnectionState.CONNECTED ) {
                    disconnect( ChatColor.RED + "Error in handshake" );
                    return true;
                }

                // Parse the chat packet
                PacketText packetText = new PacketText();
                packetText.deserialize( buffer );

                // We only care for commands currently
                // TODO: Add some sort of general chat event
                if ( packetText.getType() == PacketText.Type.PLAYER_CHAT && packetText.getMessage().startsWith( "/" ) ) {
                    return this.proxProx.getPluginManager().dispatchCommand( this, packetText.getMessage().substring( 1 ) );
                } else {
                    return false;
                }

            default:
                return false;
        }
    }

    /**
     * Connect to a new DownStream server
     *
     * @param ip   The ip of the server
     * @param port The port of the server
     */
    public void connect( String ip, int port ) {
        // Event first
        PlayerSwitchEvent switchEvent = this.proxProx.getPluginManager().callEvent( new PlayerSwitchEvent( this, this.currentDownStream, new ServerDataHolder( ip, port ) ) );

        // Check if we have a pending connection
        if ( this.pendingDownStream != null ) {
            // Disconnect
            this.pendingDownStream.close();
            this.pendingDownStream = null;
        }

        this.pendingDownStream = new DownstreamConnection( this.proxProx, this, switchEvent.getTo().getIP(), switchEvent.getTo().getPort() );
    }

    @Override
    public boolean isValid() {
        return this.valid;
    }

    @Override
    public long getXboxId() {
        return this.xboxId;
    }

    @Override
    public InetSocketAddress getAddress() {
        return ( InetSocketAddress ) this.connection.getAddress();
    }

    @Override
    public <T> T getMetaData( String key ) {
        return (T) this.metaData.get( key );
    }

    @Override
    public void setMetaData( String key, Object data ) {
        this.metaData.put( key, data );
    }

    @Override
    public long getPing() {
        return this.connection.getPing();
    }

    /**
     * New downstream connected
     *
     * @param downstreamConnection The downstream which connected
     */
    void onDownSteamConnected( DownstreamConnection downstreamConnection ) {
        // Close old connection and store new one
        if ( this.currentDownStream != null ) {
            this.lastKnownServer = new ServerDataHolder( this.currentDownStream.getIP(), this.currentDownStream.getPort() );
            this.currentDownStream.close();

            // Cleanup all entities
            for ( Long eID : this.currentDownStream.getSpawnedEntities() ) {
                send( new PacketRemoveEntity( eID ) );
            }

            // Loading screen (holy did this take long to figure out :D)
            send( new PacketChangeDimension( (byte) 0 ) );
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );
            send( new PacketChangeDimension( (byte) 1 ) );
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );

            move( 0, 4000, 0 );
            sendEmptyChunks();

            send( new PacketChangeDimension( (byte) 1 ) );
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );
            send( new PacketChangeDimension( (byte) 0 ) );
            // There needs to be one additional spawn but the downstream server sends one so its ok

            this.currentDownStream = null;
        }

        // Send login packet
        downstreamConnection.send( this.loginPacket.getBuffer() );
    }

    private void sendEmptyChunks() {
        for ( int x = -3; x < 3; x++ ) {
            for ( int z = -3; z < 3; z++ ) {
                PacketFullChunkData chunk = new PacketFullChunkData();
                chunk.setChunkX( x );
                chunk.setChunkZ( z );
                chunk.setChunkData( new byte[0] );
                send( chunk );
            }
        }
    }

    private void move( float x, float y, float z ) {
        PacketMovePlayer packet = new PacketMovePlayer();
        packet.setEntityId( 0 );
        packet.setX( x );
        packet.setY( y + 1.62f );
        packet.setZ( z );
        packet.setMode( (byte) 1 );
        send( packet );
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
        connection.send( PacketReliability.RELIABLE, packet.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
    }

    /**
     * Disconnect this user
     *
     * @param reason The reason to display
     */
    public void disconnect( String reason ) {
        send( new PacketDisconnect( reason ) );
        this.connection.disconnect( reason );

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

    // ---------- Player API --------------- //

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    @Override
    public String getName() {
        return this.username;
    }

    // ----------- CommandSender API -------------- //

    @Override
    public void sendMessage( String... messages ) {
        for ( String message : messages ) {
            PacketText text = new PacketText();
            text.setType( PacketText.Type.CLIENT_MESSAGE );
            text.setMessage( message );
            this.send( text );
        }
    }

    @Override
    public boolean hasPermission( String permission ) {
        PermissionCheckEvent permissionCheckEvent = this.proxProx.getPluginManager().callEvent( new PermissionCheckEvent( this, permission ) );
        return permissionCheckEvent.isResult();
    }

    @Override
    public String getColor() {
        return ChatColor.AQUA;
    }

    @Override
    public String getPrefix() {
        return "YO MAMA";
    }

    @Override
    public Locale getLocale() {
        return Locale.ENGLISH;
    }

    @Override
    public Server getServer() {
        return this.currentDownStream;
    }

    /**
     * Connect to last known server
     *
     * @return true when connecting, false when no server was found
     */
    public boolean connectToLastKnown() {
        if ( this.lastKnownServer != null ) {
            this.connect( this.lastKnownServer.getIP(), this.lastKnownServer.getPort() );
            this.lastKnownServer = null;
            return true;
        }

        return false;
    }

    /**
     * Get the connection to the user
     *
     * @return The jRakNet Connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Downstream is ready for the player to connect
     *
     * @param downstreamConnection The connection which we want to use
     */
    public void switchToDownstream( DownstreamConnection downstreamConnection ) {
        // Clean up pending
        this.pendingDownStream = null;

        // Take over new downstream
        downstreamConnection.sendInit();

        // Send Loggedin Event on first downstram connect
        if ( this.firstServer ) {
            this.proxProx.getPluginManager().callEvent( new PlayerLoggedinEvent( this ) );
            this.firstServer = false;
        }

        this.currentDownStream = downstreamConnection;
    }

    public void resetPendingDownStream() {
        this.pendingDownStream = null;
    }

}
