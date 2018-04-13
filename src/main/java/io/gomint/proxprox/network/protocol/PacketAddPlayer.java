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
import lombok.Getter;

import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketAddPlayer extends Packet {

    private UUID uuid;
    private String name;

    private String thirdPartyName;
    private int platformID;

    private long entityId;

    private byte[] data;

    /**
     * Constructor for implemented Packet Add Player
     */
    public PacketAddPlayer() {
        super( Protocol.ADD_PLAYER_PACKET );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {
        buffer.writeUUID( this.uuid );
        buffer.writeString( this.name );

        buffer.writeString( this.thirdPartyName );
        buffer.writeSignedVarInt( this.platformID );

        buffer.writeSignedVarLong( this.entityId );
        buffer.writeUnsignedVarLong( this.entityId );

        buffer.writeBytes( this.data );
    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        // I only care for the entity id long
        this.uuid = buffer.readUUID();
        this.name = buffer.readString();

        this.thirdPartyName = buffer.readString();
        this.platformID = buffer.readSignedVarInt();

        this.entityId = buffer.readSignedVarLong().longValue();
        buffer.readUnsignedVarLong();

        this.data = new byte[buffer.getRemaining()];
        buffer.readBytes( this.data );
    }

}
