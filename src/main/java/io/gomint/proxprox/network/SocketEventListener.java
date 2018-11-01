/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.Socket;
import io.gomint.jraknet.SocketEvent;
import io.gomint.jraknet.SocketEventHandler;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.event.ProxyPingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author geNAZt
 * @version 1.0
 */
public class SocketEventListener implements SocketEventHandler {

    private static final Logger logger = LoggerFactory.getLogger( SocketEventListener.class );

    private final Map<Long, UpstreamConnection> connections = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final ProxProx proxProx;

    /**
     * Construct a listener for accepting and closing connections in RakNet
     *
     * @param proxProx The ProxProx instance for which we should handle connections
     */
    public SocketEventListener( ProxProx proxProx ) {
        this.proxProx = proxProx;
    }

    @Override
    public void onSocketEvent( Socket socket, SocketEvent socketEvent ) {
        switch ( socketEvent.getType() ) {
            case UNCONNECTED_PING:
                // Throw event around
                ProxyPingEvent event = this.proxProx.getPluginManager().callEvent( new ProxyPingEvent(
                        this.proxProx.getConfig().getMotd(),
                        this.proxProx.getPlayers().size(),
                        this.proxProx.getConfig().getMaxPlayers()
                ) );

                socketEvent.getPingPongInfo().setMotd( "MCPE;" + event.getMotd() + ";" + Protocol.MINECRAFT_PE_PROTOCOL_VERSION +
                        ";" + Protocol.MINECRAFT_PE_NETWORK_VERSION + ";" + event.getOnlinePlayers() + ";" + event.getMaxPlayers() );

                break;

            case NEW_INCOMING_CONNECTION:
                // Maybe we should accept this?
                this.lock.writeLock().lock();
                try {
                    // First check if we already have a client like this
                    UpstreamConnection upstreamConnection = this.connections.get( socketEvent.getConnection().getGuid() );
                    if ( upstreamConnection != null ) {
                        socketEvent.getConnection().disconnect( "Another connection is active on this GUID" );
                        return;
                    }

                    this.connections.put( socketEvent.getConnection().getGuid(), new UpstreamConnection( this.proxProx, socketEvent.getConnection() ) );
                } finally {
                    this.lock.writeLock().unlock();
                }

                break;

            case CONNECTION_CLOSED:
            case CONNECTION_DISCONNECTED:
                // Remove connection
                this.lock.writeLock().lock();
                try {
                    // First check if we already have a client like this
                    UpstreamConnection upstreamConnection = this.connections.remove( socketEvent.getConnection().getGuid() );
                    if ( upstreamConnection != null ) {
                        logger.info( "Disconnected upstream: " + socketEvent.getConnection().getGuid() );

                        if ( upstreamConnection.getUUID() != null ) {
                            this.proxProx.removePlayer( upstreamConnection );
                        }

                        if ( upstreamConnection.getPendingDownStream() != null ) {
                            upstreamConnection.getPendingDownStream().disconnect( "Connection closed" );
                            upstreamConnection.resetPendingDownStream();
                        }

                        if ( upstreamConnection.getDownStream() != null ) {
                            upstreamConnection.getDownStream().disconnect( "Connection closed" );
                            upstreamConnection.resetCurrentDownStream();
                        }

                        upstreamConnection.close();
                    }
                } finally {
                    this.lock.writeLock().unlock();
                }

                break;
        }
    }

    public void update() {
        this.lock.readLock().lock();
        try {
            for ( UpstreamConnection upstreamConnection : this.connections.values() ) {
                upstreamConnection.updateIncoming();
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void disconnectAll( String reason ) {
        this.lock.readLock().lock();
        try {
            for ( UpstreamConnection upstreamConnection : this.connections.values() ) {
                upstreamConnection.disconnect( reason );
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

}
