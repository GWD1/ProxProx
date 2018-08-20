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
public class PacketStartGame extends Packet {

    // Entity data
    private long runtimeEntityId;
    private float spawnX;
    private float spawnY;
    private float spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    private int gamemode;
    private int worldGamemode;
    private int difficulty;

    public PacketStartGame() {
        super( Protocol.PACKET_START_GAME );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {

    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        // We only need the runtime id
        buffer.readSignedVarLong();
        this.runtimeEntityId = buffer.readUnsignedVarLong();

        this.gamemode = buffer.readSignedVarInt();

        this.spawnX = buffer.readLFloat();
        this.spawnY = buffer.readLFloat();
        this.spawnZ = buffer.readLFloat();
        this.spawnYaw = buffer.readLFloat();
        this.spawnPitch = buffer.readLFloat();

        buffer.readSignedVarInt();
        buffer.readSignedVarInt();
        buffer.readSignedVarInt();

        this.worldGamemode = buffer.readSignedVarInt();
        this.difficulty = buffer.readSignedVarInt();
    }

}
