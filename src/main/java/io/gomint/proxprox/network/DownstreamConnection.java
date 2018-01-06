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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.net.SocketException;
import java.security.Key;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

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
    private Thread connectionReadThread;
    private PostProcessWorker postProcessWorker;
    @Getter
    private ConnectionHandler tcpConnection;
    private boolean manualClose;

    // Upstream
    @Getter
    private UpstreamConnection upstreamConnection;

    // Proxy instance
    private ProxProx proxProx;

    // Entities
    private Set<Long> spawnedEntities = new HashSet<>();
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
    public DownstreamConnection( ProxProx proxProx, UpstreamConnection upstreamConnection, String ip, int port ) {
        this.upstreamConnection = upstreamConnection;
        this.proxProx = proxProx;

        this.ip = ip;
        this.port = port;

        // Check if we use UDP or TCP for downstream connections
        if ( proxProx.getConfig().isUseTCP() ) {
            try {
                io.netty.bootstrap.Bootstrap bootstrap = Initializer.buildBootstrap( this.upstreamConnection, new Consumer<ConnectionHandler>() {
                    @Override
                    public void accept( ConnectionHandler connectionHandler ) {
                        DownstreamConnection.this.tcpConnection = connectionHandler;

                        connectionHandler.onData( new Consumer<PacketBuffer>() {
                            @Override
                            public void accept( PacketBuffer buffer ) {
                                handlePacket( buffer, PacketReliability.RELIABLE_ORDERED, 0, true ); // There are no batches in TCP
                            }
                        } );

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
                bootstrap.connect( this.ip, this.port ).sync();
            } catch ( InterruptedException e ) {
                e.printStackTrace();
                this.upstreamConnection.resetPendingDownStream();
            }
        } else {
            this.connection = new ClientSocket();
            this.connection.setMojangModificationEnabled( true );
            this.connection.setEventLoopFactory( new ThreadFactoryBuilder().setNameFormat( "DownStream " + this.upstreamConnection.getUUID() + " -> " + this.ip + ":" + this.port ).build() );
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
                e.printStackTrace();
            }

            this.connection.connect( ip, port );
        }
    }

    @Override
    protected void setup() {
        super.setup();

        this.postProcessWorker = new PostProcessWorker( this.getConnection() );
        this.connectionReadThread = this.proxProx.getNewServerConnectionThread( new Runnable() {
            @Override
            public void run() {
                // Give a better name
                Thread.currentThread().setName( "DownStream " + upstreamConnection.getUUID() + " -> " + ip + ":" + port + " [Packet Read/Rewrite]" );

                LOGGER.debug( "Connection status: " + connection.getConnection().isConnected() );
                while ( connection.getConnection() != null && connection.getConnection().isConnected() ) {
                    EncapsulatedPacket data = connection.getConnection().receive();
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

                    try {
                        handlePacket( buffer, data.getReliability(), data.getOrderingChannel(), false );
                    } catch ( Throwable t ) {
                        t.printStackTrace();
                    }
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

        LOGGER.debug( "Got packet {}. Upstream pending: {}, down: {}, this: {}", Integer.toHexString( packetId & 0xFF ), this.upstreamConnection.getPendingDownStream(), this.upstreamConnection.getDownStream(), this );

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.PACKET_BATCH:
                handleBatchPacket( buffer, reliability, orderingChannel, batched );
                break;

            case Protocol.PACKET_START_GAME:
                PacketStartGame startGame = new PacketStartGame();
                startGame.deserialize( buffer );

                if ( this.upstreamConnection.isFirstServer() ) {
                    this.upstreamConnection.getEntityRewriter().setOwnId( startGame.getRuntimeEntityId() );
                    this.upstreamConnection.getEntityRewriter().setDebugger( this.upstreamConnection.getDebugger() );
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
                }

                PacketSetChunkRadius setChunkRadius = new PacketSetChunkRadius();
                setChunkRadius.setChunkRadius( 4 );
                send( setChunkRadius );

                break;

            case Protocol.REMOVE_ENTITY_PACKET:
                PacketRemoveEntity removeEntity = new PacketRemoveEntity();
                removeEntity.deserialize( buffer );

                long entityId = this.upstreamConnection.getEntityRewriter().removeEntity( this.ip + ":" + this.port, removeEntity.getEntityId() );

                removeEntity.setEntityId( entityId );

                spawnedEntities.remove( entityId );

                upstreamConnection.send( removeEntity );
                break;

            case Protocol.ADD_ITEM_ENTITY:
                PacketAddItem packetAddItem = new PacketAddItem();
                packetAddItem.deserialize( buffer );

                long addedId = this.upstreamConnection.getEntityRewriter().addEntity( this.ip + ":" + this.port, packetAddItem.getEntityId() );
                packetAddItem.setEntityId( addedId );
                spawnedEntities.add( addedId );

                upstreamConnection.send( packetAddItem );
                break;

            case Protocol.ADD_ENTITY_PACKET:
                PacketAddEntity packetAddEntity = new PacketAddEntity();
                packetAddEntity.deserialize( buffer );

                addedId = this.upstreamConnection.getEntityRewriter().addEntity( this.ip + ":" + this.port, packetAddEntity.getEntityId() );
                packetAddEntity.setEntityId( addedId );
                spawnedEntities.add( addedId );

                // Rewrite metadata if needed
                if ( packetAddEntity.getMetadataContainer().has( 5 ) ) {
                    long replacementId = this.upstreamConnection.getEntityRewriter().getReplacementId( packetAddEntity.getMetadataContainer().getLong( 5 ) );
                    packetAddEntity.getMetadataContainer().putLong( 5, replacementId );
                }

                if ( packetAddEntity.getMetadataContainer().has( 6 ) ) {
                    long replacementId = this.upstreamConnection.getEntityRewriter().getReplacementId( packetAddEntity.getMetadataContainer().getLong( 6 ) );
                    packetAddEntity.getMetadataContainer().putLong( 6, replacementId );
                }

                upstreamConnection.send( packetAddEntity );
                break;

            case Protocol.ADD_PLAYER_PACKET:
                PacketAddPlayer packetAddPlayer = new PacketAddPlayer();
                packetAddPlayer.deserialize( buffer );

                addedId = this.upstreamConnection.getEntityRewriter().addEntity( this.ip + ":" + this.port, packetAddPlayer.getEntityId() );
                packetAddPlayer.setEntityId( addedId );
                spawnedEntities.add( addedId );

                upstreamConnection.send( packetAddPlayer );
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
                this.postProcessWorker.setEncryptionHandler( this.encryptionHandler );

                // Tell the server that we are ready to receive encrypted packets from now on:
                PacketEncryptionReady response = new PacketEncryptionReady();
                this.send( response );

                break;

            case Protocol.PACKET_PLAY_STATE:
                PacketPlayState packetPlayState = new PacketPlayState();
                packetPlayState.deserialize( buffer );

                // We have been logged in. But we miss a spawn packet
                if ( packetPlayState.getState() == PacketPlayState.PlayState.LOGIN_SUCCESS && state != ConnectionState.CONNECTED ) {
                    this.state = ConnectionState.CONNECTED;
                }

                // The first spawn state must come through
                if ( packetPlayState.getState() == PacketPlayState.PlayState.SPAWN ) {
                    if ( this.upstreamConnection.isFirstServer() ) {
                        this.upstreamConnection.sendPlayState( PacketPlayState.PlayState.SPAWN );
                    }

                    // Send chunk radius


                    this.upstreamConnection.switchToDownstream( this );
                    this.proxProx.getPluginManager().callEvent( new PlayerSwitchedEvent( this.upstreamConnection, this ) );
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
                if ( this.equals( upstreamConnection.getDownStream() ) ) {
                    String host = buffer.readString();
                    short port = buffer.readLShort();

                    this.upstreamConnection.connect( host, port );
                } else {
                    this.upstreamConnection.send( packetId, buffer );
                }

                break;

            default:
                buffer = this.upstreamConnection.getEntityRewriter().rewriteServerToClient( this.ip + ":" + this.port, packetId, pos, buffer );
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

        if ( this.connectionReadThread != null ) {
            this.connectionReadThread.interrupt();
            this.connectionReadThread = null;
        }
    }

    public void disconnect( String reason ) {
        LOGGER.info( "Disconnecting DownStream for " + this.upstreamConnection.getUUID() );

        if ( this.connection != null && this.connection.getConnection() != null ) {
            this.connection.getConnection().disconnect( reason );

            if ( this.connection != null ) {
                this.connection.close();
                this.connection = null;
            }

            if ( this.connectionReadThread != null ) {
                this.connectionReadThread.interrupt();
                this.connectionReadThread = null;
            }
        }

        if ( this.tcpConnection != null ) {
            this.tcpConnection.disconnect();
            this.tcpConnection = null;
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
        return this.spawnedEntities;
    }

    @Override
    public void send( Packet packet ) {
        PacketBuffer buffer = new PacketBuffer( 64 );
        buffer.writeByte( packet.getId() );
        buffer.writeShort( (short) 0 );
        packet.serialize( buffer );

        this.upstreamConnection.getDebugger().addPacket( "DownStream", this.ip + ":" + this.port, packet.getId(), buffer );

        // Do we send via TCP or UDP?
        if ( this.tcpConnection != null ) {
            WrappedMCPEPacket mcpePacket = new WrappedMCPEPacket();
            mcpePacket.setBuffer( buffer );
            this.tcpConnection.send( mcpePacket );
        } else if ( this.connection != null ) {
            if ( !( packet instanceof PacketBatch ) ) {
                this.postProcessWorker.sendPacket( buffer );
            } else {
                this.getConnection().send( PacketReliability.RELIABLE_ORDERED, packet.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
            }
        }
    }

    public void send( byte packetId, PacketBuffer buffer ) {
        this.upstreamConnection.getDebugger().addPacket( "DownStream", this.ip + ":" + this.port, packetId, buffer );

        if ( this.tcpConnection != null ) {
            PacketBuffer newBuffer = new PacketBuffer( 64 );
            newBuffer.writeByte( packetId );
            newBuffer.writeShort( (short) 0 );

            byte[] data = new byte[buffer.getRemaining()];
            buffer.readBytes( data );

            newBuffer.writeBytes( data );

            WrappedMCPEPacket mcpePacket = new WrappedMCPEPacket();
            mcpePacket.setBuffer( newBuffer );
            this.tcpConnection.send( mcpePacket );
        } else {
            byte[] data = new byte[buffer.getRemaining()];
            buffer.readBytes( data );

            PacketBuffer packetBuffer = new PacketBuffer( 64 );
            packetBuffer.writeByte( packetId );
            packetBuffer.writeShort( (short) 0 );
            packetBuffer.writeBytes( data );
            this.postProcessWorker.sendPacket( packetBuffer );
        }
    }

}
