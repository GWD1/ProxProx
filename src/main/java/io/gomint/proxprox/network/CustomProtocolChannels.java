/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.network.Channel;
import io.gomint.proxprox.api.network.NetworkChannels;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.api.network.PacketSender;
import io.gomint.proxprox.network.protocol.PacketCustomProtocol;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
public class CustomProtocolChannels implements NetworkChannels {

    private Map<String, Byte> nameToId = new HashMap<>();
    private Map<Byte, String> idToName = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock( true );

    private Map<String, Channel> channels = new HashMap<>();
    private Lock channelLock = new ReentrantLock( true );

    private AtomicInteger channelIds = new AtomicInteger( 0 );
    private final ProxProx proxProx;

    @Override
    public Channel channel( String name ) {
        this.channelLock.lock();
        try {
            return this.channels.computeIfAbsent( name, new Function<String, Channel>() {
                @Override
                public Channel apply( String s ) {
                    // Generate new ID
                    int channelId = channelIds.getAndIncrement();

                    lock.writeLock().lock();
                    try {
                        nameToId.put( s, (byte) channelId );
                        idToName.put( (byte) channelId, s );
                    } finally {
                        lock.writeLock().unlock();
                    }

                    // Register to all currently connected downstreams
                    if ( proxProx.getSocketEventListener() != null ) {
                        proxProx.getSocketEventListener().registerNewChannel( s, channelId );
                    }

                    return new Channel( s, CustomProtocolChannels.this );
                }
            } );
        } finally {
            this.channelLock.unlock();
        }
    }

    public void send( PacketSender sender, Packet packet, String channel ) {
        Byte id = null;
        this.lock.readLock().lock();
        try {
            id = this.nameToId.get( channel );
        } finally {
            this.lock.readLock().unlock();
        }

        if ( id == null ) return;

        PacketBuffer packetBuffer = new PacketBuffer( packet.estimateLength() == -1 ? 64 : packet.estimateLength() + 1 );
        packetBuffer.writeByte( packet.getId() );
        packet.serialize( packetBuffer );

        PacketCustomProtocol packetCustomProtocol = new PacketCustomProtocol();
        packetCustomProtocol.setMode( 2 );
        packetCustomProtocol.setChannel( id );
        packetCustomProtocol.setData( packetBuffer.getBuffer() );
        sender.send( packetCustomProtocol );
    }

    public Channel channel( byte id ) {
        String name = null;
        this.lock.readLock().lock();
        try {
            name = this.idToName.get( id );
        } finally {
            this.lock.readLock().unlock();
        }

        if ( name == null ) return null;

        this.channelLock.lock();
        try {
            return this.channels.get( name );
        } finally {
            this.channelLock.unlock();
        }
    }

    public Map<String, Byte> getChannels() {
        this.lock.readLock().lock();
        try {
            return new HashMap<>( nameToId );
        } finally {
            this.lock.readLock().unlock();
        }
    }

}
