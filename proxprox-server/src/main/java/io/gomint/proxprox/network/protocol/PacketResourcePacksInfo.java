package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;

import io.gomint.proxprox.network.Protocol;
import io.gomint.proxprox.network.protocol.type.PackIdVersion;
import io.gomint.proxprox.network.protocol.type.ResourcePack;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketResourcePacksInfo extends Packet {

    private boolean mustAccept;
    private boolean hasScripts = false;
    private List<ResourcePack> behaviourPackEntries;
    private List<ResourcePack> resourcePackEntries;

    public PacketResourcePacksInfo() {
        super( Protocol.PACKET_RESOURCEPACK_INFO );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeBoolean( this.mustAccept );
        buffer.writeBoolean( this.hasScripts );

        buffer.writeLShort( (short) ( this.behaviourPackEntries == null ? 0 : this.behaviourPackEntries.size() ) );
        if ( this.behaviourPackEntries != null ) {
            for ( ResourcePack entry : this.behaviourPackEntries ) {
                buffer.writeString( entry.getVersion().getId().toString() );
                buffer.writeString( entry.getVersion().getVersion() );
                buffer.writeLLong( entry.getSize() );
                buffer.writeString( "" );
                buffer.writeString( "" );
                buffer.writeString( "" );
                buffer.writeBoolean(false);
            }
        }

        buffer.writeLShort( (short) ( this.resourcePackEntries == null ? 0 : this.resourcePackEntries.size() ) );
        if ( this.resourcePackEntries != null ) {
            for ( ResourcePack entry : this.resourcePackEntries ) {
                buffer.writeString( entry.getVersion().getId().toString() );
                buffer.writeString( entry.getVersion().getVersion() );
                buffer.writeLLong( entry.getSize() );
                buffer.writeString( "" );
                buffer.writeString( "" );
                buffer.writeString( "" );
                buffer.writeBoolean(false);
            }
        }
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.mustAccept = buffer.readBoolean();
        this.hasScripts = buffer.readBoolean();

        short behaviourPackLength = buffer.readLShort();
        if ( behaviourPackLength > 0 ) {
            this.behaviourPackEntries = new ArrayList<>();

            for ( short i = 0; i < behaviourPackLength; i++ ) {
                PackIdVersion version = new PackIdVersion( UUID.fromString( buffer.readString() ), buffer.readString() );
                ResourcePack pack = new ResourcePack( version, buffer.readLLong() );
                this.behaviourPackEntries.add( pack );
                buffer.readString();
                buffer.readString();
                buffer.readBoolean();
            }
        }

        short resourcePackLength = buffer.readLShort();
        if ( resourcePackLength > 0 ) {
            this.resourcePackEntries = new ArrayList<>();

            for ( short i = 0; i < resourcePackLength; i++ ) {
                PackIdVersion version = new PackIdVersion( UUID.fromString( buffer.readString() ), buffer.readString() );
                ResourcePack pack = new ResourcePack( version, buffer.readLLong() );
                this.resourcePackEntries.add( pack );
                buffer.readString();
                buffer.readString();
                buffer.readBoolean();
            }
        }
    }

}
