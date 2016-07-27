package io.gomint.proxprox.api.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.network.exception.AlreadyRegisteredException;
import io.gomint.proxprox.network.CustomProtocolChannels;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
public class Channel {

    private final String name;
    private final CustomProtocolChannels parent;

    private Map<Byte, PacketFactory> customPackets = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock( true );

    private Map<Byte, List<PacketListener>> customPacketListener = new HashMap<>();
    private ReadWriteLock lockListener = new ReentrantReadWriteLock( true );

    public void registerPacket( byte packetId, PacketFactory packetFactory ) throws AlreadyRegisteredException {
        // First check for duplicates
        this.lock.readLock().lock();
        try {
            if ( this.customPackets.containsKey( packetId ) ) {
                throw new AlreadyRegisteredException();
            }
        } finally {
            this.lock.readLock().unlock();
        }

        this.lock.writeLock().lock();
        try {
            this.customPackets.put( packetId, packetFactory );
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void registerPacketListener( byte packetId, PacketListener packetListener ) {
        this.lockListener.writeLock().lock();
        try {
            this.customPacketListener.computeIfAbsent( packetId, new Function<Byte, List<PacketListener>>() {
                @Override
                public List<PacketListener> apply( Byte aByte ) {
                    return new ArrayList<>();
                }
            } ).add( packetListener );
        } finally {
            this.lockListener.writeLock().unlock();
        }
    }

    public void send( PacketSender sender, Packet packet ) {
        this.parent.send( sender, packet, this.name );
    }

    public void receivePacket( Player player, byte[] bytes ) {
        PacketBuffer buffer = new PacketBuffer( bytes, 0 );
        byte packetId = buffer.readByte();

        this.lock.readLock().lock();
        try {
            PacketFactory packetFactory = this.customPackets.get( packetId );
            if ( packetFactory != null ) {
                Packet packet = packetFactory.createPacket();
                packet.deserialize( buffer );
                handlePacket( player, packet );
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private void handlePacket( Player player, Packet packet ) {
        this.lockListener.readLock().lock();
        try {
            List<PacketListener> packetListeners = this.customPacketListener.get( packet.getId() );
            if ( packetListeners != null ) {
                for ( PacketListener packetListener : packetListeners ) {
                    packetListener.receive( player, packet );
                }
            }
        } finally {
            this.lockListener.readLock().unlock();
        }
    }

}
