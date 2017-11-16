/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketAddItem extends Packet {

    private long entityId;

    private byte[] data;

    /**
     * Constructor for implemented Packet AddEntity
     */
    public PacketAddItem() {
        super( Protocol.ADD_ITEM_ENTITY );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeSignedVarLong( this.entityId );
        buffer.writeUnsignedVarLong( this.entityId );

        buffer.writeBytes( this.data );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        this.entityId = buffer.readSignedVarLong().longValue();
        buffer.readUnsignedVarLong();

        this.data = new byte[buffer.getRemaining()];
        buffer.readBytes( this.data );
    }

}
