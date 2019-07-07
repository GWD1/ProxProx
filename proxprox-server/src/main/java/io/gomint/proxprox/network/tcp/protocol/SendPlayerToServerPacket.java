package io.gomint.proxprox.network.tcp.protocol;

import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class SendPlayerToServerPacket extends Packet {

    private String host;
    private int port;

    public SendPlayerToServerPacket() {
        super( (byte) 0x03 );
    }

    @Override
    public void write( ByteBuf buf ) {
        buf.writeInt( this.host.length() );
        buf.writeBytes( this.host.getBytes( StandardCharsets.UTF_8 ) );
        buf.writeInt( this.port );
    }

    @Override
    public void read( ByteBuf buf ) {
        int hostLength = buf.readInt();
        byte[] hostData = new byte[hostLength];
        buf.readBytes( hostData );
        this.host = new String( hostData, StandardCharsets.UTF_8 );
        this.port = buf.readInt();
    }

}
