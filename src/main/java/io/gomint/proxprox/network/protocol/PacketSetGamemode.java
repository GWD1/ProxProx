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
public class PacketSetGamemode extends Packet {

    private int gameMode;

    public PacketSetGamemode() {
        super( Protocol.PACKET_SET_GAMEMODE );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeSignedVarInt( this.gameMode );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {

    }

}
