package io.gomint.proxprox.util;

import io.gomint.jraknet.PacketBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
public class EntityRewriter {

    private static final Logger LOGGER = LoggerFactory.getLogger( EntityRewriter.class );

    @Getter
    private final long ownId;
    @Getter
    @Setter
    private long currentDownStreamId;

    public long addEntity( long entityID ) {
        // This happens only on server to client connections
        return this.getReplacementId( entityID );
    }

    public PacketBuffer rewriteServerToClient( byte packetId, int pos, PacketBuffer buffer ) {
        // Entity ID rewrites
        long entityId;
        switch ( packetId ) {
            case 0x28:
            case 0x1f:
            case 0x20:
            case 0x13:  // Move player
            case 0x1B:  // Entity event
            case 0x27:  // Entity metadata
            case 0x1D:  // Update attributes
                entityId = buffer.readUnsignedVarLong();
                long replacementID = getReplacementId( entityId );

                LOGGER.debug( "Rewriting " + Integer.toHexString( packetId & 0xFF ) + " with entity ID " + replacementID );
                if ( entityId != replacementID ) {
                    byte[] data = new byte[buffer.getRemaining()];
                    buffer.readBytes( data );

                    buffer = new PacketBuffer( 64 );
                    buffer.writeUnsignedVarLong( replacementID );
                    buffer.writeBytes( data );
                    buffer.resetPosition();
                } else {
                    buffer.setPosition( pos );
                }

                break;
        }

        return buffer;
    }

    private long getReplacementId( long entityId ) {
        LOGGER.debug( "Rewrite entity id: " + entityId + " " + this.ownId + " " + this.currentDownStreamId );

        // Check if the ID is either our own from up or down stream
        if ( entityId == this.currentDownStreamId ) {
            return this.ownId;
        } else if ( entityId == this.ownId ) {
            return this.currentDownStreamId;
        }

        return entityId;
    }

    private long getReplacementIdForServer( long entityId ) {
        if ( entityId == this.ownId ) {
            return this.currentDownStreamId;
        }

        return entityId;
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
        }

        return buffer;
    }

    public long removeEntity( long entityId ) {
        return this.getReplacementId( entityId );
    }

}
