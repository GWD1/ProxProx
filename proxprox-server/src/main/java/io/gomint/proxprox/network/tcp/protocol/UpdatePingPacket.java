package io.gomint.proxprox.network.tcp.protocol;

import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class UpdatePingPacket extends Packet {

    private int ping;

    public UpdatePingPacket() {
        super( (byte) 0x02 );
    }

    @Override
    public void write( ByteBuf buf ) {
        buf.writeInt( this.ping );
    }

    @Override
    public void read( ByteBuf buf ) {
        this.ping = buf.readInt();
    }

}
