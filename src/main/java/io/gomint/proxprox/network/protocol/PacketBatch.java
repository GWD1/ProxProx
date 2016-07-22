/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.Protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@Data
@EqualsAndHashCode( callSuper = false )
public class PacketBatch extends Packet {

	private byte[] payload;

	public PacketBatch() {
		super( Protocol.BATCH_PACKET );
	}

	@Override
	public void serialize( PacketBuffer buffer ) {
        buffer.writeInt( this.payload.length );
        buffer.writeBytes( this.payload );
	}

	@Override
	public void deserialize( PacketBuffer buffer ) {
		int length = buffer.readInt();
		this.payload = new byte[length];
		buffer.readBytes( this.payload );
	}

	@Override
	public int estimateLength() {
		return 4 + this.payload.length;
	}

}