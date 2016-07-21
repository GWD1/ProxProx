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
public class PacketText extends Packet {

	public enum Type {

		/**
		 * Type value for unformatted messages.
		 */
		CLIENT_MESSAGE( (byte) 0 ),

		/**
		 * Type value for usual player chat.
		 */
		PLAYER_CHAT( (byte) 1 ),

		/**
		 * Type value for localizable messages included in Minecraft's language files.
		 */
		LOCALIZABLE_MESSAGE( (byte) 2 ),

		/**
		 * Type value for displaying text right above a player's action bar.
		 */
		POPUP_NOTICE( (byte) 3 ),

		/**
		 * Type value for displaying text slightly below the center of the screen (similar to title
		 * text of PC edition).
		 */
		TIP_MESSAGE( (byte) 4 ),

		/**
		 * Type value for unformatted messages. Actual use unknown, same as system, apparently.
		 */
		SYSTEM_MESSAGE( (byte) 5 );

		private final byte id;

		Type( byte id ) {
			this.id = id;
		}

		/**
		 * Get MC:PE magic number
		 *
		 * @return MC:PE magic number
         */
		public byte getId() {
			return this.id;
		}

		/**
		 * Get the enum constant for the Type of message this packet contains
		 *
		 * @param id The MC:PE magic id
         * @return Enum Constant
         */
		public static Type getById( byte id ) {
			switch ( id ) {
				case 0:
					return CLIENT_MESSAGE;
				case 1:
					return PLAYER_CHAT;
				case 2:
					return LOCALIZABLE_MESSAGE;
				case 3:
					return POPUP_NOTICE;
				case 4:
					return TIP_MESSAGE;
				case 5:
					return SYSTEM_MESSAGE;
				default:
					return null;
			}
		}
	}

	private Type type;
	private String sender;
	private String message;
	private String[] arguments;

	public PacketText() {
		super( Protocol.TEXT_PACKET );
	}

	/**
	 * Shorthand constructor for PLAYER_CHAT messages.
	 *
	 * @param sender The sender of the chat message
	 * @param message The actual chat message
	 */
	public PacketText( String sender, String message ) {
		this();
		this.type = Type.PLAYER_CHAT;
		this.sender = sender;
		this.message = message;
	}

	public void setSubtitle( String subtitle ) {
		this.sender = subtitle;
	}

	public String getSubtitle() {
		return this.sender;
	}

	@Override
	public void serialize( PacketBuffer buffer ) {
		buffer.writeByte( this.type.getId() );
		switch ( this.type ) {
			case CLIENT_MESSAGE:
			case TIP_MESSAGE:
			case SYSTEM_MESSAGE:
				buffer.writeString( this.message );
				break;

			case PLAYER_CHAT:
				buffer.writeString( this.sender );
				buffer.writeString( this.message );
				break;

			case LOCALIZABLE_MESSAGE:
				buffer.writeString( this.message );
				buffer.writeByte( (byte) this.arguments.length );
				for ( int i = 0; i < this.arguments.length; ++i ) {
					buffer.writeString( this.arguments[i] );
				}
				break;

			case POPUP_NOTICE:
				buffer.writeString( this.message );
				buffer.writeString( this.sender );
				break;
		}
	}

	@Override
	public void deserialize( PacketBuffer buffer ) {
		this.type = Type.getById( buffer.readByte() );
		switch ( this.type ) {
			case CLIENT_MESSAGE:
			case TIP_MESSAGE:
			case SYSTEM_MESSAGE:
				this.message = buffer.readString();
				break;

			case PLAYER_CHAT:
				this.sender = buffer.readString();
				this.message = buffer.readString();
				break;

			case LOCALIZABLE_MESSAGE:
				this.message = buffer.readString();
				byte count = buffer.readByte();
				this.arguments = new String[count];
				for ( byte i = 0; i < count; ++i ) {
					arguments[i] = buffer.readString();
				}
				break;

			case POPUP_NOTICE:
				this.message = buffer.readString();
				this.sender = buffer.readString();
				break;
		}
	}
}
