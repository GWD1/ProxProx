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
public class PacketFullChunkData extends Packet {

    private int chunkX;
    private int chunkZ;
    private byte[] chunkData;

    /**
     * Constructor for implemented Packets
     */
    public PacketFullChunkData() {
        super( Protocol.CHUNK_DATA_PACKET );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeSignedVarInt( this.chunkX );
        buffer.writeSignedVarInt( this.chunkZ );
        buffer.writeUnsignedVarInt( this.chunkData.length );
        buffer.writeBytes( this.chunkData );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {

    }

}
