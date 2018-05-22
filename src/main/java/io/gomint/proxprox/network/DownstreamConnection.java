/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.*;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.entity.Server;
import io.gomint.proxprox.api.event.PlayerSwitchedEvent;
import io.gomint.proxprox.api.event.ServerKickedPlayerEvent;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.api.network.PacketSender;
import io.gomint.proxprox.jwt.JwtSignatureException;
import io.gomint.proxprox.jwt.JwtToken;
import io.gomint.proxprox.network.protocol.*;
import io.gomint.proxprox.network.protocol.type.ResourceResponseStatus;
import io.gomint.proxprox.network.tcp.ConnectionHandler;
import io.gomint.proxprox.network.tcp.Initializer;
import io.gomint.proxprox.network.tcp.protocol.WrappedMCPEPacket;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.security.Key;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( of = { "ip", "port" }, callSuper = false )
public class DownstreamConnection extends AbstractConnection implements Server, PacketSender {

    private static final Logger LOGGER = LoggerFactory.getLogger( DownstreamConnection.class );

    // Needed connection data to reach the server
    private String ip;
    private int port;

    // Client connection
    private ClientSocket connection;
    @Getter
    private ConnectionHandler tcpConnection;
    private boolean manualClose;

    // Upstream
    @Getter
    private UpstreamConnection upstreamConnection;

    // Proxy instance
    private ProxProx proxProx;

    // Entities
    private Set<Long> spawnedEntities = Collections.synchronizedSet( new HashSet<>() );
    @Getter
    private float spawnX;
    @Getter
    private float spawnY;
    @Getter
    private float spawnZ;
    @Getter
    private float spawnYaw;
    @Getter
    private float spawnPitch;

    /**
     * Create a new AbstractConnection to a server.
     *
     * @param proxProx           The proxy instance
     * @param upstreamConnection The upstream connection which requested to connect to this downstream
     * @param ip                 The ip of the server we want to connect to
     * @param port               The port of the server we want to connect to
     */
    DownstreamConnection( ProxProx proxProx, UpstreamConnection upstreamConnection, String ip, int port ) {
        this.upstreamConnection = upstreamConnection;
        this.proxProx = proxProx;

        this.ip = ip;
        this.port = port;

        // Check if we use UDP or TCP for downstream connections
        if ( proxProx.getConfig().isUseTCP() ) {
            io.netty.bootstrap.Bootstrap bootstrap = Initializer.buildBootstrap( this.upstreamConnection, new Consumer<ConnectionHandler>() {
                @Override
                public void accept( ConnectionHandler connectionHandler ) {
                    DownstreamConnection.this.tcpConnection = connectionHandler;

                    // There are no batches in TCP
                    connectionHandler.onData( DownstreamConnection.this::handlePacket );

                    connectionHandler.whenDisconnected( new Consumer<Void>() {
                        @Override
                        public void accept( Void aVoid ) {
                            if ( upstreamConnection.isConnected() ) {
                                LOGGER.info( "Disconnected downstream..." );
                                if ( !DownstreamConnection.this.manualClose ) {
                                    DownstreamConnection.this.close( true );

                                    // Check if we need to disconnect upstream
                                    if ( DownstreamConnection.this.equals( upstreamConnection.getDownStream() ) ) {
                                        if ( upstreamConnection.getPendingDownStream() != null || upstreamConnection.connectToLastKnown() ) {
                                            return;
                                        } else {
                                            upstreamConnection.disconnect( "The Server has gone down" );
                                        }
                                    } else {
                                        upstreamConnection.resetPendingDownStream();
                                    }
                                }
                            }
                        }
                    } );

                    DownstreamConnection.this.upstreamConnection.onDownStreamConnected( DownstreamConnection.this );
                }
            } );
            bootstrap.connect( this.ip, this.port ).addListener( new ChannelFutureListener() {
                @Override
                public void operationComplete( ChannelFuture channelFuture ) throws Exception {
                    if ( !channelFuture.isSuccess() ) {
                        LOGGER.warn( "Could not connect to {}:{}", DownstreamConnection.this.ip, DownstreamConnection.this.port, channelFuture.cause() );
                        DownstreamConnection.this.upstreamConnection.resetPendingDownStream();
                    }
                }
            } );
        } else {
            this.connection = new ClientSocket();
            this.connection.setMojangModificationEnabled( true );
            this.connection.setEventHandler( new SocketEventHandler() {
                @Override
                public void onSocketEvent( Socket socket, SocketEvent socketEvent ) {
                    LOGGER.debug( "Got socketEvent: " + socketEvent.getType().name() );
                    switch ( socketEvent.getType() ) {
                        case CONNECTION_ATTEMPT_SUCCEEDED:
                            // We got accepted *yay*
                            DownstreamConnection.this.setup();
                            DownstreamConnection.this.upstreamConnection.onDownStreamConnected( DownstreamConnection.this );
                            break;

                        case CONNECTION_CLOSED:
                        case CONNECTION_DISCONNECTED:
                            LOGGER.info( "Disconnected downstream..." );
                            if ( !DownstreamConnection.this.manualClose ) {
                                DownstreamConnection.this.close( true );

                                // Check if we need to disconnect upstream
                                if ( DownstreamConnection.this.equals( upstreamConnection.getDownStream() ) ) {
                                    if ( upstreamConnection.getPendingDownStream() != null || upstreamConnection.connectToLastKnown() ) {
                                        return;
                                    } else {
                                        upstreamConnection.disconnect( "The Server has gone down" );
                                    }
                                } else {
                                    upstreamConnection.resetPendingDownStream();
                                }
                            }

                            break;

                        default:
                            break;
                    }
                }
            } );

            try {
                this.connection.initialize();
            } catch ( SocketException e ) {
                LOGGER.warn( "Could not connect to {}:{}", this.ip, this.port, e );
            }

            this.connection.connect( ip, port );
        }
    }

    @Override
    protected void setup() {
        super.setup();

        this.connection.getConnection().addDataProcessor( new Function<EncapsulatedPacket, EncapsulatedPacket>() {
            @Override
            public EncapsulatedPacket apply( EncapsulatedPacket data ) {
                PacketBuffer buffer = new PacketBuffer( data.getPacketData(), 0 );
                if ( buffer.getRemaining() <= 0 ) {
                    // Malformed packet:
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

    public void updateIncoming() {
        if ( ProxProx.instance.getConfig().isUseTCP() ) {
            return;
        }

        // It seems that movement is sent last, but we need it first to check if player position of other packets align
        List<PacketBuffer> packetBuffers = null;

        EncapsulatedPacket packetData;
        while ( ( packetData = this.getConnection().receive() ) != null ) {
            if ( packetBuffers == null ) {
                packetBuffers = new ArrayList<>();
            }

            packetBuffers.add( new PacketBuffer( packetData.getPacketData(), 0 ) );
        }

        if ( packetBuffers != null ) {
            for ( PacketBuffer buffer : packetBuffers ) {
                // CHECKSTYLE:OFF
                try {
                    this.handlePacket( buffer );
                } catch ( Exception e ) {
                    LOGGER.error( "Error whilst processing packet: ", e );
                }
                // CHECKSTYLE:ON
            }
        }
    }

    @Override
    protected void handlePacket( PacketBuffer buffer ) {
        // Grab the packet ID from the packet's data
        byte packetId = buffer.readByte();
        if ( packetId != Protocol.PACKET_BATCH ) {
            buffer.readShort();
        }

        int pos = buffer.getPosition();

        LOGGER.debug( "Got packet {}. Upstream pending: {}, down: {}, this: {}", Integer.toHexString( packetId & 0xFF ), this.upstreamConnection.getPendingDownStream(), this.upstreamConnection.getDownStream(), this );

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.PACKET_BATCH:
                this.disconnect( "Batch inside batch" );
                break;

            case Protocol.PACKET_START_GAME:
                PacketStartGame startGame = new PacketStartGame();
                startGame.deserialize( buffer );

                if ( this.upstreamConnection.isFirstServer() ) {
                    this.upstreamConnection.getEntityRewriter().setOwnId( startGame.getRuntimeEntityId() );
                }

                this.upstreamConnection.getEntityRewriter().setCurrentDownStreamId( startGame.getRuntimeEntityId() );
                this.spawnX = startGame.getSpawnX();
                this.spawnY = startGame.getSpawnY();
                this.spawnZ = startGame.getSpawnZ();
                this.spawnYaw = startGame.getSpawnYaw();
                this.spawnPitch = startGame.getSpawnPitch();

                if ( this.upstreamConnection.isFirstServer() ) {
                    buffer.setPosition( pos );
                    this.upstreamConnection.send( packetId, buffer );
                } else {
                    this.upstreamConnection.move( this.getSpawnX(), this.getSpawnY(), this.getSpawnZ(),
                            this.getSpawnYaw(), this.getSpawnPitch() );

                    // Send chunk radius
                    if ( this.upstreamConnection.getViewDistance() > 0 ) {
                        PacketSetChunkRadius setChunkRadius = new PacketSetChunkRadius();
                        setChunkRadius.setChunkRadius( this.upstreamConnection.getViewDistance() );
                        send( setChunkRadius );
                    }
                }

                break;

            case Protocol.REMOVE_ENTITY_PACKET:
                PacketRemoveEntity removeEntity = new PacketRemoveEntity();
                removeEntity.deserialize( buffer );

                Long entityId = this.upstreamConnection.getEntityRewriter().removeEntity( removeEntity.getEntityId(), this );
                if ( entityId != null ) {
                    removeEntity.setEntityId( entityId );

                    if ( this.spawnedEntities.remove( entityId ) ) {
                        this.upstreamConnection.send( removeEntity );
                    }
                } else {
                    LOGGER.warn( "Could not remove entity with id {}", removeEntity.getEntityId() );
                }

                break;

            case Protocol.PACKET_ENTITY_METADATA:
                PacketEntityMetadata metadata = new PacketEntityMetadata();
                metadata.deserialize( buffer );

                metadata.setEntityId( this.upstreamConnection.getEntityRewriter().getReplacementId( metadata.getEntityId(), this ) );

                // Rewrite metadata if needed
                if ( metadata.getMetadata().has( 5 ) ) {
                    long replacementId = this.upstreamConnection.getEntityRewriter().getReplacementId( metadata.getMetadata().getLong( 5 ), this );
                    metadata.getMetadata().putLong( 5, replacementId );
                }

                if ( metadata.getMetadata().has( 6 ) ) {
                    long replacementId = this.upstreamConnection.getEntityRewriter().getReplacementId( metadata.getMetadata().getLong( 6 ), this );
                    metadata.getMetadata().putLong( 6, replacementId );
                }

                this.upstreamConnection.send( metadata );

                break;

            case Protocol.ADD_ITEM_ENTITY:
                PacketAddItem packetAddItem = new PacketAddItem();
                packetAddItem.deserialize( buffer );

                long addedId = this.upstreamConnection.getEntityRewriter().addEntity( packetAddItem.getEntityId(), this );
                packetAddItem.setEntityId( addedId );
                spawnedEntities.add( addedId );

                upstreamConnection.send( packetAddItem );
                break;

            case Protocol.ADD_ENTITY_PACKET:
                PacketAddEntity packetAddEntity = new PacketAddEntity();
                packetAddEntity.deserialize( buffer );

                addedId = this.upstreamConnection.getEntityRewriter().addEntity( packetAddEntity.getEntityId(), this );
                packetAddEntity.setEntityId( addedId );

                this.spawnedEntities.add( addedId );

                // Rewrite metadata if needed
                if ( packetAddEntity.getMetadataContainer().has( 5 ) ) {
                    long replacementId = this.upstreamConnection.getEntityRewriter().getReplacementId( packetAddEntity.getMetadataContainer().getLong( 5 ), this );
                    packetAddEntity.getMetadataContainer().putLong( 5, replacementId );
                }

                if ( packetAddEntity.getMetadataContainer().has( 6 ) ) {
                    long replacementId = this.upstreamConnection.getEntityRewriter().getReplacementId( packetAddEntity.getMetadataContainer().getLong( 6 ), this );
                    packetAddEntity.getMetadataContainer().putLong( 6, replacementId );
                }

                this.upstreamConnection.send( packetAddEntity );
                break;

            case Protocol.ADD_PLAYER_PACKET:
                PacketAddPlayer packetAddPlayer = new PacketAddPlayer();
                packetAddPlayer.deserialize( buffer );

                addedId = this.upstreamConnection.getEntityRewriter().addEntity( packetAddPlayer.getEntityId(), this );
                packetAddPlayer.setEntityId( addedId );
                this.spawnedEntities.add( addedId );
                this.upstreamConnection.send( packetAddPlayer );

                break;

            case Protocol.PACKET_ENCRYPTION_REQUEST:
                PacketEncryptionRequest packet = new PacketEncryptionRequest();
                packet.deserialize( buffer );

                // We need to verify the JWT request
                JwtToken token = JwtToken.parse( packet.getJwt() );
                String keyDataBase64 = (String) token.getHeader().getProperty( "x5u" );
                Key key = EncryptionHandler.createPublicKey( keyDataBase64 );

                try {
                    if ( token.validateSignature( key ) ) {
                        LOGGER.debug( "For server: Valid encryption start JWT" );
                    }
                } catch ( JwtSignatureException e ) {
                    LOGGER.error( "Invalid JWT signature from server: ", e );
                }

                this.encryptionHandler = new EncryptionHandler();
                this.encryptionHandler.setServerPublicKey( keyDataBase64 );
                this.encryptionHandler.beginServersideEncryption( Base64.getDecoder().decode( (String) token.getClaim( "salt" ) ) );
                this.state = ConnectionState.ENCRYPTED;

                // Tell the server that we are ready to receive encrypted packets from now on:
                PacketEncryptionReady response = new PacketEncryptionReady();
                this.send( response );

                break;

            case Protocol.PACKET_PLAY_STATE:
                PacketPlayState packetPlayState = new PacketPlayState();
                packetPlayState.deserialize( buffer );

                // We have been logged in. But we miss a spawn packet
                if ( packetPlayState.getState() == PacketPlayState.PlayState.LOGIN_SUCCESS ) {
                    this.upstreamConnection.switchToDownstream( this );
                    this.proxProx.getPluginManager().callEvent( new PlayerSwitchedEvent( this.upstreamConnection, this ) );
                }

                // The first spawn state must come through
                if ( packetPlayState.getState() == PacketPlayState.PlayState.SPAWN && this.upstreamConnection.isFirstServer() ) {
                    this.upstreamConnection.sendPlayState( PacketPlayState.PlayState.SPAWN );
                    this.upstreamConnection.setFirstServer( false );
                }

                break;

            case Protocol.PACKET_RESOURCEPACK_INFO:
                PacketResourcePacksInfo packetResourcePacksInfo = new PacketResourcePacksInfo();
                packetResourcePacksInfo.deserialize( buffer );

                // We don't support resources with proxy connections, simply answer that we have all
                PacketResourcePackResponse resourcePackResponse = new PacketResourcePackResponse();
                resourcePackResponse.setInfo( new HashMap<>() );
                resourcePackResponse.setStatus( ResourceResponseStatus.COMPLETED );
                this.send( resourcePackResponse );

                break;

            case Protocol.DISONNECT_PACKET:
                PacketDisconnect packetDisconnect = new PacketDisconnect();
                packetDisconnect.deserialize( buffer );

                if ( this.equals( upstreamConnection.getDownStream() ) ) {
                    if ( upstreamConnection.getPendingDownStream() != null || upstreamConnection.connectToLastKnown() ) {
                        upstreamConnection.sendMessage( packetDisconnect.getMessage() );
                        return;
                    } else {
                        return;
                    }
                } else {
                    upstreamConnection.resetPendingDownStream();
                }

                break;

            case Protocol.PACKET_TRANSFER:
                if ( this.equals( this.upstreamConnection.getDownStream() ) ) {
                    this.upstreamConnection.connect( buffer.readString(), buffer.readLShort() );
                } else {
                    this.upstreamConnection.send( packetId, buffer );
                }

                break;

            case Protocol.PACKET_MOB_EFFECT:
                PacketMobEffect mobEffect = new PacketMobEffect();
                mobEffect.deserialize( buffer );

                mobEffect.setEntityId( this.upstreamConnection.getEntityRewriter().getReplacementId( mobEffect.getEntityId(), this ) );

                if ( mobEffect.getAction() == PacketMobEffect.EVENT_REMOVE ) {
                    this.upstreamConnection.getEffectManager().remove( mobEffect.getEffectId() );
                } else {
                    this.upstreamConnection.getEffectManager().add( mobEffect.getEffectId(), System.currentTimeMillis() + mobEffect.getDuration() * 50 );
                }

                this.upstreamConnection.send( mobEffect );

                break;

            default:
                buffer = this.upstreamConnection.getEntityRewriter().rewriteServerToClient( packetId, pos, buffer, this );
                this.upstreamConnection.send( packetId, buffer );
                break;
        }
    }

    /**
     * Close the connection to the underlying RakNet Server
     */
    void close( boolean fireEvent ) {
        this.manualClose = true;

        LOGGER.info( "Player {} disconnected from server {}", this.upstreamConnection, this );

        if ( ( this.tcpConnection != null || this.connection != null ) && fireEvent ) {
            ServerKickedPlayerEvent serverKickedPlayerEvent = new ServerKickedPlayerEvent( this.upstreamConnection, this );
            ProxProx.instance.getPluginManager().callEvent( serverKickedPlayerEvent );
        }

        if ( this.tcpConnection != null ) {
            this.tcpConnection.disconnect();
            this.tcpConnection = null;
        }

        if ( this.connection != null ) {
            this.connection.close();
            this.connection = null;
        }

        super.close();
    }

    public void disconnect( String reason ) {
        LOGGER.info( "Disconnecting DownStream for " + this.upstreamConnection.getUUID() );

        if ( this.connection != null && this.connection.getConnection() != null ) {
            LOGGER.info( "Disconnecting DownStream for " + this.upstreamConnection.getUUID() );

            this.connection.getConnection().disconnect( reason );
        }

        if ( this.connection != null ) {
            this.connection.close();
            this.connection = null;
        }

        if ( this.tcpConnection != null ) {
            this.tcpConnection.disconnect();
            this.tcpConnection = null;
        }

        super.close();
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
        return this.spawnedEntities;
    }

    @Override
    public void send( Packet packet ) {
        PacketBuffer buffer = new PacketBuffer( 64 );
        buffer.writeByte( packet.getId() );

        if ( !( packet instanceof PacketBatch ) ) {
            buffer.writeShort( (short) 0 );
        }

        packet.serialize( buffer );

        // Do we send via TCP or UDP?
        if ( this.tcpConnection != null ) {
            WrappedMCPEPacket mcpePacket = new WrappedMCPEPacket();
            mcpePacket.setBuffer( new PacketBuffer[]{ buffer } );
            this.tcpConnection.send( mcpePacket );
        } else if ( this.connection != null ) {
            if ( !( packet instanceof PacketBatch ) ) {
                this.executor.addWork( this, new PacketBuffer[]{ buffer } );
            } else {
                this.getConnection().send( PacketReliability.RELIABLE_ORDERED, packet.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
            }
        }
    }

    public void send( byte packetId, PacketBuffer buffer ) {
        if ( this.tcpConnection != null ) {
            PacketBuffer newBuffer = new PacketBuffer( 64 );
            newBuffer.writeByte( packetId );
            newBuffer.writeShort( (short) 0 );

            byte[] data = new byte[buffer.getRemaining()];
            buffer.readBytes( data );

            newBuffer.writeBytes( data );

            WrappedMCPEPacket mcpePacket = new WrappedMCPEPacket();
            mcpePacket.setBuffer( new PacketBuffer[]{ newBuffer } );
            this.tcpConnection.send( mcpePacket );
        } else {
            byte[] data = new byte[buffer.getRemaining()];
            buffer.readBytes( data );

            PacketBuffer packetBuffer = new PacketBuffer( 64 );
            packetBuffer.writeByte( packetId );
            packetBuffer.writeShort( (short) 0 );
            packetBuffer.writeBytes( data );

            this.executor.addWork( this, new PacketBuffer[]{ packetBuffer } );
        }
    }

}
