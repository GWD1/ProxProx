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
import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
@Getter
public class PacketAddPlayer extends Packet {

    private long entityId;

    /**
     * Constructor for implemented Packet Add Player
     */
    public PacketAddPlayer() {
        super( Protocol.ADD_PLAYER_PACKET );
    }

    @Override
    public void serialize( PacketBuffer buffer ) {

    }

    @Override
    public void deserialize( PacketBuffer buffer ) {
        // I only care for the entity id long
        buffer.readUUID();
        buffer.readString();
        this.entityId = buffer.readSignedVarLong().longValue();
    }

}
