package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketSetScoreboardIdentity extends Packet {

    private byte type;
    private List<ScoreboardIdentity> entries;

    /**
     * Construct a new packet
     */
    protected PacketSetScoreboardIdentity() {
        super( Protocol.PACKET_SET_SCOREBOARD_IDENTITY );
    }

    @Override
    public void serialize(PacketBuffer buffer, int protocolID ) {
        buffer.writeByte( this.type );
        buffer.writeUnsignedVarInt( this.entries.size() );

        for ( ScoreboardIdentity entry : this.entries ) {
            buffer.writeUnsignedVarLong( entry.scoreId );

            if ( this.type == 0 ) {
                buffer.writeUnsignedVarLong( entry.entityId );
            }
        }
    }

    @Override
    public void deserialize(PacketBuffer buffer, int protocolID ) {
        this.type = buffer.readByte();

        ScoreboardIdentity[] identities = new ScoreboardIdentity[buffer.readUnsignedVarInt()];

        for ( int i = 0; i < identities.length; i++ ) {
            ScoreboardIdentity identity = new ScoreboardIdentity( buffer.readSignedVarLong().longValue() );

            if ( this.type == 0 ) {
                identity.entityId = buffer.readUnsignedVarLong();
            }
        }

        this.entries = Arrays.asList( identities );
    }

    @RequiredArgsConstructor
    @Getter
    public static class ScoreboardIdentity {
        private final long scoreId;
        private long entityId;
    }

}
