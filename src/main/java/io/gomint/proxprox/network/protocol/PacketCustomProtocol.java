/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import lombok.Data;
import lombok.ToString;

/**
 * @author geNAZt
 * @version 1.0
 */
@ToString
@Data
public class PacketCustomProtocol extends Packet {

    private int mode;
    private int channel;

    // If mode is 0 (register)
    private String channelName;

    private byte[] data;

    public PacketCustomProtocol() {
        super( (byte) 0xFF );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeByte( (byte) this.mode );
        buffer.writeByte( (byte) this.channel );

        if ( this.mode == 0 ) {
            buffer.writeString( this.channelName );
        }

        if ( this.mode == 2 ) {
            buffer.writeShort( (short) this.data.length );
            buffer.writeBytes( this.data );
        }
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        this.mode = buffer.readByte();
        this.channel = buffer.readByte();

        // Data mode
        if ( this.mode == 2 ) {
            int dataLength = buffer.readShort();
            this.data = new byte[dataLength];
            buffer.readBytes( this.data );
        }
    }

}
