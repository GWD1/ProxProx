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

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketMobEffect extends Packet {

    // CHECKSTYLE:OFF
    public static final byte EVENT_ADD = 1;
    public static final byte EVENT_MODIFY = 2;
    public static final byte EVENT_REMOVE = 3;
    // CHECKSTYLE:ON

    private long entityId;
    private byte action;
    private int effectId;
    private int amplifier;
    private boolean visible;
    private int duration;

    /**
     * Construct a new packet
     */
    public PacketMobEffect() {
        super( Protocol.PACKET_MOB_EFFECT );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeUnsignedVarLong( this.entityId );
        buffer.writeByte( this.action );
        buffer.writeSignedVarInt( this.effectId );
        buffer.writeSignedVarInt( this.amplifier );
        buffer.writeBoolean( this.visible );
        buffer.writeSignedVarInt( this.duration );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.entityId = buffer.readUnsignedVarLong();
        this.action = buffer.readByte();
        this.effectId = buffer.readSignedVarInt();
        this.amplifier = buffer.readSignedVarInt();
        this.visible = buffer.readBoolean();
        this.duration = buffer.readSignedVarInt();
    }

}
