package io.gomint.proxprox.util;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.protocol.PacketInventoryTransaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Getter private final long ownId;
    @Getter @Setter private long currentDownStreamId;
    private AtomicLong idCounter = new AtomicLong( 0 );

    private Map<Long, Long> rewriteIds = new ConcurrentHashMap<>();
    private Map<Long, Long> serverRewriteIds = new ConcurrentHashMap<>();

    public long addEntity( long entityID ) {
        long newEntityId = this.idCounter.incrementAndGet();
        if ( newEntityId == this.ownId ) {
            newEntityId = this.idCounter.incrementAndGet();
        }

        this.serverRewriteIds.put( newEntityId, entityID );
        this.rewriteIds.put( entityID, newEntityId );
        return newEntityId;
    }

    public PacketBuffer rewriteServerToClient( byte packetId, int pos, PacketBuffer buffer ) {
        // Entity ID rewrites
        long entityId;
        switch ( packetId ) {
            case 0x28:  // Entity motion
            case 0x1f:  // Mob equip
            case 0x20:  // Mob Armor
            case 0x12:  // Entity move
            case 0x13:  // Move player
            case 0x1B:  // Entity event
            case 0x27:  // Entity metadata
            case 0x1D:  // Update attributes
                entityId = buffer.readUnsignedVarLong();
                long replacementID = getReplacementId( entityId );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( 8 );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x2C:  // Animation
                int actionId = buffer.readSignedVarInt();

                entityId = buffer.readUnsignedVarLong();
                replacementID = getReplacementId( entityId );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( 8 );
                    buffer.writeSignedVarInt( actionId );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x11: // Pickup entity
                long itemId = buffer.readUnsignedVarLong();
                long playerId = buffer.readUnsignedVarLong();

                buffer = new PacketBuffer( 8 );
                buffer.writeUnsignedVarLong( itemId );
                buffer.writeUnsignedVarLong( playerId );
                buffer.resetPosition();

                break;
        }

        return buffer;
    }

    private long getReplacementId( long entityId ) {
        if ( entityId == this.currentDownStreamId ) {
            return this.ownId;
        }

        Long rewrite = this.rewriteIds.get( entityId );
        if ( rewrite == null ) {
            LOGGER.warn( "Got entity packet for entity not spawned yet: " + entityId );
            return entityId;
        }

        return rewrite;
    }

    private long getReplacementIdForServer( long entityId ) {
        if ( entityId == this.ownId ) {
            return this.currentDownStreamId;
        }

        return this.serverRewriteIds.get( entityId );
    }

    public PacketBuffer rewriteClientToServer( byte packetId, int pos, PacketBuffer buffer ) {
        long entityId;

        switch ( packetId ) {
            case 0x1f:
            case 0x20:
            case 0x13: // Move player
            case 0x27: // Entity metadata
            case 0x1B: // Entity Event
                entityId = buffer.readUnsignedVarLong();
                long replacementID = getReplacementIdForServer( entityId );

                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( 8 );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
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
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x21:  // Interact
                byte action = buffer.readByte();
                entityId = buffer.readUnsignedVarLong();
                replacementID = getReplacementIdForServer( entityId );

                byte[] data = new byte[buffer.getRemaining()];
                buffer.readBytes( data );

                if ( entityId != replacementID ) {
                    buffer = new PacketBuffer( 6 );
                    buffer.writeByte( action );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;

            case 0x1E: // Inventory transaction
                PacketInventoryTransaction inventoryTransaction = new PacketInventoryTransaction();
                inventoryTransaction.deserialize( buffer );

                // Check if the action is a entity based
                if ( inventoryTransaction.getType() == PacketInventoryTransaction.TYPE_USE_ITEM_ON_ENTITY ) {
                    inventoryTransaction.setEntityId( getReplacementIdForServer( inventoryTransaction.getEntityId() ) );
                    buffer = new PacketBuffer( 8 );
                    inventoryTransaction.serialize( buffer );
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;
        }

        return buffer;
    }

    public long removeEntity( long entityId ) {
        Long newEntity = this.rewriteIds.remove( entityId );
        if ( newEntity == null ) {
            return entityId;
        }

        this.serverRewriteIds.remove( newEntity );
        return newEntity;
    }

}
