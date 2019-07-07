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
import io.gomint.proxprox.ProxProxProxy;
import io.gomint.proxprox.api.ChatColor;
import io.gomint.proxprox.api.data.ServerDataHolder;
import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.entity.Server;
import io.gomint.proxprox.api.event.PermissionCheckEvent;
import io.gomint.proxprox.api.event.PlayerLoggedinEvent;
import io.gomint.proxprox.api.event.PlayerLoginEvent;
import io.gomint.proxprox.api.event.PlayerSwitchEvent;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.debug.Debugger;
import io.gomint.proxprox.api.inventory.ItemStack;
import io.gomint.proxprox.jwt.EncryptionRequestForger;
import io.gomint.proxprox.jwt.JwtAlgorithm;
import io.gomint.proxprox.jwt.JwtSignatureException;
import io.gomint.proxprox.jwt.JwtToken;
import io.gomint.proxprox.jwt.MojangChainValidator;
import io.gomint.proxprox.jwt.MojangLoginForger;
import io.gomint.proxprox.network.protocol.PacketDisconnect;
import io.gomint.proxprox.network.protocol.PacketEncryptionRequest;
import io.gomint.proxprox.network.protocol.PacketLogin;
import io.gomint.proxprox.network.protocol.PacketMobEffect;
import io.gomint.proxprox.network.protocol.PacketMobEquipment;
import io.gomint.proxprox.network.protocol.PacketMovePlayer;
import io.gomint.proxprox.network.protocol.PacketPlayState;
import io.gomint.proxprox.network.protocol.PacketPlayerlist;
import io.gomint.proxprox.network.protocol.PacketRemoveEntity;
import io.gomint.proxprox.network.protocol.PacketRemoveObjective;
import io.gomint.proxprox.network.protocol.PacketResourcePackResponse;
import io.gomint.proxprox.network.protocol.PacketResourcePackStack;
import io.gomint.proxprox.network.protocol.PacketResourcePacksInfo;
import io.gomint.proxprox.network.protocol.PacketSetChunkRadius;
import io.gomint.proxprox.network.protocol.PacketSetGamemode;
import io.gomint.proxprox.network.protocol.PacketSetScore;
import io.gomint.proxprox.network.protocol.PacketText;
import io.gomint.proxprox.network.tcp.protocol.UpdatePingPacket;
import io.gomint.proxprox.scheduler.SyncScheduledTask;
import io.gomint.proxprox.util.EffectManager;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( of = { "uuid" }, callSuper = false )
public class UpstreamConnection extends AbstractConnection implements Player {

    private static final EncryptionRequestForger FORGER = new EncryptionRequestForger();
    private static final Logger LOGGER = LoggerFactory.getLogger( UpstreamConnection.class );
    private final ProxProxProxy proxProx;

    // AbstractConnection stuff
    private final Connection connection;
    private BlockingQueue<PacketBuffer> packetQueue = new LinkedBlockingQueue<>();

    // Downstream
    private DownstreamConnection currentDownStream;
    private DownstreamConnection pendingDownStream;

    // User data
    private UUID uuid;
    private String username;
    private boolean valid;
    private String xboxId;
    private JSONObject skinData;
    @Getter
    private Debugger debugger;
    @Getter
    private boolean localPlayerInit;

    @Getter
    private EntityRewriter entityRewriter;
    @Getter
    private EffectManager effectManager = new EffectManager();
    @Getter
    private int protocolVersion;

    // Last known good server
    private ServerDataHolder lastKnownServer;
    @Getter
    @Setter
    private boolean firstServer = true;

    // Metadata
    private Map<String, Object> metaData = new ConcurrentHashMap<>();
    private String disconnect = null;
    @Getter
    private int viewDistance = -1;
    private boolean disconnectNotified;

    private float lastUpdate;

    /**
     * Create a new AbstractConnection wrapper which represents the communication from User <-> Proxy
     *
     * @param proxProx   The proxy instance from which this connection comes from
     * @param connection The established connection
     */
    public UpstreamConnection(ProxProxProxy proxProx, Connection connection ) {
        super();

        this.entityRewriter = new EntityRewriter( this );

        this.proxProx = proxProx;
        this.connection = connection;

        this.debugger = new Debugger( connection.getGuid() );
        this.setup();
    }

    @Override
    protected void setup() {
        this.initDecompressor();
        super.setup();

        // Create thread for reading data
        this.connection.addDataProcessor( new Function<EncapsulatedPacket, EncapsulatedPacket>() {
            @Override
            public EncapsulatedPacket apply( EncapsulatedPacket data ) {
                PacketBuffer buffer = new PacketBuffer( data.getPacketData(), 0 );
                if ( buffer.getRemaining() <= 0 ) {
                    // Malformed packet:
                    LOGGER.warn( "Got 0 length packet" );
                    return null;
                }

                // Check if packet is batched
                byte packetId = buffer.readByte();
                if ( packetId == Protocol.PACKET_BATCH ) {
                    // Decompress and decrypt
                    byte[] pureData = handleBatchPacket( buffer );
                    EncapsulatedPacket newPacket = new EncapsulatedPacket();
                    newPacket.setPacketData( pureData );
                    return newPacket;
                }

                return data;
            }
        } );
    }

    @Override
    protected void handlePacket( PacketBuffer buffer ) {
        // Grab the packet ID from the packet's data
        int rawId = buffer.readUnsignedVarInt();
        byte packetId = (byte) rawId;

        // There is some data behind the packet id when non batched packets (2 bytes)
        if ( packetId == Protocol.PACKET_BATCH ) {
            LOGGER.error( "Malformed batch packet payload: Batch packets are not allowed to contain further batch packets" );
        }

        int pos = buffer.getPosition();

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.PACKET_BATCH:
                this.disconnect( "Batch inside batch" );
                break;

            case Protocol.PACKET_LOGIN:
                // Parse the login packet
                PacketLogin packet = new PacketLogin();
                packet.deserialize( buffer, -1 );

                LOGGER.info( "Login version number: " + packet.getProtocol() );

                // Check versions
                this.protocolVersion = packet.getProtocol();
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
                if ( !( jsonChainRaw instanceof JSONArray ) ) {
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
                    LOGGER.info( "Got valid XBOX Live Account ID: " + chainValidator.getXboxId() );
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
                    skinToken.validateSignature( JwtAlgorithm.ES384, chainValidator.getClientPublicKey() );
                    this.skinData = skinToken.getClaims();
                } catch ( JwtSignatureException e ) {
                    e.printStackTrace();
                }

                PlayerLoginEvent event = this.proxProx.getPluginManager().callEvent( new PlayerLoginEvent( this ) );
                if ( event.isCancelled() ) {
                    disconnect( event.getDisconnectReason() );
                    return;
                }

                LOGGER.info( "Logged in as {} (UUID: {}; GUID: {})", chainValidator.getUsername(), chainValidator.getUUID(), connection.getGuid() );

                this.state = ConnectionState.CONNECTED;
                this.proxProx.addPlayer( this );

                if ( this.proxProx.getConfig().isDisableEncryption() ) {
                    send( new PacketPlayState( PacketPlayState.PlayState.LOGIN_SUCCESS ) );

                    // Send resource pack stuff
                    PacketResourcePacksInfo packetResourcePacksInfo = new PacketResourcePacksInfo();
                    packetResourcePacksInfo.setMustAccept( false );
                    packetResourcePacksInfo.setBehaviourPackEntries( new ArrayList<>() );
                    packetResourcePacksInfo.setResourcePackEntries( new ArrayList<>() );
                    send( packetResourcePacksInfo );

                    // Flush the queue once to get the disconnect out
                    this.update( 0.06f );
                } else {
                    // We need to start encryption first
                    this.proxProx.getWatchdog().add( 500, TimeUnit.MILLISECONDS );

                    this.encryptionHandler = new EncryptionHandler();
                    this.encryptionHandler.supplyClientKey( chainValidator.getClientPublicKey() );
                    if ( this.encryptionHandler.beginClientsideEncryption() ) {
                        // Forge a JWT
                        String encryptionRequestJWT = FORGER.forge( encryptionHandler.getServerPublic(), encryptionHandler.getServerPrivate(), encryptionHandler.getClientSalt() );
                        LOGGER.info( "Crafted JWT for client: {}", encryptionRequestJWT );

                        PacketEncryptionRequest packetEncryptionRequest = new PacketEncryptionRequest();
                        packetEncryptionRequest.setJwt( encryptionRequestJWT );
                        send( packetEncryptionRequest );

                        // Flush the queue once to get the disconnect out
                        this.update( 0.06f );
                    } else {
                        disconnect( "Error in creating AES token" );
                    }

                    this.proxProx.getWatchdog().done();
                }

                break;

            case Protocol.PACKET_ENCRYPTION_READY:
                this.state = ConnectionState.ENCRYPTED;

                send( new PacketPlayState( PacketPlayState.PlayState.LOGIN_SUCCESS ) );

                // Send resource pack stuff
                PacketResourcePacksInfo packetResourcePacksInfo = new PacketResourcePacksInfo();
                packetResourcePacksInfo.setMustAccept( false );
                packetResourcePacksInfo.setBehaviourPackEntries( new ArrayList<>() );
                packetResourcePacksInfo.setResourcePackEntries( new ArrayList<>() );
                send( packetResourcePacksInfo );

                break;

            case Protocol.PACKET_RESOURCEPACK_RESPONSE:
                PacketResourcePackResponse resourcePackResponse = new PacketResourcePackResponse();
                resourcePackResponse.deserialize( buffer, this.protocolVersion );

                switch ( resourcePackResponse.getStatus() ) {
                    case HAVE_ALL_PACKS:
                        PacketResourcePackStack resourcePackStack = new PacketResourcePackStack();
                        resourcePackStack.setMustAccept( false );
                        resourcePackStack.setBehaviourPackEntries( new ArrayList<>() );
                        resourcePackStack.setResourcePackEntries( new ArrayList<>() );
                        send( resourcePackStack );
                        break;
                    case REFUSED:
                    case COMPLETED:
                        this.connect( this.proxProx.getConfig().getDefaultServer().getIp(), this.proxProx.getConfig().getDefaultServer().getPort() );
                        break;
                }

                break;

            case Protocol.PACKET_SET_CHUNK_RADIUS:
                PacketSetChunkRadius packetSetChunkRadius = new PacketSetChunkRadius();
                packetSetChunkRadius.deserialize( buffer, this.protocolVersion );

                this.viewDistance = packetSetChunkRadius.getChunkRadius();

                if ( this.currentDownStream != null ) {
                    this.currentDownStream.send( packetSetChunkRadius );
                }

                if ( this.firstServer && this.pendingDownStream != null ) {
                    this.pendingDownStream.send( packetSetChunkRadius );
                }

                break;

            case Protocol.PACKET_SET_LOCAL_PLAYER_INITIALIZED:
                this.localPlayerInit = true;
                if ( this.currentDownStream != null ) {
                    this.currentDownStream.send( packetId, buffer );
                }

                break;

            default:
                if ( this.currentDownStream != null ) {
                    buffer = this.entityRewriter.rewriteClientToServer( packetId, pos, buffer );
                    this.currentDownStream.send( packetId, buffer );
                }

                break;
        }
    }

    @Override
    public void connect( String ip, int port ) {
        // Event first
        PlayerSwitchEvent switchEvent = this.proxProx.getPluginManager().callEvent( new PlayerSwitchEvent( this, this.currentDownStream, new ServerDataHolder( ip, port ) ) );

        // Check if we have a pending connection
        if ( this.pendingDownStream != null ) {
            // Disconnect
            this.pendingDownStream.close( false, "Aborting login" );
            this.pendingDownStream = null;
        }

        LOGGER.debug( "New connection to " + switchEvent.getTo().getIP() + ":" + switchEvent.getTo().getPort() );
        this.pendingDownStream = new DownstreamConnection( this.proxProx, this, switchEvent.getTo().getIP(), switchEvent.getTo().getPort() );
    }

    void sendPlayState( PacketPlayState.PlayState state ) {
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
        LOGGER.info( "Connected to " + downstreamConnection.getIP() + ":" + downstreamConnection.getPort() );

        // Close old connection and store new one
        if ( this.currentDownStream != null ) {
            this.lastKnownServer = new ServerDataHolder( this.currentDownStream.getIP(), this.currentDownStream.getPort() );
            this.currentDownStream.close( false, "Transfer to new server" );

            // Cleanup all player list
            PacketPlayerlist packetPlayerlist = new PacketPlayerlist();
            packetPlayerlist.setMode( (byte) 1 );

            packetPlayerlist.setEntries( new ArrayList<>() );

            for ( UUID entryId : this.currentDownStream.getPlayerListEntries() ) {
                packetPlayerlist.getEntries().add( new PacketPlayerlist.Entry( entryId ) );
            }

            send( packetPlayerlist );
            this.currentDownStream.getPlayerListEntries().clear();

            // Cleanup scoreboards
            if ( !this.currentDownStream.getKnownScores().isEmpty() ) {
                PacketSetScore packetSetScore = new PacketSetScore();
                packetSetScore.setType( (byte) 1 );

                packetSetScore.setEntries( new ArrayList<>() );

                for ( Long scoreId : this.currentDownStream.getKnownScores() ) {
                    packetSetScore.getEntries().add( new PacketSetScore.ScoreEntry( scoreId, "", 0 ) );
                }

                send( packetSetScore );
                this.currentDownStream.getKnownScores().clear();
            }

            for ( String objectiveName : this.currentDownStream.getObjectiveNames() ) {
                PacketRemoveObjective packetRemoveObjective = new PacketRemoveObjective();
                packetRemoveObjective.setObjectiveName( objectiveName );

                send( packetRemoveObjective );
            }

            this.currentDownStream.getObjectiveNames().clear();

            // Cleanup all entities
            for ( Long eID : this.currentDownStream.getSpawnedEntities() ) {
                send( new PacketRemoveEntity( eID ) );
                this.entityRewriter.removeServerEntity( eID );
            }

            // Remove all effects
            for ( Integer effectId : this.effectManager.getEffects() ) {
                PacketMobEffect mobEffect = new PacketMobEffect();
                mobEffect.setEntityId( this.entityRewriter.getOwnId() );
                mobEffect.setAction( PacketMobEffect.EVENT_REMOVE );
                mobEffect.setEffectId( effectId );
                send( mobEffect );
            }

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
        packetClientHandshake.setProtocol( this.protocolVersion );
        packetClientHandshake.setPayload( byteBuffer.array() );
        downstreamConnection.send( packetClientHandshake );
    }

    void setGameMode( int gamemode ) {
        PacketSetGamemode packet = new PacketSetGamemode();
        packet.setGameMode( gamemode );
        send( packet );
    }

    void move( float x, float y, float z, float yaw, float pitch ) {
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
        PacketBuffer buffer = new PacketBuffer( 4 );

        LOGGER.debug( "Sending packet {}", Integer.toHexString( packet.getId() & 0xFF ) );

        if ( packet.mustBeInBatch() ) {
            packet.serializeHeader( buffer );
            packet.serialize( buffer, this.protocolVersion );
            this.packetQueue.offer( buffer );
        } else {
            buffer.writeByte( packet.getId() );
            packet.serialize( buffer, this.protocolVersion );
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
        this.disconnect = reason;
        LOGGER.info( "Disconnected: {}", this.disconnect );

        // Flush the queue once to get the disconnect out
        this.update( 0.06f );
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

        // Send logged in Event on first downstream connect
        if ( this.firstServer ) {
            this.proxProx.getPluginManager().callEvent( new PlayerLoggedinEvent( this ) );
        } else {
            PacketMobEquipment packetMobEquipment = new PacketMobEquipment();
            packetMobEquipment.setEntityId( this.entityRewriter.getOwnId() );
            packetMobEquipment.setSelectedSlot( (byte) 0 );
            packetMobEquipment.setSlot( (byte) 0 );
            packetMobEquipment.setStack( new ItemStack( 0, (short) 0, 0 ) );
            this.send( packetMobEquipment );
        }

        this.currentDownStream = downstreamConnection;
    }

    public void resetPendingDownStream() {
        this.pendingDownStream = null;
    }

    public void resetCurrentDownStream() {
        this.currentDownStream = null;
    }

    public void send( byte packetId, PacketBuffer buffer ) {
        byte[] data = new byte[buffer.getRemaining()];
        buffer.readBytes( data );

        PacketBuffer packetBuffer = new PacketBuffer( data.length + 3 );
        packetBuffer.writeByte( packetId );
        packetBuffer.writeBytes( data );

        this.packetQueue.offer( packetBuffer );
    }

    public boolean isConnected() {
        return this.connection.isConnected();
    }

    public void updateIncoming() {
        // Update downstream first
        if ( this.currentDownStream != null ) {
            this.currentDownStream.updateIncoming( this.currentDownStream.getConnection() );
        }

        if ( this.pendingDownStream != null ) {
            this.pendingDownStream.updateIncoming( this.pendingDownStream.getConnection() );
        }

        // It seems that movement is sent last, but we need it first to check if player position of other packets align
        List<PacketBuffer> packetBuffers = null;

        EncapsulatedPacket packetData;
        while ( ( packetData = this.connection.receive() ) != null ) {
            if ( packetBuffers == null ) {
                packetBuffers = new ArrayList<>();
            }

            packetBuffers.add( new PacketBuffer( packetData.getPacketData(), 0 ) );
        }

        if ( packetBuffers != null ) {
            for ( PacketBuffer buffer : packetBuffers ) {
                while ( buffer.getRemaining() > 0 ) {
                    int packetLength = buffer.readUnsignedVarInt();

                    byte[] payData = new byte[packetLength];
                    buffer.readBytes( payData );
                    PacketBuffer pktBuf = new PacketBuffer( payData, 0 );
                    this.handlePacket( pktBuf );

                    if ( pktBuf.getRemaining() > 0 ) {
                        LOGGER.debug( "Malformed batch packet payload: Could not read enclosed packet data correctly: 0x{} remaining {} bytes", Integer.toHexString( payData[0] ), pktBuf.getRemaining() );
                        return;
                    }
                }
            }
        }
    }

    public void update( float dT ) {
        // Send packets
        if ( !this.proxProx.getConfig().isUseTCP() || this.currentDownStream == null ) {
            this.flushSendQueue( dT );
        }

        // Disconnect if needed
        if ( this.disconnect != null && !this.disconnectNotified ) {
            // Delay closing connection so the client has enough time to react
            ProxProxProxy.getInstance().getSyncTaskManager().addTask(new SyncScheduledTask(() -> {
                UpstreamConnection.this.connection.disconnect( UpstreamConnection.this.disconnect );
            }, 5, -1, TimeUnit.SECONDS ) );

            this.disconnectNotified = true;

            if ( this.pendingDownStream != null ) {
                this.pendingDownStream.disconnect( this.disconnect );
                this.pendingDownStream = null;
            }

            if ( this.currentDownStream != null ) {
                this.currentDownStream.disconnect( this.disconnect );
                this.currentDownStream = null;
            }
        }

        // Update downstream ping
        if ( this.proxProx.getConfig().isUseTCP() && this.currentDownStream != null && this.currentDownStream.getTcpConnection() != null ) {
            UpdatePingPacket pingPacket = new UpdatePingPacket();
            pingPacket.setPing( (int) this.connection.getPing() );
            this.currentDownStream.getTcpConnection().send( pingPacket );
        }
    }

    public void flushSendQueue( float dT ) {
        if( this.connection.isDisconnecting()
            || !this.connection.isConnected()
            || this.state == ConnectionState.DISCONNECTED ) {
            return;
        }

        this.lastUpdate += dT;

        if ( 0.05f - this.lastUpdate < 0.0000001f ) {
            if ( !this.packetQueue.isEmpty() ) {
                List<PacketBuffer> drained = new ArrayList<>();
                this.packetQueue.drainTo( drained );
                this.executor.addWork( this, drained );
            }

            this.lastUpdate = 0;
        }
    }

    @Override
    public void close() {
        this.packetQueue.clear();
        super.close();
    }
}
