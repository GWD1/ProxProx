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
 * @author BlackyPaw
 * @version 1.0
 */
@Data
public class PacketPlayState extends Packet {

	/**
	 * Enumeration of play states observed to be sent inside certain packets.
	 */
	public enum PlayState {

		/**
		 * Login success
         */
		LOGIN_SUCCESS( 0 ),

		/**
		 * Login failed due to client issues
         */
		LOGIN_FAILED_CLIENT( 1 ),

		/**
		 * Login failed due to server issues
         */
		LOGIN_FAILED_SERVER( 2 ),

		/**
		 * Player should be spawned
         */
		SPAWN( 3 );

		private int value;

		/**
		 * New constant
		 *
		 * @param value MC:PE magic of the constant
         */
		PlayState( int value ) {
			this.value = value;
		}

		/**
		 * MC:PE Magic representation of the state
		 *
		 * @return Integer representation of the state magic
         */
		public int getValue() {
			return this.value;
		}

		/**
		 * Get the enum value for the State this packet wants to set
		 *
		 * @param value Integer from the packet (MC:PE magic value)
         * @return Enum Constant for the MC:PE Magic
         */
		public static PlayState fromValue( int value ) {
			switch ( value ) {
				case 0:
					return LOGIN_SUCCESS;
				case 1:
					return LOGIN_FAILED_CLIENT;
				case 2:
					return LOGIN_FAILED_SERVER;
				case 3:
					return SPAWN;
				default:
					return null;
			}
		}

	}

	private PlayState state;

	/**
	 * Construct new PlayState packet which has been sent from the Downstream server
     */
	public PacketPlayState() {
		super( Protocol.PACKET_PLAY_STATE );
	}

	/**
	 * Construct new PlayState packet
	 *
	 * @param state The state this packet should set
     */
	public PacketPlayState( PlayState state ) {
		super( Protocol.PACKET_PLAY_STATE );
		this.state = state;
	}

	@Override
	public void serialize( PacketBuffer buffer, int protocolVersion ) {
		buffer.writeInt( this.state.getValue() );
	}

	@Override
	public void deserialize( PacketBuffer buffer, int protocolVersion ) {
		this.state = PlayState.fromValue( buffer.readInt() );
	}

	@Override
	public int estimateLength() {
		return 4;
	}

}