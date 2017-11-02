package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import io.gomint.proxprox.network.protocol.type.ResourceResponseStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
@EqualsAndHashCode( callSuper = false )
public class PacketResourcePackResponse extends Packet {

    private ResourceResponseStatus status;
    private Map<String, String> info;

    public PacketResourcePackResponse() {
        super( Protocol.PACKET_RESOURCEPACK_RESPONSE );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeByte( this.status.getId() );
        buffer.writeLShort( (short) this.info.size() );
        for ( Map.Entry<String, String> entry : this.info.entrySet() ) {
            buffer.writeString( entry.getKey() );
            buffer.writeString( entry.getValue() );
        }
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        this.status = ResourceResponseStatus.valueOf( buffer.readByte() );
        this.info = new HashMap<>();

        int count = buffer.readLShort();
        for ( int i = 0; i < count; i++ ) {
            this.info.put( buffer.readString(), buffer.readString() );
        }
    }

}
