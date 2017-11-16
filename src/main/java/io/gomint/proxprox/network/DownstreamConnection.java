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
import io.gomint.proxprox.api.network.Channel;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.api.network.PacketSender;
import io.gomint.proxprox.jwt.JwtSignatureException;
import io.gomint.proxprox.jwt.JwtToken;
import io.gomint.proxprox.network.protocol.*;
import io.gomint.proxprox.network.protocol.type.ResourceResponseStatus;
import io.gomint.proxprox.network.tcp.ConnectionHandler;
import io.gomint.proxprox.network.tcp.Initializer;
import io.gomint.proxprox.network.tcp.protocol.WrappedMCPEPacket;
import io.gomint.proxprox.util.EntityRewriter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.security.Key;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( of = { "ip", "port" }, callSuper = false )
public class DownstreamConnection extends AbstractConnection implements Server, PacketSender {

    private static final Logger logger = LoggerFactory.getLogger( DownstreamConnection.class );

    // Needed connection data to reach the server
    private String ip;
    private int port;

    // Client connection
    private ClientSocket connection;
    private Thread connectionReadThread;
    private PostProcessWorker postProcessWorker;
    private boolean isFirst = true;
    private ConnectionHandler tcpConnection;
    private boolean manualClose;

    // Upstream
    private UpstreamConnection upstreamConnection;

    // Proxy instance
    private ProxProx proxProx;

    // Entities
    private long entityId;
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
    @Getter
    private int difficulty;
    @Getter
    private int gamemode;

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
                io.netty.bootstrap.Bootstrap bootstrap = Initializer.buildBootstrap( "DownStream " + this.upstreamConnection.getUUID() + " -> " + this.ip + ":" + this.port, new Consumer<ConnectionHandler>() {
                    @Override
                    public void accept( ConnectionHandler connectionHandler ) {
                        DownstreamConnection.this.tcpConnection = connectionHandler;
                        DownstreamConnection.this.upstreamConnection.onDownStreamConnected( DownstreamConnection.this );

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
                                    logger.info( "Disconnected downstream..." );
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
                    logger.debug( "Got socketEvent: " + socketEvent.getType().name() );
                    switch ( socketEvent.getType() ) {
                        case CONNECTION_ATTEMPT_SUCCEEDED:
                            // We got accepted *yay*
                            DownstreamConnection.this.setup();
                            DownstreamConnection.this.upstreamConnection.onDownStreamConnected( DownstreamConnection.this );
                            break;

                        case CONNECTION_CLOSED:
                        case CONNECTION_DISCONNECTED:
                            logger.info( "Disconnected downstream..." );
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

        new Exception().printStackTrace();

        this.postProcessWorker = new PostProcessWorker( this.getConnection() );
        this.connectionReadThread = this.proxProx.getNewServerConnectionThread( new Runnable() {
            @Override
            public void run() {
                // Give a better name
                Thread.currentThread().setName( "DownStream " + upstreamConnection.getUUID() + " -> " + ip + ":" + port + " [Packet Read/Rewrite]" );

                logger.debug( "Connection status: " + connection.getConnection().isConnected() );
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

        // Check if we are in custom protocol mode :D
        if ( packetId == (byte) 0xFF ) {
            PacketCustomProtocol packetCustomProtocol = new PacketCustomProtocol();
            packetCustomProtocol.deserialize( buffer );

            if ( packetCustomProtocol.getMode() == 2 ) {
                Channel channel = this.proxProx.getNetworkChannels().channel( (byte) packetCustomProtocol.getChannel() );
                if ( channel != null ) {
                    channel.receivePacket( upstreamConnection, packetCustomProtocol.getData() );
                }
            }

            return;
        }

        int pos = buffer.getPosition();

        // Minimalistic protocol
        switch ( packetId ) {
            case Protocol.PACKET_BATCH:
                handleBatchPacket( buffer, reliability, orderingChannel, batched );
                break;

            case Protocol.PACKET_START_GAME:
                PacketStartGame startGame = new PacketStartGame();
                startGame.deserialize( buffer );

                if ( upstreamConnection.getEntityRewriter() == null ) {
                    upstreamConnection.setEntityRewriter( new EntityRewriter( startGame.getRuntimeEntityId() ) );
                } else {
                    this.isFirst = false;
                }

                this.entityId = startGame.getRuntimeEntityId();
                this.spawnX = startGame.getSpawnX();
                this.spawnY = startGame.getSpawnY();
                this.spawnZ = startGame.getSpawnZ();
                this.spawnYaw = startGame.getSpawnYaw();
                this.spawnPitch = startGame.getSpawnPitch();
                this.difficulty = startGame.getDifficulty();
                this.gamemode = startGame.getGamemode();

                if ( upstreamConnection.isFirstServer() ) {
                    buffer.setPosition( pos );
                    upstreamConnection.send( packetId, buffer );
                } else {
                    upstreamConnection.move( this.getSpawnX(), this.getSpawnY(), this.getSpawnZ(),
                            this.getSpawnYaw(), this.getSpawnPitch() );
                }

                break;

            case Protocol.REMOVE_ENTITY_PACKET:
                PacketRemoveEntity removeEntity = new PacketRemoveEntity();
                removeEntity.deserialize( buffer );

                long entityId = this.upstreamConnection.getEntityRewriter().removeEntity( removeEntity.getEntityId() );

                removeEntity.setEntityId( entityId );

                spawnedEntities.remove( entityId );

                upstreamConnection.send( removeEntity );
                break;

            case Protocol.ADD_ITEM_ENTITY:
                PacketAddItem packetAddItem = new PacketAddItem();
                packetAddItem.deserialize( buffer );

                long addedId = this.upstreamConnection.getEntityRewriter().addEntity( packetAddItem.getEntityId() );
                packetAddItem.setEntityId( addedId );
                spawnedEntities.add( addedId );

                upstreamConnection.send( packetAddItem );
                break;

            case Protocol.ADD_ENTITY_PACKET:
                PacketAddEntity packetAddEntity = new PacketAddEntity();
                packetAddEntity.deserialize( buffer );

                addedId = this.upstreamConnection.getEntityRewriter().addEntity( packetAddEntity.getEntityId() );
                packetAddEntity.setEntityId( addedId );
                spawnedEntities.add( addedId );

                upstreamConnection.send( packetAddEntity );
                break;

            case Protocol.ADD_PLAYER_PACKET:
                PacketAddPlayer packetAddPlayer = new PacketAddPlayer();
                packetAddPlayer.deserialize( buffer );

                addedId = this.upstreamConnection.getEntityRewriter().addEntity( packetAddPlayer.getEntityId() );
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
                        logger.debug( "For server: Valid encryption start JWT" );
                    }
                } catch ( JwtSignatureException e ) {
                    e.printStackTrace();
                }

                logger.debug( "Encryption JWT public: " + keyDataBase64 );
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
                    logger.info( "Connected to downstream (" + this.connection.getConnection().getGuid() + ") for " + this.upstreamConnection.getName() );
                    this.state = ConnectionState.CONNECTED;

                    // First of all send channels
                    for ( Map.Entry<String, Byte> stringByteEntry : this.proxProx.getNetworkChannels().getChannels().entrySet() ) {
                        PacketCustomProtocol packetCustomProtocol = new PacketCustomProtocol();
                        packetCustomProtocol.setMode( 0 );
                        packetCustomProtocol.setChannel( stringByteEntry.getValue() );
                        packetCustomProtocol.setChannelName( stringByteEntry.getKey() );
                        send( packetCustomProtocol );
                    }
                }

                // The first spawn state must come through
                if ( packetPlayState.getState() == PacketPlayState.PlayState.SPAWN ) {
                    this.upstreamConnection.sendPlayState( PacketPlayState.PlayState.SPAWN );

                    this.upstreamConnection.getEntityRewriter().setCurrentDownStreamId( this.entityId );
                    this.upstreamConnection.switchToDownstream( this );
                    this.proxProx.getPluginManager().callEvent( new PlayerSwitchedEvent( this.upstreamConnection, this ) );

                    PacketSetChunkRadius setChunkRadius = new PacketSetChunkRadius();
                    setChunkRadius.setChunkRadius( this.upstreamConnection.getViewDistance() );
                    send( setChunkRadius );
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

            default:
                if ( !this.isFirst ) {
                    buffer = this.upstreamConnection.getEntityRewriter().rewriteServerToClient( packetId, pos, buffer );
                }

                this.upstreamConnection.send( packetId, buffer );
                break;
        }
    }

    /**
     * Close the connection to the underlying RakNet Server
     */
    void close( boolean fireEvent ) {
        this.manualClose = true;

        if ( ( this.tcpConnection != null || this.connection != null ) && fireEvent ) {
            ServerKickedPlayerEvent serverKickedPlayerEvent = new ServerKickedPlayerEvent( this.upstreamConnection, this );
            ProxProx.instance.getPluginManager().callEvent( serverKickedPlayerEvent );
        }

        if ( this.tcpConnection != null ) {
            this.tcpConnection.disconnect();
        }

        if ( this.connection != null ) {
            this.connection.close();
        }

        if ( this.connectionReadThread != null ) {
            this.connectionReadThread.interrupt();
        }
    }

    public void disconnect( String reason ) {
        if ( this.connection != null && this.connection.getConnection() != null ) {
            logger.info( "Disconnecting DownStream for " + this.upstreamConnection.getUUID() );

            this.connection.getConnection().disconnect( reason );
            this.connection.close();
        } else if ( this.tcpConnection != null ) {
            this.tcpConnection.disconnect();
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

    @Override
    public void send( Packet packet ) {
        // Do we send via TCP or UDP?
        if ( this.tcpConnection != null ) {
            PacketBuffer buffer = new PacketBuffer( 64 );
            buffer.writeByte( packet.getId() );
            buffer.writeShort( (short) 0 );
            packet.serialize( buffer );

            WrappedMCPEPacket mcpePacket = new WrappedMCPEPacket();
            mcpePacket.setBuffer( buffer );
            this.tcpConnection.send( mcpePacket );
        } else if ( this.connection != null ) {
            if ( !( packet instanceof PacketBatch ) ) {
                this.postProcessWorker.sendPacket( packet );
            } else {
                PacketBuffer buffer = new PacketBuffer( 64 );
                buffer.writeByte( packet.getId() );
                packet.serialize( buffer );

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
            mcpePacket.setBuffer( newBuffer );
            this.tcpConnection.send( mcpePacket );
        } else {
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
            } );
        }
    }

}
