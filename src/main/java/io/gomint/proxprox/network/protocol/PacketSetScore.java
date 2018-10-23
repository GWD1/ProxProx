/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import lombok.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
@ToString
public class PacketSetScore extends Packet {

    private byte type;
    private List<ScoreEntry> entries;

    /**
     * Construct a new packet
     */
    public PacketSetScore() {
        super( Protocol.PACKET_SET_SCORE );
    }

    @Override
    public void serialize(PacketBuffer buffer, int protocolID ) {
        buffer.writeByte( this.type );
        buffer.writeUnsignedVarInt( this.entries.size() );

        for ( ScoreEntry entry : this.entries ) {
            buffer.writeSignedVarLong( entry.scoreId );
            buffer.writeString( entry.objective );
            buffer.writeLInt( entry.score );

            if ( this.type == 0 ) {
                buffer.writeByte( entry.entityType );
                switch ( entry.entityType ) {
                    case 3: // Fake entity
                        buffer.writeString( entry.fakeEntity );
                        break;
                    case 1:
                    case 2:
                        buffer.writeUnsignedVarLong( entry.entityId );
                        break;
                }
            }
        }
    }

    @Override
    public void deserialize(PacketBuffer buffer, int protocolID ) {
        this.type = buffer.readByte();
        ScoreEntry[] entries = new ScoreEntry[buffer.readUnsignedVarInt()];

        for ( int i = 0; i < entries.length; i++ ) {
            long scoreId = buffer.readSignedVarLong().longValue();
            String objective = buffer.readString();
            int score = buffer.readLInt();

            ScoreEntry scoreEntry = new ScoreEntry( scoreId, objective, score );

            if ( this.type == 0 ) {
                byte entityType = buffer.readByte();
                switch ( entityType ) {
                    case 3: // Fake entity
                        scoreEntry.fakeEntity = buffer.readString();
                        break;
                    case 1:
                    case 2:
                        scoreEntry.entityId = buffer.readUnsignedVarLong();
                        break;
                }
            }

            entries[i] = scoreEntry;
        }

        this.entries = Arrays.asList( entries );
    }

    @AllArgsConstructor
    @RequiredArgsConstructor
    @Getter
    @ToString
    public static class ScoreEntry {
        private final long scoreId;
        private final String objective;
        private final int score;

        // Add entity type
        private byte entityType;
        private String fakeEntity;
        private long entityId;
    }

}
