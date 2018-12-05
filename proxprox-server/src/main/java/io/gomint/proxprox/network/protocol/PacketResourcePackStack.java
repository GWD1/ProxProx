package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import io.gomint.proxprox.network.protocol.type.ResourcePack;
import lombok.Data;

import java.util.List;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketResourcePackStack extends Packet {

    private boolean mustAccept;
    private List<ResourcePack> behaviourPackEntries;
    private List<ResourcePack> resourcePackEntries;

    public PacketResourcePackStack() {
        super( Protocol.PACKET_RESOURCEPACK_STACK );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeBoolean( this.mustAccept );

        buffer.writeUnsignedVarInt( ( this.behaviourPackEntries == null ? 0 : this.behaviourPackEntries.size() ) );
        if ( this.behaviourPackEntries != null ) {
            for ( ResourcePack entry : this.behaviourPackEntries ) {
                buffer.writeString( entry.getVersion().getId().toString() );
                buffer.writeString( entry.getVersion().getVersion() );
                buffer.writeString( "" );
            }
        }

        buffer.writeUnsignedVarInt( (short) ( this.resourcePackEntries == null ? 0 : this.resourcePackEntries.size() ) );
        if ( this.resourcePackEntries != null ) {
            for ( ResourcePack entry : this.resourcePackEntries ) {
                buffer.writeString( entry.getVersion().getId().toString() );
                buffer.writeString( entry.getVersion().getVersion() );
                buffer.writeString( "" );
            }
        }
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {

    }

}
