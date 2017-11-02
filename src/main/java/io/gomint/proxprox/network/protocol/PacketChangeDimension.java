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
import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
@Getter
public class PacketChangeDimension extends Packet {

    private byte dimension;
    private float x;
    private float y;
    private float z;
    private byte unknown;

    /**
     * Constructor for implemented Packet AddEntity
     *
     * @param dimension The dimension we want to travel to
     */
    public PacketChangeDimension( byte dimension ) {
        super( Protocol.CHANGE_DIMENSION_PACKET );
        this.dimension = dimension;
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeUnsignedVarInt( this.dimension );
        buffer.writeLFloat( this.x );
        buffer.writeLFloat( this.y );
        buffer.writeLFloat( this.z );
        buffer.writeByte( this.unknown );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {

    }

    @Override
    public boolean mustBeInBatch() {
        return false;
    }

}
