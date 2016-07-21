/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.Protocol;
import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
@Getter
public class PacketDisconnect extends Packet {

    private String message;

    /**
     * Constructor for implemented Packets
     */
    public PacketDisconnect() {
        super( Protocol.DISONNECT_PACKET );
    }

    public PacketDisconnect( String message ) {
        super( Protocol.DISONNECT_PACKET );
        this.message = message;
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeString( this.message );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        this.message = buffer.readString();
    }

}
