/*
 *  Copyright (c) 2018, GoMint, BlackyPaw and geNAZt
 *
 *  This code is licensed under the BSD license found in the
 *  LICENSE file in the root directory of this source tree.
 */
package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;

@Data
public class PacketSetLocalPlayerAsInitialized extends Packet {

    private long entityId;

    /**
     * Construct a new packet
     */
    public PacketSetLocalPlayerAsInitialized() {
        super( Protocol.PACKET_SET_LOCAL_PLAYER_INITIALIZED );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeUnsignedVarLong( this.entityId );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.entityId = buffer.readUnsignedVarLong();
    }

}
