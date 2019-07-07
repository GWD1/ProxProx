package io.gomint.proxprox.util;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.DownstreamConnection;
import io.gomint.proxprox.network.UpstreamConnection;
import io.gomint.proxprox.network.protocol.PacketInventoryTransaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
public class EntityRewriter {

    private static final Logger LOGGER = LoggerFactory.getLogger( EntityRewriter.class );
    private final UpstreamConnection upstream;

    @Getter @Setter private long ownId;
    @Getter @Setter private long currentDownStreamId;
    private AtomicLong idCounter = new AtomicLong( 0 );

    private Map<Long, Long> rewriteIds = new ConcurrentHashMap<>();
    private Map<Long, Long> serverRewriteIds = new ConcurrentHashMap<>();

    public long addEntity( long entityID, DownstreamConnection connection ) {
        long newEntityId = this.idCounter.incrementAndGet();
        if ( newEntityId == this.ownId ) {
            newEntityId = this.idCounter.incrementAndGet();
        }

        LOGGER.debug( "Got new entity {} -> {} for {} (Server: {}:{})", entityID, newEntityId, connection.getUpstreamConnection().getName(), connection.getIP(), connection.getPort() );

        this.serverRewriteIds.put( newEntityId, entityID );
        this.rewriteIds.put( entityID, newEntityId );
        return newEntityId;
    }

    public PacketBuffer rewriteServerToClient( byte packetId, int pos, PacketBuffer buffer, DownstreamConnection connection ) {
        // Entity ID rewrites
        long entityId;
        switch ( packetId ) {
            case 0x4a:  // Boss event
                entityId = buffer.readSignedVarInt();
                long replacementID = getReplacementId( entityId, connection );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( data.length );
                    buffer.writeSignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x28:  // Entity motion
            case 0x1f:  // Mob equip
            case 0x20:  // Mob Armor
            case 0x12:  // Entity move
            case 0x13:  // Move player
            case 0x1B:  // Entity event
            case 0x27:  // Entity metadata
            case 0x1D:  // Update attributes
                entityId = buffer.readUnsignedVarLong();
                replacementID = getReplacementId( entityId, connection );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( 8 );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x2C:  // Animation
                int actionId = buffer.readSignedVarInt();

                entityId = buffer.readUnsignedVarLong();
                replacementID = getReplacementId( entityId, connection );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( 8 );
                    buffer.writeSignedVarInt( actionId );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x11: // Pickup entity
                long itemId = buffer.readUnsignedVarLong();
                long playerId = buffer.readUnsignedVarLong();
                long replaceItemId = getReplacementId( itemId, connection );
                long replacePlayerId = getReplacementId( playerId, connection );

                buffer = new PacketBuffer( 8 );
                buffer.writeUnsignedVarLong( replaceItemId );
                buffer.writeUnsignedVarLong( replacePlayerId );
                buffer.shrink();
                buffer.resetPosition();

                break;

            case 0x37:  // Adventure settings
                int a = buffer.readUnsignedVarInt();
                int b = buffer.readUnsignedVarInt();
                int c = buffer.readUnsignedVarInt();
                int d = buffer.readUnsignedVarInt();
                int e = buffer.readUnsignedVarInt();
                entityId = buffer.readLLong(); // Yes, a LE long
                replacementID = getReplacementId( entityId, connection );

                buffer = new PacketBuffer( 8 );
                buffer.writeUnsignedVarInt( a );
                buffer.writeUnsignedVarInt( b );
                buffer.writeUnsignedVarInt( c );
                buffer.writeUnsignedVarInt( d );
                buffer.writeUnsignedVarInt( e );
                buffer.writeLLong( replacementID );
                buffer.shrink();
                buffer.resetPosition();

                break;
        }

        return buffer;
    }

    public long getReplacementId( long entityId, DownstreamConnection connection ) {
        if ( entityId == this.currentDownStreamId ) {
            return this.ownId;
        }

        Long rewrite = this.rewriteIds.get( entityId );
        if ( rewrite == null ) {
            if ( LOGGER.isDebugEnabled() ) {
                LOGGER.debug( "Got entity packet for entity not spawned yet: {} for {} (Server {}:{})", entityId, connection.getUpstreamConnection().getName(), connection.getIP(), connection.getPort() );

                for ( Map.Entry<Long, Long> longLongEntry : this.rewriteIds.entrySet() ) {
                    LOGGER.debug( "{} -> {}", longLongEntry.getKey(), longLongEntry.getValue() );
                }
            }

            return entityId;
        }

        return rewrite;
    }

    private long getReplacementIdForServer( long entityId ) {
        if ( entityId == this.ownId ) {
            return this.currentDownStreamId;
        }

        Long rewriteId = this.serverRewriteIds.get( entityId );
        if ( rewriteId == null ) {
            return entityId;
        }

        return rewriteId;
    }

    public PacketBuffer rewriteClientToServer( byte packetId, int pos, PacketBuffer buffer ) {
        long entityId;

        switch ( packetId ) {
            case 0x4a:  // Boss event
                entityId = buffer.readSignedVarInt();
                long replacementID = getReplacementIdForServer( entityId );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( data.length );
                    buffer.writeSignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x1f:
            case 0x20:
            case 0x13: // Move player
            case 0x27: // Entity metadata
            case 0x1B: // Entity Event
            case 0x24: // Player action
                entityId = buffer.readUnsignedVarLong();
                replacementID = getReplacementIdForServer( entityId );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( data.length );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x2c:  // Animate
                int actionId = buffer.readSignedVarInt();
                entityId = buffer.readUnsignedVarLong();
                replacementID = getReplacementIdForServer( entityId );

                if ( entityId != replacementID ) {
                    buffer = new PacketBuffer( 6 );
                    buffer.writeSignedVarInt( actionId );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x21:  // Interact
                byte action = buffer.readByte();
                entityId = buffer.readUnsignedVarLong();

                // Special case id 0 (own reference)
                if ( entityId != 0 ) {
                    replacementID = getReplacementIdForServer( entityId );

                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    if ( entityId != replacementID ) {
                        buffer = new PacketBuffer( 6 );
                        buffer.writeByte( action );
                        buffer.writeUnsignedVarLong( replacementID );
                        buffer.writeBytes( data );
                        buffer.shrink();
                        buffer.resetPosition();
                    } else {
                        buffer.setPosition( pos );
                    }
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x1E: // Inventory transaction
                PacketInventoryTransaction inventoryTransaction = new PacketInventoryTransaction();
                inventoryTransaction.deserialize( buffer, this.upstream.getProtocolVersion() );

                // Check if the action is a entity based
                if ( inventoryTransaction.getType() == PacketInventoryTransaction.TYPE_USE_ITEM_ON_ENTITY ) {
                    entityId = inventoryTransaction.getEntityId();
                    inventoryTransaction.setEntityId( getReplacementIdForServer( entityId ) );
                    buffer = new PacketBuffer( 8 );
                    inventoryTransaction.serialize( buffer, this.upstream.getProtocolVersion() );
                    buffer.shrink();
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;
        }

        return buffer;
    }

    public Long removeEntity( long entityId, DownstreamConnection connection ) {
        Long newEntity = this.rewriteIds.remove( entityId );
        if ( newEntity == null ) {
            LOGGER.debug( "Removing an entity which wasn't known. This could lead to side effect like players not showing correctly" );
            return null;
        }

        LOGGER.debug( "Removing entity {} for {} (Server: {}:{})", entityId, connection.getUpstreamConnection().getName(), connection.getIP(), connection.getPort() );

        this.serverRewriteIds.remove( newEntity );
        return newEntity;
    }

    public void removeServerEntity( long entityId ) {
        Long oldId = this.serverRewriteIds.remove( entityId );
        if ( oldId != null ) {
            this.rewriteIds.remove( oldId );
        }
    }

}
