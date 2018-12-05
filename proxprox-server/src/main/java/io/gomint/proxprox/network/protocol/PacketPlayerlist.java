package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.entity.PlayerSkin;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketPlayerlist extends Packet {

    private byte mode;
    private List<Entry> entries;

    public PacketPlayerlist() {
        super( Protocol.PACKET_PLAYER_LIST );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolID ) {
        buffer.writeByte( this.mode );
        buffer.writeUnsignedVarInt( this.entries.size() );

        if ( this.mode == 0 ) {
            for ( Entry entry : this.entries ) {
                buffer.writeUUID( entry.getUuid() );
                buffer.writeSignedVarLong( entry.getEntityId() );
                buffer.writeString( entry.getName() );

                buffer.writeString( entry.getSkin().getName() );

                // Raw skin data
                buffer.writeUnsignedVarInt( entry.getSkin().getData().length );
                buffer.writeBytes( entry.getSkin().getData() );

                // Cape data
                if ( entry.skin.getCapeData() != null ) {
                    buffer.writeUnsignedVarInt( entry.skin.getCapeData().length );
                    buffer.writeBytes( entry.skin.getCapeData() );
                } else {
                    buffer.writeUnsignedVarInt( 0 );
                }

                // Geometry name
                buffer.writeString( entry.skin.getGeometryName() );

                // Geometry data
                buffer.writeUnsignedVarInt( entry.skin.getGeometryData().length );
                buffer.writeBytes( entry.skin.getGeometryData() );

                // xbox user id
                buffer.writeString( entry.xboxId );

                // TODO: Is this the same as the unknown one in SpawnPlayer?
                buffer.writeString( entry.uuid.toString() );
            }
        } else {
            for ( Entry entry : this.entries ) {
                buffer.writeUUID( entry.getUuid() );
            }
        }
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolID ) {
        this.mode = buffer.readByte();
        Entry[] entries = new Entry[buffer.readUnsignedVarInt()];

        if ( this.mode == 0 ) {
            for ( int i = 0; i < entries.length; i++ ) {
                Entry entry = new Entry( buffer.readUUID() );

                entry.entityId = buffer.readSignedVarLong().longValue();
                entry.name = buffer.readString();

                String skinName = buffer.readString();
                byte[] skinData = new byte[buffer.readUnsignedVarInt()];
                buffer.readBytes( skinData );

                byte[] capeData = new byte[buffer.readUnsignedVarInt()];
                if ( capeData.length > 0 ) {
                    buffer.readBytes( capeData );
                }

                String geometryName = buffer.readString();
                byte[] geometryData = new byte[buffer.readUnsignedVarInt()];
                buffer.readBytes( geometryData );

                entry.skin = new PlayerSkin( skinName, skinData, capeData, geometryName, geometryData );

                entry.xboxId = buffer.readString();

                // Read out the unknown one
                buffer.readString();

                entries[i] = entry;
            }
        } else {
            for ( int i = 0; i < entries.length; i++ ) {
                entries[i] = new Entry( buffer.readUUID() );
            }
        }

        this.entries = Arrays.asList( entries );
    }

    @Data
    @AllArgsConstructor
    @RequiredArgsConstructor
    public static class Entry {
        private final UUID uuid;
        private long entityId = 0;
        private String name = "";
        private String xboxId = "";
        private PlayerSkin skin = null;
    }

}
