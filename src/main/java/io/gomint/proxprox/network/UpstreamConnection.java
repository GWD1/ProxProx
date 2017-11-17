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
import io.gomint.proxprox.jwt.*;
import io.gomint.proxprox.network.protocol.*;
import io.gomint.proxprox.util.EntityRewriter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( of = { "uuid" }, callSuper = false )
public class UpstreamConnection extends AbstractConnection implements Player {

    private static final PacketBuffer EMPTY_CHUNK = new PacketBuffer( 1 + ( 16 * 16 * 2 ) + ( 16 * 16 ) + 2 );
    private static final EncryptionRequestForger FORGER = new EncryptionRequestForger();
    private static final Logger logger = LoggerFactory.getLogger( UpstreamConnection.class );
    private final ProxProx proxProx;

    // AbstractConnection stuff
    private final Connection connection;
    private Thread connectionReadThread;
    private PostProcessWorker postProcessWorker;

    // Downstream
    private DownstreamConnection currentDownStream;
    private DownstreamConnection pendingDownStream;

    // User data
    private UUID uuid;
    private String username;
    private boolean valid;
    private String xboxId;
    private JSONObject skinData;

    @Setter
    @Getter
    private EntityRewriter entityRewriter;
    private int protocolVersion;

    // Last known good server
    private ServerDataHolder lastKnownServer;
    @Getter
    private boolean firstServer = true;

    // Metadata
    private Map<String, Object> metaData = new ConcurrentHashMap<>();

    // View distance
    @Getter
    private int viewDistance = 6;

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
        this.postProcessWorker = new PostProcessWorker( connection );
        this.connectionReadThread = this.proxProx.getNewClientConnectionThread( new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName( "UpStream unknown (" + connection.getGuid() + ") [Packet Read/Rewrite]" );

                while ( connection.isConnected() ) {
                    EncapsulatedPacket data = connection.receive();
                    if ( data == null ) {
                        try {
                            Thread.sleep( 10 );
                        } catch ( InterruptedException e ) {
                            e.printStackTrace();
                        }

                        continue;
                    }

                    PacketBuffer buffer = new PacketBuffer( data.getPacketData(), 0 );
                    if ( buffer.getRemaining() <= 0 ) {
                        // Malformed packet:
                        return;
                    }

                    // Do we want to handle it?
                    handlePacket( buffer, data.getReliability(), data.getOrderingChannel(), false );
                }
            }
        } );
        this.connectionReadThread.start();
    }

    @Override
    protected void handlePacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batched ) {
        // Grab the packet ID from the packet's data
        byte packetId = buffer.readByte();
        if ( packetId != Protocol.PACKET_BATCH ) {
            buffer.readShort();
        }

        int pos = buffer.getPosition();

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.PACKET_BATCH:
                this.handleBatchPacket( buffer, reliability, orderingChannel, batched );
                break;

            case Protocol.PACKET_SET_CHUNK_RADIUS:
                PacketSetChunkRadius setChunkRadius = new PacketSetChunkRadius();
                setChunkRadius.deserialize( buffer );

                this.viewDistance = setChunkRadius.getChunkRadius();
                logger.debug( "New view distance: " + this.viewDistance );

                if ( currentDownStream != null ) {
                    currentDownStream.send( setChunkRadius );
                }

                break;

            case Protocol.PACKET_LOGIN:
                // Parse the login packet
                PacketLogin packet = new PacketLogin();
                packet.deserialize( buffer );

                // Check versions
                protocolVersion = packet.getProtocol();
                logger.debug( "Trying to login with protocol version: " + packet.getProtocol() );
                if ( packet.getProtocol() != Protocol.MINECRAFT_PE_PROTOCOL_VERSION
                        && packet.getProtocol() != Protocol.MINECRAFT_PE_BETA_PROTOCOL_VERSION ) {
                    String message;
                    if ( packet.getProtocol() < Protocol.MINECRAFT_PE_PROTOCOL_VERSION ) {
                        message = "disconnectionScreen.outdatedClient";
                        sendPlayState( PacketPlayState.PlayState.LOGIN_FAILED_CLIENT );
                    } else {
                        message = "disconnectionScreen.outdatedServer";
                        sendPlayState( PacketPlayState.PlayState.LOGIN_FAILED_SERVER );
                    }

                    disconnect( message );
                    return;
                }

                // More data please
                ByteBuffer byteBuffer = ByteBuffer.wrap( packet.getPayload() );
                byteBuffer.order( ByteOrder.LITTLE_ENDIAN );
                byte[] stringBuffer = new byte[byteBuffer.getInt()];
                byteBuffer.get( stringBuffer );

                // Parse chain and validate
                String jwt = new String( stringBuffer );
                JSONObject json;
                try {
                    json = parseJwtString( jwt );
                } catch ( ParseException e ) {
                    e.printStackTrace();
                    return;
                }

                Object jsonChainRaw = json.get( "chain" );
                if ( jsonChainRaw == null || !( jsonChainRaw instanceof JSONArray ) ) {
                    return;
                }

                MojangChainValidator chainValidator = new MojangChainValidator();
                JSONArray jsonChain = (JSONArray) jsonChainRaw;
                for ( Object jsonTokenRaw : jsonChain ) {
                    if ( jsonTokenRaw instanceof String ) {
                        try {
                            JwtToken token = JwtToken.parse( (String) jsonTokenRaw );
                            chainValidator.addToken( token );
                        } catch ( IllegalArgumentException e ) {
                            e.printStackTrace();
                        }
                    }
                }

                this.valid = chainValidator.validate();

                // When we are in online mode kick all invalid users
                if ( this.proxProx.getConfig().isOnlineMode() && !this.valid ) {
                    disconnect( "Only valid xbox live accounts can join. Please login" );
                    return;
                }

                // Log xbox accounts if needed
                if ( this.valid ) {
                    logger.info( "Got valid XBOX Live Account ID: " + chainValidator.getXboxId() );
                    this.xboxId = chainValidator.getXboxId();
                }

                // Check for uuid or name equals
                for ( Player upstreamConnection : this.proxProx.getPlayers() ) {
                    if ( chainValidator.getUUID().equals( upstreamConnection.getUUID() ) || chainValidator.getUsername().equals( upstreamConnection.getName() ) ) {
                        disconnect( "Logged in from another location" );
                        return;
                    }
                }

                this.uuid = chainValidator.getUUID();
                this.username = chainValidator.getUsername();

                // Parse skin
                byte[] skin = new byte[byteBuffer.getInt()];
                byteBuffer.get( skin );

                JwtToken skinToken = JwtToken.parse( new String( skin ) );

                try {
                    skinToken.validateSignature( JwtAlgorithm.ES384, chainValidator.getTrustedKeys().get( skinToken.getHeader().getProperty( "x5u" ) ) );
                    this.skinData = skinToken.getClaims();
                } catch ( JwtSignatureException e ) {
                    e.printStackTrace();
                }

                send( new PacketPlayState( PacketPlayState.PlayState.LOGIN_SUCCESS ) );

                PlayerLoginEvent event = this.proxProx.getPluginManager().callEvent( new PlayerLoginEvent( this ) );
                if ( event.isCancelled() ) {
                    disconnect( event.getDisconnectReason() );
                    return;
                }

                logger.info( "Logged in as " + chainValidator.getUsername() + " (UUID: " + chainValidator.getUUID().toString() + ")" );
                Thread.currentThread().setName( "UpStream " + getUUID() + " [Packet Read/Rewrite]" );
                this.state = ConnectionState.CONNECTED;

                this.proxProx.addPlayer( this );

                // We need to start encryption first
                this.encryptionHandler = new EncryptionHandler();
                this.encryptionHandler.supplyClientKey( chainValidator.getClientPublicKey() );
                if ( this.encryptionHandler.beginClientsideEncryption() ) {
                    // Forge a JWT
                    String encryptionRequestJWT = FORGER.forge( encryptionHandler.getServerPublic(), encryptionHandler.getServerPrivate(), encryptionHandler.getClientSalt() );

                    PacketEncryptionRequest packetEncryptionRequest = new PacketEncryptionRequest();
                    packetEncryptionRequest.setJwt( encryptionRequestJWT );
                    send( packetEncryptionRequest );
                } else {
                    disconnect( "Error in creating AES token" );
                }

                break;

            case Protocol.PACKET_ENCRYPTION_READY:
                this.postProcessWorker.setEncryptionHandler( this.encryptionHandler );

                // Send resource pack stuff
                PacketResourcePacksInfo packetResourcePacksInfo = new PacketResourcePacksInfo();
                packetResourcePacksInfo.setMustAccept( false );
                packetResourcePacksInfo.setBehaviourPackEntries( new ArrayList<>() );
                packetResourcePacksInfo.setResourcePackEntries( new ArrayList<>() );
                send( packetResourcePacksInfo );

                break;

            case Protocol.PACKET_RESOURCEPACK_RESPONSE:
                PacketResourcePackResponse resourcePackResponse = new PacketResourcePackResponse();
                resourcePackResponse.deserialize( buffer );

                switch ( resourcePackResponse.getStatus() ) {
                    case HAVE_ALL_PACKS:
                        PacketResourcePackStack resourcePackStack = new PacketResourcePackStack();
                        resourcePackStack.setMustAccept( false );
                        resourcePackStack.setBehaviourPackEntries( new ArrayList<>() );
                        resourcePackStack.setResourcePackEntries( new ArrayList<>() );
                        send( resourcePackStack );
                        break;
                    case COMPLETED:
                        this.connect( this.proxProx.getConfig().getDefaultServer().getIp(), this.proxProx.getConfig().getDefaultServer().getPort() );
                        break;
                }

                break;

            default:
                if ( this.currentDownStream != null ) {
                    if ( this.entityRewriter.getCurrentDownStreamId() != this.entityRewriter.getOwnId() ) {
                        buffer = this.entityRewriter.rewriteClientToServer( packetId, pos, buffer );
                    }

                    this.currentDownStream.send( packetId, buffer );
                }

                break;
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
            this.pendingDownStream.close( false );
            this.pendingDownStream = null;
        }

        this.pendingDownStream = new DownstreamConnection( this.proxProx, this, switchEvent.getTo().getIP(), switchEvent.getTo().getPort() );
    }

    public void sendPlayState( PacketPlayState.PlayState state ) {
        PacketPlayState packet = new PacketPlayState();
        packet.setState( state );
        this.send( packet );
    }

    @Override
    public boolean isValid() {
        return this.valid;
    }

    @Override
    public String getXboxId() {
        return this.xboxId;
    }

    @Override
    public InetSocketAddress getAddress() {
        return this.connection.getAddress();
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
    void onDownStreamConnected( DownstreamConnection downstreamConnection ) {
        // Close old connection and store new one
        if ( this.currentDownStream != null ) {
            this.lastKnownServer = new ServerDataHolder( this.currentDownStream.getIP(), this.currentDownStream.getPort() );
            this.currentDownStream.close( false );

            // Cleanup all entities
            for ( Long eID : this.currentDownStream.getSpawnedEntities() ) {
                send( new PacketRemoveEntity( eID ) );
                this.entityRewriter.removeEntity( eID );
            }

            // Loading screen (holy did this take long to figure out :D)
            send( new PacketChangeDimension( (byte) 0 ) );
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );
            send( new PacketChangeDimension( (byte) 1 ) );
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );

            move( 0, 4000, 0, 0, 0 );
            sendEmptyChunks();

            send( new PacketChangeDimension( (byte) 1 ) );
            send( new PacketPlayState( PacketPlayState.PlayState.SPAWN ) );
            send( new PacketChangeDimension( (byte) 0 ) );
            // There needs to be one additional spawn but the downstream server sends one so its ok

            this.currentDownStream = null;
        }

        // Send our handshake to the server -> this will trigger it to respond with a 0x03 ServerHandshake packet:
        MojangLoginForger mojangLoginForger = new MojangLoginForger();
        mojangLoginForger.setPublicKey( EncryptionHandler.PROXY_KEY_PAIR.getPublic() );
        mojangLoginForger.setUsername( this.username );
        mojangLoginForger.setUuid( this.uuid );
        mojangLoginForger.setSkinData( this.skinData );
        mojangLoginForger.setXuid( this.xboxId );

        String jwt = "{\"chain\":[\"" + mojangLoginForger.forge( EncryptionHandler.PROXY_KEY_PAIR.getPrivate() ) + "\"]}";
        String skin = mojangLoginForger.forgeSkin( EncryptionHandler.PROXY_KEY_PAIR.getPrivate() );

        // More data please
        ByteBuffer byteBuffer = ByteBuffer.allocate( jwt.length() + skin.length() + 8 );
        byteBuffer.order( ByteOrder.LITTLE_ENDIAN );
        byteBuffer.putInt( jwt.length() );
        byteBuffer.put( jwt.getBytes() );

        // We need the skin
        byteBuffer.putInt( skin.length() );
        byteBuffer.put( skin.getBytes() );

        PacketLogin packetClientHandshake = new PacketLogin();
        packetClientHandshake.setProtocol( protocolVersion );
        packetClientHandshake.setPayload( byteBuffer.array() );
        downstreamConnection.send( packetClientHandshake );
    }

    private void sendEmptyChunks() {
        for ( int x = -3; x < 3; x++ ) {
            for ( int z = -3; z < 3; z++ ) {
                PacketFullChunkData chunk = new PacketFullChunkData();
                chunk.setChunkX( x );
                chunk.setChunkZ( z );
                chunk.setChunkData( EMPTY_CHUNK.getBuffer() );
                send( chunk );
            }
        }
    }

    public void move( float x, float y, float z, float yaw, float pitch ) {
        PacketMovePlayer packet = new PacketMovePlayer();
        packet.setEntityId( this.entityRewriter.getOwnId() );
        packet.setX( x );
        packet.setY( y + 1.62f );
        packet.setZ( z );
        packet.setYaw( yaw );
        packet.setPitch( pitch );
        packet.setMode( (byte) 2 );
        send( packet );
    }

    /**
     * Send a packet to the client
     *
     * @param packet The packet which should be send
     */
    public void send( Packet packet ) {
        if ( !( packet instanceof PacketBatch ) && packet.mustBeInBatch() ) {
            this.postProcessWorker.sendPacket( packet );
        } else {
            PacketBuffer buffer = new PacketBuffer( 64 );
            buffer.writeByte( packet.getId() );
            packet.serialize( buffer );

            this.connection.send( PacketReliability.RELIABLE_ORDERED, packet.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
        }
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
        return this.skinData.containsKey( "LanguageCode" ) ?
                Locale.forLanguageTag( ( (String) this.skinData.get( "LanguageCode" ) ).replace( "_", "-" ) ) :
                Locale.US;
    }

    @Override
    public void kick( String reason ) {
        disconnect( reason );
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

        // Send Loggedin Event on first downstram connect
        if ( this.firstServer ) {
            this.proxProx.getPluginManager().callEvent( new PlayerLoggedinEvent( this ) );
            this.firstServer = false;
        }

        this.currentDownStream = downstreamConnection;

        PacketSetDifficulty setDifficulty = new PacketSetDifficulty();
        setDifficulty.setDifficulty( this.currentDownStream.getDifficulty() );
        send( setDifficulty );

        PacketSetGamemode gamemode = new PacketSetGamemode();
        gamemode.setGameMode( this.currentDownStream.getGamemode() );
        send( gamemode );
    }

    public void resetPendingDownStream() {
        this.pendingDownStream = null;
    }

    public void send( byte packetId, PacketBuffer buffer ) {
        byte[] data = new byte[buffer.getRemaining()];
        buffer.readBytes( data );

        this.postProcessWorker.sendPacket( new Packet( packetId ) {
            @Override
            public void serialize( PacketBuffer pktBuffer ) {
                pktBuffer.writeBytes( data );
            }

            @Override
            public void deserialize( PacketBuffer buffer ) {

            }

            @Override
            public String toString() {
                return Integer.toHexString( packetId & 0xFF );
            }
        } );
    }

    public boolean isConnected() {
        return this.connection.isConnected();
    }

}
