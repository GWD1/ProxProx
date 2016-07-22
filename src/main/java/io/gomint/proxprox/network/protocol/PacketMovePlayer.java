/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketMovePlayer extends Packet {

    private long entityId;
    private float x;
    private float y;
    private float z;
    private float yaw;
    private float headYaw;
    private float pitch;
    private byte mode;
    private boolean onGround;

    /**
     * At this point i am too lazy to document every little shit
     */
    public PacketMovePlayer() {
        super( Protocol.MOVE_PLAYER_PACKET );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeLong( this.entityId );
        buffer.writeFloat( this.x );
        buffer.writeFloat( this.y );
        buffer.writeFloat( this.z );
        buffer.writeFloat( this.yaw );
        buffer.writeFloat( this.headYaw );
        buffer.writeFloat( this.pitch );
        buffer.writeByte( this.mode );
        buffer.writeBoolean( this.onGround );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        // Maybe we don't need this
    }

}
