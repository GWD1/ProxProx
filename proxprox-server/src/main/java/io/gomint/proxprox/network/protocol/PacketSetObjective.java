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
public class PacketSetObjective extends Packet {

    private String displaySlot;
    private String objectiveName;
    private String displayName;
    private String criteriaName;
    private int sortOrder;

    /**
     * Create new packet
     */
    public PacketSetObjective() {
        super( Protocol.PACKET_SET_OBJECTIVE );
    }

    @Override
    public void serialize(PacketBuffer buffer, int protocolID ) {
        buffer.writeString( this.displaySlot );
        buffer.writeString( this.objectiveName );
        buffer.writeString( this.displayName );
        buffer.writeString( this.criteriaName );
        buffer.writeSignedVarInt( this.sortOrder );
    }

    @Override
    public void deserialize(PacketBuffer buffer, int protocolID ) {
        this.displaySlot = buffer.readString();
        this.objectiveName = buffer.readString();
        this.displayName = buffer.readString();
        this.criteriaName = buffer.readString();
        this.sortOrder = buffer.readSignedVarInt();
    }

}
