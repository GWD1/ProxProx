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
import io.gomint.proxprox.network.type.metadata.MetadataContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@Data
@EqualsAndHashCode( callSuper = false )
public class PacketEntityMetadata extends Packet {

    private long entityId;
    private MetadataContainer metadata;

    public PacketEntityMetadata() {
        super( Protocol.PACKET_ENTITY_METADATA );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeUnsignedVarLong( this.entityId );
        this.metadata.serialize( buffer );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.entityId = buffer.readUnsignedVarLong();
        this.metadata = new MetadataContainer();
        this.metadata.deserialize( buffer );
    }

}
