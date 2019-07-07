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
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
@EqualsAndHashCode( callSuper = false )
public class PacketSetChunkRadius extends Packet {

    private int chunkRadius;

    public PacketSetChunkRadius() {
        super( Protocol.PACKET_SET_CHUNK_RADIUS );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeSignedVarInt( this.chunkRadius );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.chunkRadius = buffer.readSignedVarInt();
    }

}
