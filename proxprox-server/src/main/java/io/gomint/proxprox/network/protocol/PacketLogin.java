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
public class PacketLogin extends Packet {

    private int protocol;
    private byte[] payload;

    public PacketLogin() {
        super( Protocol.PACKET_LOGIN );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeInt( this.protocol );
        buffer.writeUnsignedVarInt( this.payload.length );
        buffer.writeBytes( this.payload );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.protocol = buffer.readInt();
        this.payload = new byte[buffer.readUnsignedVarInt()];
        buffer.readBytes( this.payload );
    }

}